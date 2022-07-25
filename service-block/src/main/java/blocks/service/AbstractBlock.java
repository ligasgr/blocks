package blocks.service;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;

public abstract class AbstractBlock<T> implements Block<T> {
    protected CompletableFuture<T> outputFuture;

    public static <T> BlockRef<T> getRef(final String name) {
        return new BlockRef<>(name, new BlockTypeReference<>() {
        });
    }

    @Override
    public BlockRef<T> ref() {
        return getRef(this.getClass().getName());
    }

    @Override
    public CompletionStage<T> initialize(final BlockContext blockContext) {
        if (this.outputFuture != null) {
            throw new IllegalStateException(String.format("Trying to initialize block %s again", getClass()));
        }
        this.outputFuture = getBlockOutputFuture(blockContext);
        return this.outputFuture;
    }

    @Override
    public Throwable failureInfo() {
        if (outputFuture != null && outputFuture.isCompletedExceptionally()) {
            try {
                outputFuture.get();
                return null;
            } catch (InterruptedException | ExecutionException e) {
                return e.getCause();
            }
        } else {
            return null;
        }
    }

    @Override
    public Optional<T> getBlockOutput() {
        if (this.outputFuture == null) {
            throw new IllegalStateException(String.format("Block %s has not started initializing yet, have you missed adding dependency?", getClass()));
        }
        return outputFuture.isDone() ? extract(outputFuture) : Optional.empty();
    }

    @Override
    public BlockStatus getStatus() {
        if (outputFuture == null) {
            return BlockStatus.NOT_INITIALIZED;
        }
        return outputFuture.isDone()
                ? (outputFuture.isCompletedExceptionally() ? BlockStatus.FAILED : BlockStatus.INITIALIZED)
                : BlockStatus.INITIALIZING;
    }

    protected abstract CompletableFuture<T> getBlockOutputFuture(final BlockContext blockContext);

    private Optional<T> extract(CompletableFuture<T> outputFuture) {
        try {
            return outputFuture.isCompletedExceptionally()
                    ? Optional.empty()
                    : Optional.of(outputFuture.get());
        } catch (InterruptedException | ExecutionException e) {
            return Optional.empty();
        }
    }
}
