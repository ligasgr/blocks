package blocks.websocket;

import org.apache.pekko.NotUsed;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.http.javadsl.model.ws.Message;
import org.apache.pekko.http.javadsl.model.ws.TextMessage;
import org.apache.pekko.http.javadsl.server.Directives;
import org.apache.pekko.http.javadsl.server.PathMatcher0;
import org.apache.pekko.http.javadsl.server.PathMatchers;
import org.apache.pekko.http.javadsl.server.Route;
import org.apache.pekko.japi.JavaPartialFunction;
import org.apache.pekko.japi.Pair;
import org.apache.pekko.stream.OverflowStrategy;
import org.apache.pekko.stream.javadsl.Flow;
import org.apache.pekko.stream.javadsl.Keep;
import org.apache.pekko.stream.javadsl.MergeHub;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.stream.javadsl.Source;
import org.apache.pekko.stream.javadsl.SourceQueueWithComplete;
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

@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public class WebSocketBlock extends AbstractBlock<Route> {
    private final Function<BlockContext, WebSocketMessageHandler> handlerCreator;
    private final String blockConfigPath;
    private final String webSocketPath;
    private final Optional<Function<BlockContext, Runnable>> requestsStartNotificationRunnableCreator;
    private final Optional<Function<BlockContext, Runnable>> requestsEndNotificationRunnableCreator;
    private WebSocketMessageHandler handler;

    public WebSocketBlock(final Function<BlockContext, WebSocketMessageHandler> handlerCreator,
                          final String blockConfigPath,
                          final String webSocketPath) {
        this(handlerCreator, blockConfigPath, webSocketPath, Optional.empty(), Optional.empty());
    }

    public WebSocketBlock(final Function<BlockContext, WebSocketMessageHandler> handlerCreator,
                          final String blockConfigPath,
                          final String webSocketPath,
                          final Function<BlockContext, Runnable> requestsStartNotificationRunnableCreator,
                          final Function<BlockContext, Runnable> requestsEndNotificationRunnableCreator
    ) {
        this(handlerCreator, blockConfigPath, webSocketPath, Optional.of(requestsStartNotificationRunnableCreator), Optional.of(requestsEndNotificationRunnableCreator));
    }

    private WebSocketBlock(final Function<BlockContext, WebSocketMessageHandler> handlerCreator,
                           final String blockConfigPath,
                           final String webSocketPath,
                           final Optional<Function<BlockContext, Runnable>> requestsStartNotificationRunnableCreator,
                           final Optional<Function<BlockContext, Runnable>> requestsEndNotificationRunnableCreator
    ) {
        this.handlerCreator = handlerCreator;
        this.blockConfigPath = blockConfigPath;
        this.webSocketPath = webSocketPath;
        this.requestsStartNotificationRunnableCreator = requestsStartNotificationRunnableCreator;
        this.requestsEndNotificationRunnableCreator = requestsEndNotificationRunnableCreator;
    }

    @Override
    protected CompletableFuture<Route> getBlockOutputFuture(final BlockContext blockContext) {
        handler = handlerCreator.apply(blockContext);
        final Optional<Runnable> requestStartNotificationRunnable = requestsStartNotificationRunnableCreator.map(f -> f.apply(blockContext));
        final Optional<Runnable> requestEndNotificationRunnable = requestsEndNotificationRunnableCreator.map(f -> f.apply(blockContext));
        final BlockConfig blockConfig = blockContext.config.getBlockConfig(blockConfigPath);
        final String strippedOfFirstSlash = this.webSocketPath.charAt(0) == '/' ? this.webSocketPath.substring(1) : this.webSocketPath;
        final String[] webSocketPath = strippedOfFirstSlash.split("/");
        PathMatcher0 webSocketPathMatcher = PathMatchers.segment(webSocketPath[0]);
        int i = 1;
        while (i < webSocketPath.length) {
            webSocketPathMatcher = webSocketPathMatcher.slash(webSocketPath[i]);
            i++;
        }
        final Logger log = blockContext.context.getLog();
        return CompletableFuture.completedFuture(Directives.path(webSocketPathMatcher, () ->
                Directives.get(() -> {
                    try {
                        requestStartNotificationRunnable.ifPresent(Runnable::run);
                    } catch (Exception e) {
                        log.error("Failed to run start notification", e);
                    }
                    final Flow<Message, Message, NotUsed> keepAliveMessageFrequency = messageHandlingFlow(blockContext.context.getSystem(), blockConfig.getDuration("keepAliveMessageFrequency"))
                            .watchTermination((mat, eventuallyDone) -> eventuallyDone.handle((d, t) -> {
                                try {
                                    requestEndNotificationRunnable.ifPresent(Runnable::run);
                                } catch (Exception e) {
                                    log.error("Failed to run end notification", e);
                                }
                                return NotUsed.getInstance();
                            }))
                            .mapMaterializedValue(v -> NotUsed.getInstance());
                    return Directives.handleWebSocketMessages(keepAliveMessageFrequency);
                })
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

    protected Flow<Message, Message, NotUsed> messageHandlingFlow(final ActorSystem<Void> system,
                                                                  final Duration keepAliveMessageFrequency) {
        final String session = handler.generateSessionId();
        final Logger log = system.log();
        log.info("Starting new session: " + session);
        final Pair<Sink<Message, NotUsed>, Source<Message, NotUsed>> mergingMultipleSourcesIntoSink = MergeHub.of(Message.class).preMaterialize(system);
        final Sink<Message, NotUsed> outgoingMessagesSink = mergingMultipleSourcesIntoSink.first();
        final Source<Message, NotUsed> outgoingMessagesSource = mergingMultipleSourcesIntoSink.second();
        final Source<Message, Subscriber<Message>> incomingMessagesSource = Source.asSubscriber();
        final SourceQueueWithComplete<TextMessage> outgoingMessageQueue = Source.<TextMessage>queue(0, OverflowStrategy.backpressure())
                .map(v -> (Message) v)
                .toMat(outgoingMessagesSink, Keep.left())
                .run(system);
        handler.registerOutgoingQueue(session, outgoingMessageQueue);
        final Subscriber<Message> incomingMessagesSubscriber = incomingMessagesSource
                .recover(exceptionLoggingFunction(session, outgoingMessageQueue))
                .via(messageHandlingFlow(session, outgoingMessageQueue))
                .toMat(outgoingMessagesSink, Keep.left())
                .run(system);
        final Sink<Message, NotUsed> messageConsumingSink = Sink.fromSubscriber(incomingMessagesSubscriber);
        final Source<Message, NotUsed> output = outgoingMessagesSource
                .map(v -> {
                    String asText = v.isText() && v.asTextMessage().isStrict() ? v.asTextMessage().getStrictText() : v.toString();
                    log.debug("Sending out: {}", asText);
                    return v;
                })
                .keepAlive(keepAliveMessageFrequency, handler::keepAliveMessage);
        return Flow.fromSinkAndSource(messageConsumingSink, output);

    }

    private PartialFunction<Throwable, Message> exceptionLoggingFunction(final String session,
                                                                         final SourceQueueWithComplete<TextMessage> queue) {
        return new JavaPartialFunction<>() {
            @Override
            public Message apply(final Throwable throwable, final boolean isCheck) {
                if (isCheck) {
                    return null;
                } else {
                    return handler.handleException(session, throwable, queue);
                }
            }
        };
    }

    private Flow<Message, Message, NotUsed> messageHandlingFlow(final String session,
                                                                final SourceQueueWithComplete<TextMessage> queue) {
        final JavaPartialFunction<Message, Message> partialFunction = new JavaPartialFunction<>() {
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
