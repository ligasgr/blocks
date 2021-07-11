package blocks.rest;

import java.util.Map;
import java.util.concurrent.CompletionStage;

public interface RestEndpointsHealthChecks {
    CompletionStage<Map<String, EndpointStatus>> run();
}
