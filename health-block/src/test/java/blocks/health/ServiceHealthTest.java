package blocks.health;

import blocks.service.BlockStatus;
import blocks.service.JsonUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.node.TextNode;
import org.junit.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;

import static java.util.Collections.singletonList;
import static org.junit.Assert.assertEquals;

public class ServiceHealthTest {
    private static final ObjectWriter OBJECT_WRITER = JsonUtil.DEFAULT_OBJECT_MAPPER.writerFor(ServiceHealth.class);
    private static final String NEW_LINE = System.lineSeparator();
    private static final String EXPECTED_OUTPUT = "{" + NEW_LINE +
            "  \"isHealthy\" : false," + NEW_LINE +
            "  \"isInitialized\" : true," + NEW_LINE +
            "  \"blocks\" : {" + NEW_LINE +
            "    \"rest\" : {" + NEW_LINE +
            "      \"status\" : \"INITIALIZED\"," + NEW_LINE +
            "      \"mandatory\" : true" + NEW_LINE +
            "    }" + NEW_LINE +
            "  }," + NEW_LINE +
            "  \"dependencies\" : [ {" + NEW_LINE +
            "    \"name\" : \"rest\"," + NEW_LINE +
            "    \"isHealthy\" : false," + NEW_LINE +
            "    \"isInitialized\" : true," + NEW_LINE +
            "    \"error\" : \"java.lang.RuntimeException: Status code not OK\"," + NEW_LINE +
            "    \"refreshedAt\" : \"1970-01-01T00:00:00.123Z\"," + NEW_LINE +
            "    \"checkDurationInNanoseconds\" : 123" + NEW_LINE +
            "  } ]," + NEW_LINE +
            "  \"startedAt\" : \"1970-01-01T00:00:00.123Z\"," + NEW_LINE +
            "  \"healthAt\" : \"1970-01-01T00:00:00.123Z\"," + NEW_LINE +
            "  \"serviceName\" : \"sample\"" + NEW_LINE +
            "}";
    private final Clock clock = Clock.fixed(Instant.ofEpochMilli(123L), ZoneId.of("UTC"));

    @Test
    public void inlinesStaticProperties() throws JsonProcessingException {
        final ZonedDateTime now = ZonedDateTime.now(clock);
        final Map<String, BlockHealthInfo> blockList = new HashMap<>() {{
            put("rest", new BlockHealthInfo(BlockStatus.INITIALIZED, true));
        }};
        final List<ComponentHealth> dependencies = singletonList(new ComponentHealth("rest", false, true, Optional.of("java.lang.RuntimeException: Status code not OK"), Collections.emptyList(), now, OptionalLong.of(123L)));
        final Map<String, JsonNode> properties = new HashMap<>() {{
            put("serviceName", TextNode.valueOf("sample"));
        }};
        final ServiceHealth serviceHealth = new ServiceHealth(false, true, blockList, dependencies, now, now, properties);

        final String healthAsString = OBJECT_WRITER.writeValueAsString(serviceHealth);

        assertEquals(EXPECTED_OUTPUT, healthAsString);
    }
}