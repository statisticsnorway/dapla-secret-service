package no.ssb.dapla.secret;

import io.grpc.stub.StreamObserver;
import no.ssb.dapla.auth.dataset.protobuf.AccessCheckRequest;
import no.ssb.dapla.auth.dataset.protobuf.AccessCheckResponse;
import no.ssb.dapla.auth.dataset.protobuf.AuthServiceGrpc;
import no.ssb.dapla.secret.service.protobuf.Secret;
import no.ssb.helidon.media.protobuf.ProtobufJsonUtils;
import no.ssb.testing.helidon.GrpcMockRegistry;
import no.ssb.testing.helidon.GrpcMockRegistryConfig;
import no.ssb.testing.helidon.IntegrationTestExtension;
import no.ssb.testing.helidon.TestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import javax.inject.Inject;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

@GrpcMockRegistryConfig(SecretServiceHttpTest.SecretServiceTestMockRegistry.class)
@ExtendWith(IntegrationTestExtension.class)
class SecretServiceHttpTest {

    @Inject
    Application application;

    @Inject
    TestClient testClient;

    private static final Set<String> ACCESS = Set.of("a-user");

    @BeforeEach
    void clearSecretRepository() throws InterruptedException, ExecutionException, TimeoutException {
        application.get(SecretRepository.class).deleteAllSecrets().get(6, TimeUnit.SECONDS);
    }

    Secret repositoryGet(String secretId) {
        return application.get(SecretRepository.class).getSecret(secretId).join();
    }

    void repositoryCreate(Secret secret) {
        application.get(SecretRepository.class).createSecret(secret).join();
    }

    @Test
    void thatCreateOrGetWorksWhenSecretAlreadyExists() {
        Secret existingSecret = Secret.newBuilder()
                .setId("id-exists")
                .setContent("content-that-exists")
                .setType("type-exists")
                .build();
        repositoryCreate(existingSecret);

        String body = testClient
                .post(
                        "/secret/id-exists?namespace=/some/namespace&state=RAW&valuation=SENSITIVE&privilege=PSEUDONYMIZE",
                        Secret.newBuilder().setId("id-exists").build()
                )
                .expect200Ok()
                .body();

        assertThat(ProtobufJsonUtils.toPojo(body, Secret.class)).isEqualTo(existingSecret);
    }

    @Test
    void thatCreateOrGetWorksWhenSecretDoesntExist() {
        String body = testClient
                .post(
                        "/secret/id-non-existing?namespace=/some/namespace&state=RAW&valuation=SENSITIVE&privilege=PSEUDONYMIZE",
                        Secret.newBuilder().setId("id-non-existing").setType("AES32").build()
                )
                .expect201Created()
                .body();

        Secret actual = repositoryGet("id-non-existing");
        assertThat(actual.getId()).isEqualTo("id-non-existing");
        assertThat(actual.getType()).isEqualTo("AES32");
        assertThat(actual.getContent()).isEqualTo(ProtobufJsonUtils.toPojo(body, Secret.class).getContent());
    }

//    @Test
//    void thatGetWorks() {
//        PseudoKey expected = PseudoKey.newBuilder().setKey("secret-stuff").build();
//        repositoryCreate("a-secret-id", expected);
//        PseudoKey actual = testClient.get("/secret/a-secret-id", PseudoKey.class).expect200Ok().body();
//        assertThat(actual).isEqualTo(expected);
//    }

    @Test
    void thatGetReturns404WhenNoSecretIsFound() {
        testClient.get("/secret/id-non-existing").expect404NotFound();
    }

    @Test
    void thatDeleteWorks() {
        repositoryCreate(Secret.newBuilder().setId("key-to-delete").build());
        testClient.delete("/secret/id-to-delete").expect200Ok();
        assertThat(repositoryGet("id-to-delete")).isNull();
    }

    @Test
    void thatDeleteReturns404WhenNoSecretIsFound() {
        testClient.delete("/secret/id-non-existing").expect404NotFound();
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
        }
    }

}
