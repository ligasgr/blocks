package example;


import org.apache.pekko.NotUsed;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.Scheduler;
import org.apache.pekko.actor.typed.javadsl.AskPattern;
import org.apache.pekko.event.Logging;
import org.apache.pekko.http.javadsl.Http;
import org.apache.pekko.http.javadsl.common.EntityStreamingSupport;
import org.apache.pekko.http.javadsl.marshallers.jackson.Jackson;
import org.apache.pekko.http.javadsl.model.ContentType;
import org.apache.pekko.http.javadsl.model.ContentTypes;
import org.apache.pekko.http.javadsl.model.HttpEntities;
import org.apache.pekko.http.javadsl.model.HttpRequest;
import org.apache.pekko.http.javadsl.model.StatusCodes;
import org.apache.pekko.http.javadsl.model.ws.TextMessage;
import org.apache.pekko.http.javadsl.server.AllDirectives;
import org.apache.pekko.http.javadsl.server.Route;
import org.apache.pekko.http.javadsl.unmarshalling.Unmarshaller;
import org.apache.pekko.japi.Pair;
import org.apache.pekko.japi.function.Procedure;
import org.apache.pekko.stream.Materializer;
import org.apache.pekko.stream.OverflowStrategy;
import org.apache.pekko.stream.connectors.jms.JmsMessage;
import org.apache.pekko.stream.connectors.jms.JmsProducerSettings;
import org.apache.pekko.stream.connectors.jms.JmsTextMessage;
import org.apache.pekko.stream.connectors.jms.javadsl.JmsConsumer;
import org.apache.pekko.stream.connectors.jms.javadsl.JmsProducer;
import org.apache.pekko.stream.javadsl.Flow;
import org.apache.pekko.stream.javadsl.Keep;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.stream.javadsl.Source;
import org.apache.pekko.stream.javadsl.SourceQueueWithComplete;
import org.apache.pekko.util.ByteString;
import blocks.couchbase.sdk3.CouchbaseSdk3Block;
//import blocks.couchbase.sdk2.CouchbaseSdk2Block;
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
import blocks.secrets.config.SecretsConfigBlock;
import blocks.service.BlockContext;
import blocks.service.BlockRef;
import blocks.service.JsonUtil;
import blocks.service.RequestMetrics;
import blocks.service.SecretsConfig;
import blocks.service.ServiceBuilder;
import blocks.service.ServiceConfig;
import blocks.service.ServiceProperties;
import blocks.service.ServiceProtocol;
import blocks.service.TypesafeServiceConfig;
import blocks.service.info.ServiceInfo;
import blocks.service.info.ServiceInfoBlock;
import blocks.service.info.ServiceInfoProtocol;
import blocks.storage.file.FileStorageBlock;
import blocks.storage.file.Storage;
import blocks.swagger.SwaggerBlock;
import blocks.swagger.ui.SwaggerUiBlock;
import blocks.ui.UiBlock;
import blocks.websocket.WebSocketBlock;
import blocks.websocket.WebSocketMessageHandler;
//import com.couchbase.client.java.AsyncCluster;
//import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.ReactiveCluster;
//import com.couchbase.client.java.document.json.JsonObject;
import com.couchbase.client.java.json.JsonObject;
//import com.couchbase.client.java.query.N1qlQuery;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.LongNode;
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
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.bson.Document;
import org.slf4j.Logger;
import reactor.core.publisher.Flux;
//import rx.RxReactiveStreams;

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
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.apache.pekko.http.javadsl.server.PathMatchers.integerSegment;
import static org.apache.pekko.http.javadsl.server.PathMatchers.remaining;
import static org.apache.pekko.http.javadsl.server.PathMatchers.segment;
import static blocks.service.ServiceProperties.Builder.serviceProperties;
import static com.couchbase.client.java.query.QueryOptions.queryOptions;

public class Main {

    public static final ObjectReader OBJECT_READER = JsonUtil.DEFAULT_OBJECT_MAPPER.reader();

    public Main() {
    }

    public static void main(String[] args) {
        startApplication(new TypesafeServiceConfig());
    }

    public static ActorSystem<ServiceProtocol.Message> startApplication(final TypesafeServiceConfig config) {
        BlockRef<ActorRef<HealthProtocol.Message>> healthBlockRef = HealthBlock.getRef("health");
        BlockRef<SecretsConfig> secretsConfigBlockRef = SecretsConfigBlock.getRef("secrets-config");
        BlockRef<ConnectionPool> rdbmsBlockRef = RdbmsBlock.getRef("rdbms-db");
        BlockRef<ReactiveCluster> couchbaseBlockRef = CouchbaseSdk3Block.getRef("couchbase");
//        BlockRef<Cluster> couchbaseBlockRef = CouchbaseSdk2Block.getRef("couchbase");
        BlockRef<MongoClient> mongoBlockRef = MongoBlock.getRef("mongo");
        BlockRef<Storage> fileStorageBlockRef = FileStorageBlock.getRef("fs");
        BlockRef<JmsObjectFactory> jmsBlockRef = JmsBlock.getRef("jms-activemq");
        BlockRef<ActorRef<ServiceInfoProtocol.Message>> serviceInfoBlockRef = ServiceInfoBlock.getRef("serviceInfo");
        Function<BlockContext, Routes> routesCreator = (context) -> new DirectiveRoutes(context, secretsConfigBlockRef, rdbmsBlockRef, couchbaseBlockRef, fileStorageBlockRef, mongoBlockRef, jmsBlockRef);
//        Function<BlockContext, WebSocketMessageHandler> wsHandlerCreator = (context) -> new WsHandler(context.context.getLog());
        Function<BlockContext, WebSocketMessageHandler> wsHandlerCreator = (context) -> new CountersAndHealthWsHandler(context.context.getLog(), context, serviceInfoBlockRef, healthBlockRef, context.context.getSystem().scheduler());
        BlockRef<KeyStore> keyStoreBlockRef = KeystoreBlock.getRef("httpsKeystore");
        final ServiceProperties staticProperties = serviceProperties()
            .add("serviceName", "app-example").build();
        Info info = new Info()
                .title("example app - " + config.getEnv())
                .description("<p>" +
                        "Simple akka-http application. "+
                        "<table>" +
                        "<tr><th>ENV</th><td>" + config.getEnv() +"</td></tr>"+
                        "</table>" +
                        "</p>")
                .contact(new Contact().name("Developer").email("dev@blocks"))
                .version("1.0");
        WebSocketBlock webSocketBlock = new WebSocketBlock(wsHandlerCreator, "ws", "ws",
                ctx -> {
                    final ActorRef<ServiceInfoProtocol.Message> blockOutput = ctx.getBlockOutput(serviceInfoBlockRef);
                    return () -> blockOutput.tell(new ServiceInfoProtocol.UpdateCounter("activeWebSockets", 1L));
                },
                ctx -> {
                    final ActorRef<ServiceInfoProtocol.Message> blockOutput = ctx.getBlockOutput(serviceInfoBlockRef);
                    return () -> blockOutput.tell(new ServiceInfoProtocol.UpdateCounter("activeWebSockets", -1L));
                });
        return ServiceBuilder.newService()
                .withBlock(keyStoreBlockRef, new KeystoreBlock("PKCS12", "p12", "https.keystore.password", secretsConfigBlockRef), secretsConfigBlockRef)
                .withBlock(new HttpsBlock(keyStoreBlockRef, "https.keystore.password", secretsConfigBlockRef), keyStoreBlockRef, secretsConfigBlockRef)
                .withBlock(serviceInfoBlockRef, new ServiceInfoBlock(staticProperties))
                .withBlock(healthBlockRef, new HealthBlock(staticProperties))
                .withBlock(new SwaggerBlock(info))
                .withBlock(new SwaggerUiBlock())
                .withBlock(new UiBlock())
                .withBlock(webSocketBlock, serviceInfoBlockRef, healthBlockRef)
                .withBlock(couchbaseBlockRef, new CouchbaseSdk3Block(healthBlockRef, secretsConfigBlockRef, "couchbase"), healthBlockRef, secretsConfigBlockRef)
//                .withBlock(couchbaseBlockRef, new CouchbaseSdk2Block(healthBlockRef, secretsConfigBlockRef, "couchbase"), healthBlockRef, secretsConfigBlockRef)
                .withBlock(mongoBlockRef, new MongoBlock(healthBlockRef, secretsConfigBlockRef, "mongo"), healthBlockRef, secretsConfigBlockRef)
                .withBlock(secretsConfigBlockRef, new SecretsConfigBlock())
                .withBlock(rdbmsBlockRef, new RdbmsBlock(healthBlockRef, secretsConfigBlockRef, "db"), healthBlockRef, secretsConfigBlockRef)
                .withBlock(fileStorageBlockRef, new FileStorageBlock("storage"))
                .withBlock(jmsBlockRef, new JmsBlock(healthBlockRef, "activemq", Optional.of(blockContext -> blockContext.getBlockOutput(secretsConfigBlockRef).getSecret("activemq.securityCredentials"))), healthBlockRef, secretsConfigBlockRef)
                .withBlock(new RestEndpointsBlock(routesCreator, Collections.singleton(DirectiveRoutes.class), healthBlockRef, "rest"), healthBlockRef, secretsConfigBlockRef, rdbmsBlockRef, couchbaseBlockRef, fileStorageBlockRef, mongoBlockRef, jmsBlockRef)
                .withRequestLogger(system -> Logging.getLogger(system.classicSystem(), "requests-logging"))
                .withRequestsMessageFunction(RequestMetrics.DEFAULT_MESSAGE_FUNCTION)
                .withRequestsStartNotificationRunnableCreator(ctx -> {
                    final ActorRef<ServiceInfoProtocol.Message> blockOutput = ctx.getBlockOutput(serviceInfoBlockRef);
                    return () -> {
                        blockOutput.tell(new ServiceInfoProtocol.UpdateCounter("activeRequests", 1L));
                        blockOutput.tell(new ServiceInfoProtocol.UpdateCounter("totalRequests", 1L));
                    };
                })
                .withRequestsEndNotificationRunnableCreator(ctx -> {
                    final ActorRef<ServiceInfoProtocol.Message> blockOutput = ctx.getBlockOutput(serviceInfoBlockRef);
                    return () -> blockOutput.tell(new ServiceInfoProtocol.UpdateCounter("activeRequests", -1L));
                })
                .start(Clock.systemDefaultZone(), config);
    }

    @Path("")
    private static class DirectiveRoutes extends AllDirectives implements Routes {
        final AtomicInteger count = new AtomicInteger(0);
        private final SecretsConfig secretsConfig;
        private final ConnectionPool connectionPool;
        private final ReactiveCluster reactiveCluster;
//        private final AsyncCluster asyncCluster;
        private final Storage storage;
        private final MongoClient mongoClient;
        private final ConcurrentLinkedQueue<String> messages = new ConcurrentLinkedQueue<>();
        private final SourceQueueWithComplete<String> messagesQueue;

        public DirectiveRoutes(final BlockContext context,
                               final BlockRef<SecretsConfig> secretsConfigBlockRef,
                               final BlockRef<ConnectionPool> rdbmsBlockRef,
                               final BlockRef<ReactiveCluster> couchbaseBlockRef,
//                               final BlockRef<Cluster> couchbaseBlockRef,
                               final BlockRef<Storage> fileStorageBlockRef,
                               final BlockRef<MongoClient> mongoBlockRef,
                               final BlockRef<JmsObjectFactory> jmsBlockRef) {
            secretsConfig = context.getBlockOutput(secretsConfigBlockRef);
            connectionPool = context.getBlockOutput(rdbmsBlockRef);
            reactiveCluster = context.getBlockOutput(couchbaseBlockRef);
//            asyncCluster = context.getBlockOutput(couchbaseBlockRef).async();
            mongoClient = context.getBlockOutput(mongoBlockRef);
            storage = context.getBlockOutput(fileStorageBlockRef);
            final JmsObjectFactory jmsObjectFactory = context.getBlockOutput(jmsBlockRef);
            String queueName = "test";
            ActorSystem<Void> system = context.context.getSystem();
            final Materializer abc = Materializer.matFromSystem(system);
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
//                Publisher<Object> publisher = RxReactiveStreams.toPublisher(asyncCluster.query(N1qlQuery.parameterized("select * from `beer-sample` where `type`='brewery' and `state` = 'California' limit $count", JsonObject.create().put("count", count)))
//                        .flatMap(rs -> rs.rows().<Object>map(asyncN1qlQueryRow -> getJsonNode(new String(asyncN1qlQueryRow.byteValue())))));
//                return completeOKWithSource(Source.fromPublisher(publisher), Jackson.marshaller(), EntityStreamingSupport.json());
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

    private static JsonNode getJsonNode(final String json) {
        try {
            return OBJECT_READER.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(e);
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

    private static class CountersAndHealthWsHandler implements WebSocketMessageHandler {
        private final Logger log;
        private final Scheduler scheduler;
        private final ActorRef<ServiceInfoProtocol.Message> serviceInfoActor;
        private final Map<String, SourceQueueWithComplete<TextMessage>> queueForSession = new ConcurrentHashMap<>();

        public CountersAndHealthWsHandler(final Logger log, final BlockContext context, final BlockRef<ActorRef<ServiceInfoProtocol.Message>> serviceInfoActorFuture, final BlockRef<ActorRef<HealthProtocol.Message>> healthBlockRef, final Scheduler scheduler) {
            this.log = log;
            this.scheduler = scheduler;
            this.serviceInfoActor = context.getBlockOutput(serviceInfoActorFuture);
            this.serviceInfoActor.tell(new ServiceInfoProtocol.SubscribeToCounterUpdates("counterWsHandler", (name, value)-> {
                TextMessage message = createMessage(log, Collections.singletonMap(name, LongNode.valueOf(value)));
                log.info("Got update of counter: {}", message.getStrictText());
                queueForSession.values().forEach(q -> q.offer(message));
            }));
            ActorRef<HealthProtocol.Message> healthActor = context.getBlockOutput(healthBlockRef);
            healthActor.tell(new HealthProtocol.SubscribeToHealthChangeUpdates("serviceInfo", healthAndLatestUpdate -> {
                Boolean isHealthy = healthAndLatestUpdate.first();
                TextMessage message = createMessage(log, Collections.singletonMap("isHealthy", BooleanNode.valueOf(isHealthy)));
                log.info("Got health update isHealthy: {}", message.getStrictText());
                queueForSession.values().forEach(q -> q.offer(message));
            }));
        }

        @Override
        public String generateSessionId() {
            return UUID.randomUUID().toString();
        }

        @Override
        public void registerOutgoingQueue(final String session, final SourceQueueWithComplete<TextMessage> outgoingMessageQueue) {
            queueForSession.put(session, outgoingMessageQueue);
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
            CompletionStage<ServiceInfo> serviceInfoFuture = AskPattern.ask(this.serviceInfoActor, ServiceInfoProtocol.GetServiceInfo::new, Duration.ofSeconds(10), scheduler);
            Map<String, JsonNode> initialServiceInfoValue;
            try {
                initialServiceInfoValue = serviceInfoFuture.toCompletableFuture().get().serviceInfo;
            } catch (InterruptedException | ExecutionException e) {
                log.error("Failed to obtain current service info", e);
                initialServiceInfoValue = Collections.emptyMap();
            }
            return createMessage(log, initialServiceInfoValue);
        }

    }
    private static TextMessage createMessage(final Logger log, final Map<String, JsonNode> values) {
        try {
            return TextMessage.create(JsonUtil.DEFAULT_OBJECT_MAPPER.writeValueAsString(values));
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize message", e);
        }
        return TextMessage.create("{}");
    }
}
