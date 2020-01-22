package no.ssb.dapla.secret;

import io.helidon.config.Config;
import io.helidon.grpc.server.GrpcRouting;
import io.helidon.grpc.server.GrpcServer;
import io.helidon.grpc.server.GrpcServerConfiguration;
import io.helidon.metrics.MetricsSupport;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerConfiguration;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.accesslog.AccessLogSupport;
import no.ssb.helidon.application.DefaultHelidonApplication;
import no.ssb.helidon.media.protobuf.ProtobufJsonSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

public class Application extends DefaultHelidonApplication {

    private static final Logger LOG;

    static {
        installSlf4jJulBridge();
        LOG = LoggerFactory.getLogger(Application.class);
    }

    public Application(Config config) {
        put(Config.class, config);

        SecretRepository secretRepository = new SecretRepository();
        put(SecretRepository.class, secretRepository);

        SecretService secretService = new SecretService(secretRepository);
        put(SecretService.class, secretService);

        GrpcServer grpcserver = GrpcServer.create(
                GrpcServerConfiguration.create(config.get("grpcserver")),
                GrpcRouting.builder()
                        .register(secretService)
                        .build()
        );
        put(GrpcServer.class, grpcserver);

        Routing routing = Routing.builder()
                .register(AccessLogSupport.create(config.get("webserver.access-log")))
                .register(ProtobufJsonSupport.create())
                .register(MetricsSupport.create())
                .register("/secret", secretService)
                .build();
        put(Routing.class, routing);

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
}
