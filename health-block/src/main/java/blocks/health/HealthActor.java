package blocks.health;

import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.actor.typed.javadsl.ReceiveBuilder;
import blocks.service.BlockStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class HealthActor extends AbstractBehavior<HealthProtocol.Message> {
    private final ZonedDateTime startDateTime;
    private final Clock clock;
    private final Map<String, ComponentHealth> dependencies = new HashMap<>();
    private final Map<String, BlockHealthInfo> blocks = new HashMap<>();

    public static Behavior<HealthProtocol.Message> behavior(final Instant startInstant, final Clock clock) {
        return Behaviors.setup(context -> new HealthActor(context, startInstant, clock));
    }

    private HealthActor(final ActorContext<HealthProtocol.Message> context, final Instant startInstant, final Clock clock) {
        super(context);
        this.startDateTime = ZonedDateTime.ofInstant(startInstant, ZoneId.systemDefault());
        this.clock = clock;
    }

    @Override
    public Receive<HealthProtocol.Message> createReceive() {
        return ReceiveBuilder.<HealthProtocol.Message>create()
                .onMessage(HealthProtocol.GetHealth.class, this::onGetHealth)
                .onMessage(HealthProtocol.RegisterBlock.class, this::onRegisterBlock)
                .onMessage(HealthProtocol.UpdateBlockStatus.class, this::onUpdateBlockStatus)
                .onMessage(HealthProtocol.RegisterComponent.class, this::onRegisterComponent)
                .onMessage(HealthProtocol.UpdateComponentHealth.class, this::onUpdateComponentHealth)
                .build();
    }

    private Behavior<HealthProtocol.Message> onGetHealth(final HealthProtocol.GetHealth m) {
        boolean isHealthy = true;
        boolean isInitialized = true;
        for (BlockHealthInfo blockHealthInfoEntry : blocks.values()) {
            if (blockHealthInfoEntry.mandatory && !(blockHealthInfoEntry.status == BlockStatus.INITIALIZED)) {
                isHealthy = false;
            }
        }
        Collection<ComponentHealth> dependencyList = dependencies.values();
        for (ComponentHealth dependency : dependencyList) {
            if (!dependency.isHealthy) {
                isHealthy = false;
            }
            if (!dependency.isInitialized) {
                isInitialized = false;
            }
        }
        final ServiceHealth serviceHealth = new ServiceHealth(isHealthy, isInitialized, blocks, new ArrayList<>(dependencies.values()), startDateTime, getNow());
        m.replyTo.tell(new HealthProtocol.Health(serviceHealth));
        return Behaviors.same();
    }

    private ZonedDateTime getNow() {
        return ZonedDateTime.now(clock);
    }

    private Behavior<HealthProtocol.Message> onRegisterBlock(final HealthProtocol.RegisterBlock message) {
        blocks.put(message.block, new BlockHealthInfo(BlockStatus.NOT_INITIALIZED, message.mandatory));
        return Behaviors.same();
    }

    private Behavior<HealthProtocol.Message> onUpdateBlockStatus(final HealthProtocol.UpdateBlockStatus message) {
        BlockHealthInfo existing = blocks.get(message.block);
        blocks.put(message.block, new BlockHealthInfo(message.status, existing.mandatory));
        return Behaviors.same();
    }

    private Behavior<HealthProtocol.Message> onRegisterComponent(final HealthProtocol.RegisterComponent message) {
        if (dependencies.containsKey(message.component)) {
            getContext().getLog().error("Tried to register component '{}' again", message.component);
        } else {
            dependencies.put(message.component, new ComponentHealth(message.component, false, false, Optional.empty(), Collections.emptyList(), getNow(), Optional.empty()));
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
            }
            dependencies.put(message.component, message.health);
        } else {
            getContext().getLog().error("Tried to update health of unregistered component '{}'", message.component);
        }
        return Behaviors.same();
    }
}
