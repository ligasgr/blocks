package blocks.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.LongNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.junit.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;

import static blocks.service.ServiceProperties.Builder.serviceProperties;
import static org.junit.Assert.assertEquals;

public class ServicePropertiesTest {

    @Test
    public void shouldMarshalSupportedTypesToJsonNodes() {
        final LocalDateTime localDateTime = LocalDateTime.of(2022, 2, 9, 21, 5, 49);
        final ServiceProperties properties = serviceProperties()
            .add("integer", 1)
            .add("long", 2L)
            .add("string", "aString")
            .add("localDateTime", localDateTime)
            .add("zonedDateTime", ZonedDateTime.of(localDateTime, ZoneId.of("UTC")))
            .build();

        final Map<String, JsonNode> expected = new HashMap<>() {{
           put("integer", IntNode.valueOf(1));
           put("long", LongNode.valueOf(2L));
           put("string", TextNode.valueOf("aString"));
           put("localDateTime", TextNode.valueOf("2022-02-09T21:05:49"));
           put("zonedDateTime", TextNode.valueOf("2022-02-09T21:05:49Z[UTC]"));
        }};

        assertEquals(expected, properties.configuredProperties);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldThrowTheExceptionWhenUnknownPropertyTypeIsUsed() {
        serviceProperties().add("date", LocalDate.now()).build();
    }

}