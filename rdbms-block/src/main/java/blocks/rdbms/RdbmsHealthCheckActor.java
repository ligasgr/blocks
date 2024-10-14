package blocks.rdbms;

import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.Receive;
import org.apache.pekko.actor.typed.javadsl.ReceiveBuilder;
import org.apache.pekko.actor.typed.javadsl.TimerScheduler;
import blocks.health.ComponentHealth;
import blocks.health.HealthProtocol;
import blocks.service.BlockStatus;
import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.spi.Connection;
import reactor.core.publisher.Flux;

import java.time.Clock;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class RdbmsHealthCheckActor extends AbstractBehavior<RdbmsHealthCheckActor.Protocol.Message> {
    private static final Protocol.CheckHealth CHECK_HEALTH = new Protocol.CheckHealth();
    private final TimerScheduler<Protocol.Message> timer;
    private final ActorRef<HealthProtocol.Message> healthActor;
    private final Clock clock;
    private final RdbmsBlock rdbmsBlock;
    private final String connectionHealthCheckQuery;
    private final String blockConfigPath;

    public static Behavior<Protocol.Message> behavior(
            final ActorRef<HealthProtocol.Message> healthActor, final Clock clock, final RdbmsBlock rdbmsBlock, final String connectionHealthCheckQuery, final String blockConfigPath) {
        return Behaviors.setup(ctx -> Behaviors.withTimers(timer -> new RdbmsHealthCheckActor(ctx, timer, healthActor, clock, rdbmsBlock, connectionHealthCheckQuery, blockConfigPath)));
    }

    public RdbmsHealthCheckActor(final ActorContext<Protocol.Message> context,
                                 final TimerScheduler<Protocol.Message> timer,
                                 final ActorRef<HealthProtocol.Message> healthActor,
                                 final Clock clock,
                                 final RdbmsBlock rdbmsBlock,
                                 final String connectionHealthCheckQuery,
                                 final String blockConfigPath) {
        super(context);
        this.timer = timer;
        this.healthActor = healthActor;
        this.clock = clock;
        this.rdbmsBlock = rdbmsBlock;
        this.connectionHealthCheckQuery = connectionHealthCheckQuery;
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
        if (rdbmsBlock.getStatus() != BlockStatus.INITIALIZED) {
            getContext().getSelf().tell(new Protocol.HealthInfo(false, rdbmsBlock.failureInfo()));
        } else {
            getContext().pipeToSelf(runHealthCheck(), (h, t) -> {
                getContext().getLog().info(String.format("RDBMS health check (health=%s, exception=%s)", h, t));
                return t != null ? new Protocol.HealthInfo(true, t) : h;
            });
        }
        return Behaviors.same();
    }

    private CompletionStage<Protocol.HealthInfo> runHealthCheck() {
        Optional<ConnectionPool> maybeConnectionFactory = rdbmsBlock.getBlockOutput();
        if (maybeConnectionFactory.isEmpty()) {
            return CompletableFuture.completedFuture(new Protocol.HealthInfo(false, rdbmsBlock.failureInfo()));
        } else {
            return Flux.usingWhen(maybeConnectionFactory.get().create(), conn ->
                            Flux.from(conn.createStatement(connectionHealthCheckQuery).execute())
                                    .flatMap(r -> r.map((row, meta) -> row.get(0, String.class))),
                    Connection::close)
                    .collectList().toFuture()
                    .thenApply(l -> new Protocol.HealthInfo(true, null));
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
            ComponentHealth health = new ComponentHealth(componentName(), true, msg.initialized, Optional.empty(), Collections.emptyList(), ZonedDateTime.now(clock), OptionalLong.empty());
            healthActor.tell(new HealthProtocol.UpdateComponentHealth(componentName(), health));
            checkDelay = Duration.ofSeconds(15);
        }
        timer.startSingleTimer(CHECK_HEALTH, checkDelay);
        return Behaviors.same();
    }

    private String componentName() {
        return "rdbms-" + blockConfigPath;
    }

    public interface Protocol {

        interface Message {
        }

        class CheckHealth implements Message {

        }

        class HealthInfo implements Message {
            public final boolean initialized;
            public final Throwable exception;

            public HealthInfo(final boolean initialized, final Throwable exception) {
                this.initialized = initialized;
                this.exception = exception;
            }

            @Override
            public String toString() {
                return "HealthInfo{" +
                        "initialized=" + initialized +
                        ", exception=" + exception +
                        '}';
            }
        }
    }
}
