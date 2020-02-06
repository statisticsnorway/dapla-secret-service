package no.ssb.dapla.secret;

import io.helidon.health.HealthSupport;
import io.helidon.health.checks.HealthChecks;
import io.helidon.webserver.Routing;
import io.helidon.webserver.Service;
import io.helidon.webserver.WebServer;
import no.ssb.dapla.readiness.Readiness;
import no.ssb.dapla.readiness.ReadinessSample;
import org.eclipse.microprofile.health.HealthCheckResponse;

import java.util.function.Supplier;

public class HealthService implements Service {

    private final Readiness readiness;
    private final Supplier<WebServer> webServerSupplier;

    public HealthService(Readiness readiness, Supplier<WebServer> webServerSupplier) {
        this.readiness = readiness;
        this.webServerSupplier = webServerSupplier;
    }

    @Override
    public void update(Routing.Rules rules) {
        rules.register(HealthSupport.builder()
                .addLiveness(HealthChecks.healthChecks())
                .addLiveness(() -> HealthCheckResponse.named("LivenessCheck")
                        .up()
                        .withData("time", System.currentTimeMillis())
                        .build())
                .addReadiness(() -> {
                    ReadinessSample sample = readiness.getAndKeepaliveReadinessSample();
                    return HealthCheckResponse.named("ReadinessCheck")
                            .state(webServerSupplier.get().isRunning() && sample.isReady())
                            .withData("time", sample.getTime())
                            .build();
                })
                .build());
    }
}
