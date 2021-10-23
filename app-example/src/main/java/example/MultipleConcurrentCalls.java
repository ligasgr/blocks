package example;

import akka.Done;
import akka.actor.ActorSystem;
import akka.http.javadsl.Http;
import akka.http.javadsl.model.HttpRequest;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;

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
                    .<CompletionStage<Done>>runWith(Sink.<String>foreach(System.out::println), system);
            System.out.println(result.toCompletableFuture().get());
        } finally {
            system.terminate();
        }
    }
}
