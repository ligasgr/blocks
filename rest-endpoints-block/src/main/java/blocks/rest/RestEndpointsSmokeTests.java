package blocks.rest;

import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.http.javadsl.Http;
import org.apache.pekko.japi.Pair;
import org.apache.pekko.japi.function.Procedure;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.stream.javadsl.Source;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

public class RestEndpointsSmokeTests implements RestEndpointsHealthChecks {
    private final ActorSystem<Void> system;
    private final Http http;
    private final Map<String, Procedure<Pair<Http, ActorSystem<Void>>>> smokeTests;

    public RestEndpointsSmokeTests(final ActorSystem<Void> system, final Map<String, Procedure<Pair<Http, ActorSystem<Void>>>> smokeTests) {
        this.system = system;
        this.http = Http.get(system);
        this.smokeTests = smokeTests;
    }

    @Override
    public CompletionStage<Map<String, EndpointStatus>> run() {
        return Source.from(smokeTests.entrySet())
                .throttle(1, Duration.ofSeconds(1L))
                .map(this::runSmokeTest)
                .fold(new HashMap<String, EndpointStatus>(), (acc, next) -> {
                    acc.put(next.first(), next.second());
                    return acc;
                })
                .map(m -> (Map<String, EndpointStatus>) m)
                .runWith(Sink.head(), system);
    }

    private Pair<String, EndpointStatus> runSmokeTest(final Map.Entry<String, Procedure<Pair<Http, ActorSystem<Void>>>> test) {
        String name = test.getKey();
        Procedure<Pair<Http, ActorSystem<Void>>> endpointStatusFunction = test.getValue();
        long start = System.nanoTime();
        try {
            endpointStatusFunction.apply(Pair.create(http, system));
            return Pair.create(name, new EndpointStatus(name, true, ZonedDateTime.now(), Optional.empty(), System.nanoTime() - start));
        } catch (Exception e) {
            String message = e.getMessage() != null ? e.getMessage() : e.getClass().getCanonicalName();
            return Pair.create(name, new EndpointStatus(name, false, ZonedDateTime.now(), Optional.of(message), System.nanoTime() - start));
        }
    }
}
