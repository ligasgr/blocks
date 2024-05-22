package blocks.couchbase.sdk2;

import org.apache.pekko.actor.typed.ActorRef;
import blocks.health.HealthProtocol;
import blocks.service.AbstractBlock;
import blocks.service.Block;
import blocks.service.BlockConfig;
import blocks.service.BlockContext;
import blocks.service.BlockRef;
import blocks.service.FutureUtils;
import blocks.service.SecretsConfig;
import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.CouchbaseCluster;
import com.couchbase.client.java.cluster.BucketSettings;
import com.couchbase.client.java.env.CouchbaseEnvironment;
import com.couchbase.client.java.env.DefaultCouchbaseEnvironment;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

public class CouchbaseSdk2Block extends AbstractBlock<Cluster> {
    private final BlockRef<ActorRef<HealthProtocol.Message>> healthBlockRef;
    private final BlockRef<SecretsConfig> secretsConfigBlockRef;
    private final String blockConfigPath;

    public CouchbaseSdk2Block(final BlockRef<ActorRef<HealthProtocol.Message>> healthBlockRef, final BlockRef<SecretsConfig> secretsConfigBlockRef, String blockConfigPath) {
        this.healthBlockRef = healthBlockRef;
        this.secretsConfigBlockRef = secretsConfigBlockRef;
        this.blockConfigPath = blockConfigPath;
    }

    @Override
    protected CompletableFuture<Cluster> getBlockOutputFuture(final BlockContext blockContext) {
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
        final Optional<Integer> maybePort = blockConfig.hasPath("port")
                ? Optional.of(blockConfig.getInt("port"))
                : Optional.empty();
        final List<String> hosts = blockConfig.getStringList("hosts");
        final Duration autoreleaseAfter = blockConfig.getDuration("autoreleaseAfter");
        final Duration queryTimeout = blockConfig.getDuration("queryTimeout");
        final Duration maxRequestLifetime = blockConfig.getDuration("maxRequestLifetime");
        final Duration connectionTimeout = blockConfig.getDuration("connectionTimeout");
        final Duration socketConnectTimeout = blockConfig.getDuration("socketConnectTimeout");
        final Duration waitUntilReadyTimeout = blockConfig.getDuration("waitUntilReadyTimeout");
        final String user = blockConfig.getString("user");
        final Optional<String> bucketForHealthCheck = blockConfig.hasPath("bucketForHealthCheck") ? Optional.of(blockConfig.getString("bucketForHealthCheck")) : Optional.empty();
        final String password = secretsConfig.getSecret(blockConfigPath + "." + "password");
        final Duration healthyCheckDelay = blockConfig.hasPath("healthyCheckDelay") ? blockConfig.getDuration("healthyCheckDelay") : Duration.ofSeconds(15);
        final Duration unhealthyCheckDelay = blockConfig.hasPath("unhealthyCheckDelay") ? blockConfig.getDuration("unhealthyCheckDelay") : Duration.ofSeconds(3);
        ActorRef<HealthProtocol.Message> healthActor = maybeHealthActor.get();
        ActorRef<CouchbaseSdk2HealthCheckActor.Protocol.Message> couchbaseHealthCheckActor = blockContext.context.spawn(CouchbaseSdk2HealthCheckActor.behavior(healthActor, blockContext.clock, this, blockConfigPath, healthyCheckDelay, unhealthyCheckDelay), "couchbaseHealthCheckActor-" + blockConfigPath);
        CompletableFuture<Cluster> resultFuture = FutureUtils.futureOnDefaultDispatcher(blockContext.context, () -> {
            final CouchbaseEnvironment env = DefaultCouchbaseEnvironment.builder()
                    .autoreleaseAfter(autoreleaseAfter.toMillis())
                    .queryTimeout(queryTimeout.toMillis())
                    .maxRequestLifetime(maxRequestLifetime.toMillis())
                    .connectTimeout(connectionTimeout.toMillis())
                    .socketConnectTimeout(Long.valueOf(socketConnectTimeout.toMillis()).intValue())
                    .build();
            return Failsafe.with(getRetryPolicy(waitUntilReadyTimeout)).get(() -> initializeCluster(hosts, user, password, env, maybePort));
        });
        final Function<Cluster, Optional<String>> bucketNameObservableCreator = bucketForHealthCheck.isPresent()
                ? ignored -> bucketForHealthCheck
                : cluster -> {
            List<BucketSettings> buckets = cluster.clusterManager().getBuckets();
            return buckets.isEmpty() ? Optional.empty() : Optional.of(buckets.get(0).name());
        };
        final long startMillis = blockContext.clock.millis();
        return resultFuture
                .thenCompose(cluster -> {
                            final Duration timeLeft = waitUntilReadyTimeout.minus(Duration.ofMillis(blockContext.clock.millis() - startMillis));
                            return Failsafe.with(getRetryPolicy(timeLeft)).getAsync(() -> {
                                        final Optional<String> maybeBucketName = bucketNameObservableCreator.apply(cluster);
                                        return maybeBucketName.map(cluster::openBucket).orElseThrow(() -> new IllegalStateException("No bucket found to open"));
                                    })
                                    .thenAccept(bucket -> couchbaseHealthCheckActor.tell(new CouchbaseSdk2HealthCheckActor.Protocol.CheckHealth()))
                                    .thenApply(ignored -> cluster);
                        }
                );
    }

    private <T> RetryPolicy<T> getRetryPolicy(final Duration waitUntilReadyTimeout) {
        return new RetryPolicy<T>()
                .withBackoff(100, 6400, ChronoUnit.MILLIS)
                .withMaxDuration(waitUntilReadyTimeout)
                .withMaxRetries(-1);
    }

    private CouchbaseCluster initializeCluster(final List<String> hosts, final String user, final String password, final CouchbaseEnvironment env, final Optional<Integer> maybePort) {
        final String connectionString = hosts.stream().map(h -> maybePort.map(p -> h + ":" + p).orElse(h)).collect(Collectors.joining(","));;
        return CouchbaseCluster.create(env, connectionString)
                .authenticate(user, password);
    }

    @Override
    public boolean isMandatory() {
        return true;
    }
}
