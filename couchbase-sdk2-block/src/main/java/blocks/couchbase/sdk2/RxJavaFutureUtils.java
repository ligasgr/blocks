package blocks.couchbase.sdk2;

import rx.Observable;

import java.util.concurrent.CompletableFuture;

public class RxJavaFutureUtils {
    public static <T> CompletableFuture<T> fromObservable(Observable<T> observable) {
        final CompletableFuture<T> future = new CompletableFuture<>();
        observable
                .doOnError(future::completeExceptionally)
                .forEach(future::complete);
        return future;
    }
}
