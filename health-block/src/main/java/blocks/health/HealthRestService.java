package blocks.health;

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

@Path("health/v1")
public class HealthRestService extends AllDirectives {
    private final ActorRef<HealthProtocol.Message> healthActor;
    private final Scheduler scheduler;

    public HealthRestService(final ActorRef<HealthProtocol.Message> healthActor, final Scheduler scheduler) {
        this.healthActor = healthActor;
        this.scheduler = scheduler;
    }

    @GET
    @Path("")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Get health information", description = "Breakdown of health of each component", tags = {"health"})
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "ok",
                    content = @Content(schema = @Schema(implementation = String.class))
            ),
            @ApiResponse(responseCode = "500", description = "failed",
                    content = @Content(schema = @Schema(implementation = String.class))
            )
    })
    public Route route() {
        return path(segment("health").slash("v1"), () ->
                onComplete(askForHealth(), healthTry -> {
                    if (healthTry.isSuccess()) {
                        return complete(OK, healthTry.get().serviceHealth, ServiceHealth.MARSHALLER);
                    } else {
                        return complete(StatusCodes.INTERNAL_SERVER_ERROR, healthTry.failed().get().getMessage());
                    }
                })
        );
    }

    private CompletionStage<HealthProtocol.Health> askForHealth() {
        return AskPattern.ask(healthActor, HealthProtocol.GetHealth::new, Duration.ofSeconds(2), scheduler);
    }
}
