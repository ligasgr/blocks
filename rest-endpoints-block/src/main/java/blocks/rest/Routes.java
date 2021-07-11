package blocks.rest;

import akka.actor.typed.ActorSystem;
import akka.http.javadsl.server.Route;
import blocks.service.ServiceConfig;

public interface Routes {
    <T> Route route();

    RestEndpointsHealthChecks healthChecks(final ActorSystem<Void> system, final ServiceConfig config);
}
