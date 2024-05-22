package blocks.mongo;

import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.Receive;
import org.apache.pekko.actor.typed.javadsl.ReceiveBuilder;
import org.apache.pekko.actor.typed.javadsl.TimerScheduler;
import org.apache.pekko.japi.Pair;
import blocks.health.ComponentHealth;
import blocks.health.HealthProtocol;
import blocks.service.BlockStatus;
import com.mongodb.connection.ServerConnectionState;
import com.mongodb.reactivestreams.client.MongoClient;

import java.time.Clock;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

public class MongoHealthCheckActor extends AbstractBehavior<MongoHealthCheckActor.Protocol.Message> {
    private static final Protocol.CheckHealth CHECK_HEALTH = new Protocol.CheckHealth();
    private final TimerScheduler<Protocol.Message> timer;
    private final ActorRef<HealthProtocol.Message> healthActor;
    private final Clock clock;
    private final MongoBlock mongoBlock;
    private final String blockConfigPath;

    public static Behavior<Protocol.Message> behavior(
            final ActorRef<HealthProtocol.Message> healthActor, final Clock clock, final MongoBlock mongoBlock, final String blockConfigPath) {
        return Behaviors.setup(ctx -> Behaviors.withTimers(timer -> new MongoHealthCheckActor(ctx, timer, healthActor, clock, mongoBlock, blockConfigPath)));
    }

    public MongoHealthCheckActor(final ActorContext<Protocol.Message> context,
                                 final TimerScheduler<Protocol.Message> timer,
                                 final ActorRef<HealthProtocol.Message> healthActor,
                                 final Clock clock,
                                 final MongoBlock mongoBlock,
                                 final String blockConfigPath) {
        super(context);
        this.timer = timer;
        this.healthActor = healthActor;
        this.clock = clock;
        this.mongoBlock = mongoBlock;
        this.blockConfigPath = blockConfigPath;
        context.getSelf().tell(CHECK_HEALTH);
        healthActor.tell(new HealthProtocol.RegisterComponent(componentName()));
    }

    @Override
    public Receive<Protocol.Message> createReceive() {
        return ReceiveBuilder.<Protocol.Message>create()
                .onMessage(Protocol.CheckHealth.class, this::onCheckHealth)
                .onMessage(Protocol.HealthInfo.class, this::onHealthInfo)
                .build();
    }

    private Behavior<Protocol.Message> onCheckHealth(final Protocol.CheckHealth msg) {
        if (mongoBlock.getStatus() != BlockStatus.INITIALIZED) {
            getContext().getSelf().tell(new Protocol.HealthInfo(false, mongoBlock.failureInfo(), Collections.emptyList()));
        } else {
            getContext().pipeToSelf(runHealthCheck(), (h, t) -> {
                getContext().getLog().info(String.format("Mongo health check (health=%s, exception=%s)", h, t));
                return t != null ? new Protocol.HealthInfo(true, t, Collections.emptyList()) : h;
            });
        }
        return Behaviors.same();
    }

    private CompletionStage<Protocol.HealthInfo> runHealthCheck() {
        Optional<MongoClient> maybeClient = mongoBlock.getBlockOutput();
        if (maybeClient.isEmpty()) {
            return CompletableFuture.completedFuture(new Protocol.HealthInfo(false, mongoBlock.failureInfo(), Collections.emptyList()));
        } else {
            List<Pair<String, Boolean>> endpoints = maybeClient.get().getClusterDescription().getServerDescriptions().stream()
                    .map(d -> Pair.create(d.getAddress().toString(), d.getState() == ServerConnectionState.CONNECTED))
                    .collect(Collectors.toList());
            return CompletableFuture.completedFuture(new Protocol.HealthInfo(true, null, endpoints));
        }
    }

    private Behavior<Protocol.Message> onHealthInfo(final Protocol.HealthInfo msg) {
        Duration checkDelay;
        if (msg.exception != null) {
            String message = msg.exception.getMessage() != null ? msg.exception.getMessage() : msg.exception.getClass().getCanonicalName();
            ComponentHealth health = new ComponentHealth(componentName(), false, msg.initialized, Optional.of(message), Collections.emptyList(), ZonedDateTime.now(clock), OptionalLong.empty());
            healthActor.tell(new HealthProtocol.UpdateComponentHealth(componentName(), health));
            checkDelay = Duration.ofSeconds(3);
        } else {
            final List<ComponentHealth> dependencies = msg.endpoints.stream()
                    .map(endpointDetails -> new ComponentHealth(endpointDetails.first(), endpointDetails.second(), true, Optional.empty(), Collections.emptyList(), ZonedDateTime.now(clock), OptionalLong.empty()))
                    .collect(Collectors.toList());
            boolean allEndpointsHealthy = dependencies.stream().allMatch(c -> c.isHealthy);
            boolean isHealthy = !dependencies.isEmpty() && allEndpointsHealthy;
            ComponentHealth health = new ComponentHealth(componentName(), isHealthy, msg.initialized, Optional.empty(), dependencies, ZonedDateTime.now(clock), OptionalLong.empty());
            healthActor.tell(new HealthProtocol.UpdateComponentHealth(componentName(), health));
            checkDelay = Duration.ofSeconds(15);
        }
        timer.startSingleTimer(CHECK_HEALTH, checkDelay);
        return Behaviors.same();
    }

    private String componentName() {
        return "mongo-" + blockConfigPath;
    }

    public interface Protocol {

        interface Message {
        }

        class CheckHealth implements Message {

        }

        class HealthInfo implements Message {
            public final boolean initialized;
            public final Throwable exception;
            public final List<Pair<String, Boolean>> endpoints;

            public HealthInfo(final boolean initialized, final Throwable exception, final List<Pair<String, Boolean>> endpoints) {
                this.initialized = initialized;
                this.exception = exception;
                this.endpoints = endpoints;
            }

            @Override
            public String toString() {
                return "HealthInfo{" +
                        "initialized=" + initialized +
                        ", exception=" + exception +
                        ", endpoints=" + endpoints +
                        '}';
            }
        }
    }
}
