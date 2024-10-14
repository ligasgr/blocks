package blocks.service.info;

import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.Receive;
import org.apache.pekko.actor.typed.javadsl.ReceiveBuilder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.LongNode;

import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

import static java.util.Objects.requireNonNull;

public class ServiceInfoActor extends AbstractBehavior<ServiceInfoProtocol.Message> {
    private final Clock clock;
    private final Map<String, Long> counters = new HashMap<>();
    private final Map<String, JsonNode> properties = new HashMap<>();
    private final Map<String, BiConsumer<String, JsonNode>> propertyUpdateSubscribers = new HashMap<>();
    private final Map<String, BiConsumer<String, Long>> counterUpdateSubscribers = new HashMap<>();

    public static Behavior<ServiceInfoProtocol.Message> behavior(final Clock clock, final Map<String, JsonNode> staticProperties) {
        return Behaviors.setup(context -> new ServiceInfoActor(context,
                requireNonNull(clock),
                requireNonNull(staticProperties)));
    }

    private ServiceInfoActor(final ActorContext<ServiceInfoProtocol.Message> context, final Clock clock, final Map<String, JsonNode> staticProperties) {
        super(context);
        this.clock = clock;
        this.properties.putAll(staticProperties);
    }

    @Override
    public Receive<ServiceInfoProtocol.Message> createReceive() {
        return ReceiveBuilder.<ServiceInfoProtocol.Message>create()
                .onMessage(ServiceInfoProtocol.GetServiceInfo.class, this::onGetServiceInfo)
                .onMessage(ServiceInfoProtocol.SetProperty.class, this::onSetProperty)
                .onMessage(ServiceInfoProtocol.UpdateCounter.class, this::onUpdateCounter)
                .onMessage(ServiceInfoProtocol.SubscribeToPropertyUpdates.class, this::onSubscribeToPropertyUpdates)
                .onMessage(ServiceInfoProtocol.UnSubscribeFromPropertyUpdates.class, this::onUnsubscribeFromPropertyUpdates)
                .onMessage(ServiceInfoProtocol.SubscribeToCounterUpdates.class, this::onSubscribeToCounterUpdates)
                .onMessage(ServiceInfoProtocol.UnSubscribeFromCounterUpdates.class, this::onUnsubscribeFromCounterUpdates)
                .build();
    }

    private Behavior<ServiceInfoProtocol.Message> onGetServiceInfo(final ServiceInfoProtocol.GetServiceInfo m) {
        final Map<String, JsonNode> allInfo = new HashMap<>(properties);
        counters.forEach((name, value) -> allInfo.put(name, LongNode.valueOf(value)));
        m.replyTo.tell(new ServiceInfo(allInfo, getNow()));
        return Behaviors.same();
    }

    private ZonedDateTime getNow() {
        return ZonedDateTime.now(clock);
    }

    private Behavior<ServiceInfoProtocol.Message> onSetProperty(final ServiceInfoProtocol.SetProperty message) {
        properties.put(message.name, message.value);
        propertyUpdateSubscribers.forEach((name, consumer) -> {
            try {
                consumer.accept(message.name, message.value);
            } catch (Exception e) {
                getContext().getLog().error("Failed to run static property change notification for subscriber: " + name, e);
            }
        });
        return Behaviors.same();
    }

    private Behavior<ServiceInfoProtocol.Message> onUpdateCounter(final ServiceInfoProtocol.UpdateCounter message) {
        String name = message.name;
        final Long currentValue = counters.getOrDefault(name, 0L);
        long nextValue = currentValue + message.delta;
        if (nextValue < 0) {
            nextValue = 0L;
        }
        getContext().getLog().trace("{}={}", name, nextValue);
        final long finalNextValue = nextValue;
        counters.put(name, finalNextValue);
        counterUpdateSubscribers.forEach((subscriberName, consumer) -> {
            try {
                consumer.accept(message.name, finalNextValue);
            } catch (Exception e) {
                getContext().getLog().error("Failed to run counter change notification for subscriber: " + subscriberName, e);
            }
        });
        return Behaviors.same();
    }

    private Behavior<ServiceInfoProtocol.Message> onSubscribeToPropertyUpdates(final ServiceInfoProtocol.SubscribeToPropertyUpdates message) {
        propertyUpdateSubscribers.put(message.subscriberName, message.consumerFunction);
        return Behaviors.same();
    }

    private Behavior<ServiceInfoProtocol.Message> onUnsubscribeFromPropertyUpdates(final ServiceInfoProtocol.UnSubscribeFromPropertyUpdates message) {
        propertyUpdateSubscribers.remove(message.subscriberName);
        return Behaviors.same();
    }

    private Behavior<ServiceInfoProtocol.Message> onSubscribeToCounterUpdates(final ServiceInfoProtocol.SubscribeToCounterUpdates message) {
        counterUpdateSubscribers.put(message.subscriberName, message.consumerFunction);
        return Behaviors.same();
    }

    private Behavior<ServiceInfoProtocol.Message> onUnsubscribeFromCounterUpdates(final ServiceInfoProtocol.UnSubscribeFromCounterUpdates message) {
        counterUpdateSubscribers.remove(message.subscriberName);
        return Behaviors.same();
    }
}
