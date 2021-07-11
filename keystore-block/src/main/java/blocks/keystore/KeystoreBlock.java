package blocks.keystore;

import blocks.secrets.config.SecretsConfig;
import blocks.service.AbstractBlock;
import blocks.service.BlockContext;
import blocks.service.BlockRef;
import blocks.service.FutureUtils;

import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.util.concurrent.CompletableFuture;

public class KeystoreBlock extends AbstractBlock<KeyStore> {
    private final String type;
    private final String extension;
    private final String keystorePasswordKey;
    private final BlockRef<SecretsConfig> secretsConfigBlockRef;

    public KeystoreBlock(final String type, final String extension, final String keystorePasswordKey, final BlockRef<SecretsConfig> secretsConfigBlockRef) {
        this.type = type;
        this.extension = extension;
        this.keystorePasswordKey = keystorePasswordKey;
        this.secretsConfigBlockRef = secretsConfigBlockRef;
    }

    @Override
    protected CompletableFuture<KeyStore> getBlockOutputFuture(final BlockContext blockContext) {
        return FutureUtils.futureOnDefaultDispatcher(blockContext.context, () -> {
            SecretsConfig secretsConfig = blockContext.getBlockOutput(secretsConfigBlockRef);
            final char[] password = secretsConfig.getSecret(keystorePasswordKey).toCharArray();
            String keystoreName = "keys/" + blockContext.env + "." + extension;
            try (final InputStream keystore = KeystoreBlock.class.getClassLoader().getResourceAsStream(keystoreName)) {
                final KeyStore ks = KeyStore.getInstance(type);
                if (keystore == null) {
                    throw new IllegalArgumentException(keystoreName + " keystore was missing");
                }
                ks.load(keystore, password);
                return ks;
            } catch (IOException | KeyStoreException e) {
                throw new IllegalStateException("Failed to initialize keystore " + keystoreName);
            }
        });
    }

    @Override
    public boolean isMandatory() {
        return true;
    }
}
