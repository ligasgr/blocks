package blocks.service;

import akka.event.LoggingAdapter;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.RemoteAddress;
import akka.http.javadsl.server.Complete;
import akka.http.javadsl.server.Directives;
import akka.http.javadsl.server.RequestContext;
import akka.http.javadsl.server.Route;
import akka.http.javadsl.server.RouteResult;
import akka.http.javadsl.server.RouteResults;
import akka.stream.javadsl.Flow;
import akka.util.ByteString;

import java.util.function.Supplier;

public class RequestMetricsLogging {
    public static Route logRequests(final LoggingAdapter log, final Supplier<Route> route) {
        return Directives.extractClientIP(address ->
                Directives.extractRequestContext(requestContext -> {
                    final long start = System.nanoTime();
                    return Directives.mapRouteResult(routeResult ->
                                    logRequestAndMaybeChangeRouteResult(log, address, requestContext, routeResult, start),
                            route);
                })
        );
    }

    private static RouteResult logRequestAndMaybeChangeRouteResult(final LoggingAdapter log, final RemoteAddress address, final RequestContext requestContext, final RouteResult routeResult, final long start) {
        HttpRequest request = requestContext.getRequest();
        if (routeResult instanceof Complete) {
            HttpResponse response = ((Complete) routeResult).getResponse();
            if (response.entity().isKnownEmpty() || !response.entity().isChunked()) {
                logRequest(log, address, start, request, response.status().intValue(), false);
                return routeResult;
            } else {
                return logRequestAfterStreamingCompletion(log, address, start, request, response);
            }
        }
        return routeResult;
    }

    private static Complete logRequestAfterStreamingCompletion(final LoggingAdapter log, final RemoteAddress address, final long start, final HttpRequest request, final HttpResponse response) {
        HttpResponse modifiedResponse = response.transformEntityDataBytes(Flow.<ByteString>create()
                .watchTermination((materialization, futureDone) -> {
                    futureDone.whenComplete((done, throwable) -> {
                        if (throwable != null) {
                            log.error(throwable, "Request failed");
                        }
                        logRequest(log, address, start, request, response.status().intValue(), true);
                    });
                    return materialization;
                })
        );
        return RouteResults.complete(modifiedResponse);
    }

    private static void logRequest(final LoggingAdapter log, final RemoteAddress address, final long start, final HttpRequest request, final int statusCode, final boolean streamed) {
        if (log.isInfoEnabled()) {
            final String template = "HttpRequest" +
                    " method=%s" +
                    " uri=\"%s\"" +
                    " clientIp=%s" +
                    " status=%d" +
                    " timeToRespondInNano=%d" +
                    " streamed=%s";
            final String message = String.format(template, request.method().name(),
                    request.getUri(),
                    address,
                    statusCode,
                    System.nanoTime() - start,
                    streamed);
            log.info(message);
        }
    }
}
