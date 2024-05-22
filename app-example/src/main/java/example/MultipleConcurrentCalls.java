package example;

import org.apache.pekko.Done;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.http.javadsl.Http;
import org.apache.pekko.http.javadsl.model.HttpRequest;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.stream.javadsl.Source;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;

public class MultipleConcurrentCalls {
    public static void main(String[] args) throws ExecutionException, InterruptedException {
        final ActorSystem system = ActorSystem.create();
        try {
            Http http = Http.get(system);
            CompletionStage<Done> result = Source.repeat(HttpRequest.create("http://localhost:8080/info/v1"))
                    .mapAsync(20, http::singleRequest)
                    .mapAsync(20, r -> r.entity().toStrict(1000L, system))
                    .map(e -> e.getData().utf8String())
                    .runWith(Sink.foreach(System.out::println), system);
            System.out.println(result.toCompletableFuture().get());
        } finally {
            system.terminate();
        }
    }
}
