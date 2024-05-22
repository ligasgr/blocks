package blocks.https;

import org.apache.pekko.NotUsed;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.http.javadsl.HttpsConnectionContext;
import org.apache.pekko.http.javadsl.ServerBinding;
import org.apache.pekko.http.javadsl.ServerBuilder;
import org.apache.pekko.http.javadsl.model.HttpRequest;
import org.apache.pekko.http.javadsl.model.HttpResponse;
import org.apache.pekko.http.javadsl.settings.ServerSettings;
import org.apache.pekko.http.javadsl.settings.WebSocketSettings;
import org.apache.pekko.stream.javadsl.Flow;
import org.apache.pekko.util.ByteString;
import blocks.service.AbstractBlock;
import blocks.service.BlockContext;
import blocks.service.BlockRef;
import blocks.service.FutureUtils;
import blocks.service.SecretsConfig;
import org.slf4j.Logger;

import java.security.KeyStore;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class HttpsBlock extends AbstractBlock<ServerBinding> {
    private static final Duration HARD_TERMINATION_DEADLINE = Duration.ofSeconds(10);
    private final BlockRef<KeyStore> keyStoreBlockRef;
    private final String privateKeyPasswordKey;
    private final BlockRef<SecretsConfig> secretsConfigBlockRef;

    public HttpsBlock(final BlockRef<KeyStore> keyStoreBlockRef, final String privateKeyPasswordKey, final BlockRef<SecretsConfig> secretsConfigBlockRef) {
        this.keyStoreBlockRef = keyStoreBlockRef;
        this.privateKeyPasswordKey = privateKeyPasswordKey;
        this.secretsConfigBlockRef = secretsConfigBlockRef;
    }

    @Override
    protected CompletableFuture<ServerBinding> getBlockOutputFuture(final BlockContext blockContext) {
        KeyStore httpsKeystore = blockContext.getBlockOutput(keyStoreBlockRef);
        SecretsConfig secretsConfig = blockContext.getBlockOutput(secretsConfigBlockRef);
        final char[] privateKeyPassword = secretsConfig.getSecret(privateKeyPasswordKey).toCharArray();
        org.apache.pekko.actor.typed.ActorSystem<Void> system = blockContext.context.getSystem();
        ServerSettings settings = customServerSettings(system.classicSystem());
        Optional<Integer> httpsPort = blockContext.config.getHttpsPort();
        if (httpsPort.isPresent()) {
            String host = blockContext.config.getHost();
            HttpsConnectionContext httpsContext = HttpsContextUtil.createHttpsContext(httpsKeystore, privateKeyPassword);
            Integer port = httpsPort.get();
            ServerBuilder serverBuilder = blockContext.http.newServerAt(host, port).enableHttps(httpsContext).withSettings(settings);
            Flow<HttpRequest, HttpResponse, NotUsed> flow = blockContext.route.flow(system);
            Logger log = blockContext.context.getLog();
            return serverBuilder.bindFlow(flow).toCompletableFuture().thenApply(sb -> {
                httpsBoundBanner(log, blockContext.env, host, port, blockContext.clock, blockContext.startInstant);
                sb.addToCoordinatedShutdown(HARD_TERMINATION_DEADLINE, system);
                return sb;
            });
        } else {
            return FutureUtils.failed(new IllegalArgumentException("Missing configuration of app.httpsPort"));
        }
    }

    @Override
    public List<ServerBinding> serverBindings() {
        return getBlockOutput().map(Collections::singletonList).orElse(Collections.emptyList());
    }

    private ServerSettings customServerSettings(ActorSystem system) {
        ServerSettings defaultSettings = ServerSettings.create(system);
        WebSocketSettings customWebSocketSettings = defaultSettings.getWebsocketSettings().withPeriodicKeepAliveData(() -> ByteString.fromString("{}"));
        return defaultSettings.withWebsocketSettings(customWebSocketSettings);
    }

    private void httpsBoundBanner(final Logger log, final String env, final String host, final Integer port, final Clock clock, final Instant startInstant) {
        log.info("-----------------------------------------");
        log.info("Https port bound for server:");
        log.info("env: {}", env);
        log.info("host: {}", host);
        log.info("httpsPort: {}", port);
        log.info("in: {}ms from start", clock.millis() - startInstant.toEpochMilli());
        log.info("-----------------------------------------");
    }

    @Override
    public boolean isMandatory() {
        return true;
    }
}
