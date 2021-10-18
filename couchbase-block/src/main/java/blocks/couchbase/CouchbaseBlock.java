package blocks.couchbase;

import akka.actor.typed.ActorRef;
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

import static com.couchbase.client.java.ClusterOptions.clusterOptions;

public class CouchbaseBlock extends AbstractBlock<ReactiveCluster> {
    private final BlockRef<ActorRef<HealthProtocol.Message>> healthBlockRef;
    private final BlockRef<SecretsConfig> secretsConfigBlockRef;
    private final String blockConfigPath;

    public CouchbaseBlock(final BlockRef<ActorRef<HealthProtocol.Message>> healthBlockRef, final BlockRef<SecretsConfig> secretsConfigBlockRef, String blockConfigPath) {
        this.healthBlockRef = healthBlockRef;
        this.secretsConfigBlockRef = secretsConfigBlockRef;
        this.blockConfigPath = blockConfigPath;
    }

    @Override
    protected CompletableFuture<ReactiveCluster> getBlockOutputFuture(final BlockContext blockContext) {
        Block<ActorRef<HealthProtocol.Message>> healthBlock = blockContext.getBlock(healthBlockRef);
        Block<SecretsConfig> secretsConfigBlock = blockContext.getBlock(secretsConfigBlockRef);
        Optional<ActorRef<HealthProtocol.Message>> maybeHealthActor = healthBlock.getBlockOutput();
        if (!maybeHealthActor.isPresent()) {
            throw new IllegalStateException("Cannot initialize block without health actor");
        }
        Optional<SecretsConfig> maybeSecretsConfig = secretsConfigBlock.getBlockOutput();
        if (!maybeSecretsConfig.isPresent()) {
            throw new IllegalStateException("Cannot initialize block without secrets config");
        }
        SecretsConfig secretsConfig = maybeSecretsConfig.get();
        BlockConfig blockConfig = blockContext.config.getBlockConfig(blockConfigPath);
        final List<String> hosts = blockConfig.getStringList("hosts");
        final Duration queryTimeout = blockConfig.getDuration("queryTimeout");
        final Duration connectionTimeout = blockConfig.getDuration("connectionTimeout");
        final Duration waitUntilReadyTimeout = blockConfig.getDuration("waitUntilReadyTimeout");
        final String user = blockConfig.getString("user");
        final String password = secretsConfig.getSecret(blockConfigPath + "." + "password");
        ActorRef<HealthProtocol.Message> healthActor = maybeHealthActor.get();
        ActorRef<CouchbaseHealthCheckActor.Protocol.Message> couchbaseHealthCheckActor = blockContext.context.spawn(CouchbaseHealthCheckActor.behavior(healthActor, blockContext.clock, this, blockConfigPath), "couchbaseHealthCheckActor-" + blockConfigPath);
        CompletableFuture<ReactiveCluster> resultFuture = FutureUtils.futureOnDefaultDispatcher(blockContext.context, () -> {
            final ClusterEnvironment env = ClusterEnvironment.builder()
                    .timeoutConfig(TimeoutConfig.connectTimeout(connectionTimeout).queryTimeout(queryTimeout)).build();
            final Cluster cluster = Cluster.connect(String.join(",", hosts),
                    clusterOptions(user, password).environment(env));
            cluster.waitUntilReady(waitUntilReadyTimeout);
            return cluster.reactive();
        });
        resultFuture.thenAccept(ignore -> couchbaseHealthCheckActor.tell(new CouchbaseHealthCheckActor.Protocol.CheckHealth()));
        return resultFuture;
    }

    @Override
    public boolean isMandatory() {
        return true;
    }
}
