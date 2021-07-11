package blocks.jms;

import akka.Done;
import akka.NotUsed;
import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.stream.Materializer;
import akka.stream.alpakka.jms.JmsConsumerSettings;
import akka.stream.alpakka.jms.JmsProducerSettings;
import akka.stream.alpakka.jms.javadsl.JmsConnectorState;
import akka.stream.alpakka.jms.javadsl.JmsConsumerControl;
import akka.stream.alpakka.jms.javadsl.JmsProducerStatus;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import blocks.health.HealthProtocol;
import blocks.jms.JmsBlockHealthCheckActor.Protocol.DestinationState;
import blocks.service.AbstractBlock;
import blocks.service.BlockConfig;
import blocks.service.BlockContext;
import blocks.service.BlockRef;
import blocks.service.FutureUtils;

import javax.jms.ConnectionFactory;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import java.util.Hashtable;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

import static blocks.jms.JmsBlockHealthCheckActor.DestinationType.CONSUMER;
import static blocks.jms.JmsBlockHealthCheckActor.DestinationType.PRODUCER;

public class JmsBlock extends AbstractBlock<JmsObjectFactory> {
    private final BlockRef<ActorRef<HealthProtocol.Message>> healthBlockRef;
    private final String blockConfigPath;
    private final Optional<CredentialsProvider> maybeCredentialsProvider;

    public JmsBlock(final BlockRef<ActorRef<HealthProtocol.Message>> healthBlockRef, final String blockConfigPath, final Optional<CredentialsProvider> maybeCredentialsProvider) {
        this.healthBlockRef = healthBlockRef;
        this.blockConfigPath = blockConfigPath;
        this.maybeCredentialsProvider = maybeCredentialsProvider;
    }

    @Override
    protected CompletableFuture<JmsObjectFactory> getBlockOutputFuture(final BlockContext blockContext) {
        ActorRef<HealthProtocol.Message> healthActor = blockContext.getBlockOutput(healthBlockRef);
        ActorRef<JmsBlockHealthCheckActor.Protocol.Message> healthCheckActor = blockContext.context.spawn(JmsBlockHealthCheckActor.behavior(healthActor, this, blockConfigPath, blockContext.clock), "jmsBlockHealthCheckActor-" + blockConfigPath);
        return FutureUtils.futureOnDefaultDispatcher(blockContext.context, () -> {
            ConnectionFactory connectionFactory = getConnectionFactory(blockContext.config.getBlockConfig(blockConfigPath), blockContext);
            ActorSystem<Void> system = blockContext.context.getSystem();
            return new JmsObjectFactory() {
                @Override
                public <T> Source<T, NotUsed> getConsumer(String destinationName, final Function<JmsConsumerSettings, Source<T, JmsConsumerControl>> creatorFunction) {
                    healthCheckActor.tell(new DestinationState(destinationName, CONSUMER, JmsConnectorState.Disconnected));
                    Source<T, JmsConsumerControl> source = creatorFunction.apply(JmsConsumerSettings.create(system, connectionFactory));
                    return source.mapMaterializedValue(control -> {
                        control.connectorState().<CompletionStage<Done>>runWith(Sink.<JmsConnectorState>foreach(state -> healthCheckActor.tell(new DestinationState(destinationName, CONSUMER, state))), Materializer.matFromSystem(system));
                        return NotUsed.getInstance();
                    });
                }

                @Override
                public <T> Sink<T, NotUsed> getProducer(String destinationName, final Function<JmsProducerSettings, Sink<T, JmsProducerStatus>> creatorFunction) {
                    healthCheckActor.tell(new DestinationState(destinationName, CONSUMER, JmsConnectorState.Disconnected));
                    Sink<T, JmsProducerStatus> sink = creatorFunction.apply(JmsProducerSettings.create(system, connectionFactory));
                    return sink.mapMaterializedValue(status -> {
                        status.connectorState().<CompletionStage<Done>>runWith(Sink.<JmsConnectorState>foreach(state -> healthCheckActor.tell(new DestinationState(destinationName, PRODUCER, state))), Materializer.matFromSystem(system));
                        return NotUsed.getInstance();
                    });
                }
            };
        });
    }

    private ConnectionFactory getConnectionFactory(final BlockConfig blockConfig, final BlockContext blockContext) {
        Hashtable<String, Object> environment = new Hashtable<>();
        environment.put(InitialContext.INITIAL_CONTEXT_FACTORY, blockConfig.getString("initialContextFactory"));
        environment.put(InitialContext.PROVIDER_URL, blockConfig.getString("providerUrl"));
        if (blockConfig.hasPath("securityPrincipal")) {
            environment.put(Context.SECURITY_PRINCIPAL, blockConfig.getString("securityPrincipal"));
        }
        maybeCredentialsProvider.ifPresent(credentialsProvider -> environment.put(Context.SECURITY_CREDENTIALS, credentialsProvider.getCredentials(blockContext)));
        try {
            InitialContext initialContext = new InitialContext(environment);
            return (ConnectionFactory) initialContext.lookup("ConnectionFactory");
        } catch (NamingException e) {
            throw new IllegalArgumentException("Failed to look up jms connection factory", e);
        }
    }

    @Override
    public boolean isMandatory() {
        return true;
    }
}
