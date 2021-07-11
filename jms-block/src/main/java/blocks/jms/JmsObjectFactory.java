package blocks.jms;

import akka.NotUsed;
import akka.stream.alpakka.jms.JmsConsumerSettings;
import akka.stream.alpakka.jms.JmsProducerSettings;
import akka.stream.alpakka.jms.javadsl.JmsConsumerControl;
import akka.stream.alpakka.jms.javadsl.JmsProducerStatus;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;

import java.util.function.Function;

public interface JmsObjectFactory {
    <T> Source<T, NotUsed> getConsumer(String destinationName, Function<JmsConsumerSettings, Source<T, JmsConsumerControl>> creatorFunction);

    <T> Sink<T, NotUsed> getProducer(String destinationName, Function<JmsProducerSettings, Sink<T, JmsProducerStatus>> creatorFunction);
}
