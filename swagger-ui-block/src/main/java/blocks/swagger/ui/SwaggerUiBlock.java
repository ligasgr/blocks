package blocks.swagger.ui;

import org.apache.pekko.http.javadsl.server.Route;
import blocks.service.AbstractBlock;
import blocks.service.BlockContext;
import blocks.service.RouteCreator;
import org.slf4j.Logger;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static blocks.service.FutureUtils.futureOnDefaultDispatcher;
import static java.util.Objects.requireNonNull;

public final class SwaggerUiBlock extends AbstractBlock<Route> {
    private Logger log;
    private RouteCreator swaggerUiRoutes;

    public SwaggerUiBlock(final RouteCreator swaggerUiRoutes) {
        this.swaggerUiRoutes = requireNonNull(swaggerUiRoutes);
    }

    public SwaggerUiBlock() {
        this.swaggerUiRoutes = null;
    }

    @Override
    public boolean isMandatory() {
        return true;
    }

    @Override
    protected CompletableFuture<Route> getBlockOutputFuture(final BlockContext blockContext) {
        this.log = blockContext.context.getLog();
        return futureOnDefaultDispatcher(blockContext.context, () -> {
            if (swaggerUiRoutes == null) {
                log.info("Obtaining default swagger-ui route");
                swaggerUiRoutes = new SwaggerUiService();
            }
            return swaggerUiRoutes.createRoute();
        });
    }

    @Override
    public Optional<Route> getCreatedRoutes() {
        return Optional.of(getBlockOutput()
            .orElseThrow(() -> new IllegalStateException("SwaggerUiBlock is not initialized")));
    }
}
