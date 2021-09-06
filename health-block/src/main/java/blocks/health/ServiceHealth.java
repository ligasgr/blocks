package blocks.health;

import akka.http.javadsl.marshalling.Marshaller;
import akka.http.javadsl.model.RequestEntity;
import blocks.service.JsonUtil;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public final class ServiceHealth {
    public static final Marshaller<ServiceHealth, RequestEntity> MARSHALLER = JsonUtil.marshaller();

    public final boolean isHealthy;
    public final boolean isInitialized;
    public final Map<String, BlockHealthInfo> blocks;
    public final List<ComponentHealth> dependencies;
    public final ZonedDateTime startedAt;
    public final ZonedDateTime healthAt;

    public ServiceHealth(final boolean isHealthy,
                         final boolean isInitialized,
                         final Map<String, BlockHealthInfo> blocks,
                         final List<ComponentHealth> dependencies,
                         final ZonedDateTime startedAt,
                         final ZonedDateTime healthAt) {
        this.isHealthy = isHealthy;
        this.isInitialized = isInitialized;
        this.blocks = blocks;
        this.dependencies = dependencies;
        this.startedAt = startedAt;
        this.healthAt = healthAt;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ServiceHealth that = (ServiceHealth) o;
        return isHealthy == that.isHealthy && isInitialized == that.isInitialized && Objects.equals(blocks, that.blocks) && Objects.equals(dependencies, that.dependencies) && Objects.equals(startedAt, that.startedAt) && Objects.equals(healthAt, that.healthAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(isHealthy, isInitialized, blocks, dependencies, startedAt, healthAt);
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
            '}';
    }
}