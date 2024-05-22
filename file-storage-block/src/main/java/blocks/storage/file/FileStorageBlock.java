package blocks.storage.file;

import org.apache.pekko.stream.IOResult;
import org.apache.pekko.stream.javadsl.FileIO;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.stream.javadsl.Source;
import org.apache.pekko.util.ByteString;
import blocks.service.AbstractBlock;
import blocks.service.BlockConfig;
import blocks.service.BlockContext;
import blocks.service.FutureUtils;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class FileStorageBlock extends AbstractBlock<Storage> {
    private final String blockConfigPath;

    public FileStorageBlock(final String blockConfigPath) {
        this.blockConfigPath = blockConfigPath;
    }

    @Override
    protected CompletableFuture<Storage> getBlockOutputFuture(final BlockContext blockContext) {
        BlockConfig blockConfig = blockContext.config.getBlockConfig(blockConfigPath);
        return FutureUtils.futureOnDefaultDispatcher(blockContext.context, () -> {
            String mainPath = blockConfig.getString("path");
            return new Storage() {
                @Override
                public Sink<ByteString, CompletionStage<IOResult>> toPath(final String path) {
                    return FileIO.toPath(Paths.get(mainPath, path));
                }

                @Override
                public Source<ByteString, CompletionStage<IOResult>> fromPath(final String path) {
                    return FileIO.fromPath(Paths.get(mainPath, path));
                }

                @Override
                public boolean hasPath(final String path) {
                    return Files.exists(Paths.get(mainPath, path));
                }
            };
        });
    }

    @Override
    public boolean isMandatory() {
        return true;
    }
}
