package blocks.rest;

import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.Optional;

@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public final class EndpointStatus {
    public final String name;
    public final boolean isHealthy;
    public final ZonedDateTime lastChecked;
    public final Optional<String> error;
    public final long checkDurationInNanos;

    public EndpointStatus(final String name,
                          final boolean isHealthy,
                          final ZonedDateTime lastChecked,
                          final Optional<String> error,
                          final long checkDurationInNanos) {
        this.name = name;
        this.isHealthy = isHealthy;
        this.lastChecked = lastChecked;
        this.error = error;
        this.checkDurationInNanos = checkDurationInNanos;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final EndpointStatus that = (EndpointStatus) o;
        return isHealthy == that.isHealthy
            && checkDurationInNanos == that.checkDurationInNanos
            && Objects.equals(name, that.name)
            && Objects.equals(lastChecked, that.lastChecked)
            && Objects.equals(error, that.error);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, isHealthy, lastChecked, error, checkDurationInNanos);
    }
}
