package blocks.health;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ComponentHealth {
    public final String name;
    public final boolean isHealthy;
    public final boolean isInitialized;
    public final Optional<String> error;
    public final List<ComponentHealth> dependencies;
    public final Map<String, JsonNode> details;
    public final ZonedDateTime refreshedAt;
    public final Optional<Long> checkDurationInNanoseconds;

    public ComponentHealth(
            final String name,
            final boolean isHealthy,
            final boolean isInitialized,
            final Optional<String> error,
            final List<ComponentHealth> dependencies,
            final ZonedDateTime refreshedAt,
            final Optional<Long> checkDurationInNanoseconds) {
        this(name, isHealthy, isInitialized, error, dependencies, Collections.emptyMap(), refreshedAt, checkDurationInNanoseconds);
    }

    public ComponentHealth(
            final String name,
            final boolean isHealthy,
            final boolean isInitialized,
            final Optional<String> error,
            final List<ComponentHealth> dependencies,
            final Map<String, JsonNode> details,
            final ZonedDateTime refreshedAt,
            final Optional<Long> checkDurationInNanoseconds) {
        this.name = name;
        this.isHealthy = isHealthy;
        this.isInitialized = isInitialized;
        this.error = error;
        this.dependencies = dependencies;
        this.details = details;
        this.refreshedAt = refreshedAt;
        this.checkDurationInNanoseconds = checkDurationInNanoseconds;
    }
}
