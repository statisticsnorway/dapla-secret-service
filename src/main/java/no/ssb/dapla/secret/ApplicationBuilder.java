package no.ssb.dapla.secret;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.helidon.config.Config;
import no.ssb.dapla.auth.dataset.protobuf.AuthServiceGrpc;
import no.ssb.helidon.application.DefaultHelidonApplicationBuilder;
import no.ssb.helidon.application.HelidonApplication;
import no.ssb.helidon.application.HelidonApplicationBuilder;

import static java.util.Optional.ofNullable;

public class ApplicationBuilder extends DefaultHelidonApplicationBuilder {

    ManagedChannel datasetAccessChannel;

    @Override
    public <T> HelidonApplicationBuilder override(Class<T> clazz, T instance) {
        super.override(clazz, instance);
        if (ManagedChannel.class.isAssignableFrom(clazz)) {
            datasetAccessChannel = (ManagedChannel) instance;
        }
        return this;
    }

    @Override
    public HelidonApplication build() {
        Config config = ofNullable(this.config).orElseGet(DefaultHelidonApplicationBuilder::createDefaultConfig);

        if (datasetAccessChannel == null) {
            datasetAccessChannel = ManagedChannelBuilder
                    .forAddress(
                            config.get("auth-service").get("host").asString().orElseThrow(() ->
                                    new RuntimeException("missing configuration: auth-service.host")),
                            config.get("auth-service").get("port").asInt().orElseThrow(() ->
                                    new RuntimeException("missing configuration: auth-service.port"))
                    )
                    .usePlaintext()
                    .build();
        }

        AuthServiceGrpc.AuthServiceFutureStub authService = AuthServiceGrpc.newFutureStub(datasetAccessChannel);

        return new Application(config, authService);
    }
}
