package blocks.couchbase.sdk3;

import org.apache.pekko.actor.typed.ActorRef;
import blocks.health.HealthProtocol;
import blocks.service.AbstractBlock;
import blocks.service.Block;
import blocks.service.BlockConfig;
import blocks.service.BlockContext;
import blocks.service.BlockRef;
import blocks.service.FutureUtils;
import blocks.service.SecretsConfig;
import com.couchbase.client.core.env.TimeoutConfig;
import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.ReactiveCluster;
import com.couchbase.client.java.env.ClusterEnvironment;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static com.couchbase.client.java.ClusterOptions.clusterOptions;

public class CouchbaseSdk3Block extends AbstractBlock<ReactiveCluster> {
    private final BlockRef<ActorRef<HealthProtocol.Message>> healthBlockRef;
    private final BlockRef<SecretsConfig> secretsConfigBlockRef;
    private final String blockConfigPath;

    public CouchbaseSdk3Block(final BlockRef<ActorRef<HealthProtocol.Message>> healthBlockRef,
                              final BlockRef<SecretsConfig> secretsConfigBlockRef,
                              final String blockConfigPath) {
        this.healthBlockRef = healthBlockRef;
        this.secretsConfigBlockRef = secretsConfigBlockRef;
        this.blockConfigPath = blockConfigPath;
    }

    @Override
    protected CompletableFuture<ReactiveCluster> getBlockOutputFuture(final BlockContext blockContext) {
        final Block<ActorRef<HealthProtocol.Message>> healthBlock = blockContext.getBlock(healthBlockRef);
        final Block<SecretsConfig> secretsConfigBlock = blockContext.getBlock(secretsConfigBlockRef);
        final Optional<ActorRef<HealthProtocol.Message>> maybeHealthActor = healthBlock.getBlockOutput();
        if (maybeHealthActor.isEmpty()) {
            throw new IllegalStateException("Cannot initialize block without health actor");
        }
        final Optional<SecretsConfig> maybeSecretsConfig = secretsConfigBlock.getBlockOutput();
        if (maybeSecretsConfig.isEmpty()) {
            throw new IllegalStateException("Cannot initialize block without secrets config");
        }
        final SecretsConfig secretsConfig = maybeSecretsConfig.get();
        final BlockConfig blockConfig = blockContext.config.getBlockConfig(blockConfigPath);
        final Optional<Integer> maybePort = blockConfig.hasPath("port")
                ? Optional.of(blockConfig.getInt("port"))
                : Optional.empty();
        final List<String> hosts = blockConfig.getStringList("hosts");
        final Duration queryTimeout = blockConfig.getDuration("queryTimeout");
        final Duration connectionTimeout = blockConfig.getDuration("connectionTimeout");
        final Duration waitUntilReadyTimeout = blockConfig.getDuration("waitUntilReadyTimeout");
        final String user = blockConfig.getString("user");
        final String password = secretsConfig.getSecret(blockConfigPath + "." + "password");
        final Duration healthyCheckDelay = blockConfig.hasPath("healthyCheckDelay") ? blockConfig.getDuration("healthyCheckDelay") : Duration.ofSeconds(15);
        final Duration unhealthyCheckDelay = blockConfig.hasPath("unhealthyCheckDelay") ? blockConfig.getDuration("unhealthyCheckDelay") : Duration.ofSeconds(3);
        final ActorRef<HealthProtocol.Message> healthActor = maybeHealthActor.get();
        final ActorRef<CouchbaseSdk3HealthCheckActor.Protocol.Message> couchbaseHealthCheckActor = blockContext.context.spawn(CouchbaseSdk3HealthCheckActor.behavior(healthActor, blockContext.clock, this, blockConfigPath, healthyCheckDelay, unhealthyCheckDelay), "couchbaseHealthCheckActor-" + blockConfigPath);
        final CompletableFuture<ReactiveCluster> resultFuture = FutureUtils.futureOnDefaultDispatcher(blockContext.context, () -> {
            final ClusterEnvironment env = ClusterEnvironment.builder()
                .timeoutConfig(TimeoutConfig.connectTimeout(connectionTimeout).queryTimeout(queryTimeout)).build();
            final String connectionString = hosts.stream().map(h -> maybePort.map(p -> h + ":" + p).orElse(h)).collect(Collectors.joining(","));
            final Cluster cluster = Cluster.connect(connectionString,
                clusterOptions(user, password).environment(env));
            cluster.waitUntilReady(waitUntilReadyTimeout);
            return cluster.reactive();
        });
        resultFuture.thenAccept(ignore -> couchbaseHealthCheckActor.tell(new CouchbaseSdk3HealthCheckActor.Protocol.CheckHealth()));
        return resultFuture;
    }

    @Override
    public boolean isMandatory() {
        return true;
    }
}
