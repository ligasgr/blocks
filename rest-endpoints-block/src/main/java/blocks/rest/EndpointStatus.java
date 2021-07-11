package blocks.rest;

import java.time.ZonedDateTime;
import java.util.Optional;

public class EndpointStatus {
    public final String name;
    public final boolean isHealthy;
    public final ZonedDateTime lastChecked;
    public final Optional<String> error;
    public final long checkDurationInNanos;

    public EndpointStatus(final String name, final boolean isHealthy, final ZonedDateTime lastChecked, final Optional<String> error, final long checkDurationInNanos) {
        this.name = name;
        this.isHealthy = isHealthy;
        this.lastChecked = lastChecked;
        this.error = error;
        this.checkDurationInNanos = checkDurationInNanos;
    }
}
