package example;


import akka.NotUsed;
import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.http.javadsl.Http;
import akka.http.javadsl.common.EntityStreamingSupport;
import akka.http.javadsl.marshallers.jackson.Jackson;
import akka.http.javadsl.model.ContentType;
import akka.http.javadsl.model.ContentTypes;
import akka.http.javadsl.model.HttpEntities;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.model.ws.TextMessage;
import akka.http.javadsl.server.AllDirectives;
import akka.http.javadsl.server.Route;
import akka.http.javadsl.unmarshalling.Unmarshaller;
import akka.japi.Pair;
import akka.japi.function.Procedure;
import akka.stream.OverflowStrategy;
import akka.stream.alpakka.jms.JmsMessage;
import akka.stream.alpakka.jms.JmsProducerSettings;
import akka.stream.alpakka.jms.JmsTextMessage;
import akka.stream.alpakka.jms.javadsl.JmsConsumer;
import akka.stream.alpakka.jms.javadsl.JmsProducer;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Keep;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.stream.javadsl.SourceQueueWithComplete;
import akka.util.ByteString;
import blocks.couchbase.CouchbaseBlock;
import blocks.health.HealthBlock;
import blocks.health.HealthProtocol;
import blocks.https.HttpsBlock;
import blocks.jms.JmsBlock;
import blocks.jms.JmsObjectFactory;
import blocks.keystore.KeystoreBlock;
import blocks.mongo.MongoBlock;
import blocks.rdbms.RdbmsBlock;
import blocks.rest.RestEndpointsBlock;
import blocks.rest.RestEndpointsHealthChecks;
import blocks.rest.RestEndpointsSmokeTests;
import blocks.rest.Routes;
import blocks.secrets.config.SecretsConfig;
import blocks.secrets.config.SecretsConfigBlock;
import blocks.service.BlockContext;
import blocks.service.BlockRef;
import blocks.service.ServiceBuilder;
import blocks.service.ServiceConfig;
import blocks.service.TypesafeServiceConfig;
import blocks.storage.file.FileStorageBlock;
import blocks.storage.file.Storage;
import blocks.swagger.SwaggerBlock;
import blocks.swagger.ui.SwaggerUiBlock;
import blocks.ui.UiBlock;
import blocks.websocket.WebSocketBlock;
import blocks.websocket.WebSocketMessageHandler;
import com.couchbase.client.java.ReactiveCluster;
import com.couchbase.client.java.json.JsonObject;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.mongodb.reactivestreams.client.FindPublisher;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.MongoDatabase;
import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.spi.Connection;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.bson.Document;
import org.slf4j.Logger;
import reactor.core.publisher.Flux;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.time.Clock;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static akka.http.javadsl.server.PathMatchers.integerSegment;
import static akka.http.javadsl.server.PathMatchers.remaining;
import static akka.http.javadsl.server.PathMatchers.segment;
import static com.couchbase.client.java.query.QueryOptions.queryOptions;

public class Main {
    public static void main(String[] args) {
        BlockRef<ActorRef<HealthProtocol.Message>> healthBlockRef = HealthBlock.getRef("health");
        BlockRef<SecretsConfig> secretsConfigBlockRef = SecretsConfigBlock.getRef("secrets-config");
        BlockRef<ConnectionPool> rdbmsBlockRef = RdbmsBlock.getRef("rdbms-db");
        BlockRef<ReactiveCluster> couchbaseBlockRef = CouchbaseBlock.getRef("couchbase");
        BlockRef<MongoClient> mongoBlockRef = CouchbaseBlock.getRef("mongo");
        BlockRef<Storage> fileStorageBlockRef = FileStorageBlock.getRef("fs");
        BlockRef<JmsObjectFactory> jmsBlockRef = JmsBlock.getRef("jms-activemq");
        Function<BlockContext, Routes> routesCreator = (context) -> new DirectiveRoutes(context, secretsConfigBlockRef, rdbmsBlockRef, couchbaseBlockRef, fileStorageBlockRef, mongoBlockRef, jmsBlockRef);
        Function<BlockContext, WebSocketMessageHandler> wsHandlerCreator = (context) -> new WsHandler(context.context.getLog());
        BlockRef<KeyStore> keyStoreBlockRef = KeystoreBlock.getRef("httpsKeystore");
        final Map<String, JsonNode> staticProperties = new HashMap<>();
        staticProperties.put("serviceName", TextNode.valueOf("app-example"));
        ServiceBuilder.newService()
                .withBlock(keyStoreBlockRef, new KeystoreBlock("PKCS12", "p12", "https.keystore.password", secretsConfigBlockRef), secretsConfigBlockRef)
                .withBlock(HttpsBlock.getRef("https"), new HttpsBlock(keyStoreBlockRef, "https.keystore.password", secretsConfigBlockRef), keyStoreBlockRef, secretsConfigBlockRef)
                .withBlock(healthBlockRef, new HealthBlock(staticProperties))
                .withBlock(SwaggerBlock.getRef("swagger"), new SwaggerBlock())
                .withBlock(SwaggerUiBlock.getRef("swagger-ui"), new SwaggerUiBlock())
                .withBlock(UiBlock.getRef("ui"), new UiBlock())
                .withBlock(WebSocketBlock.getRef("ws"), new WebSocketBlock(wsHandlerCreator, "ws", "ws"))
                .withBlock(couchbaseBlockRef, new CouchbaseBlock(healthBlockRef, secretsConfigBlockRef, "couchbase"), healthBlockRef, secretsConfigBlockRef)
                .withBlock(mongoBlockRef, new MongoBlock(healthBlockRef, secretsConfigBlockRef, "mongo"), healthBlockRef, secretsConfigBlockRef)
                .withBlock(secretsConfigBlockRef, new SecretsConfigBlock())
                .withBlock(rdbmsBlockRef, new RdbmsBlock(healthBlockRef, secretsConfigBlockRef, "db"), healthBlockRef, secretsConfigBlockRef)
                .withBlock(fileStorageBlockRef, new FileStorageBlock("storage"))
                .withBlock(jmsBlockRef, new JmsBlock(healthBlockRef, "activemq", Optional.of(blockContext -> blockContext.getBlockOutput(secretsConfigBlockRef).getSecret("activemq.securityCredentials"))), healthBlockRef, secretsConfigBlockRef)
                .withBlock(RestEndpointsBlock.getRef("rest"), new RestEndpointsBlock(routesCreator, Collections.singleton(DirectiveRoutes.class), healthBlockRef), healthBlockRef, secretsConfigBlockRef, rdbmsBlockRef, couchbaseBlockRef, fileStorageBlockRef, mongoBlockRef, jmsBlockRef)
                .start(Clock.systemDefaultZone(), new TypesafeServiceConfig());
    }

    @Path("")
    private static class DirectiveRoutes extends AllDirectives implements Routes {
        final AtomicInteger count = new AtomicInteger(0);
        private final SecretsConfig secretsConfig;
        private final ConnectionPool connectionPool;
        private final ReactiveCluster reactiveCluster;
        private final Storage storage;
        private final MongoClient mongoClient;
        private final ConcurrentLinkedQueue<String> messages = new ConcurrentLinkedQueue<>();
        private final SourceQueueWithComplete<String> messagesQueue;

        public DirectiveRoutes(final BlockContext context,
                               final BlockRef<SecretsConfig> secretsConfigBlockRef,
                               final BlockRef<ConnectionPool> rdbmsBlockRef,
                               final BlockRef<ReactiveCluster> couchbaseBlockRef,
                               final BlockRef<Storage> fileStorageBlockRef,
                               final BlockRef<MongoClient> mongoBlockRef,
                               final BlockRef<JmsObjectFactory> jmsBlockRef) {
            secretsConfig = context.getBlockOutput(secretsConfigBlockRef);
            connectionPool = context.getBlockOutput(rdbmsBlockRef);
            reactiveCluster = context.getBlockOutput(couchbaseBlockRef);
            mongoClient = context.getBlockOutput(mongoBlockRef);
            storage = context.getBlockOutput(fileStorageBlockRef);
            final JmsObjectFactory jmsObjectFactory = context.getBlockOutput(jmsBlockRef);
            String queueName = "test";
            jmsObjectFactory.<String>getConsumer(queueName, s -> JmsConsumer.textSource(s.withBufferSize(10).withQueue(queueName)))
                    .runWith(Sink.foreach(messages::add), context.context.getSystem());
            final Sink<String, NotUsed> messagesSink = jmsObjectFactory.<String>getProducer(queueName, s -> {
                        JmsProducerSettings settings = s.withQueue(queueName);
                        return Flow.<String, JmsMessage>fromFunction(JmsTextMessage::create)
                                .viaMat(JmsProducer.flow(settings), Keep.right())
                                .to(Sink.ignore());
                    }
            );
            messagesQueue = Source.<String>queue(0, OverflowStrategy.backpressure())
                    .to(messagesSink)
                    .run(context.context.getSystem());
        }

        @Override
        public Route route() {
            return concat(
                    get(this::getSample),
                    get(this::getSecret),
                    get(this::getRdbmsValue),
                    get(this::getCouchbaseValues),
                    get(this::getMongoValues),
                    get(this::getStorageFile),
                    get(this::getJmsMessages),
                    post(this::postJmsMessage)
            );
        }

        @GET
        @Path("sample/v1/{value}")
        @Produces(MediaType.TEXT_PLAIN)
        @Operation(
                summary = "Get some sample value back", description = "More notes about this method", tags = {"sample"},
                parameters = {
                        @Parameter(name = "value", description = "Value in path", in = ParameterIn.PATH, required = true),
                        @Parameter(name = "param", description = "Value in query param", in = ParameterIn.QUERY)
                }
        )
        @ApiResponses({
                @ApiResponse(responseCode = "200", description = "ok",
                        content = @Content(schema = @Schema(implementation = String.class))
                ),
                @ApiResponse(responseCode = "500", description = "failed",
                        content = @Content(schema = @Schema(implementation = String.class))
                )
        })
        public Route getSample() {
            return path(segment("sample").slash("v1").slash(remaining()), (valueUndecoded) -> get(() ->
                            parameterOptional("param", param -> {
                                String value = decode(valueUndecoded);
//                log.info("running sample");
//                if (count.incrementAndGet() % 2 != 1) {
//                    try {
//                        Thread.sleep(2000L);
//                    } catch (InterruptedException e) {
//                        e.printStackTrace();
//                    }
//                    //                                    return complete(StatusCodes.INTERNAL_SERVER_ERROR);
//                }
                                return complete("response: " + value + param.map(v -> " (" + v + ")").orElse(""));
                            })

            ));
        }

        @GET
        @Path("secret/v1/{key}")
        @Produces(MediaType.TEXT_PLAIN)
        @Operation(
                summary = "Get some sample secret value back", description = "More notes about this method", tags = {"secret"},
                parameters = {@Parameter(name = "key", description = "Value in path", in = ParameterIn.PATH, required = true)}
        )
        @ApiResponses({
                @ApiResponse(responseCode = "200", description = "ok",
                        content = @Content(schema = @Schema(implementation = String.class))
                ),
                @ApiResponse(responseCode = "500", description = "failed",
                        content = @Content(schema = @Schema(implementation = String.class))
                )
        })
        public Route getSecret() {
            return path(segment("secret").slash("v1").slash(remaining()), (value) -> get(() -> {
                        String key = decode(value);
                        try {
                            return complete(secretsConfig.getSecret(key));
                        } catch (Exception e) {
                            return complete(StatusCodes.BAD_REQUEST, e.getMessage());
                        }
                    }
            ));
        }

        @GET
        @Path("rdbms/v1/{key}")
        @Produces(MediaType.APPLICATION_JSON)
        @Operation(
                summary = "Get some db value back", description = "More notes about this method", tags = {"rdbms"},
                parameters = {@Parameter(name = "key", description = "Value in path", in = ParameterIn.PATH, required = true)}
        )
        @ApiResponses({
                @ApiResponse(responseCode = "200", description = "ok",
                        content = @Content(schema = @Schema(implementation = String.class))
                ),
                @ApiResponse(responseCode = "500", description = "failed",
                        content = @Content(schema = @Schema(implementation = String.class))
                )
        })
        public Route getRdbmsValue() {
            return path(segment("rdbms").slash("v1").slash(remaining()), (value) -> get(() -> {
                String key = decode(value);
                Flux<String> publisher = Flux.usingWhen(connectionPool.create(), c ->
                                Flux.from(c.createStatement("select key from sample_schema.sample_table where key = $1").bind("$1", key).execute())
                                        .flatMap(r -> Flux.from(r.map((result, meta) -> Optional.ofNullable(result.get(0, String.class)))))
                                        .flatMap(z -> z.map(Flux::just).orElse(Flux.empty())),
                        Connection::close
                );
                return completeOKWithSource(Source.fromPublisher(publisher), Jackson.marshaller(), EntityStreamingSupport.json());
            }));
        }

        @GET
        @Path("couchbase/v1/{count}")
        @Produces(MediaType.APPLICATION_JSON)
        @Operation(
                summary = "Get some db value back", description = "More notes about this method", tags = {"couchbase"},
                parameters = {@Parameter(name = "count", description = "Number of items to fetch", in = ParameterIn.PATH, required = true, schema = @Schema(implementation = Integer.class))}
        )
        @ApiResponses({
                @ApiResponse(responseCode = "200", description = "ok",
                        content = @Content(schema = @Schema(implementation = JsonNode.class))
                ),
                @ApiResponse(responseCode = "500", description = "failed",
                        content = @Content(schema = @Schema(implementation = String.class))
                )
        })
        public Route getCouchbaseValues() {
            return path(segment("couchbase").slash("v1").slash(integerSegment()), (count) -> get(() -> {
                Flux<Object> publisher = reactiveCluster.query("select * from `beer-sample` where `type`='brewery' and `state` = 'California' limit $count", queryOptions().parameters(JsonObject.create().put("count", count)))
                        .flatMapMany(r -> r.rowsAs(JsonNode.class));
                return completeOKWithSource(Source.fromPublisher(publisher), Jackson.marshaller(), EntityStreamingSupport.json());
            }));
        }

        @GET
        @Path("mongo/v1/{count}")
        @Produces(MediaType.APPLICATION_JSON)
        @Operation(
                summary = "Get some db value back", description = "More notes about this method", tags = {"mongo"},
                parameters = {@Parameter(name = "count", description = "Number of items to fetch", in = ParameterIn.PATH, required = true, schema = @Schema(implementation = Integer.class))}
        )
        @ApiResponses({
                @ApiResponse(responseCode = "200", description = "ok",
                        content = @Content(schema = @Schema(implementation = JsonNode.class))
                ),
                @ApiResponse(responseCode = "500", description = "failed",
                        content = @Content(schema = @Schema(implementation = String.class))
                )
        })
        public Route getMongoValues() {
            return path(segment("mongo").slash("v1").slash(integerSegment()), (count) -> get(() -> {
                MongoDatabase database = mongoClient.getDatabase("local");
                MongoCollection<Document> collection = database.getCollection("testCollection");
                FindPublisher<Document> publisher = collection.find().limit(count);
                return completeOKWithSource(Source.fromPublisher(publisher), Jackson.marshaller(), EntityStreamingSupport.json());
            }));
        }

        @GET
        @Path("storage/v1/{path}")
        @Operation(
                summary = "Get some db value back", description = "More notes about this method", tags = {"storage"},
                parameters = {@Parameter(name = "path", description = "File path", in = ParameterIn.PATH, required = true, schema = @Schema(implementation = String.class))}
        )
        @ApiResponses({
                @ApiResponse(responseCode = "200", description = "ok",
                        content = @Content(schema = @Schema(implementation = String.class))
                ),
                @ApiResponse(responseCode = "500", description = "failed",
                        content = @Content(schema = @Schema(implementation = String.class))
                )
        })
        public Route getStorageFile() {
            return path(segment("storage").slash("v1").slash(remaining()), (path) -> get(() ->
                    extractLog(log -> {
                        ContentType.WithCharset contentType = ContentTypes.TEXT_PLAIN_UTF8;
                        Source<ByteString, NotUsed> source = storage.fromPath(path).mapMaterializedValue(ignore -> NotUsed.getInstance());
                        if (storage.hasPath(path)) {
                            return complete(StatusCodes.OK, HttpEntities.create(contentType, source));
                        } else {
                            return complete(StatusCodes.NOT_FOUND);
                        }
                    })
            ));
        }

        @GET
        @Path("jms/v1/messages")
        @Operation(summary = "Get all jms messages received", description = "More notes about this method", tags = {"jms"})
        @ApiResponses({
                @ApiResponse(responseCode = "200", description = "ok",
                        content = @Content(schema = @Schema(implementation = String.class))
                ),
                @ApiResponse(responseCode = "500", description = "failed",
                        content = @Content(schema = @Schema(implementation = String.class))
                )
        })
        public Route getJmsMessages() {
            return completeOKWithSource(Source.from(messages).map(TextNode::valueOf), Jackson.marshaller(), EntityStreamingSupport.json());
        }

        @POST
        @Path("jms/v1/messages")
        @Operation(
                summary = "Post another jms message received", description = "More notes about this method", tags = {"jms"},
                requestBody = @RequestBody(required = true, content = @Content(mediaType = "text/plain", schema = @Schema(implementation = String.class)))
        )
        @ApiResponses({
                @ApiResponse(responseCode = "200", description = "ok",
                        content = @Content(schema = @Schema(implementation = String.class))
                ),
                @ApiResponse(responseCode = "500", description = "failed",
                        content = @Content(schema = @Schema(implementation = String.class))
                )
        })
        public Route postJmsMessage() {
            return entity(Unmarshaller.entityToString(), message ->
                    onComplete(messagesQueue.offer(message), tryResult -> {
                        if (tryResult.isSuccess()) {
                            return complete("OK");
                        } else {
                            return complete(StatusCodes.INTERNAL_SERVER_ERROR, tryResult.failed().get().getMessage());
                        }
                    })
            );
        }

        @Override
        public RestEndpointsHealthChecks healthChecks(final ActorSystem<Void> system, final ServiceConfig config) {
            final String uri = config.getHttpPort().isPresent()
                    ? "http://" + config.getHost() + ":" + config.getHttpPort().get()
                    : "https://" + config.getHost() + ":" + config.getHttpsPort().get();
            final Map<String, Procedure<Pair<Http, ActorSystem<Void>>>> tests = new LinkedHashMap<>();
            tests.put("sample", httpActorSystemPair -> {
                ActorSystem<Void> actorSystem = httpActorSystemPair.second();
                Logger log = actorSystem.log();
                log.info("Starting sample check");
                httpActorSystemPair.first().singleRequest(HttpRequest.GET(uri + "/sample/v1/abc"))
                        .toCompletableFuture()
                        .thenAccept(response -> {
                            response.entity().discardBytes(actorSystem);
                            if (response.status() != StatusCodes.OK) {
                                throw new RuntimeException("Status code not OK");
                            }
                        }).get(1L, TimeUnit.SECONDS);
                log.info("Finished sample check");
            });

            return new RestEndpointsSmokeTests(system, tests);
        }
    }

    private static String decode(String valueUndecoded) {
        try {
            return URLDecoder.decode(valueUndecoded, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private static class WsHandler implements WebSocketMessageHandler {
        private final Logger log;

        public WsHandler(final Logger log) {
            this.log = log;
        }

        @Override
        public String generateSessionId() {
            return UUID.randomUUID().toString();
        }

        @Override
        public TextMessage keepAliveMessage() {
            return TextMessage.create("{}");
        }

        @Override
        public TextMessage handleException(final String session, final Throwable throwable, final SourceQueueWithComplete<TextMessage> outgoingMessagesQueue) {
            log.error("[" + session + "] Got exception in session: {}" + throwable.getMessage(), throwable);
            return TextMessage.create("{}");
        }

        @Override
        public TextMessage handleTextMessage(final String session, final TextMessage msg, final SourceQueueWithComplete<TextMessage> outgoingMessagesQueue) {
            String messageText = msg.getStrictText();
            log.info("Got message '{}' in session: {}", messageText, session);
            outgoingMessagesQueue.offer(TextMessage.create("{\"" + session + "\":\"" + messageText + "\"}"));
            return TextMessage.create("{\"" + session + "\":\"ok\"}");
        }
    }
}
