package blocks.jms;

import org.apache.pekko.NotUsed;
import org.apache.pekko.stream.connectors.jms.JmsConsumerSettings;
import org.apache.pekko.stream.connectors.jms.JmsProducerSettings;
import org.apache.pekko.stream.connectors.jms.javadsl.JmsConsumerControl;
import org.apache.pekko.stream.connectors.jms.javadsl.JmsProducerStatus;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.stream.javadsl.Source;

import java.util.function.Function;

public interface JmsObjectFactory {
    <T> Source<T, NotUsed> getConsumer(String destinationName, Function<JmsConsumerSettings, Source<T, JmsConsumerControl>> creatorFunction);

    <T> Sink<T, NotUsed> getProducer(String destinationName, Function<JmsProducerSettings, Sink<T, JmsProducerStatus>> creatorFunction);
}
