package blocks.service.info;

import org.apache.pekko.http.javadsl.marshalling.Marshaller;
import org.apache.pekko.http.javadsl.model.RequestEntity;
import blocks.service.JsonUtil;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Objects;

public class ServiceInfo {
    public static final Marshaller<ServiceInfo, RequestEntity> MARSHALLER = JsonUtil.marshaller();
    public final Map<String, JsonNode> serviceInfo;
    public final ZonedDateTime at;

    public ServiceInfo(final Map<String, JsonNode> serviceInfo, final ZonedDateTime at) {
        this.serviceInfo = serviceInfo;
        this.at = at;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final ServiceInfo that = (ServiceInfo) o;
        return Objects.equals(serviceInfo, that.serviceInfo) && Objects.equals(at, that.at);
    }

    @Override
    public int hashCode() {
        return Objects.hash(serviceInfo, at);
    }

    @Override
    public String toString() {
        return "ServiceInfo{" +
                "serviceInfo=" + serviceInfo +
                ", at=" + at +
                '}';
    }
}
