package blocks.health;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public final class ComponentHealth {
    public final String name;
    public final boolean isHealthy;
    public final boolean isInitialized;
    public final Optional<String> error;
    public final List<ComponentHealth> dependencies;
    public final Map<String, JsonNode> details;
    public final ZonedDateTime refreshedAt;
    public final OptionalLong checkDurationInNanoseconds;

    @JsonCreator
    public ComponentHealth(
            @JsonProperty("name") final String name,
            @JsonProperty("isHealthy") final boolean isHealthy,
            @JsonProperty("isInitialized") final boolean isInitialized,
            @JsonProperty("error") final Optional<String> error,
            @JsonProperty("dependencies") final List<ComponentHealth> dependencies,
            @JsonProperty("refreshedAt") final ZonedDateTime refreshedAt,
            @JsonProperty("checkDurationInNanoseconds") final OptionalLong checkDurationInNanoseconds
    ) {
        this(name, isHealthy, isInitialized, error, dependencies, Collections.emptyMap(), refreshedAt, checkDurationInNanoseconds);
    }

    public ComponentHealth(final String name,
                           final boolean isHealthy,
                           final boolean isInitialized,
                           final Optional<String> error,
                           final List<ComponentHealth> dependencies,
                           final Map<String, JsonNode> details,
                           final ZonedDateTime refreshedAt,
                           final OptionalLong checkDurationInNanoseconds) {
        this.name = name;
        this.isHealthy = isHealthy;
        this.isInitialized = isInitialized;
        this.error = error;
        this.dependencies = dependencies;
        this.details = details;
        this.refreshedAt = refreshedAt;
        this.checkDurationInNanoseconds = checkDurationInNanoseconds;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ComponentHealth that = (ComponentHealth) o;
        return isHealthy == that.isHealthy && isInitialized == that.isInitialized && Objects.equals(name, that.name) && Objects.equals(error, that.error) && Objects.equals(dependencies, that.dependencies) && Objects.equals(details, that.details) && Objects.equals(refreshedAt, that.refreshedAt) && Objects.equals(checkDurationInNanoseconds, that.checkDurationInNanoseconds);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, isHealthy, isInitialized, error, dependencies, details, refreshedAt, checkDurationInNanoseconds);
    }

    @Override
    public String toString() {
        return "ComponentHealth{" +
                "name='" + name + '\'' +
                ", isHealthy=" + isHealthy +
                ", isInitialized=" + isInitialized +
                ", error=" + error +
                ", dependencies=" + dependencies +
                ", details=" + details +
                ", refreshedAt=" + refreshedAt +
                ", checkDurationInNanoseconds=" + checkDurationInNanoseconds +
                '}';
    }
}
