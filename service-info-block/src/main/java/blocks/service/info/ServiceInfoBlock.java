package blocks.service.info;

import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.http.javadsl.server.Route;
import blocks.service.AbstractBlock;
import blocks.service.BlockContext;
import blocks.service.ServiceProperties;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class ServiceInfoBlock extends AbstractBlock<ActorRef<ServiceInfoProtocol.Message>> {
    private final Map<String, JsonNode> staticProperties;
    private Logger log;
    private ServiceInfoRestService serviceInfoRestService;

    public ServiceInfoBlock(final ServiceProperties serviceProperties) {
        this(serviceProperties.configuredProperties);
    }

    public ServiceInfoBlock() {
        this(Collections.emptyMap());
    }

    public ServiceInfoBlock(final Map<String, JsonNode> staticProperties) {
        this.staticProperties = staticProperties;
    }

    @Override
    public boolean isMandatory() {
        return true;
    }

    @Override
    public Optional<Route> getCreatedRoutes() {
        return Optional.of(serviceInfoRestService.route());
    }

    @Override
    public Set<Class<?>> serviceClasses() {
        return Collections.singleton(ServiceInfoRestService.class);
    }


    @Override
    protected CompletableFuture<ActorRef<ServiceInfoProtocol.Message>> getBlockOutputFuture(final BlockContext blockContext) {
        this.log = blockContext.context.getLog();
        this.log.info("Initializing ServiceInfoBlock");
        final ActorRef<ServiceInfoProtocol.Message> serviceInfoActor = blockContext.context.spawn(ServiceInfoActor.behavior(blockContext.clock, staticProperties), "serviceInfoActor");
        serviceInfoRestService = new ServiceInfoRestService(serviceInfoActor, blockContext.context.getSystem().scheduler());
        return CompletableFuture.completedFuture(serviceInfoActor);
    }
}
