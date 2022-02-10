package blocks.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.LongNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.junit.Test;

import java.time.Instant;
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
            .add("localDate", localDateTime.toLocalDate())
            .add("localTime", localDateTime.toLocalTime())
            .add("zonedDateTime", ZonedDateTime.of(localDateTime, ZoneId.of("UTC")))
            .add("instant", Instant.ofEpochSecond(1644440749L))
            .build();

        final Map<String, JsonNode> expected = new HashMap<>() {{
            put("integer", IntNode.valueOf(1));
            put("long", LongNode.valueOf(2L));
            put("string", TextNode.valueOf("aString"));
            put("localDateTime", TextNode.valueOf("2022-02-09T21:05:49"));
            put("localDate", TextNode.valueOf("2022-02-09"));
            put("localTime", TextNode.valueOf("21:05:49"));
            put("zonedDateTime", TextNode.valueOf("2022-02-09T21:05:49Z[UTC]"));
            put("instant", TextNode.valueOf("2022-02-09T21:05:49Z"));
        }};

        assertEquals(expected, properties.configuredProperties);
    }

    @Test
    public void shouldApplyCustomMarshallerFunctions() {
        final ServiceProperties properties = serviceProperties()
            .add("customString", "a string", s -> TextNode.valueOf("This is a custom string \""+ s + "\"."))
            .add("regularString", "regular String")
            .build();

        final Map<String, JsonNode> expected = new HashMap<>() {{
            put("customString", TextNode.valueOf("This is a custom string \"a string\"."));
            put("regularString", TextNode.valueOf("regular String"));
        }};

        assertEquals(expected, properties.configuredProperties);
    }

    @Test
    public void propertiesWithCustomTransformersTakePrecedenceOverStandardOnes() {
        final ServiceProperties properties = serviceProperties()
            .add("aString", "a string", s -> TextNode.valueOf("This is a custom string \""+ s + "\"."))
            .add("aString", "regular String")
            .build();

        final Map<String, JsonNode> expected = new HashMap<>() {{
            put("aString", TextNode.valueOf("This is a custom string \"a string\"."));
        }};

        assertEquals(expected, properties.configuredProperties);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldThrowTheExceptionWhenUnknownPropertyTypeIsUsed() {
        serviceProperties().add("clazz", this.getClass()).build();
    }

}