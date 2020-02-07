package no.ssb.dapla.secret;

import io.grpc.stub.StreamObserver;
import no.ssb.dapla.auth.dataset.protobuf.AccessCheckRequest;
import no.ssb.dapla.auth.dataset.protobuf.AccessCheckResponse;
import no.ssb.dapla.auth.dataset.protobuf.AuthServiceGrpc;
import no.ssb.dapla.secret.service.protobuf.PseudoKey;
import no.ssb.testing.helidon.GrpcMockRegistry;
import no.ssb.testing.helidon.GrpcMockRegistryConfig;
import no.ssb.testing.helidon.IntegrationTestExtension;
import no.ssb.testing.helidon.ResponseHelper;
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
        application.get(SecretRepository.class).deleteAllKeys().get(6, TimeUnit.SECONDS);
    }

    PseudoKey repositoryGet(String secretId) {
        return application.get(SecretRepository.class).getKey(secretId).join();
    }

    void repositoryCreate(String secretId, PseudoKey key) {
        application.get(SecretRepository.class).createKey(secretId, key).join();
    }

    @Test
    void thatPostWorks() {
        PseudoKey keyToCreate = PseudoKey.newBuilder().setKey("a-very-secret-key").build();
        ResponseHelper<String> responseHelper = testClient.post("/secret/123-456-789", keyToCreate).expect201Created();

        assertThat(responseHelper.response().headers().firstValue("Location")).contains("/secret/123-456-789");
        assertThat(repositoryGet("123-456-789")).isEqualTo(keyToCreate);
    }

    @Test
    void thatPostDoesntUpsert() {
        PseudoKey initialKey = PseudoKey.newBuilder().setKey("key-inital").build();
        repositoryCreate("id-initial", initialKey);

        PseudoKey newKey = PseudoKey.newBuilder().setKey("key-updated").build();
        ResponseHelper<String> responseHelper = testClient.post("/secret/id-initial", newKey).expect201Created();

        assertThat(responseHelper.response().headers().firstValue("Location")).contains("/secret/id-initial");
        assertThat(repositoryGet("id-initial")).isEqualTo(initialKey);
    }

    @Test
    void thatGetWorks() {
        PseudoKey expected = PseudoKey.newBuilder().setKey("secret-stuff").build();
        repositoryCreate("a-secret-id", expected);
        PseudoKey actual = testClient.get("/secret/a-secret-id", PseudoKey.class).expect200Ok().body();
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void thatGetReturns404WhenNoSecretIsFound() {
        testClient.get("/secret/id-non-existing").expect404NotFound();
    }

    @Test
    void thatDeleteWorks() {
        repositoryCreate("id-to-delete", PseudoKey.newBuilder().setKey("key-to-delete").build());
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
