package blocks.service;

import akka.http.javadsl.server.Route;

@FunctionalInterface
public interface RouteCreator {
    Route createRoute();
}
