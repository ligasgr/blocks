package blocks.service;

import org.apache.pekko.actor.typed.DispatcherSelector;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.dispatch.Futures$;
import scala.concurrent.ExecutionContextExecutor;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class FutureUtils {
    public static <T> CompletableFuture<T> futureOnDefaultDispatcher(final ActorContext<?> context, final Callable<T> callable) {
        return futureOnDispatcher(context.getSystem().dispatchers().lookup(DispatcherSelector.defaultDispatcher()), callable);
    }

    public static <T> CompletableFuture<T> futureOnDispatcher(final ExecutionContextExecutor executionContextExecutor, final Callable<T> callable) {
        return scala.jdk.javaapi.FutureConverters.asJava(Futures$.MODULE$.future(callable, executionContextExecutor)).toCompletableFuture();
    }

    public static <R> CompletableFuture<R> failed(Throwable error) {
        CompletableFuture<R> future = new CompletableFuture<>();
        future.completeExceptionally(error);
        return future;
    }

    static<T> CompletableFuture<List<T>> sequence(List<CompletableFuture<T>> com) {
        return CompletableFuture.allOf(com.toArray(new CompletableFuture<?>[0]))
                .thenApply(v -> com.stream()
                        .map(CompletableFuture::join)
                        .collect(Collectors.toList())
                );
    }
}
