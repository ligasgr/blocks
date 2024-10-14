package blocks.ui;

import org.apache.pekko.http.javadsl.server.Route;
import blocks.service.AbstractBlock;
import blocks.service.BlockContext;
import blocks.service.RouteCreator;
import org.slf4j.Logger;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static blocks.service.FutureUtils.futureOnDefaultDispatcher;
import static java.util.Objects.requireNonNull;

public class UiBlock extends AbstractBlock<Route> {
    private Logger log;
    private RouteCreator uiRouteCreator;

    public UiBlock() {
        uiRouteCreator = null;
    }

    public UiBlock(final RouteCreator uiRouteCreator) {
        this.uiRouteCreator = requireNonNull(uiRouteCreator);
    }

    @Override
    public boolean isMandatory() {
        return true;
    }

    @Override
    protected CompletableFuture<Route> getBlockOutputFuture(final BlockContext blockContext) {
        this.log = blockContext.context.getLog();
        return futureOnDefaultDispatcher(blockContext.context, () -> {
            if (this.uiRouteCreator == null) {
                log.info("Obtaining default ui route");
                uiRouteCreator = new DefaultUiService();
            }
            return uiRouteCreator.createRoute();
        });
    }

    @Override
    public Optional<Route> getCreatedRoutes() {
        return Optional.of(getBlockOutput()
            .orElseThrow(() -> new IllegalStateException("UiBlock is not initialized")));
    }
}
