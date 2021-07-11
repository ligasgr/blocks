package blocks.websocket;

import akka.NotUsed;
import akka.actor.typed.ActorSystem;
import akka.http.javadsl.model.ws.Message;
import akka.http.javadsl.model.ws.TextMessage;
import akka.http.javadsl.server.Directives;
import akka.http.javadsl.server.PathMatcher0;
import akka.http.javadsl.server.PathMatchers;
import akka.http.javadsl.server.Route;
import akka.japi.JavaPartialFunction;
import akka.japi.Pair;
import akka.stream.OverflowStrategy;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Keep;
import akka.stream.javadsl.MergeHub;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.stream.javadsl.SourceQueueWithComplete;
import blocks.service.AbstractBlock;
import blocks.service.BlockConfig;
import blocks.service.BlockContext;
import org.reactivestreams.Subscriber;
import org.slf4j.Logger;
import scala.PartialFunction;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class WebSocketBlock extends AbstractBlock<Route> {
    private final Function<BlockContext, WebSocketMessageHandler> handlerCreator;
    private final String blockConfigPath;
    private final String webSocketPath;
    private WebSocketMessageHandler handler;

    public WebSocketBlock(final Function<BlockContext, WebSocketMessageHandler> handlerCreator, final String blockConfigPath, final String webSocketPath) {
        this.handlerCreator = handlerCreator;
        this.blockConfigPath = blockConfigPath;
        this.webSocketPath = webSocketPath;
    }

    @Override
    protected CompletableFuture<Route> getBlockOutputFuture(final BlockContext blockContext) {
        handler = handlerCreator.apply(blockContext);
        BlockConfig blockConfig = blockContext.config.getBlockConfig(blockConfigPath);
        String strippedOfFirstSlash = this.webSocketPath.charAt(0) == '/' ? this.webSocketPath.substring(1) : this.webSocketPath;
        String[] webSocketPath = strippedOfFirstSlash.split("/");
        PathMatcher0 webSocketPathMatcher = PathMatchers.segment(webSocketPath[0]);
        int i = 1;
        while (i < webSocketPath.length) {
            webSocketPathMatcher = webSocketPathMatcher.slash(webSocketPath[i]);
            i++;
        }
        return CompletableFuture.completedFuture(Directives.path(webSocketPathMatcher, () ->
                Directives.get(() ->
                        Directives.<NotUsed>handleWebSocketMessages(messageHandlingFlow(blockContext.context.getSystem(), blockConfig.getDuration("keepAliveMessageFrequency")))
                )
        ));
    }

    @Override
    public Optional<Route> getCreatedRoutes() {
        return Optional.of(getBlockOutput()
                .orElseThrow(() -> new IllegalStateException("WebSocketBlock is not initialized")));
    }

    @Override
    public boolean isMandatory() {
        return true;
    }

    private Flow<Message, Message, NotUsed> messageHandlingFlow(ActorSystem<Void> system, final Duration keepAliveMessageFrequency) {
        String session = handler.generateSessionId();
        final Logger log = system.log();
        log.info("Starting new session: " + session);
        Pair<Sink<Message, NotUsed>, Source<Message, NotUsed>> mergingMultipleSourcesIntoSink = MergeHub.of(Message.class).preMaterialize(system);
        Sink<Message, NotUsed> outgoingMessagesSink = mergingMultipleSourcesIntoSink.first();
        Source<Message, NotUsed> outgoingMessagesSource = mergingMultipleSourcesIntoSink.second();
        Source<Message, Subscriber<Message>> incomingMessagesSource = Source.asSubscriber();
        SourceQueueWithComplete<TextMessage> outgoingMessageQueue = Source.<TextMessage>queue(0, OverflowStrategy.backpressure())
                .map(v -> (Message) v)
                .toMat(outgoingMessagesSink, Keep.left())
                .run(system);
        Subscriber<Message> incomingMessagesSubscriber = incomingMessagesSource
                .recover(exceptionLoggingFunction(session, outgoingMessageQueue))
                .via(messageHandlingFlow(session, outgoingMessageQueue))
                .toMat(outgoingMessagesSink, Keep.left())
                .run(system);
        Sink<Message, NotUsed> messageConsumingSink = Sink.fromSubscriber(incomingMessagesSubscriber);
        Source<Message, NotUsed> output = outgoingMessagesSource
                .map(v -> {
                    String asText = v.isText() && v.asTextMessage().isStrict() ? v.asTextMessage().getStrictText() : v.toString();
                    log.debug("Sending out: {}", asText);
                    return v;
                })
                .keepAlive(keepAliveMessageFrequency, handler::keepAliveMessage);
        return Flow.fromSinkAndSource(messageConsumingSink, output);

    }

    private PartialFunction<Throwable, Message> exceptionLoggingFunction(String session, SourceQueueWithComplete<TextMessage> queue) {
        return new JavaPartialFunction<Throwable, Message>() {
            @Override
            public Message apply(Throwable throwable, boolean isCheck) {
                if (isCheck) {
                    return null;
                } else {
                    return handler.handleException(session, throwable, queue);
                }
            }
        };
    }

    private Flow<Message, Message, NotUsed> messageHandlingFlow(String session, SourceQueueWithComplete<TextMessage> queue) {
        JavaPartialFunction<Message, Message> partialFunction = new JavaPartialFunction<Message, Message>() {
            @Override
            public Message apply(Message msg, boolean isCheck) {
                if (isCheck) {
                    if (msg.isText()) {
                        return null;
                    } else {
                        throw noMatch();
                    }
                } else {
                    return handler.handleTextMessage(session, msg.asTextMessage(), queue);
                }
            }
        };
        return Flow.<Message>create().collect(partialFunction);
    }
}
