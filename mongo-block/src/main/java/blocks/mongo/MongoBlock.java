package blocks.mongo;

import org.apache.pekko.actor.typed.ActorRef;
import blocks.health.HealthProtocol;
import blocks.service.AbstractBlock;
import blocks.service.Block;
import blocks.service.BlockConfig;
import blocks.service.BlockContext;
import blocks.service.BlockRef;
import blocks.service.FutureUtils;
import blocks.service.SecretsConfig;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoCredential;
import com.mongodb.ServerAddress;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class MongoBlock extends AbstractBlock<MongoClient> {
    private final BlockRef<ActorRef<HealthProtocol.Message>> healthBlockRef;
    private final BlockRef<SecretsConfig> secretsConfigBlockRef;
    private final String blockConfigPath;

    public MongoBlock(final BlockRef<ActorRef<HealthProtocol.Message>> healthBlockRef, final BlockRef<SecretsConfig> secretsConfigBlockRef, String blockConfigPath) {
        this.healthBlockRef = healthBlockRef;
        this.secretsConfigBlockRef = secretsConfigBlockRef;
        this.blockConfigPath = blockConfigPath;
    }

    @Override
    protected CompletableFuture<MongoClient> getBlockOutputFuture(final BlockContext blockContext) {
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
        final List<String> hosts = blockConfig.getStringList("hosts");
        final Duration connectionTimeout = blockConfig.getDuration("connectionTimeout");
        final int port = blockConfig.getInt("port");
        final String user = blockConfig.getString("user");
        final String password = secretsConfig.getSecret(blockConfigPath + "." + "password");
        final String authenticationDb = blockConfig.getString("authenticationDb");
        ActorRef<HealthProtocol.Message> healthActor = maybeHealthActor.get();
        ActorRef<MongoHealthCheckActor.Protocol.Message> mongoHealthCheckActor = blockContext.context.spawn(MongoHealthCheckActor.behavior(healthActor, blockContext.clock, this, blockConfigPath), "mongoHealthCheckActor-" + blockConfigPath);
        CompletableFuture<MongoClient> resultFuture = FutureUtils.futureOnDefaultDispatcher(blockContext.context, () -> {
            MongoCredential credential = MongoCredential.createCredential(user, authenticationDb, password.toCharArray());
            List<ServerAddress> serverAddressList = hosts.stream().map(host -> new ServerAddress(host, port)).collect(Collectors.toList());
            return MongoClients.create(
                    MongoClientSettings.builder()
                            .applyToClusterSettings(builder -> builder.hosts(serverAddressList))
                            .credential(credential)
                            .applyToSocketSettings(b -> b.connectTimeout((int) connectionTimeout.toMillis(), TimeUnit.MILLISECONDS))
                            .build());
        });
        resultFuture.thenAccept(ignore -> mongoHealthCheckActor.tell(new MongoHealthCheckActor.Protocol.CheckHealth()));
        return resultFuture;
    }

    @Override
    public boolean isMandatory() {
        return true;
    }
}
