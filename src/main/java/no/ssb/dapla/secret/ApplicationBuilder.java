package no.ssb.dapla.secret;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.helidon.config.Config;
import no.ssb.dapla.auth.dataset.protobuf.AuthServiceGrpc;
import no.ssb.dapla.auth.dataset.protobuf.AuthServiceGrpc.AuthServiceFutureStub;
import no.ssb.dapla.catalog.protobuf.CatalogServiceGrpc;
import no.ssb.dapla.catalog.protobuf.CatalogServiceGrpc.CatalogServiceFutureStub;
import no.ssb.helidon.application.DefaultHelidonApplicationBuilder;
import no.ssb.helidon.application.HelidonApplication;
import no.ssb.helidon.application.HelidonApplicationBuilder;

import static java.util.Optional.ofNullable;

public class ApplicationBuilder extends DefaultHelidonApplicationBuilder {

    ManagedChannel datasetAccessChannel;
    ManagedChannel catalogServiceChannel;

    @Override
    public <T> HelidonApplicationBuilder override(Class<T> clazz, T instance) {
        super.override(clazz, instance);
        if (ManagedChannel.class.isAssignableFrom(clazz)) {
            datasetAccessChannel = (ManagedChannel) instance;
            catalogServiceChannel = (ManagedChannel) instance;
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

        if (catalogServiceChannel == null) {
            catalogServiceChannel = ManagedChannelBuilder
                    .forAddress(
                            config.get("catalog-service").get("host").asString().orElseThrow(() ->
                                    new RuntimeException("missing configuration: catalog-service.host")),
                            config.get("catalog-service").get("port").asInt().orElseThrow(() ->
                                    new RuntimeException("missing configuration: catalog-service.port")
                            )
                    )
                    .usePlaintext()
                    .build();
        }


        AuthServiceFutureStub authService = AuthServiceGrpc.newFutureStub(datasetAccessChannel);
        CatalogServiceFutureStub catalogService = CatalogServiceGrpc.newFutureStub(catalogServiceChannel);

        return new Application(config, authService, catalogService);
    }
}
