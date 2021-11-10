package blocks.service;

import akka.NotUsed;
import akka.actor.CoordinatedShutdown;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.PostStop;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.actor.typed.javadsl.ReceiveBuilder;
import akka.event.LoggingAdapter;
import akka.http.javadsl.Http;
import akka.http.javadsl.HttpTerminated;
import akka.http.javadsl.ServerBinding;
import akka.http.javadsl.ServerBuilder;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.server.Directives;
import akka.http.javadsl.server.ExceptionHandler;
import akka.http.javadsl.server.RejectionHandler;
import akka.http.javadsl.server.Route;
import akka.http.javadsl.server.directives.RouteAdapter;
import akka.http.javadsl.settings.ServerSettings;
import akka.http.javadsl.settings.WebSocketSettings;
import akka.stream.javadsl.Flow;
import akka.util.ByteString;
import ch.megard.akka.http.cors.javadsl.CorsDirectives;
import ch.megard.akka.http.cors.javadsl.settings.CorsSettings;
import com.typesafe.config.Config;
import org.slf4j.Logger;
import scala.compat.java8.functionConverterImpls.FromJavaFunction;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static ch.megard.akka.http.cors.javadsl.CorsDirectives.cors;

public class ServiceActor extends AbstractBehavior<ServiceProtocol.Message> {
    private static final Duration TERMINATION_GRACE_PERIOD = Duration.ofSeconds(30);
    private static final Duration HARD_TERMINATION_DEADLINE = Duration.ofSeconds(10);
    public static final ServiceProtocol.InitializedMandatoryBlocks INITIALIZED_MANDATORY_BLOCKS = new ServiceProtocol.InitializedMandatoryBlocks();
    public static final ServiceProtocol.InitializedAllBlocks INITIALIZED_ALL_BLOCKS = new ServiceProtocol.InitializedAllBlocks();
    private final Clock clock;
    private final Instant startInstant;
    private final Logger log;
    private final String env;
    private final String host;
    private final Optional<Integer> httpPort;
    private final Set<BlockRef<?>> blocksToInitialize = new HashSet<>();
    private final Set<BlockRef<?>> mandatoryBlocksToInitialize = new HashSet<>();
    private final Map<BlockRef<?>, Block<?>> blocks;
    private final Optional<Function<BlockContext, Runnable>> requestsStartNotificationRunnableCreator;
    private final Optional<Function<BlockContext, Runnable>> requestsEndNotificationRunnableCreator;
    private final Map<BlockRef<?>, Set<BlockRef<?>>> blockOutstandingDependencies = new HashMap<>();
    private final Map<BlockRef<?>, Set<BlockRef<?>>> blocksDependingOn = new HashMap<>();
    private final ServiceConfig config;
    private final AtomicReference<Route> dynamicRoute = new AtomicReference<>(Directives.reject());
    private final List<ServerBinding> serverBindings = new ArrayList<>();
    private boolean isReady = false;
    private final Http http;
    private final Route finalRoute;
    private final AtomicReference<Runnable> requestsStartNotificationRunnable = new AtomicReference<>();
    private final AtomicReference<Runnable> requestsEndNotificationRunnable = new AtomicReference<>();

    public static Behavior<ServiceProtocol.Message> behavior(Clock clock,
                                                             ServiceConfig config,
                                                             Map<BlockRef<?>, Block<?>> blocks,
                                                             final Map<BlockRef<?>, Set<BlockRef<?>>> blockDependencies,
                                                             final Function<ActorSystem<?>, LoggingAdapter> requestsLoggerCreator,
                                                             final Function<RequestLoggingDetails, String> requestsMessageFunction,
                                                             final Optional<Function<BlockContext, Runnable>> requestsStartNotificationRunnableCreator,
                                                             final Optional<Function<BlockContext, Runnable>> requestsEndNotificationRunnableCreator,
                                                             final Optional<ExceptionHandler> exceptionHandler,
                                                             final Optional<RejectionHandler> rejectionHandler) {
        return Behaviors.setup(context -> new ServiceActor(context, clock, config, blocks, blockDependencies, requestsLoggerCreator, requestsMessageFunction,
                requestsStartNotificationRunnableCreator, requestsEndNotificationRunnableCreator, exceptionHandler, rejectionHandler));
    }

    public ServiceActor(final ActorContext<ServiceProtocol.Message> context,
                        final Clock clock,
                        final ServiceConfig config,
                        final Map<BlockRef<?>, Block<?>> blocks,
                        final Map<BlockRef<?>, Set<BlockRef<?>>> blockDependencies,
                        final Function<ActorSystem<?>, LoggingAdapter> requestsLoggerCreator,
                        final Function<RequestLoggingDetails, String> requestsMessageFunction,
                        final Optional<Function<BlockContext, Runnable>> requestsStartNotificationRunnableCreator,
                        final Optional<Function<BlockContext, Runnable>> requestsEndNotificationRunnableCreator,
                        final Optional<ExceptionHandler> exceptionHandler,
                        final Optional<RejectionHandler> rejectionHandler) {
        super(context);
        this.clock = clock;
        this.startInstant = clock.instant();
        this.log = context.getLog();
        this.env = config.getEnv();
        this.host = config.getHost();
        this.httpPort = config.getHttpPort();
        this.config = config;
        this.blocks = blocks;
        this.requestsStartNotificationRunnableCreator = requestsStartNotificationRunnableCreator;
        this.requestsEndNotificationRunnableCreator = requestsEndNotificationRunnableCreator;
        initializationBanner();
        buildDependenciesMappings(blocks, blockDependencies);
        final CorsSettings settings = CorsSettings.defaultSettings();
        final Route dynamicRouteAdapter = RouteAdapter.asJava(new FromJavaFunction<>(ctx -> this.dynamicRoute.get().asScala().apply(ctx)));
        final Route route = basicRoutes().orElse(dynamicRouteAdapter);
        akka.actor.typed.ActorSystem<Void> system = getContext().getSystem();
        final LoggingAdapter metricsLog = requestsLoggerCreator.apply(system);
        finalRoute = RequestMetrics.captureRequests(
                metricsLog,
                requestsMessageFunction,
                () -> {
                    final Runnable actualRunnable = requestsStartNotificationRunnable.get();
                    if (actualRunnable != null) {
                        actualRunnable.run();
                    }
                },
                () -> {
                    final Runnable actualRunnable = requestsEndNotificationRunnable.get();
                    if (actualRunnable != null) {
                        actualRunnable.run();
                    }
                },
                () -> {
                    Route corsSupportingRoute = cors(settings, () -> route);
                    RejectionHandler corsRejectionHandler = CorsDirectives.corsRejectionHandler();
                    RejectionHandler finalRejectionHandler = rejectionHandler.isPresent() ? corsRejectionHandler.withFallback(rejectionHandler.get()) : corsRejectionHandler;
                    ExceptionHandler finalExceptionHandler = exceptionHandler.orElse(ExceptionHandler.newBuilder().build());
                    return corsSupportingRoute.seal(finalRejectionHandler, finalExceptionHandler);
                });
        getContext().getSelf().tell(new ServiceProtocol.BindPorts(finalRoute));
        CoordinatedShutdown.get(system.classicSystem()).addJvmShutdownHook(this::shutdownBanner);
        http = Http.get(system);
    }

    @Override
    public Receive<ServiceProtocol.Message> createReceive() {
        return ReceiveBuilder.<ServiceProtocol.Message>create()
                .onMessage(ServiceProtocol.InitializeBlocks.class, message -> onInitializeBlocks())
                .onMessage(ServiceProtocol.InitializedBlock.class, this::onInitializedBlock)
                .onMessage(ServiceProtocol.InitializedMandatoryBlocks.class, message -> onInitializedMandatoryBlocks())
                .onMessage(ServiceProtocol.InitializedAllBlocks.class, message -> onInitializedAllBlocks())
                .onMessage(ServiceProtocol.BindPorts.class, this::onBindPorts)
                .onMessage(ServiceProtocol.PortsBound.class, this::onPortsBound)
                .onMessage(ServiceProtocol.PortsFailedToBind.class, this::onPortsFailedToBind)
                .onSignal(PostStop.class, signal -> {
                    HttpTerminated artificialTerminated = akka.http.scaladsl.Http.HttpServerTerminated$.MODULE$;
                    serverBindings.stream().map(sb -> sb.terminate(TERMINATION_GRACE_PERIOD))
                            .reduce(CompletableFuture.completedFuture(artificialTerminated), (prev, current) -> prev.thenCompose(sb -> current))
                            .whenComplete((terminated, t) -> getContext().getSystem().terminate());
                    return Behaviors.same();
                })
                .build();
    }

    private Behavior<ServiceProtocol.Message> onInitializeBlocks() {
        log.info("Starting block initialization...");
        for (Map.Entry<BlockRef<?>, Block<?>> kevAndValue : blocks.entrySet()) {
            Block<?> block = kevAndValue.getValue();
            BlockRef<?> blockRef = kevAndValue.getKey();
            if (block.isMandatory()) {
                mandatoryBlocksToInitialize.add(blockRef);
            }
            blocksToInitialize.add(blockRef);
            block.onInitializeBlocks(blocks);
        }
        triggerBlocksWithAllDependenciesMet();
        return Behaviors.same();
    }

    private Behavior<ServiceProtocol.Message> onInitializedBlock(final ServiceProtocol.InitializedBlock<?> message) {
        BlockRef<?> initializedBlockRef = message.blockRef;
        log.info("Finished initializing {}", initializedBlockRef);
        if (message.t != null) {
            log.error("Initialization of " + initializedBlockRef + " failed", message.t);
        } else {
            for (final BlockRef<?> key : blocksDependingOn.get(initializedBlockRef)) {
                blockOutstandingDependencies.get(key).remove(initializedBlockRef);
            }
            Block<?> block = blocks.get(initializedBlockRef);
            if (block.getStatus() == BlockStatus.INITIALIZED) {
                Optional<Route> maybeRoutes = block.getCreatedRoutes();
                maybeRoutes.ifPresent(this::addToDynamicRoutes);
                serverBindings.addAll(block.serverBindings());
            }
            triggerBlocksWithAllDependenciesMet();
        }
        blocks.values().forEach(b -> b.onInitializedBlock(initializedBlockRef));
        mandatoryBlocksToInitialize.remove(initializedBlockRef);
        if (mandatoryBlocksToInitialize.isEmpty()) {
            getContext().getSelf().tell(INITIALIZED_MANDATORY_BLOCKS);
        }
        blocksToInitialize.remove(initializedBlockRef);
        if (blocksToInitialize.isEmpty()) {
            getContext().getSelf().tell(INITIALIZED_ALL_BLOCKS);
        }
        return Behaviors.same();
    }

    private Behavior<ServiceProtocol.Message> onInitializedMandatoryBlocks() {
        boolean failed = false;
        for (Map.Entry<BlockRef<?>, Block<?>> entry : blocks.entrySet()) {
            if (entry.getValue().getStatus() == BlockStatus.FAILED) {
                failed = true;
                getContext().getLog().error("Failed to initialize: " + entry.getKey(), entry.getValue().failureInfo());
            }
        }
        if (failed) {
            getContext().getLog().error("Stopping due to failed initialization of mandatory modules");
            return Behaviors.stopped();
        }

        return Behaviors.same();
    }

    private Behavior<ServiceProtocol.Message> onInitializedAllBlocks() {
        for (Map.Entry<BlockRef<?>, Block<?>> entry : blocks.entrySet()) {
            if (entry.getValue().getStatus() == BlockStatus.FAILED) {
                getContext().getLog().warn("Failed to initialize: " + entry.getKey(), entry.getValue().failureInfo());
            }
        }
        initializationFinishedBanner();
        this.isReady = true;
        BlockContext blockContext = getBlockContext();
        requestsStartNotificationRunnableCreator.ifPresent(creator -> requestsStartNotificationRunnable.set(creator.apply(blockContext)));
        requestsEndNotificationRunnableCreator.ifPresent(creator -> requestsEndNotificationRunnable.set(creator.apply(blockContext)));
        return Behaviors.same();
    }

    private Behavior<ServiceProtocol.Message> onBindPorts(final ServiceProtocol.BindPorts message) {
        getContext().pipeToSelf(bindRoute(message.route), (b, exception) -> {
            if (exception != null) {
                return new ServiceProtocol.PortsFailedToBind(exception);
            } else {
                return new ServiceProtocol.PortsBound(b);
            }
        });
        return Behaviors.same();
    }

    private CompletionStage<List<ServerBinding>> bindRoute(final Route route) {
        ServerSettings settings = customServerSettings();
        List<Optional<CompletionStage<ServerBinding>>> maybeFutureBindings = new ArrayList<>();
        maybeFutureBindings.add(httpPort.map(port -> {
            ServerBuilder serverBuilder = http.newServerAt(host, port).withSettings(settings);
            Flow<HttpRequest, HttpResponse, NotUsed> flow = route.flow(getContext().getSystem());
            return serverBuilder.bindFlow(flow);
        }));
        return FutureUtils.sequence(maybeFutureBindings.stream()
                .flatMap(serverBindingCompletionStage -> serverBindingCompletionStage.map(Stream::of).orElseGet(Stream::empty))
                .map(CompletionStage::toCompletableFuture)
                .collect(Collectors.toList()));
    }

    private ServerSettings customServerSettings() {
        Config config = ((TypesafeServiceConfig) this.config).config; // This is a strong assumption, ideally we'd accept any type of config and be able to convert it into Config from Typesafe
        ServerSettings defaultSettings = ServerSettings.create(config);
        WebSocketSettings customWebSocketSettings = defaultSettings.getWebsocketSettings().withPeriodicKeepAliveData(() -> ByteString.fromString("{}"));
        return defaultSettings.withWebsocketSettings(customWebSocketSettings);
    }

    private Behavior<ServiceProtocol.Message> onPortsBound(final ServiceProtocol.PortsBound message) {
        startupBanner();
        this.serverBindings.addAll(message.serverBindings);
        message.serverBindings.forEach(sb -> sb.addToCoordinatedShutdown(HARD_TERMINATION_DEADLINE, getContext().getSystem()));
        getContext().getSelf().tell(new ServiceProtocol.InitializeBlocks());
        return Behaviors.same();
    }

    private Behavior<ServiceProtocol.Message> onPortsFailedToBind(final ServiceProtocol.PortsFailedToBind message) {
        log.error("Server failed to bind to port {} due to exception: {}", httpPort, message.t.getMessage());
        return Behaviors.stopped();
    }

    private Route basicRoutes() {
        return Directives.get(() -> Directives.concat(
                Directives.path("live", () -> Directives.complete("true")),
                Directives.path("ready", () -> Directives.complete(isReady ? StatusCodes.OK : StatusCodes.INTERNAL_SERVER_ERROR, Boolean.toString(isReady)))
        ));
    }

    private void buildDependenciesMappings(final Map<BlockRef<?>, Block<?>> blocks, final Map<BlockRef<?>, Set<BlockRef<?>>> blockDependencies) {
        for (BlockRef<?> blockRef : blocks.keySet()) {
            blockOutstandingDependencies.put(blockRef, new HashSet<>());
            blocksDependingOn.put(blockRef, new HashSet<>());
        }
        for (Map.Entry<BlockRef<?>, Set<BlockRef<?>>> blockKeyAndDependencies : blockDependencies.entrySet()) {
            Set<BlockRef<?>> dependencies = blockKeyAndDependencies.getValue();
            BlockRef<?> key = blockKeyAndDependencies.getKey();
            blockOutstandingDependencies.get(key).addAll(dependencies);
            for (final BlockRef<?> dependency : dependencies) {
                blocksDependingOn.get(dependency).add(key);
            }
        }
    }

    private void triggerBlocksWithAllDependenciesMet() {
        blockOutstandingDependencies.entrySet().stream()
                .filter(e -> e.getValue().isEmpty())
                .forEach(e -> {
                    BlockRef<?> blockRef = e.getKey();
                    Block<?> block = blocks.get(blockRef);
                    if (block.getStatus() == BlockStatus.NOT_INITIALIZED) {
                        log.info("Scheduling initialization for {}", blockRef);
                        BlockContext blockContext = getBlockContext();
                        blocks.values().forEach(b -> b.onInitializeBlock(blockRef));
                        getContext().pipeToSelf(block.initialize(blockContext), (r, t) -> new ServiceProtocol.InitializedBlock<Object>(blockRef, r, t));
                    }
                });
    }

    private BlockContext getBlockContext() {
        return new BlockContext(getContext(), http, finalRoute, env, config, startInstant, clock, blocks.keySet(), blocks::get);
    }

    private void addToDynamicRoutes(final Route route) {
        dynamicRoute.set(dynamicRoute.get().orElse(route));
    }

    private void initializationBanner() {
        log.info("Starting server for env: {}", env);
    }

    private void startupBanner() {
        log.info("-----------------------------------------");
        log.info("Started server:");
        log.info("env: {}", env);
        log.info("host: {}", host);
        httpPort.ifPresent(port -> log.info("httpPort: {}", port));
        log.info("in: {}ms", clock.millis() - startInstant.toEpochMilli());
        log.info("-----------------------------------------");
    }

    private void initializationFinishedBanner() {
        log.info("-----------------------------------------");
        log.info("Fully initialized server in: {}ms", clock.millis() - startInstant.toEpochMilli());
        log.info("-----------------------------------------");
    }

    private void shutdownBanner() {
        log.info("-----------------------------------------");
        log.info("Server is going down");
        log.info("-----------------------------------------");
    }
}
