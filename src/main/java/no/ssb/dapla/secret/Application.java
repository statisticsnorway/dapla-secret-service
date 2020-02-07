package no.ssb.dapla.secret;

import io.grpc.ManagedChannel;
import io.helidon.config.Config;
import io.helidon.grpc.server.GrpcRouting;
import io.helidon.grpc.server.GrpcServer;
import io.helidon.grpc.server.GrpcServerConfiguration;
import io.helidon.metrics.MetricsSupport;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerConfiguration;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.accesslog.AccessLogSupport;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.PoolOptions;
import no.ssb.dapla.auth.dataset.protobuf.AuthServiceGrpc.AuthServiceFutureStub;
import no.ssb.dapla.readiness.Readiness;
import no.ssb.helidon.application.DefaultHelidonApplication;
import no.ssb.helidon.application.HelidonApplication;
import no.ssb.helidon.media.protobuf.ProtobufJsonSupport;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

public class Application extends DefaultHelidonApplication {

    private static final Logger LOG;

    static {
        installSlf4jJulBridge();
        LOG = LoggerFactory.getLogger(Application.class);
    }

    public Application(Config config, AuthServiceFutureStub authService) {
        put(Config.class, config);

        put(AuthServiceFutureStub.class, authService);

        // Initialize vertx postgres client
        PgPool pgPool = initPgPool(config.get("pgpool"));

        String host = config.get("pgpool.connect-options.host").asString().orElse("no-host");
        int port = config.get("pgpool.connect-options.port").asInt().orElse(-1);

        // Initialize readiness
        Readiness readiness = Readiness.newBuilder(new PostgresConnectivityCheck(pgPool, host, port))
                .setBlockingReadinessCheckMaxAttempts(config.get("readiness.db-connectivity-attempts").asInt().orElse(1))
                .setMinSampleInterval(config.get("readiness.min-sample-interval").asInt().orElse(0))
                .build();

        // Block until ready
        readiness.blockingReadinessCheck();

        // Create schema if not exists
        createDatabaseSchemaIfNotExists(config.get("flyway"));

        PgPool readinessAwarePgPool = new ReadinessAwarePgPool(pgPool, readiness);
        put(PgPool.class, readinessAwarePgPool);

        // Repository
        SecretRepository secretRepository = new SecretRepository(readinessAwarePgPool);
        put(SecretRepository.class, secretRepository);

        // Grpc Service
        SecretServiceGrpc grpcService = new SecretServiceGrpc(secretRepository, authService);
        put(SecretServiceGrpc.class, grpcService);

        // Grpc Server
        GrpcServer grpcserver = GrpcServer.create(
                GrpcServerConfiguration.create(config.get("grpcserver")),
                GrpcRouting.builder()
                        .register(grpcService)
                        .build()
        );
        put(GrpcServer.class, grpcserver);

        HealthService healthService = new HealthService(readiness, () -> get(WebServer.class));

        // HTTP Service
        SecretServiceHttp httpService = new SecretServiceHttp(secretRepository, authService);
        put(SecretServiceHttp.class, httpService);

        // Routing
        Routing routing = Routing.builder()
                .register(AccessLogSupport.create(config.get("webserver.access-log")))
                .register(ProtobufJsonSupport.create())
                .register(MetricsSupport.create())
                .register(healthService)
                .register("/secret", httpService)
                .build();
        put(Routing.class, routing);

        // HTTP Server
        ServerConfiguration configuration = ServerConfiguration.builder(config.get("webserver")).build();
        WebServer webServer = WebServer.create(configuration, routing);
        put(WebServer.class, webServer);
    }

    public static void main(String[] args) {
        long startTime = System.currentTimeMillis();
        new ApplicationBuilder().build()
                .start()
                .toCompletableFuture()
                .orTimeout(10, TimeUnit.SECONDS)
                .thenAccept(app -> LOG.info("Webserver running at port: {}, Grpcserver running at port: {}, started in {} ms",
                        app.get(WebServer.class).port(), app.get(GrpcServer.class).port(), System.currentTimeMillis() - startTime))
                .exceptionally(throwable -> {
                    LOG.error("While starting application", throwable);
                    System.exit(1);
                    return null;
                });
    }

    private void createDatabaseSchemaIfNotExists(Config flywayConfig) {
        Flyway flyway = Flyway.configure()
                .dataSource(
                        flywayConfig.get("url").asString().orElse("jdbc:postgresql://localhost:15432/rdc"),
                        flywayConfig.get("user").asString().orElse("rdc"),
                        flywayConfig.get("password").asString().orElse("rdc")
                )
                .load();
        flyway.migrate();
    }

    private PgPool initPgPool(Config pgPoolConfig) {
        Config connectConfig = pgPoolConfig.get("connect-options");
        PgConnectOptions connectOptions = new PgConnectOptions()
                .setPort(connectConfig.get("port").asInt().orElse(15432))
                .setHost(connectConfig.get("host").asString().orElse("localhost"))
                .setDatabase(connectConfig.get("database").asString().orElse("rdc"))
                .setUser(connectConfig.get("user").asString().orElse("rdc"))
                .setPassword(connectConfig.get("password").asString().orElse("rdc"));

        Config poolConfig = pgPoolConfig.get("pool-options");
        PoolOptions poolOptions = new PoolOptions().setMaxSize(poolConfig.get("max-size").asInt().orElse(5));

        return PgPool.pool(connectOptions, poolOptions);
    }

    @Override
    public CompletionStage<HelidonApplication> stop() {
        return super.stop().thenCombine(
                CompletableFuture.runAsync(() -> shutdownAndAwaitTermination((ManagedChannel) get(AuthServiceFutureStub.class).getChannel())), (application, aVoid) -> this
        );
    }
}
