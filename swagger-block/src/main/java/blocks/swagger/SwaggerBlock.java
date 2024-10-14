package blocks.swagger;

import org.apache.pekko.http.javadsl.server.Route;
import blocks.service.AbstractBlock;
import blocks.service.BlockContext;
import io.swagger.v3.oas.models.info.Info;
import org.slf4j.Logger;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static blocks.service.FutureUtils.futureOnDefaultDispatcher;
import static java.util.Objects.requireNonNull;

public final class SwaggerBlock extends AbstractBlock<Route> {
    private Logger log;
    private SwaggerDocService swaggerDocService;
    private final Info info;
    private final String basePath;

    public SwaggerBlock(final Info info) {
        this(info, "/");
    }

    public SwaggerBlock(final Info info, final String basePath) {
        this.info = info;
        this.basePath = basePath;
        this.swaggerDocService = null;
    }

    public SwaggerBlock(final SwaggerDocService swaggerDocService) {
        this.info = null;
        this.basePath = null;
        this.swaggerDocService = requireNonNull(swaggerDocService);
    }

    @Override
    public boolean isMandatory() {
        return true;
    }

    @Override
    protected CompletableFuture<Route> getBlockOutputFuture(final BlockContext blockContext) {
        this.log = blockContext.context.getLog();
        if (swaggerDocService == null) {
            log.info("Crating default swagger doc service");
            swaggerDocService = new DefaultSwaggerDocService(info, basePath);
        }
        blockContext.blockRefs.forEach(key ->
            swaggerDocService.addApiClasses(blockContext.getBlock(key).serviceClasses())
        );
        return futureOnDefaultDispatcher(blockContext.context, () -> {
            log.info("Obtaining SwaggerDocService");
            return swaggerDocService.createRoute();
        });
    }

    @Override
    public Optional<Route> getCreatedRoutes() {
        return Optional.of(getBlockOutput()
            .orElseThrow(() -> new IllegalStateException("SwaggerBlock is not initialized")));
    }
}
