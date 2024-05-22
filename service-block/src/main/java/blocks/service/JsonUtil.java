package blocks.service;

import org.apache.pekko.http.javadsl.marshallers.jackson.Jackson;
import org.apache.pekko.http.javadsl.marshalling.Marshaller;
import org.apache.pekko.http.javadsl.model.HttpEntity;
import org.apache.pekko.http.javadsl.model.RequestEntity;
import org.apache.pekko.http.javadsl.unmarshalling.Unmarshaller;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public class JsonUtil {
    public static final ObjectMapper DEFAULT_OBJECT_MAPPER = getObjectMapper();

    private static ObjectMapper getObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new Jdk8Module());
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        return objectMapper;
    }

    public static <T> Marshaller<T, RequestEntity> marshaller() {
        return Jackson.marshaller(DEFAULT_OBJECT_MAPPER);
    }

    public static <T> Unmarshaller<HttpEntity, T> unmarshaller(Class<T> classParam) {
        return Jackson.unmarshaller(DEFAULT_OBJECT_MAPPER, classParam);
    }
}
