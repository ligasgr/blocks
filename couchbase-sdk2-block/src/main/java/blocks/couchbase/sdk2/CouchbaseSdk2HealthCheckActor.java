package blocks.couchbase.sdk2;

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
import com.couchbase.client.core.service.ServiceType;
import com.couchbase.client.java.Cluster;

import java.time.Clock;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

import static com.couchbase.client.core.state.LifecycleState.CONNECTED;


public class CouchbaseSdk2HealthCheckActor extends AbstractBehavior<CouchbaseSdk2HealthCheckActor.Protocol.Message> {
    private static final Protocol.CheckHealth CHECK_HEALTH = new Protocol.CheckHealth();
    private final TimerScheduler<Protocol.Message> timer;
    private final ActorRef<HealthProtocol.Message> healthActor;
    private final Clock clock;
    private final CouchbaseSdk2Block couchbaseSdk2Block;
    private final String blockConfigPath;
    private final Duration healthyCheckDelay;
    private final Duration unhealthyCheckDelay;

    public static Behavior<Protocol.Message> behavior(
            final ActorRef<HealthProtocol.Message> healthActor, final Clock clock, final CouchbaseSdk2Block couchbaseSdk2Block, final String blockConfigPath, final Duration healthyCheckDelay, final Duration unhealthyCheckDelay) {
        return Behaviors.setup(ctx -> Behaviors.withTimers(timer -> new CouchbaseSdk2HealthCheckActor(ctx, timer, healthActor, clock, couchbaseSdk2Block, blockConfigPath, healthyCheckDelay, unhealthyCheckDelay)));
    }

    public CouchbaseSdk2HealthCheckActor(final ActorContext<Protocol.Message> context,
                                         final TimerScheduler<Protocol.Message> timer,
                                         final ActorRef<HealthProtocol.Message> healthActor,
                                         final Clock clock,
                                         final CouchbaseSdk2Block couchbaseSdk2Block,
                                         final String blockConfigPath,
                                         final Duration healthyCheckDelay,
                                         final Duration unhealthyCheckDelay) {
        super(context);
        this.timer = timer;
        this.healthActor = healthActor;
        this.clock = clock;
        this.couchbaseSdk2Block = couchbaseSdk2Block;
        this.blockConfigPath = blockConfigPath;
        context.getSelf().tell(CHECK_HEALTH);
        healthActor.tell(new HealthProtocol.RegisterComponent(componentName()));
        this.healthyCheckDelay = healthyCheckDelay;
        this.unhealthyCheckDelay = unhealthyCheckDelay;
    }

    @Override
    public Receive<Protocol.Message> createReceive() {
        return ReceiveBuilder.<Protocol.Message>create()
                .onMessage(Protocol.CheckHealth.class, this::onCheckHealth)
                .onMessage(Protocol.HealthInfo.class, this::onHealthInfo)
                .build();
    }

    private Behavior<Protocol.Message> onCheckHealth(final Protocol.CheckHealth msg) {
        if (couchbaseSdk2Block.getStatus() != BlockStatus.INITIALIZED) {
            getContext().getSelf().tell(new Protocol.HealthInfo(false, couchbaseSdk2Block.failureInfo(), Collections.emptyList()));
        } else {
            getContext().pipeToSelf(runHealthCheck(), (h, t) -> {
                getContext().getLog().info(String.format("Couchbase health check (health=%s, exception=%s)", h, t));
                return t != null ? new Protocol.HealthInfo(true, t, Collections.emptyList()) : h;
            });
        }
        return Behaviors.same();
    }

    private CompletionStage<Protocol.HealthInfo> runHealthCheck() {
        Optional<Cluster> maybeCluster = couchbaseSdk2Block.getBlockOutput();
        if (maybeCluster.isEmpty()) {
            return CompletableFuture.completedFuture(new Protocol.HealthInfo(false, couchbaseSdk2Block.failureInfo(), Collections.emptyList()));
        } else {
            return RxJavaFutureUtils.fromObservable(maybeCluster.get().async().diagnostics()
                    .map(d ->
                            d.endpoints(ServiceType.BINARY).stream()
                                    .filter(endpointHealth -> endpointHealth.remote() != null)
                                    .map(endpointHealth -> Pair.create(endpointHealth.remote().getHostName(), endpointHealth.state() == CONNECTED))
                                    .filter(e -> Objects.nonNull(e.first()))
                                    .collect(Collectors.toList()))
                    .map(endpoints -> new Protocol.HealthInfo(true, null, endpoints))
            );
        }
    }

    private Behavior<Protocol.Message> onHealthInfo(final Protocol.HealthInfo msg) {
        Duration checkDelay;
        if (msg.exception != null) {
            String message = msg.exception.getMessage() != null ? msg.exception.getMessage() : msg.exception.getClass().getCanonicalName();
            ComponentHealth health = new ComponentHealth(componentName(), false, msg.initialized, Optional.of(message), Collections.emptyList(), ZonedDateTime.now(clock), OptionalLong.empty());
            healthActor.tell(new HealthProtocol.UpdateComponentHealth(componentName(), health));
            checkDelay = unhealthyCheckDelay;
        } else {
            final List<ComponentHealth> dependencies = msg.endpoints.stream()
                    .map(endpointDetails -> new ComponentHealth(endpointDetails.first(), endpointDetails.second(), true, Optional.empty(), Collections.emptyList(), ZonedDateTime.now(clock), OptionalLong.empty()))
                    .collect(Collectors.toList());
            boolean allEndpointsHealthy = dependencies.stream().allMatch(c -> c.isHealthy);
            boolean isHealthy = !dependencies.isEmpty() && allEndpointsHealthy;
            ComponentHealth health = new ComponentHealth(componentName(), isHealthy, msg.initialized, Optional.empty(), dependencies, ZonedDateTime.now(clock), OptionalLong.empty());
            healthActor.tell(new HealthProtocol.UpdateComponentHealth(componentName(), health));
            checkDelay = healthyCheckDelay;
        }
        timer.startSingleTimer(CHECK_HEALTH, checkDelay);
        return Behaviors.same();
    }

    private String componentName() {
        return "couchbase-" + blockConfigPath;
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
