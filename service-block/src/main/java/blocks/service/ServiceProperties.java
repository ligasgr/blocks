package blocks.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.LongNode;
import com.fasterxml.jackson.databind.node.TextNode;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ServiceProperties {

    public final Map<String, JsonNode> configuredProperties;

    private ServiceProperties(final Map<String, JsonNode> systemProperties) {
        this.configuredProperties = Collections.unmodifiableMap(systemProperties);
    }

    public static class Builder {

        private static final Map<Class<?>, Function<Object, JsonNode>> objectToNode = new HashMap<>() {{
            put(java.lang.String.class, o -> TextNode.valueOf((String) o));
            put(java.lang.Long.class, o -> LongNode.valueOf((long) o));
            put(java.lang.Integer.class, o -> IntNode.valueOf((int) o));
            put(java.time.LocalDateTime.class, o -> TextNode.valueOf(((LocalDateTime) o).toString()));
            put(java.time.LocalDate.class, o -> TextNode.valueOf(((LocalDate) o).toString()));
            put(java.time.LocalTime.class, o -> TextNode.valueOf(((LocalTime) o).toString()));
            put(java.time.ZonedDateTime.class, o -> TextNode.valueOf(((ZonedDateTime) o).toString()));
            put(java.time.Instant.class, o -> TextNode.valueOf(((Instant) o).toString()));
        }};

        private final Map<String, Object> standardPropertyTransformers = new HashMap<>();
        private final Map<String, PropertyTransformerHolder<?>> customPropertyTransformers = new HashMap<>();

        public Builder() {
        }

        public Builder add(final String name, final Object value) {
            this.standardPropertyTransformers.put(name, value);
            return this;
        }

        public <T> Builder add(final String name, final T value, final Function<T, JsonNode> transformer) {
            this.customPropertyTransformers.put(name, new PropertyTransformerHolder<>(value, transformer));
            return this;
        }

        public ServiceProperties build() {
            return new ServiceProperties(new HashMap<>() {{
                putAll(transform(Builder.this.standardPropertyTransformers, Builder.this::applyStandardTransformers));
                putAll(transform(Builder.this.customPropertyTransformers, Builder.this::applyTransformerFunction));
            }});
        }

        private <T> Map<String, JsonNode> transform(final Map<String, T> propertiesMap,
                                                    final Function<T, JsonNode> transformer) {
            return propertiesMap.entrySet().stream()
                .map(entry -> new PropertyHolder(entry.getKey(), transformer.apply(entry.getValue())))
                .collect(Collectors.toMap(p -> p.name, p -> p.value));
        }

        public static Builder serviceProperties() {
            return new Builder();
        }

        private <T> JsonNode applyTransformerFunction(final PropertyTransformerHolder<T> propertyTransformerHolder) {
            return propertyTransformerHolder.transformer.apply(propertyTransformerHolder.value);
        }

        private JsonNode applyStandardTransformers(final Object value) {
            if (value instanceof JsonNode) {
                return (JsonNode) value;
            }
            if (objectToNode.containsKey(value.getClass())) {
                return objectToNode.get(value.getClass()).apply(value);
            }
            throw new IllegalArgumentException("Unsupported property value type " + value.getClass().getName());
        }

        private static class PropertyHolder {
            public final String name;
            public final JsonNode value;

            public PropertyHolder(final String name, final JsonNode node) {
                this.name = name;
                this.value = node;
            }
        }

        private static class PropertyTransformerHolder<T> {
            public final T value;
            public final Function<T, JsonNode> transformer;

            public PropertyTransformerHolder(final T value, final Function<T, JsonNode> transformer) {
                this.value = value;
                this.transformer = transformer;
            }
        }
    }
}
