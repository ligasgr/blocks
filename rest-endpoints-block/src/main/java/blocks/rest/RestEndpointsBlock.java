package blocks.rest;

import akka.actor.typed.ActorRef;
import akka.http.javadsl.server.Route;
import blocks.health.HealthProtocol;
import blocks.service.AbstractBlock;
import blocks.service.Block;
import blocks.service.BlockContext;
import blocks.service.BlockRef;
import org.slf4j.Logger;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class RestEndpointsBlock extends AbstractBlock<Route> {
    private final Function<BlockContext, Routes> routesCreator;
    private final Set<Class<?>> serviceClasses;
    private final BlockRef<ActorRef<HealthProtocol.Message>> healthBlockRef;
    private Logger log;
    private Routes routes;

    public RestEndpointsBlock(Function<BlockContext, Routes> routesCreator, Set<Class<?>> serviceClasses, final BlockRef<ActorRef<HealthProtocol.Message>> healthBlockRef) {
        this.routesCreator = routesCreator;
        this.serviceClasses = serviceClasses;
        this.healthBlockRef = healthBlockRef;
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
        if (!maybeHealthActor.isPresent()) {
            throw new IllegalStateException("Cannot initialize block without health actor");
        }
        ActorRef<HealthProtocol.Message> healthActor = maybeHealthActor.get();
        routes = routesCreator.apply(blockContext);
        RestEndpointsHealthChecks restEndpointsHealthChecks = routes.healthChecks(blockContext.context.getSystem(), blockContext.config);
        blockContext.context.spawn(RestEndpointsHealthCheckActor.behavior(restEndpointsHealthChecks, healthActor, blockContext.clock), "restEndpointsHealthCheckActor");
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
