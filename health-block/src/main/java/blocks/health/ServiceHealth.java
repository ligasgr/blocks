package blocks.health;

import akka.http.javadsl.marshalling.Marshaller;
import akka.http.javadsl.model.RequestEntity;
import blocks.service.JsonUtil;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ServiceHealth {
    public static final Marshaller<ServiceHealth, RequestEntity> MARSHALLER = JsonUtil.marshaller();

    public final boolean isHealthy;
    public final boolean isInitialized;
    public final Map<String, BlockHealthInfo> blocks;
    public final List<ComponentHealth> dependencies;
    public final ZonedDateTime startedAt;
    public final ZonedDateTime healthAt;

    public ServiceHealth(
            final boolean isHealthy,
            final boolean isInitialized,
            final Map<String, BlockHealthInfo> blocks,
            final List<ComponentHealth> dependencies,
            final ZonedDateTime startedAt,
            final ZonedDateTime healthAt
    ) {
        this.isHealthy = isHealthy;
        this.isInitialized = isInitialized;
        this.blocks = blocks;
        this.dependencies = dependencies;
        this.startedAt = startedAt;
        this.healthAt = healthAt;
    }
}
