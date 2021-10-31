package blocks.service.info;

import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.actor.typed.javadsl.ReceiveBuilder;
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
    private final Map<String, BiConsumer<String, JsonNode>> subscribers = new HashMap<>();

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
                .onMessage(ServiceInfoProtocol.SubscribeToInfoUpdates.class, this::onSubscribe)
                .onMessage(ServiceInfoProtocol.UnSubscribeFromInfoUpdates.class, this::onUnsubscribe)
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
        subscribers.forEach((name, consumer) -> {
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
        subscribers.forEach((subscriberName, consumer) -> {
            try {
                consumer.accept(message.name, LongNode.valueOf(finalNextValue));
            } catch (Exception e) {
                getContext().getLog().error("Failed to run counter change notification for subscriber: " + subscriberName, e);
            }
        });
        return Behaviors.same();
    }

    private Behavior<ServiceInfoProtocol.Message> onSubscribe(final ServiceInfoProtocol.SubscribeToInfoUpdates message) {
        subscribers.put(message.subscriberName, message.consumerFunction);
        return Behaviors.same();
    }

    private Behavior<ServiceInfoProtocol.Message> onUnsubscribe(final ServiceInfoProtocol.UnSubscribeFromInfoUpdates message) {
        subscribers.remove(message.subscriberName);
        return Behaviors.same();
    }
}
