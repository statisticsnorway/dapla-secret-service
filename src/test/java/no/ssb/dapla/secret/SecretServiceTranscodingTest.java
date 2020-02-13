package no.ssb.dapla.secret;

import com.google.common.collect.Maps;
import io.grpc.stub.StreamObserver;
import no.ssb.dapla.auth.dataset.protobuf.AccessCheckRequest;
import no.ssb.dapla.auth.dataset.protobuf.AccessCheckResponse;
import no.ssb.dapla.auth.dataset.protobuf.AuthServiceGrpc;
import no.ssb.dapla.auth.dataset.protobuf.Role.DatasetState;
import no.ssb.dapla.catalog.protobuf.CatalogServiceGrpc;
import no.ssb.dapla.catalog.protobuf.Dataset;
import no.ssb.dapla.catalog.protobuf.Dataset.Valuation;
import no.ssb.dapla.catalog.protobuf.DatasetId;
import no.ssb.dapla.catalog.protobuf.GetByNameDatasetRequest;
import no.ssb.dapla.catalog.protobuf.GetByNameDatasetResponse;
import no.ssb.dapla.catalog.protobuf.MapNameToIdRequest;
import no.ssb.dapla.catalog.protobuf.MapNameToIdResponse;
import no.ssb.dapla.catalog.protobuf.PseudoConfig;
import no.ssb.dapla.catalog.protobuf.SecretPseudoConfigItem;
import no.ssb.dapla.secret.service.protobuf.CreateOrGetSecretsRequest;
import no.ssb.dapla.secret.service.protobuf.CreateOrGetSecretsResponse;
import no.ssb.dapla.secret.service.protobuf.Secret;
import no.ssb.dapla.secret.service.protobuf.SecretRef;
import no.ssb.testing.helidon.GrpcMockRegistry;
import no.ssb.testing.helidon.GrpcMockRegistryConfig;
import no.ssb.testing.helidon.IntegrationTestExtension;
import no.ssb.testing.helidon.TestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

@GrpcMockRegistryConfig(SecretServiceTranscodingTest.SecretServiceTestMockRegistry.class)
@ExtendWith(IntegrationTestExtension.class)
class SecretServiceTranscodingTest {

    @Inject
    Application application;

    @Inject
    TestClient testClient;

    private static final Set<String> ACCESS = Set.of("userId");

    private static final Map<String, Dataset> CATALOG = Maps.newHashMap();

    private static final Map<String, String> NAME_INDEX = Maps.newHashMap();

    static {
        CATALOG.put("/directory/a-dataset",
                Dataset.newBuilder()
                        .setId(
                                DatasetId.newBuilder()
                                        .setId("9441173b-79a5-4d22-a7a7-997458071ecd")
                                        .addAllName(Arrays.asList("directory", "a-dataset"))
                        )
                        .setValuation(Valuation.INTERNAL)
                        .setState(Dataset.DatasetState.INPUT)
                        .setPseudoConfig(
                                PseudoConfig.newBuilder()
                                        .addSecrets(SecretPseudoConfigItem.newBuilder().setId("secret-id"))
                        )
                        .build());

        NAME_INDEX.put("/directory/a-dataset", "9441173b-79a5-4d22-a7a7-997458071ecd");
    }

    @BeforeEach
    void clearSecretRepository() throws InterruptedException, ExecutionException, TimeoutException {
        application.get(SecretRepository.class).deleteAllSecrets().get(6, TimeUnit.SECONDS);
    }

    Set<Secret> repositoryGet(String... secretId) {
        return application.get(SecretRepository.class).getSecrets(secretId).join();
    }

    void repositoryCreate(Secret... secrets) {
        application.get(SecretRepository.class).createSecrets(secrets).join();
    }

    @Test
    void thatCreateOrGetWorksWhenSecretDoesntExist() {
        CreateOrGetSecretsRequest request = CreateOrGetSecretsRequest.newBuilder()
                .setDatasetPath("/directory/a-dataset")
                .setDatasetState(DatasetState.RAW.name())
                .setDatasetValuation(Valuation.SENSITIVE.name())
                .addSecretRefs(SecretRef.newBuilder().setId("secret_id").setType("AES256"))
                .build();

        CreateOrGetSecretsResponse response = testClient.post("/rpc/SecretService/createOrGetSecrets", request, CreateOrGetSecretsResponse.class).expect200Ok().body();

        Set<Secret> expectedSecrets = repositoryGet("secret_id");
        assertThat(expectedSecrets).hasSize(1);
        assertThat(response.getSecretsList()).containsExactly(expectedSecrets.toArray(Secret[]::new));
    }

    public static class SecretServiceTestMockRegistry extends GrpcMockRegistry {
        public SecretServiceTestMockRegistry() {
            add(new AuthServiceGrpc.AuthServiceImplBase() {
                @Override
                public void hasAccess(AccessCheckRequest request, StreamObserver<AccessCheckResponse> responseObserver) {
                    AccessCheckResponse.Builder responseBuilder = AccessCheckResponse.newBuilder();

                    if (ACCESS.contains(request.getUserId())) {
                        responseBuilder.setAllowed(true);
                    }

                    responseObserver.onNext(responseBuilder.build());
                    responseObserver.onCompleted();
                }
            });
            add(new CatalogServiceGrpc.CatalogServiceImplBase() {
                @Override
                public void getByName(GetByNameDatasetRequest request, StreamObserver<GetByNameDatasetResponse> responseObserver) {

                    String datasetPath = String.join("/", request.getNameList());
                    GetByNameDatasetResponse.Builder responseBuilder = GetByNameDatasetResponse.newBuilder();

                    if (CATALOG.containsKey(datasetPath)) {
                        responseBuilder.setDataset(CATALOG.get(datasetPath));
                    }

                    responseObserver.onNext(responseBuilder.build());
                    responseObserver.onCompleted();
                }

                @Override
                public void mapNameToId(MapNameToIdRequest request, StreamObserver<MapNameToIdResponse> responseObserver) {
                    responseObserver.onNext(
                            MapNameToIdResponse.newBuilder()
                                    .setId(NAME_INDEX.getOrDefault(String.join("/", request.getNameList()), "")).build()
                    );
                    responseObserver.onCompleted();
                }
            });
        }
    }
}
