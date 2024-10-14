package blocks.jms;

import blocks.health.ComponentHealth;
import blocks.health.HealthProtocol;
import blocks.service.BlockStatus;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.Receive;
import org.apache.pekko.actor.typed.javadsl.ReceiveBuilder;
import org.apache.pekko.japi.Pair;
import org.apache.pekko.stream.connectors.jms.javadsl.JmsConnectorState;

import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.stream.Collectors;

import static org.apache.pekko.stream.connectors.jms.javadsl.JmsConnectorState.Connected;

public class JmsBlockHealthCheckActor extends AbstractBehavior<JmsBlockHealthCheckActor.Protocol.Message> {
    private static final Protocol.CheckHealth CHECK_HEALTH = new Protocol.CheckHealth();

    private final ActorRef<HealthProtocol.Message> healthActor;
    private final JmsBlock jmsBlock;
    private final String blockConfigPath;
    private final Clock clock;
    private final Map<String, Pair<Boolean, JmsConnectorState>> destinationHealth = new HashMap<>();

    public static Behavior<Protocol.Message> behavior(final ActorRef<HealthProtocol.Message> healthActor, final JmsBlock jmsBlock, final String blockConfigPath, final Clock clock) {
        return Behaviors.setup(context -> new JmsBlockHealthCheckActor(context, healthActor, jmsBlock, blockConfigPath, clock));
    }

    private JmsBlockHealthCheckActor(final ActorContext<Protocol.Message> context, final ActorRef<HealthProtocol.Message> healthActor, final JmsBlock jmsBlock, final String blockConfigPath, final Clock clock) {
        super(context);
        this.healthActor = healthActor;
        this.jmsBlock = jmsBlock;
        this.blockConfigPath = blockConfigPath;
        this.clock = clock;
        context.getSelf().tell(CHECK_HEALTH);
        healthActor.tell(new HealthProtocol.RegisterComponent(componentName()));
    }

    @Override
    public Receive<Protocol.Message> createReceive() {
        return ReceiveBuilder.<Protocol.Message>create()
                .onMessage(Protocol.CheckHealth.class, this::onCheckHealth)
                .onMessage(Protocol.HealthInfo.class, this::onHealthInfo)
                .onMessage(Protocol.DestinationState.class, this::onDestinationState)
                .build();
    }

    private Behavior<Protocol.Message> onCheckHealth(final Protocol.CheckHealth msg) {
        if (jmsBlock.getStatus() != BlockStatus.INITIALIZED) {
            getContext().getSelf().tell(new Protocol.HealthInfo(false, jmsBlock.failureInfo()));
        }
        return Behaviors.same();
    }

    private Behavior<Protocol.Message> onDestinationState(final Protocol.DestinationState state) {
        destinationHealth.put(state.name + "-" + state.type.name(), Pair.create(state.state == Connected, state.state));
        getContext().getSelf().tell(new Protocol.HealthInfo(true, jmsBlock.failureInfo()));
        return Behaviors.same();
    }

    private Behavior<Protocol.Message> onHealthInfo(final Protocol.HealthInfo msg) {
        if (msg.exception != null) {
            String message = msg.exception.getMessage() != null ? msg.exception.getMessage() : msg.exception.getClass().getCanonicalName();
            ComponentHealth health = new ComponentHealth(componentName(), false, msg.initialized, Optional.of(message), Collections.emptyList(), ZonedDateTime.now(clock), OptionalLong.empty());
            healthActor.tell(new HealthProtocol.UpdateComponentHealth(componentName(), health));
        } else {
            final List<ComponentHealth> dependencies = destinationHealth.entrySet().stream()
                    .map(endpointDetails -> new ComponentHealth(endpointDetails.getKey(), endpointDetails.getValue().first(), true,
                            endpointDetails.getValue().first() ? Optional.empty() : Optional.of(endpointDetails.getValue().second().name()), Collections.emptyList(), ZonedDateTime.now(clock), OptionalLong.empty()))
                    .collect(Collectors.toList());
            boolean allEndpointsHealthy = dependencies.stream().allMatch(c -> c.isHealthy);
            boolean isHealthy = !dependencies.isEmpty() && allEndpointsHealthy;
            ComponentHealth health = new ComponentHealth(componentName(), isHealthy, msg.initialized, Optional.empty(), dependencies, ZonedDateTime.now(clock), OptionalLong.empty());
            healthActor.tell(new HealthProtocol.UpdateComponentHealth(componentName(), health));
        }
        return Behaviors.same();
    }

    private String componentName() {
        return "jms-" + blockConfigPath;
    }

    public enum DestinationType {
        PRODUCER, CONSUMER
    }

    public interface Protocol {

        interface Message {
        }

        class CheckHealth implements Message {

        }

        class DestinationState implements Message {
            public final String name;
            public final DestinationType type;
            public final JmsConnectorState state;

            public DestinationState(final String name, final DestinationType type, final JmsConnectorState state) {
                this.name = name;
                this.type = type;
                this.state = state;
            }
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
