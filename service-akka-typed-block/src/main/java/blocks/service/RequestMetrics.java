package blocks.service;

import org.apache.pekko.event.LoggingAdapter;
import org.apache.pekko.http.javadsl.model.HttpRequest;
import org.apache.pekko.http.javadsl.model.HttpResponse;
import org.apache.pekko.http.javadsl.model.RemoteAddress;
import org.apache.pekko.http.javadsl.server.Complete;
import org.apache.pekko.http.javadsl.server.Directives;
import org.apache.pekko.http.javadsl.server.RequestContext;
import org.apache.pekko.http.javadsl.server.Route;
import org.apache.pekko.http.javadsl.server.RouteResult;
import org.apache.pekko.http.javadsl.server.RouteResults;
import org.apache.pekko.stream.javadsl.Flow;
import org.apache.pekko.util.ByteString;

import java.util.function.Function;
import java.util.function.Supplier;

public class RequestMetrics {
    public static final Function<RequestLoggingDetails, String> DEFAULT_MESSAGE_FUNCTION = RequestMetrics::prepareMessage;

    public static Route captureRequests(
            final LoggingAdapter log,
            final Function<RequestLoggingDetails, String> messageFunction,
            final Runnable requestsStartNotificationRunnable,
            final Runnable requestsEndNotificationRunnable,
            final Supplier<Route> route) {
        return Directives.extractClientIP(address ->
                Directives.extractRequestContext(requestContext -> {
                    final long start = System.nanoTime();
                    requestsStartNotificationRunnable.run();
                    return Directives.mapRouteResult(routeResult ->
                                    logRequestAndMaybeChangeRouteResult(log, messageFunction, requestsEndNotificationRunnable, address, requestContext, routeResult, start),
                            route);
                })
        );
    }

    private static RouteResult logRequestAndMaybeChangeRouteResult(final LoggingAdapter log,
                                                                   final Function<RequestLoggingDetails, String> messageFunction,
                                                                   final Runnable requestsEndNotificationRunnable,
                                                                   final RemoteAddress address,
                                                                   final RequestContext requestContext,
                                                                   final RouteResult routeResult,
                                                                   final long start) {
        HttpRequest request = requestContext.getRequest();
        if (routeResult instanceof Complete) {
            HttpResponse response = ((Complete) routeResult).getResponse();
            if (response.entity().isKnownEmpty() || !response.entity().isChunked()) {
                logRequest(log, messageFunction, requestsEndNotificationRunnable, address, start, request, response.status().intValue(), false);
                return routeResult;
            } else {
                return logRequestAfterStreamingCompletion(log, messageFunction, requestsEndNotificationRunnable, address, start, request, response);
            }
        }
        return routeResult;
    }

    private static Complete logRequestAfterStreamingCompletion(final LoggingAdapter log, final Function<RequestLoggingDetails, String> messageFunction, final Runnable requestsEndNotificationRunnable, final RemoteAddress address, final long start, final HttpRequest request, final HttpResponse response) {
        HttpResponse modifiedResponse = response.transformEntityDataBytes(Flow.<ByteString>create()
                .watchTermination((materialization, futureDone) -> {
                    futureDone.whenComplete((done, throwable) -> {
                        if (throwable != null) {
                            log.error(throwable, "Request failed");
                        }
                        logRequest(log, messageFunction, requestsEndNotificationRunnable, address, start, request, response.status().intValue(), true);
                    });
                    return materialization;
                })
        );
        return RouteResults.complete(modifiedResponse);
    }

    private static void logRequest(final LoggingAdapter log, final Function<RequestLoggingDetails, String> messageFunction, final Runnable requestsEndNotificationRunnable, final RemoteAddress address, final long start, final HttpRequest request, final int statusCode, final boolean streamed) {
        if (log.isInfoEnabled()) {
            long timeInNano = System.nanoTime() - start;
            RequestLoggingDetails details = new RequestLoggingDetails(address, request, statusCode, streamed, timeInNano);
            log.info(messageFunction.apply(details));
            requestsEndNotificationRunnable.run();
        }
    }

    private static String prepareMessage(RequestLoggingDetails details) {
        final String template = "HttpRequest" +
                " method=%s" +
                " uri=\"%s\"" +
                " clientIp=%s" +
                " status=%d" +
                " timeToRespondInNano=%d" +
                " streamed=%s";
        return String.format(template, details.request.method().name(),
                details.request.getUri(),
                details.address,
                details.statusCode,
                details.timeInNano,
                details.streamed);
    }
}
