package example;

import org.apache.pekko.Done;
import org.apache.pekko.NotUsed;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.http.javadsl.Http;
import org.apache.pekko.http.javadsl.model.ws.Message;
import org.apache.pekko.http.javadsl.model.ws.TextMessage;
import org.apache.pekko.http.javadsl.model.ws.WebSocketRequest;
import org.apache.pekko.http.javadsl.model.ws.WebSocketUpgradeResponse;
import org.apache.pekko.http.scaladsl.model.StatusCodes;
import org.apache.pekko.japi.Pair;
import org.apache.pekko.stream.Graph;
import org.apache.pekko.stream.OverflowStrategy;
import org.apache.pekko.stream.SinkShape;
import org.apache.pekko.stream.javadsl.Flow;
import org.apache.pekko.stream.javadsl.Keep;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.stream.javadsl.Source;
import org.apache.pekko.stream.javadsl.SourceQueueWithComplete;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletionStage;

import static java.util.Arrays.asList;

public class WsClient {
    public static void main(final String[] args)  {
        final ActorSystem system = ActorSystem.create();
        try {
            final WebSocketRequest req = WebSocketRequest.create("ws://127.0.0.1:8080/ws");
            final Http http = Http.get(system);
            final Flow<Message, Message, CompletionStage<WebSocketUpgradeResponse>> webSocketFlow = http.webSocketClientFlow(req);

            final Source<String, SourceQueueWithComplete<String>> queue = Source.queue(0, OverflowStrategy.backpressure());
            final Pair<SourceQueueWithComplete<String>, Source<String, NotUsed>> queueAndSource = queue.preMaterialize(system);
            final SourceQueueWithComplete<String> messageSourceQueue = queueAndSource.first();
            final Source<Message, NotUsed> messageSource = queueAndSource.second()
                    .throttle(1, Duration.ofSeconds(2L))
                    .map(TextMessage::create);

            final Sink<Message, CompletionStage<Done>> messageSink =
                    Sink.foreach(message -> {
                        final Graph<SinkShape<String>, CompletionStage<String>> sink = Sink.head();
                        String responseText = message.asTextMessage().getStreamedText()
                                .fold(new StringBuilder(), StringBuilder::append)
                                .map(StringBuilder::toString)
                                .runWith(sink, system).toCompletableFuture().get();
                        System.out.println("Received text message: [" + responseText + "]");
                        if (responseText.contains("Bye")) {
                            System.out.println("Got 'Bye'... Finishing!");
                            messageSourceQueue.complete();
                        }
                    });

            final Pair<Pair<NotUsed, CompletionStage<WebSocketUpgradeResponse>>, CompletionStage<Done>> result = messageSource
                    .viaMat(webSocketFlow, Keep.both())
                    .toMat(messageSink, Keep.both())
                    .run(system);

            final List<String> messages = asList("Hello", "Bonjour", "Aloha", "Czesc"/*, "Bye"*/);
            messages.forEach(message -> {
                    System.out.println("Enqueueing " + message);
                    messageSourceQueue.offer(message);
                });

            final CompletionStage<Done> connected = result.first().second().thenApply(upgrade -> {
                if (upgrade.response().status().intValue() == StatusCodes.SwitchingProtocols().intValue()) {
                    return Done.done();
                } else {
                    throw new RuntimeException("Connection failed: " + upgrade.response().status());
                }
            });

            connected.thenCompose(done -> result.second().toCompletableFuture()).toCompletableFuture().get();
            System.out.println("Finished");
        } catch (final Exception e) {
            System.err.println(e);
        } finally {
            system.terminate();
        }
    }
}
