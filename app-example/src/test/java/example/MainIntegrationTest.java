package example;

import blocks.health.ServiceHealth;
import blocks.service.JsonUtil;
import blocks.service.ServiceProtocol;
import blocks.service.TypesafeServiceConfig;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.apache.activemq.junit.EmbeddedActiveMQBroker;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.http.javadsl.Http;
import org.apache.pekko.http.javadsl.model.HttpEntity;
import org.apache.pekko.http.javadsl.model.HttpRequest;
import org.apache.pekko.http.javadsl.model.HttpResponse;
import org.apache.pekko.http.javadsl.unmarshalling.Unmarshaller;
import org.apache.pekko.japi.Pair;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.stream.javadsl.Source;
import org.junit.Rule;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.couchbase.BucketDefinition;
import org.testcontainers.couchbase.CouchbaseContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.concurrent.ExecutionException;

import static example.Main.startApplication;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.testcontainers.containers.PostgreSQLContainer.POSTGRESQL_PORT;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

public class MainIntegrationTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(MainIntegrationTest.class);
    public static final String USERNAME = "user";
    public static final String PASSWORD = "difficult";
    @Rule
    public final EmbeddedActiveMQBroker broker = new EmbeddedActiveMQBroker();
    @Rule
    public final KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:6.2.1"));
    @Rule
    public final MongoDBContainer mongoDBContainer = new MongoDBContainer(DockerImageName.parse("mongo:4.0.10"));
    @Rule
    public final CouchbaseContainer couchbaseContainer = new CouchbaseContainer(DockerImageName.parse("couchbase/server:6.6.6"))
        .withBucket(new BucketDefinition("test")).withCredentials(USERNAME, PASSWORD);
    @Rule
    public final PostgreSQLContainer postgreSQLContainer = new PostgreSQLContainer(DockerImageName.parse("postgres:14.7"))
        .withDatabaseName("sample_db").withUsername(USERNAME).withPassword(PASSWORD);
    private Http http;

    @Test
    public void startsTheServiceCorrectlyUntilIsFullyHealthy() throws IOException, ExecutionException, InterruptedException {
        try (InputStream templateStream = MainIntegrationTest.class.getResourceAsStream("/TEMPLATE.conf")) {
            final String templateString = new String(Objects.requireNonNull(templateStream).readAllBytes(), StandardCharsets.UTF_8);
            final String filledTemplate = templateString
                .replace("${postgresPort}", Integer.toString(postgreSQLContainer.getMappedPort(POSTGRESQL_PORT)))
                .replace("${mongoPort}", Integer.toString(mongoDBContainer.getMappedPort(27017)))
                .replace("${couchbasePort}", Integer.toString(couchbaseContainer.getBootstrapCarrierDirectPort()));
            final Config config = ConfigFactory.parseString(filledTemplate)
                .withFallback(ConfigFactory.defaultReference())
                .resolve();
            ActorSystem<ServiceProtocol.Message> system = null;
            try {
                system = startApplication(new TypesafeServiceConfig(Pair.create("TEST", config)));
                LOGGER.info("Started");
                http = Http.get(system);
                final ActorSystem<ServiceProtocol.Message> finalSystem = system;
                await().atMost(Duration.of(60L, ChronoUnit.SECONDS)).untilAsserted(() -> {
                    HttpResponse response = http.singleRequest(HttpRequest.GET("http://localhost:8080/health/v1")).toCompletableFuture().get();
                    assertEquals(200, response.status().intValue());
                    Unmarshaller<HttpEntity, ServiceHealth> unmarshal = JsonUtil.unmarshaller(ServiceHealth.class);
                    ServiceHealth serviceHealth = Source.single(response.entity())
                        .mapAsync(1, // unmarshal each element
                            bs -> unmarshal.unmarshal(bs, finalSystem)
                        ).runWith(Sink.head(), finalSystem).toCompletableFuture().get();
                    assertTrue(serviceHealth.isHealthy());
                });
                HttpResponse swaggerApiResponse = http.singleRequest(HttpRequest.GET("http://localhost:8080/api-docs/openapi.json")).toCompletableFuture().get();
                assertEquals(200, swaggerApiResponse.status().intValue());
            } finally {
                if (system != null) {
                    LOGGER.info("Stopping");
                    system.terminate();
                    LOGGER.info("Stopped");
                }
            }
        }
    }
    // TODO: add more test cases for all sample endpoints, testing all integrations
}
