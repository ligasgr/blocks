package blocks.service.info;

import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Scheduler;
import org.apache.pekko.actor.typed.javadsl.AskPattern;
import org.apache.pekko.http.javadsl.model.StatusCodes;
import org.apache.pekko.http.javadsl.server.AllDirectives;
import org.apache.pekko.http.javadsl.server.Route;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.time.Duration;
import java.util.concurrent.CompletionStage;

import static org.apache.pekko.http.javadsl.model.StatusCodes.OK;
import static org.apache.pekko.http.javadsl.server.PathMatchers.segment;

@Path("info/v1")
public class ServiceInfoRestService extends AllDirectives {
    private final ActorRef<ServiceInfoProtocol.Message> serviceInfoActor;
    private final Scheduler scheduler;

    public ServiceInfoRestService(final ActorRef<ServiceInfoProtocol.Message> serviceInfoActor, final Scheduler scheduler) {
        this.serviceInfoActor = serviceInfoActor;
        this.scheduler = scheduler;
    }

    @GET
    @Path("")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Get service information", description = "List of static properties and counters", tags = {"info"})
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "ok",
                    content = @Content(schema = @Schema(implementation = String.class))
            ),
            @ApiResponse(responseCode = "500", description = "failed",
                    content = @Content(schema = @Schema(implementation = String.class))
            )
    })
    public Route route() {
        return path(segment("info").slash("v1"), () ->
                onComplete(askForServiceInfo(), serviceInfoTry -> {
                    if (serviceInfoTry.isSuccess()) {
                        return complete(OK, serviceInfoTry.get(), ServiceInfo.MARSHALLER);
                    } else {
                        return complete(StatusCodes.INTERNAL_SERVER_ERROR, serviceInfoTry.failed().get().getMessage());
                    }
                })
        );
    }

    private CompletionStage<ServiceInfo> askForServiceInfo() {
        return AskPattern.ask(serviceInfoActor, ServiceInfoProtocol.GetServiceInfo::new, Duration.ofSeconds(2), scheduler);
    }
}
