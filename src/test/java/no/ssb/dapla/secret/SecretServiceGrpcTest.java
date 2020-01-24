package no.ssb.dapla.secret;

import io.grpc.Channel;
import no.ssb.dapla.secret.service.protobuf.CreateKeyRequest;
import no.ssb.dapla.secret.service.protobuf.CreateKeyResponse;
import no.ssb.dapla.secret.service.protobuf.DeleteKeyRequest;
import no.ssb.dapla.secret.service.protobuf.DeleteKeyResponse;
import no.ssb.dapla.secret.service.protobuf.GetKeyRequest;
import no.ssb.dapla.secret.service.protobuf.GetKeyResponse;
import no.ssb.dapla.secret.service.protobuf.PseudoKey;
import no.ssb.dapla.secret.service.protobuf.SecretServiceGrpc;
import no.ssb.testing.helidon.IntegrationTestExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import javax.inject.Inject;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(IntegrationTestExtension.class)
class SecretServiceGrpcTest {

    @Inject
    Application application;

    @Inject
    Channel channel;

    @BeforeEach
    void clearSecretRepository() throws InterruptedException, ExecutionException, TimeoutException {
        application.get(SecretRepository.class).deleteAllKeys().get(6, TimeUnit.SECONDS);
    }

    PseudoKey repositoryGet(String secretId) {
        return application.get(SecretRepository.class).getKey(secretId).join();
    }

    void repositoryCreate(String secretId, PseudoKey key) {
        application.get(SecretRepository.class).createOrUpdateKey(secretId, key).join();
    }

    @Test
    void thatCreateWorks() {
        PseudoKey keyToCreate = PseudoKey.newBuilder().setKey("key-to-create").build();
        CreateKeyRequest createKeyRequest = CreateKeyRequest.newBuilder().setKeyId("id-to-create").setKey(keyToCreate).build();
        CreateKeyResponse response = SecretServiceGrpc.newBlockingStub(channel).createKey(createKeyRequest);
        assertThat(response.getKeyId()).isEqualTo("id-to-create");
        assertThat(repositoryGet("id-to-create")).isEqualTo(keyToCreate);
    }

    @Test
    void thatUpsertWorks() {
        repositoryCreate("id-to-update", PseudoKey.newBuilder().setKey("key-to-update").build());

        PseudoKey updatedKey = PseudoKey.newBuilder().setKey("key-updated").build();
        CreateKeyRequest updateKeyRequest = CreateKeyRequest.newBuilder().setKeyId("id-to-update").setKey(updatedKey).build();
        CreateKeyResponse response = SecretServiceGrpc.newBlockingStub(channel).createKey(updateKeyRequest);
        assertThat(response.getKeyId()).isEqualTo("id-to-update");
        assertThat(repositoryGet("id-to-update")).isEqualTo(updatedKey);
    }

    @Test
    void thatGetWorks() {
        PseudoKey expected = PseudoKey.newBuilder().setKey("key-existing").build();
        repositoryCreate("id-should-exist", expected);

        GetKeyResponse response = SecretServiceGrpc.newBlockingStub(channel).getKey(GetKeyRequest.newBuilder().setKeyId("id-should-exist").build());
        assertThat(response.getKey()).isEqualTo(expected);
        assertThat(response.getKeyId()).isEqualTo("id-should-exist");
    }

    @Test
    void thatGetWorksWhenRepositoryIsEmpty() {
        GetKeyResponse response = SecretServiceGrpc.newBlockingStub(channel).getKey(GetKeyRequest.newBuilder().setKeyId("id-non-existing").build());
        assertThat(response.getKeyId()).isEmpty();
        assertThat(response.hasKey()).isFalse();
    }

    @Test
    void thatDeleteWorks() {
        repositoryCreate("id-to-delete", PseudoKey.newBuilder().setKey("key-to-delete").build());
        DeleteKeyResponse response = SecretServiceGrpc.newBlockingStub(channel).deleteKey(DeleteKeyRequest.newBuilder().setKeyId("id-to-delete").build());
        assertThat(response.getRowsAffected()).isEqualTo(1);
        assertThat(repositoryGet("id-to-delete")).isNull();
    }

    @Test
    void thatDeleteWorksWhenRepositoryIsEmpty() {
        DeleteKeyResponse response = SecretServiceGrpc.newBlockingStub(channel).deleteKey(DeleteKeyRequest.newBuilder().setKeyId("id-non-existing").build());
        assertThat(response.getRowsAffected()).isEqualTo(0);
    }
}
