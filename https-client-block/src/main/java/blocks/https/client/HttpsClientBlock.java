package blocks.https.client;

import akka.http.javadsl.Http;
import akka.http.javadsl.HttpsConnectionContext;
import blocks.service.AbstractBlock;
import blocks.service.BlockContext;
import blocks.service.BlockRef;
import blocks.service.SecretsConfig;

import java.security.KeyStore;
import java.util.concurrent.CompletableFuture;

public class HttpsClientBlock extends AbstractBlock<Http> {
    private final BlockRef<KeyStore> keyStoreBlockRef;
    private final String privateKeyPasswordKey;
    private final BlockRef<SecretsConfig> secretsConfigBlockRef;

    public HttpsClientBlock(final BlockRef<KeyStore> keyStoreBlockRef, final String privateKeyPasswordKey, final BlockRef<SecretsConfig> secretsConfigBlockRef) {
        this.keyStoreBlockRef = keyStoreBlockRef;
        this.privateKeyPasswordKey = privateKeyPasswordKey;
        this.secretsConfigBlockRef = secretsConfigBlockRef;
    }

    @Override
    protected CompletableFuture<Http> getBlockOutputFuture(final BlockContext blockContext) {
        KeyStore httpsKeystore = blockContext.getBlockOutput(keyStoreBlockRef);
        SecretsConfig secretsConfig = blockContext.getBlockOutput(secretsConfigBlockRef);
        final char[] privateKeyPassword = secretsConfig.getSecret(privateKeyPasswordKey).toCharArray();
        akka.actor.typed.ActorSystem<Void> system = blockContext.context.getSystem();
        HttpsConnectionContext httpsContext = HttpsContextUtil.createHttpsContext(httpsKeystore, privateKeyPassword);
        Http http = Http.get(system);
        http.setDefaultClientHttpsContext(httpsContext);
        return CompletableFuture.completedFuture(http);
    }

    @Override
    public boolean isMandatory() {
        return true;
    }
}
