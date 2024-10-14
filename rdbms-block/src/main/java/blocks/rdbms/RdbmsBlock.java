package blocks.rdbms;

import org.apache.pekko.actor.typed.ActorRef;
import blocks.health.HealthProtocol;
import blocks.service.AbstractBlock;
import blocks.service.Block;
import blocks.service.BlockConfig;
import blocks.service.BlockContext;
import blocks.service.BlockRef;
import blocks.service.FutureUtils;
import blocks.service.SecretsConfig;
import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.pool.ConnectionPoolConfiguration;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;
import io.r2dbc.spi.Option;
import io.r2dbc.spi.ValidationDepth;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static io.r2dbc.spi.ConnectionFactoryOptions.DATABASE;
import static io.r2dbc.spi.ConnectionFactoryOptions.DRIVER;
import static io.r2dbc.spi.ConnectionFactoryOptions.HOST;
import static io.r2dbc.spi.ConnectionFactoryOptions.PASSWORD;
import static io.r2dbc.spi.ConnectionFactoryOptions.PORT;
import static io.r2dbc.spi.ConnectionFactoryOptions.PROTOCOL;
import static io.r2dbc.spi.ConnectionFactoryOptions.USER;

public class RdbmsBlock extends AbstractBlock<ConnectionPool> {
    private final BlockRef<ActorRef<HealthProtocol.Message>> healthBlockRef;
    private final BlockRef<SecretsConfig> secretsConfigBlockRef;
    private final String blockConfigPath;
    private final Map<String, Object> optionalConnectionParameters;

    public RdbmsBlock(final BlockRef<ActorRef<HealthProtocol.Message>> healthBlockRef,
                      final BlockRef<SecretsConfig> secretsConfigBlockRef,
                      final String blockConfigPath) {
        this(healthBlockRef, secretsConfigBlockRef, blockConfigPath, Collections.emptyMap());
    }

    public RdbmsBlock(final BlockRef<ActorRef<HealthProtocol.Message>> healthBlockRef,
                      final BlockRef<SecretsConfig> secretsConfigBlockRef,
                      final String blockConfigPath,
                      final Map<String, Object> optionalConnectionParameters) {
        this.healthBlockRef = healthBlockRef;
        this.secretsConfigBlockRef = secretsConfigBlockRef;
        this.blockConfigPath = blockConfigPath;
        this.optionalConnectionParameters = optionalConnectionParameters;
    }

    @Override
    protected CompletableFuture<ConnectionPool> getBlockOutputFuture(final BlockContext blockContext) {
        Block<ActorRef<HealthProtocol.Message>> healthBlock = blockContext.getBlock(healthBlockRef);
        Block<SecretsConfig> secretsConfigBlock = blockContext.getBlock(secretsConfigBlockRef);
        Optional<ActorRef<HealthProtocol.Message>> maybeHealthActor = healthBlock.getBlockOutput();
        if (maybeHealthActor.isEmpty()) {
            throw new IllegalStateException("Cannot initialize block without health actor");
        }
        Optional<SecretsConfig> maybeSecretsConfig = secretsConfigBlock.getBlockOutput();
        if (maybeSecretsConfig.isEmpty()) {
            throw new IllegalStateException("Cannot initialize block without secrets config");
        }
        SecretsConfig secretsConfig = maybeSecretsConfig.get();
        BlockConfig blockConfig = blockContext.config.getBlockConfig(blockConfigPath);
        final String connectionHealthCheckQuery = blockConfig.getString("connectionHealthCheckQuery");
        final String driver = blockConfig.getString("driver");
        final String protocol = blockConfig.getString("protocol");
        final String host = blockConfig.getString("host");
        final int port = blockConfig.getInt("port");
        final String user = blockConfig.getString("user");
        final String password = secretsConfig.getSecret(blockConfigPath + "." + "password");
        final String database = blockConfig.getString("database");
        final Duration maxIdleTime = blockConfig.getDuration("maxIdleTime");
        final int initialSize = blockConfig.getInt("initialSize");
        final int maxSize = blockConfig.getInt("maxSize");
        final ActorRef<HealthProtocol.Message> healthActor = maybeHealthActor.get();
        final ActorRef<RdbmsHealthCheckActor.Protocol.Message> postgresHealthCheckActor = blockContext.context.spawn(RdbmsHealthCheckActor.behavior(healthActor, blockContext.clock, this, connectionHealthCheckQuery, blockConfigPath), "dbHealthCheckActor-" + blockConfigPath);
        final CompletableFuture<ConnectionPool> resultFuture = FutureUtils.futureOnDefaultDispatcher(blockContext.context, () -> {
            final ConnectionFactoryOptions.Builder connectionFactoryOptionsBuilder = ConnectionFactoryOptions.builder()
                .option(DRIVER, driver)
                .option(PROTOCOL, protocol)
                .option(HOST, host)
                .option(PORT, port)
                .option(USER, user)
                .option(PASSWORD, password)
                .option(DATABASE, database);
            optionalConnectionParameters.forEach((optionName, optionValue) -> connectionFactoryOptionsBuilder.option(Option.valueOf(optionName), optionValue));
            final ConnectionFactory connectionFactory = ConnectionFactories.get(connectionFactoryOptionsBuilder.build());

            final ConnectionPoolConfiguration configuration = ConnectionPoolConfiguration.builder(connectionFactory)
                .name("dbPool-" + blockConfigPath)
                .maxIdleTime(maxIdleTime)
                .initialSize(initialSize)
                .maxSize(maxSize)
                .maxLifeTime(Duration.ofMillis(Long.MIN_VALUE))
                .registerJmx(true)
                .validationDepth(ValidationDepth.LOCAL)
                .build();
            return new ConnectionPool(configuration);
        }).thenCompose(this::validate);
        resultFuture.thenAccept(ignore -> postgresHealthCheckActor.tell(new RdbmsHealthCheckActor.Protocol.CheckHealth()));
        return resultFuture;
    }

    private CompletableFuture<ConnectionPool> validate(final ConnectionPool cp) {
        return cp.warmup()
            .flatMapMany(warmedUp -> Flux.usingWhen(cp.create(),
                c -> c.validate(ValidationDepth.REMOTE),
                Connection::close)
            ).then().toFuture().thenApply(ignored -> cp);
    }

    @Override
    public boolean isMandatory() {
        return true;
    }

}
