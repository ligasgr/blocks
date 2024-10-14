package blocks.service.info;

import org.apache.pekko.actor.typed.ActorRef;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.function.BiConsumer;

public interface ServiceInfoProtocol {
    interface Message {

    }

    class GetServiceInfo implements Message {
        public final ActorRef<ServiceInfo> replyTo;

        public GetServiceInfo(final ActorRef<ServiceInfo> replyTo) {
            this.replyTo = replyTo;
        }
    }

    class SetProperty implements Message {
        public final String name;
        public final JsonNode value;

        public SetProperty(final String name, final JsonNode value) {
            this.name = name;
            this.value = value;
        }
    }

    class UpdateCounter implements Message {
        public final String name;
        public final long delta;

        public UpdateCounter(final String name, final long delta) {
            this.name = name;
            this.delta = delta;
        }
    }

    class SubscribeToPropertyUpdates implements Message {
        public final String subscriberName;
        public final BiConsumer<String, JsonNode> consumerFunction;

        public SubscribeToPropertyUpdates(final String subscriberName, final BiConsumer<String, JsonNode> consumerFunction) {
            this.subscriberName = subscriberName;
            this.consumerFunction = consumerFunction;
        }
    }

    class UnSubscribeFromPropertyUpdates implements Message {
        public final String subscriberName;

        public UnSubscribeFromPropertyUpdates(final String subscriberName) {
            this.subscriberName = subscriberName;
        }
    }

    class SubscribeToCounterUpdates implements Message {
        public final String subscriberName;
        public final BiConsumer<String, Long> consumerFunction;

        public SubscribeToCounterUpdates(final String subscriberName, final BiConsumer<String, Long> consumerFunction) {
            this.subscriberName = subscriberName;
            this.consumerFunction = consumerFunction;
        }
    }

    class UnSubscribeFromCounterUpdates implements Message {
        public final String subscriberName;

        public UnSubscribeFromCounterUpdates(final String subscriberName) {
            this.subscriberName = subscriberName;
        }
    }
}
