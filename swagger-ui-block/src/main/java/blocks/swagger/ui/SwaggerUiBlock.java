package blocks.swagger.ui;

import akka.http.javadsl.server.Route;
import blocks.service.AbstractBlock;
import blocks.service.BlockContext;
import org.slf4j.Logger;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static blocks.service.FutureUtils.futureOnDefaultDispatcher;

public class SwaggerUiBlock extends AbstractBlock<Route> {
    private Logger log;

    @Override
    public boolean isMandatory() {
        return true;
    }

    @Override
    protected CompletableFuture<Route> getBlockOutputFuture(final BlockContext blockContext) {
        this.log = blockContext.context.getLog();
        return futureOnDefaultDispatcher(blockContext.context, () -> {
            log.info("Obtaining swagger-ui route");
            SwaggerUiService swaggerUiService = new SwaggerUiService();
            return swaggerUiService.createRoute();
        });
    }

    @Override
    public Optional<Route> getCreatedRoutes() {
        return Optional.of(getBlockOutput()
                .orElseThrow(() -> new IllegalStateException("SwaggerUiBlock is not initialized")));
    }
}
