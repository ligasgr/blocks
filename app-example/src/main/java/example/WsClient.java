package example;

import akka.Done;
import akka.NotUsed;
import akka.actor.ActorSystem;
import akka.http.javadsl.Http;
import akka.http.javadsl.model.ws.Message;
import akka.http.javadsl.model.ws.TextMessage;
import akka.http.javadsl.model.ws.WebSocketRequest;
import akka.http.javadsl.model.ws.WebSocketUpgradeResponse;
import akka.http.scaladsl.model.StatusCodes;
import akka.japi.Pair;
import akka.stream.OverflowStrategy;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Keep;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.stream.javadsl.SourceQueueWithComplete;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;

import static java.util.Arrays.asList;

public class WsClient {
    public static void main(String[] args)  {
        ActorSystem system = ActorSystem.create();
        try {
            final WebSocketRequest req = WebSocketRequest.create("ws://127.0.0.1:8080/ws");
            final Http http = Http.get(system);
            final Flow<Message, Message, CompletionStage<WebSocketUpgradeResponse>> webSocketFlow = http.webSocketClientFlow(req);

            Source<String, SourceQueueWithComplete<String>> queue = Source.queue(0, OverflowStrategy.backpressure());
            Pair<SourceQueueWithComplete<String>, Source<String, NotUsed>> queueAndSource = queue.preMaterialize(system);
            SourceQueueWithComplete<String> messageSourceQueue = queueAndSource.first();
            Source<Message, NotUsed> messageSource = queueAndSource.second()
                    .throttle(1, Duration.ofSeconds(2L))
                    .map(TextMessage::create);

            Sink<Message, CompletionStage<Done>> messageSink =
                    Sink.foreach(message -> {
                        String responseText = message.asTextMessage().getStrictText();
                        System.out.println("Received text message: [" + responseText + "]");
                        if (responseText.contains("Bye")) {
                            System.out.println("Got 'Bye'... Finishing!");
                            messageSourceQueue.complete();
                        }
                    });

            Pair<Pair<NotUsed, CompletionStage<WebSocketUpgradeResponse>>, CompletionStage<Done>> result = messageSource
                    .viaMat(webSocketFlow, Keep.both())
                    .toMat(messageSink, Keep.both())
                    .run(system);

            List<String> messages = asList("Hello", "Bonjour", "Aloha", "Czesc", "Bye");
            for (String message : messages) {
                System.out.println("Enqueueing " + message);
                messageSourceQueue.offer(message);
            }

            final CompletionStage<Done> connected = result.first().second().thenApply(upgrade -> {
                if (upgrade.response().status().intValue() == StatusCodes.SwitchingProtocols().intValue()) {
                    return Done.done();
                } else {
                    throw new RuntimeException("Connection failed: " + upgrade.response().status());
                }
            });

            connected.thenCompose(done -> result.second().toCompletableFuture()).toCompletableFuture().get();
            System.out.println("Finished");
        } catch (Exception e) {
            System.err.println(e);
        } finally {
            system.terminate();
        }
    }
}
