package blocks.https.client;

import org.apache.pekko.http.javadsl.Http;
import org.apache.pekko.http.javadsl.HttpsConnectionContext;
import blocks.service.AbstractBlock;
import blocks.service.BlockContext;
import blocks.service.BlockRef;
import blocks.service.SecretsConfig;

import java.security.KeyStore;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public class HttpsClientBlock extends AbstractBlock<Http> {

    private final BlockRef<KeyStore> keyStoreBlockRef;
    private final String privateKeyPasswordKey;
    private final BlockRef<SecretsConfig> secretsConfigBlockRef;
    private final Optional<HttpsConnectionContext> maybeHttpsConnectionContext;

    public HttpsClientBlock(final BlockRef<KeyStore> keyStoreBlockRef,
                            final String privateKeyPasswordKey,
                            final BlockRef<SecretsConfig> secretsConfigBlockRef,
                            final HttpsConnectionContext httpsConnectionContext) {
        this.keyStoreBlockRef = keyStoreBlockRef;
        this.privateKeyPasswordKey = privateKeyPasswordKey;
        this.secretsConfigBlockRef = secretsConfigBlockRef;
        this.maybeHttpsConnectionContext = Optional.ofNullable(httpsConnectionContext);
    }

    public HttpsClientBlock(final BlockRef<KeyStore> keyStoreBlockRef,
                            final String privateKeyPasswordKey,
                            final BlockRef<SecretsConfig> secretsConfigBlockRef) {
        this(keyStoreBlockRef, privateKeyPasswordKey, secretsConfigBlockRef, null);
    }

    @Override
    protected CompletableFuture<Http> getBlockOutputFuture(final BlockContext blockContext) {
        final KeyStore httpsKeystore = blockContext.getBlockOutput(keyStoreBlockRef);
        final SecretsConfig secretsConfig = blockContext.getBlockOutput(secretsConfigBlockRef);
        final char[] privateKeyPassword = secretsConfig.getSecret(privateKeyPasswordKey).toCharArray();
        final org.apache.pekko.actor.typed.ActorSystem<Void> system = blockContext.context.getSystem();
        final HttpsConnectionContext httpsContext = maybeHttpsConnectionContext.orElseGet(() -> HttpsContextUtil.createHttpsContext(httpsKeystore, privateKeyPassword));
        final Http http = Http.get(system);
        http.setDefaultClientHttpsContext(httpsContext);
        return CompletableFuture.completedFuture(http);
    }

    @Override
    public boolean isMandatory() {
        return true;
    }
}
