package blocks.health;

import blocks.service.JsonUtil;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.pekko.http.javadsl.marshalling.Marshaller;
import org.apache.pekko.http.javadsl.model.RequestEntity;

import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public final class ServiceHealth {
    public static final Marshaller<ServiceHealth, RequestEntity> MARSHALLER = JsonUtil.marshaller();

    @JsonProperty("isHealthy")
    public boolean isHealthy;
    @JsonProperty("isInitialized")
    private boolean isInitialized;
    @JsonProperty("blocks")
    private Map<String, BlockHealthInfo> blocks;
    @JsonProperty("dependencies")
    private List<ComponentHealth> dependencies;
    @JsonProperty("startedAt")
    private ZonedDateTime startedAt;
    @JsonProperty("healthAt")
    private ZonedDateTime healthAt;
    @JsonAnyGetter
    @JsonAnySetter // this cannot be used in constructor JsonCreator, hence can no longer use immutable style class
    private Map<String, JsonNode> staticProperties;

    public ServiceHealth() {
    }

    public ServiceHealth(
            final boolean isHealthy,
            final boolean isInitialized,
            final Map<String, BlockHealthInfo> blocks,
            final List<ComponentHealth> dependencies,
            final ZonedDateTime startedAt,
            final ZonedDateTime healthAt,
            final Map<String, JsonNode> staticProperties
    ) {
        this.isHealthy = isHealthy;
        this.isInitialized = isInitialized;
        this.blocks = blocks;
        this.dependencies = dependencies;
        this.startedAt = startedAt;
        this.healthAt = healthAt;
        this.staticProperties = Collections.unmodifiableMap(staticProperties);
    }

    @JsonIgnore
    public boolean isHealthy() {
        return isHealthy;
    }

    public void setHealthy(final boolean healthy) {
        isHealthy = healthy;
    }

    @JsonIgnore
    public boolean isInitialized() {
        return isInitialized;
    }

    public void setInitialized(final boolean initialized) {
        isInitialized = initialized;
    }

    @JsonIgnore
    public Map<String, BlockHealthInfo> getBlocks() {
        return blocks;
    }

    public void setBlocks(final Map<String, BlockHealthInfo> blocks) {
        this.blocks = blocks;
    }

    @JsonIgnore
    public List<ComponentHealth> getDependencies() {
        return dependencies;
    }

    public void setDependencies(final List<ComponentHealth> dependencies) {
        this.dependencies = dependencies;
    }

    @JsonIgnore
    public ZonedDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(final ZonedDateTime startedAt) {
        this.startedAt = startedAt;
    }

    @JsonIgnore
    public ZonedDateTime getHealthAt() {
        return healthAt;
    }

    public void setHealthAt(final ZonedDateTime healthAt) {
        this.healthAt = healthAt;
    }

    @JsonIgnore
    public Map<String, JsonNode> getStaticProperties() {
        return staticProperties;
    }

    public void setStaticProperties(final Map<String, JsonNode> staticProperties) {
        this.staticProperties = staticProperties;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final ServiceHealth that = (ServiceHealth) o;
        return isHealthy == that.isHealthy && isInitialized == that.isInitialized && Objects.equals(blocks, that.blocks) && Objects.equals(dependencies, that.dependencies) && Objects.equals(startedAt, that.startedAt) && Objects.equals(healthAt, that.healthAt) && Objects.equals(staticProperties, that.staticProperties);
    }

    @Override
    public int hashCode() {
        return Objects.hash(isHealthy, isInitialized, blocks, dependencies, startedAt, healthAt, staticProperties);
    }

    @Override
    public String toString() {
        return "ServiceHealth{" +
                "isHealthy=" + isHealthy +
                ", isInitialized=" + isInitialized +
                ", blocks=" + blocks +
                ", dependencies=" + dependencies +
                ", startedAt=" + startedAt +
                ", healthAt=" + healthAt +
                ", staticProperties=" + staticProperties +
                '}';
    }
}