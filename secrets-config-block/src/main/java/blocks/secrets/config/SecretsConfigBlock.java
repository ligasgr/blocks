package blocks.secrets.config;

import blocks.service.AbstractBlock;
import blocks.service.BlockContext;
import blocks.service.FutureUtils;
import blocks.service.SecretsConfig;

import java.util.concurrent.CompletableFuture;

public class SecretsConfigBlock extends AbstractBlock<SecretsConfig> {
    @Override
    public boolean isMandatory() {
        return true;
    }

    @Override
    protected CompletableFuture<SecretsConfig> getBlockOutputFuture(final BlockContext blockContext) {
        return FutureUtils.futureOnDefaultDispatcher(blockContext.context, () -> new TypesafeSecretsConfig(blockContext.env));
    }
}
