package blocks.rest;

import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.http.javadsl.server.Route;
import blocks.health.HealthProtocol;
import blocks.service.AbstractBlock;
import blocks.service.Block;
import blocks.service.BlockConfig;
import blocks.service.BlockContext;
import blocks.service.BlockRef;
import org.slf4j.Logger;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class RestEndpointsBlock extends AbstractBlock<Route> {
    public static final Duration DEFAULT_REST_HEALTH_CHECK_DELAY = Duration.ofMinutes(5);
    private final Function<BlockContext, Routes> routesCreator;
    private final Set<Class<?>> serviceClasses;
    private final BlockRef<ActorRef<HealthProtocol.Message>> healthBlockRef;
    private final String blockConfigPath;
    private Logger log;
    private Routes routes;

    public RestEndpointsBlock(Function<BlockContext, Routes> routesCreator, Set<Class<?>> serviceClasses, final BlockRef<ActorRef<HealthProtocol.Message>> healthBlockRef, final String blockConfigPath) {
        this.routesCreator = routesCreator;
        this.serviceClasses = serviceClasses;
        this.healthBlockRef = healthBlockRef;
        this.blockConfigPath = blockConfigPath;
    }

    @Override
    public boolean isMandatory() {
        return true;
    }

    @Override
    protected CompletableFuture<Route> getBlockOutputFuture(final BlockContext blockContext) {
        this.log = blockContext.context.getLog();
        this.log.info("Initializing RestEndpointsBlock");
        Block<ActorRef<HealthProtocol.Message>> healthBlock = blockContext.getBlock(healthBlockRef);
        Optional<ActorRef<HealthProtocol.Message>> maybeHealthActor = healthBlock.getBlockOutput();
        if (maybeHealthActor.isEmpty()) {
            throw new IllegalStateException("Cannot initialize block without health actor");
        }
        ActorRef<HealthProtocol.Message> healthActor = maybeHealthActor.get();
        routes = routesCreator.apply(blockContext);
        RestEndpointsHealthChecks restEndpointsHealthChecks = routes.healthChecks(blockContext.context.getSystem(), blockContext.config);
        Duration restHealthCheckDelay;
        if (blockContext.config.hasPath(blockConfigPath)) {
            BlockConfig blockConfig = blockContext.config.getBlockConfig(blockConfigPath);
            restHealthCheckDelay = blockConfig.hasPath("restHealthCheckDelay") ? blockConfig.getDuration("restHealthCheckDelay") : DEFAULT_REST_HEALTH_CHECK_DELAY;
        } else {
            restHealthCheckDelay = DEFAULT_REST_HEALTH_CHECK_DELAY;
        }
        blockContext.context.spawn(RestEndpointsHealthCheckActor.behavior(restEndpointsHealthChecks, healthActor, blockContext.clock, restHealthCheckDelay), "restEndpointsHealthCheckActor");
        log.info("Obtaining routes");
        return CompletableFuture.completedFuture(routes.route());
    }

    @Override
    public Optional<Route> getCreatedRoutes() {
        return Optional.of(getBlockOutput()
                .orElseThrow(() -> new IllegalStateException("RestEndpointsBlock is not initialized")));
    }

    @Override
    public Set<Class<?>> serviceClasses() {
        return serviceClasses;
    }
}
