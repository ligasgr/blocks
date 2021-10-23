package blocks.service.info;

import akka.actor.typed.ActorRef;
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

    class SubscribeToInfoUpdates implements Message {
        public final String subscriberName;
        public final BiConsumer<String, JsonNode> consumerFunction;

        public SubscribeToInfoUpdates(final String subscriberName, final BiConsumer<String, JsonNode> consumerFunction) {
            this.subscriberName = subscriberName;
            this.consumerFunction = consumerFunction;
        }
    }

    class UnSubscribeFromInfoUpdates implements Message {
        public final String subscriberName;

        public UnSubscribeFromInfoUpdates(final String subscriberName) {
            this.subscriberName = subscriberName;
        }
    }
}
