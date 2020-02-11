package no.ssb.dapla.secret;

import io.grpc.Channel;
import no.ssb.dapla.secret.service.protobuf.Secret;
import no.ssb.testing.helidon.IntegrationTestExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;

import javax.inject.Inject;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@ExtendWith(IntegrationTestExtension.class)
class SecretServiceGrpcTest {

    @Inject
    Application application;

    @Inject
    Channel channel;

    @BeforeEach
    void clearSecretRepository() throws InterruptedException, ExecutionException, TimeoutException {
        application.get(SecretRepository.class).deleteAllSecrets().get(6, TimeUnit.SECONDS);
    }

    Secret repositoryGet(String secretId) {
        return application.get(SecretRepository.class).getSecret(secretId).join();
    }

    void repositoryCreate(String secretId, Secret secret) {
        application.get(SecretRepository.class).createSecret(secret).join();
    }

//    @Test
//    void thatCreateWorks() {
//        Secret keyToCreate = Secret.newBuilder().set("key-to-create").build();
//        CreateKeyRequest createKeyRequest = CreateKeyRequest.newBuilder().setKeyId("id-to-create").setKey(keyToCreate).build();
//        CreateKeyResponse response = SecretServiceGrpc.newBlockingStub(channel).createKey(createKeyRequest);
//        assertThat(response.getKeyId()).isEqualTo("id-to-create");
//        assertThat(repositoryGet("id-to-create")).isEqualTo(keyToCreate);
//    }
//
//    @Test
//    void thatCreateDoesntUpsert() {
//        PseudoKey initialKey = PseudoKey.newBuilder().setKey("key-to-update").build();
//        repositoryCreate("id-initial-key", initialKey);
//
//        CreateKeyRequest updateKeyRequest = CreateKeyRequest.newBuilder().setKeyId("id-initial-key").setKey(PseudoKey.newBuilder().setKey("key-new").build()).build();
//        CreateKeyResponse response = SecretServiceGrpc.newBlockingStub(channel).createKey(updateKeyRequest);
//        assertThat(response.getKeyId()).isEqualTo("id-initial-key");
//        assertThat(repositoryGet("id-initial-key")).isEqualTo(initialKey);
//    }
//
//    @Test
//    void thatGetWorks() {
//        PseudoKey expected = PseudoKey.newBuilder().setKey("key-existing").build();
//        repositoryCreate("id-should-exist", expected);
//
//        GetKeyResponse response = SecretServiceGrpc.newBlockingStub(channel).getKey(GetKeyRequest.newBuilder().setKeyId("id-should-exist").build());
//        assertThat(response.getKey()).isEqualTo(expected);
//        assertThat(response.getKeyId()).isEqualTo("id-should-exist");
//    }
//
//    @Test
//    void thatGetWorksWhenRepositoryIsEmpty() {
//        GetKeyResponse response = SecretServiceGrpc.newBlockingStub(channel).getKey(GetKeyRequest.newBuilder().setKeyId("id-non-existing").build());
//        assertThat(response.getKeyId()).isEmpty();
//        assertThat(response.hasKey()).isFalse();
//    }
//
//    @Test
//    void thatDeleteWorks() {
//        repositoryCreate("id-to-delete", PseudoKey.newBuilder().setKey("key-to-delete").build());
//        DeleteKeyResponse response = SecretServiceGrpc.newBlockingStub(channel).deleteKey(DeleteKeyRequest.newBuilder().setKeyId("id-to-delete").build());
//        assertThat(response.getRowsAffected()).isEqualTo(1);
//        assertThat(repositoryGet("id-to-delete")).isNull();
//    }
//
//    @Test
//    void thatDeleteWorksWhenRepositoryIsEmpty() {
//        DeleteKeyResponse response = SecretServiceGrpc.newBlockingStub(channel).deleteKey(DeleteKeyRequest.newBuilder().setKeyId("id-non-existing").build());
//        assertThat(response.getRowsAffected()).isEqualTo(0);
//    }
}
