package no.ssb.dapla.secret;

import io.grpc.stub.StreamObserver;
import no.ssb.dapla.auth.dataset.protobuf.AuthServiceGrpc.AuthServiceFutureStub;
import no.ssb.dapla.secret.service.protobuf.CreateKeyRequest;
import no.ssb.dapla.secret.service.protobuf.CreateKeyResponse;
import no.ssb.dapla.secret.service.protobuf.DeleteKeyRequest;
import no.ssb.dapla.secret.service.protobuf.DeleteKeyResponse;
import no.ssb.dapla.secret.service.protobuf.GetKeyRequest;
import no.ssb.dapla.secret.service.protobuf.GetKeyResponse;
import no.ssb.dapla.secret.service.protobuf.SecretServiceGrpc.SecretServiceImplBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

public class SecretServiceGrpc extends SecretServiceImplBase {

    private static final Logger LOG = LoggerFactory.getLogger(SecretServiceGrpc.class);

    final SecretRepository repository;
    final AuthServiceFutureStub authService;

    public SecretServiceGrpc(SecretRepository repository, AuthServiceFutureStub authService) {
        this.repository = repository;
        this.authService = authService;
    }

    @Override
    public void getKey(GetKeyRequest request, StreamObserver<GetKeyResponse> responseObserver) {
        String keyId = request.getKeyId();
        repository.getKey(keyId)
                .orTimeout(10, TimeUnit.SECONDS)
                .thenAccept(pseudoKey -> {
                    GetKeyResponse.Builder responseBuilder = GetKeyResponse.newBuilder();
                    if (pseudoKey != null) {
                        responseBuilder.setKey(pseudoKey);
                        responseBuilder.setKeyId(keyId);
                    }
                    responseObserver.onNext(responseBuilder.build());
                    responseObserver.onCompleted();
                })
                .exceptionally(throwable -> {
                    LOG.error(String.format("Failed to get key with id: %s", keyId), throwable);
                    responseObserver.onError(throwable);
                    return null;
                });
    }

    @Override
    public void createKey(CreateKeyRequest request, StreamObserver<CreateKeyResponse> responseObserver) {
        String keyId = request.getKeyId();
        repository.createKey(keyId, request.getKey())
                .orTimeout(10, TimeUnit.SECONDS)
                .thenRun(() -> {
                    responseObserver.onNext(CreateKeyResponse.newBuilder().setKeyId(keyId).build());
                    responseObserver.onCompleted();
                })
                .exceptionally(throwable -> {
                    LOG.error(String.format("Failed to create key with id: %s", keyId), throwable);
                    responseObserver.onError(throwable);
                    return null;
                });
    }

    @Override
    public void deleteKey(DeleteKeyRequest request, StreamObserver<DeleteKeyResponse> responseObserver) {
        repository.deleteKey(request.getKeyId())
                .orTimeout(10, TimeUnit.SECONDS)
                .thenAccept(rowsAffected -> {
                    responseObserver.onNext(DeleteKeyResponse.newBuilder().setRowsAffected(rowsAffected).build());
                    responseObserver.onCompleted();
                })
                .exceptionally(throwable -> {
                    LOG.error(String.format("Failed to delete key with id: %s", request.getKeyId()), throwable);
                    responseObserver.onError(throwable);
                    return null;
                });
    }
}
