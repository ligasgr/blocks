package blocks.couchbase.sdk2;

import akka.actor.typed.ActorRef;
import blocks.health.HealthProtocol;
import blocks.service.AbstractBlock;
import blocks.service.Block;
import blocks.service.BlockConfig;
import blocks.service.BlockContext;
import blocks.service.BlockRef;
import blocks.service.FutureUtils;
import blocks.service.SecretsConfig;
import com.couchbase.client.java.AsyncCluster;
import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.CouchbaseCluster;
import com.couchbase.client.java.cluster.BucketSettings;
import com.couchbase.client.java.env.CouchbaseEnvironment;
import com.couchbase.client.java.env.DefaultCouchbaseEnvironment;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.Policy;
import net.jodah.failsafe.RetryPolicy;
import rx.Observable;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

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
        final Duration autoreleaseAfter = blockConfig.getDuration("autoreleaseAfter");
        final Duration queryTimeout = blockConfig.getDuration("queryTimeout");
        final Duration maxRequestLifetime = blockConfig.getDuration("maxRequestLifetime");
        final Duration connectionTimeout = blockConfig.getDuration("connectionTimeout");
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
                    .build();
            final Policy<CouchbaseCluster> initializationPolicy = new RetryPolicy<CouchbaseCluster>()
                    .withBackoff(100, 6400, ChronoUnit.MILLIS)
                    .withMaxDuration(waitUntilReadyTimeout)
                    .withMaxRetries(-1);
            return Failsafe.with(initializationPolicy).get(() -> initializeCluster(hosts, user, password, env));
        });
        final Function<AsyncCluster, Observable<String>> bucketNameObservableCreator = bucketForHealthCheck.isPresent()
                ? ignored -> Observable.just(bucketForHealthCheck.get())
                : asyncCluster -> asyncCluster.clusterManager()
                .flatMap(cm -> cm.getBuckets().first())
                .map(BucketSettings::name);
        return resultFuture
                .thenCompose(cluster -> {
                            final AsyncCluster asyncCluster = cluster.async();
                            return RxJavaFutureUtils.fromObservable(bucketNameObservableCreator.apply(asyncCluster).flatMap(asyncCluster::openBucket))
                                    .thenAccept(bucket -> couchbaseHealthCheckActor.tell(new CouchbaseSdk2HealthCheckActor.Protocol.CheckHealth()))
                                    .thenApply(ignored -> cluster);
                        }
                );
    }

    private CouchbaseCluster initializeCluster(final List<String> hosts, final String user, final String password, final CouchbaseEnvironment env) {
        return CouchbaseCluster.create(env, String.join(",", hosts))
                .authenticate(user, password);
    }

    @Override
    public boolean isMandatory() {
        return true;
    }
}
