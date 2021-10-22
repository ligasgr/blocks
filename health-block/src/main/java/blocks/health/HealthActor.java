package blocks.health;

import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.actor.typed.javadsl.ReceiveBuilder;
import akka.japi.Pair;
import blocks.service.BlockStatus;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;

import static java.util.Objects.requireNonNull;

public class HealthActor extends AbstractBehavior<HealthProtocol.Message> {
    private final ZonedDateTime startDateTime;
    private final Clock clock;
    private final Map<String, JsonNode> staticProperties;
    private final Map<String, ComponentHealth> dependencies = new HashMap<>();
    private final Map<String, BlockHealthInfo> blocks = new HashMap<>();
    private final Map<String, Consumer<Pair<Boolean, ComponentHealth>>> subscribers = new HashMap<>();

    private static final BinaryOperator<Pair<Boolean, Boolean>> HEALTH_INFO_REDUCE_OPERATOR = (current, incoming) -> Pair.create(current.first() && incoming.first(), current.second() && incoming.second());

    public static Behavior<HealthProtocol.Message> behavior(final Instant startInstant, final Clock clock, final Map<String, JsonNode> staticProperties) {
        return Behaviors.setup(context -> new HealthActor(context,
                requireNonNull(startInstant),
                requireNonNull(clock),
                requireNonNull(staticProperties)));
    }

    private HealthActor(final ActorContext<HealthProtocol.Message> context, final Instant startInstant, final Clock clock, final Map<String, JsonNode> staticProperties) {
        super(context);
        this.startDateTime = ZonedDateTime.ofInstant(startInstant, ZoneId.systemDefault());
        this.clock = clock;
        this.staticProperties = staticProperties;
    }

    @Override
    public Receive<HealthProtocol.Message> createReceive() {
        return ReceiveBuilder.<HealthProtocol.Message>create()
                .onMessage(HealthProtocol.GetHealth.class, this::onGetHealth)
                .onMessage(HealthProtocol.RegisterBlock.class, this::onRegisterBlock)
                .onMessage(HealthProtocol.UpdateBlockStatus.class, this::onUpdateBlockStatus)
                .onMessage(HealthProtocol.RegisterComponent.class, this::onRegisterComponent)
                .onMessage(HealthProtocol.UpdateComponentHealth.class, this::onUpdateComponentHealth)
                .onMessage(HealthProtocol.SubscribeToHealthChangeUpdates.class, this::onSubscribe)
                .onMessage(HealthProtocol.UnSubscribeFromHealthChangeUpdates.class, this::onUnsubscribe)
                .build();
    }

    private Behavior<HealthProtocol.Message> onGetHealth(final HealthProtocol.GetHealth m) {
        final Pair<Boolean, Boolean> healthyAndInitialized = getHealthyAndInitialized();
        final ServiceHealth serviceHealth = new ServiceHealth(healthyAndInitialized.first(), healthyAndInitialized.second(), blocks, new ArrayList<>(dependencies.values()), startDateTime, getNow(), staticProperties);
        m.replyTo.tell(new HealthProtocol.Health(serviceHealth));
        return Behaviors.same();
    }

    private Pair<Boolean, Boolean> getHealthyAndInitialized() {
        boolean allMandatoryBlocksInitialized = blocks.values().stream()
                .filter(b -> b.mandatory)
                .allMatch(b -> b.status == BlockStatus.INITIALIZED);
        final Pair<Boolean, Boolean> isHealthyAndInitialized = Pair.create(allMandatoryBlocksInitialized, true);
        return dependencies.values().stream()
                .map(d -> Pair.create(d.isHealthy, d.isInitialized))
                .reduce(isHealthyAndInitialized, HEALTH_INFO_REDUCE_OPERATOR);
    }

    private ZonedDateTime getNow() {
        return ZonedDateTime.now(clock);
    }

    private Behavior<HealthProtocol.Message> onRegisterBlock(final HealthProtocol.RegisterBlock message) {
        blocks.put(message.block, new BlockHealthInfo(BlockStatus.NOT_INITIALIZED, message.mandatory));
        return Behaviors.same();
    }

    private Behavior<HealthProtocol.Message> onUpdateBlockStatus(final HealthProtocol.UpdateBlockStatus message) {
        final BlockHealthInfo existing = blocks.get(message.block);
        blocks.put(message.block, new BlockHealthInfo(message.status, existing.mandatory));
        return Behaviors.same();
    }

    private Behavior<HealthProtocol.Message> onRegisterComponent(final HealthProtocol.RegisterComponent message) {
        if (dependencies.containsKey(message.component)) {
            getContext().getLog().error("Tried to register component '{}' again", message.component);
        } else {
            ComponentHealth componentHealth = new ComponentHealth(message.component, false, false, Optional.empty(), Collections.emptyList(), getNow(), OptionalLong.empty());
            dependencies.put(message.component, componentHealth);
            notifyOfHealthChange(componentHealth);
        }
        return Behaviors.same();
    }

    private Behavior<HealthProtocol.Message> onUpdateComponentHealth(final HealthProtocol.UpdateComponentHealth message) {
        if (dependencies.containsKey(message.component)) {
            if (dependencies.get(message.component).isInitialized != message.health.isInitialized) {
                getContext().getLog().info("Changing initialization status of component '{}' to '{}'", message.component, message.health.isInitialized ? "initialized" : "uninitialized");
            }
            if (dependencies.get(message.component).isHealthy != message.health.isHealthy) {
                getContext().getLog().info("Changing health of component '{}' to '{}'", message.component, message.health.isHealthy ? "healthy" : "unhealthy");
                notifyOfHealthChange(message.health);
            }
            dependencies.put(message.component, message.health);
        } else {
            getContext().getLog().error("Tried to update health of unregistered component '{}'", message.component);
        }
        return Behaviors.same();
    }

    private void notifyOfHealthChange(final ComponentHealth componentHealth) {
        if (!subscribers.isEmpty()) {
            final Pair<Boolean, Boolean> healthyAndInitialized = getHealthyAndInitialized();
            final Boolean healthy = healthyAndInitialized.first();
            subscribers.forEach((subscriber, consumer) -> {
                consumer.accept(Pair.create(healthy, componentHealth));
            });
        }
    }

    private Behavior<HealthProtocol.Message> onSubscribe(final HealthProtocol.SubscribeToHealthChangeUpdates message) {
        subscribers.put(message.subscriberName, message.consumerFunction);
        return Behaviors.same();
    }

    private Behavior<HealthProtocol.Message> onUnsubscribe(final HealthProtocol.UnSubscribeFromHealthChangeUpdates message) {
        subscribers.remove(message.subscriberName);
        return Behaviors.same();
    }
}
