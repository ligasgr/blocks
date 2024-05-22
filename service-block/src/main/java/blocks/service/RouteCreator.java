package blocks.service;

import org.apache.pekko.http.javadsl.server.Route;

@FunctionalInterface
public interface RouteCreator {
    Route createRoute();
}
