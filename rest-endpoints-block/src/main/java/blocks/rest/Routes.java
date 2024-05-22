package blocks.rest;

import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.http.javadsl.server.Route;
import blocks.service.ServiceConfig;

public interface Routes {
    <T> Route route();

    RestEndpointsHealthChecks healthChecks(final ActorSystem<Void> system, final ServiceConfig config);
}
