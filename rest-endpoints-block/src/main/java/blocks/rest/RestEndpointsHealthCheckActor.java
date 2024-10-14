package blocks.rest;

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
import blocks.rest.RestEndpointsHealthCheckActor.Protocol.Message;

import java.time.Clock;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.stream.Collectors;

public class RestEndpointsHealthCheckActor extends AbstractBehavior<Message> {
    private static final Protocol.CheckHealth CHECK_HEALTH = new Protocol.CheckHealth();
    private static final String COMPONENT = "rest";
    private final TimerScheduler<Message> timer;
    private final RestEndpointsHealthChecks restEndpointsHealthChecks;
    private final ActorRef<HealthProtocol.Message> healthActor;
    private final Clock clock;
    private final Duration restHealthCheckDelay;

    public static Behavior<Message> behavior(final RestEndpointsHealthChecks restEndpointsHealthChecks,
                                             final ActorRef<HealthProtocol.Message> healthActor,
                                             final Clock clock,
                                             final Duration restHealthCheckDelay) {
        return Behaviors.setup(ctx -> Behaviors.withTimers(timer -> new RestEndpointsHealthCheckActor(ctx, timer, restEndpointsHealthChecks, healthActor, clock, restHealthCheckDelay)));
    }

    public RestEndpointsHealthCheckActor(final ActorContext<Message> context,
                                         final TimerScheduler<Message> timer,
                                         final RestEndpointsHealthChecks restEndpointsHealthChecks,
                                         final ActorRef<HealthProtocol.Message> healthActor,
                                         final Clock clock,
                                         final Duration restHealthCheckDelay) {
        super(context);
        this.timer = timer;
        this.restEndpointsHealthChecks = restEndpointsHealthChecks;
        this.healthActor = healthActor;
        this.clock = clock;
        timer.startSingleTimer(CHECK_HEALTH, Duration.ofSeconds(3L));
        healthActor.tell(new HealthProtocol.RegisterComponent(COMPONENT));
        this.restHealthCheckDelay = restHealthCheckDelay;
    }

    @Override
    public Receive<Message> createReceive() {
        return ReceiveBuilder.<Message>create()
                .onMessage(Protocol.CheckHealth.class, this::onCheckHealth)
                .onMessage(Protocol.HealthInfo.class, this::onHealthInfo)
                .build();
    }

    private Behavior<Message> onCheckHealth(final Protocol.CheckHealth msg) {
        getContext().pipeToSelf(restEndpointsHealthChecks.run(), Protocol.HealthInfo::new);
        return Behaviors.same();
    }

    private Behavior<Message> onHealthInfo(final Protocol.HealthInfo msg) {
        if (msg.exception != null) {
            String message = msg.exception.getMessage() != null ? msg.exception.getMessage() : msg.exception.getClass().getCanonicalName();
            ComponentHealth health = new ComponentHealth(COMPONENT, false, true, Optional.of(message), Collections.emptyList(), ZonedDateTime.now(clock), OptionalLong.empty());
            healthActor.tell(new HealthProtocol.UpdateComponentHealth(COMPONENT, health));
        } else {
            boolean isHealthy = msg.result.values().stream().allMatch(s -> s.isHealthy);
            final List<ComponentHealth> details = msg.result.values().stream()
                    .map(endpointStatus -> new ComponentHealth(endpointStatus.name, endpointStatus.isHealthy, true, endpointStatus.error, Collections.emptyList(), endpointStatus.lastChecked, OptionalLong.of(endpointStatus.checkDurationInNanos)))
                    .collect(Collectors.toList());
            ComponentHealth health = new ComponentHealth(COMPONENT, isHealthy, true, Optional.empty(), details, ZonedDateTime.now(clock), OptionalLong.empty());
            healthActor.tell(new HealthProtocol.UpdateComponentHealth(COMPONENT, health));
        }
        timer.startSingleTimer(CHECK_HEALTH, restHealthCheckDelay);
        return Behaviors.same();
    }

    public interface Protocol {
        interface Message {
        }

        class CheckHealth implements Message {
        }

        class HealthInfo implements Message {
            public final Map<String, EndpointStatus> result;
            public final Throwable exception;

            public HealthInfo(final Map<String, EndpointStatus> result, final Throwable exception) {
                this.result = result;
                this.exception = exception;
            }
        }
    }
}
