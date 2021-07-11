package blocks.health;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ComponentHealth {
    public final String name;
    public final boolean isHealthy;
    public final boolean isInitialized;
    public final Optional<String> error;
    public final List<ComponentHealth> dependencies;
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
        this.name = name;
        this.isHealthy = isHealthy;
        this.isInitialized = isInitialized;
        this.error = error;
        this.dependencies = dependencies;
        this.refreshedAt = refreshedAt;
        this.checkDurationInNanoseconds = checkDurationInNanoseconds;
    }
}
