package no.ssb.dapla.secret;

import io.grpc.stub.StreamObserver;
import no.ssb.dapla.auth.dataset.protobuf.AuthServiceGrpc.AuthServiceFutureStub;
import no.ssb.dapla.secret.service.protobuf.CreateSecretRequest;
import no.ssb.dapla.secret.service.protobuf.CreateSecretResponse;
import no.ssb.dapla.secret.service.protobuf.DeleteSecretRequest;
import no.ssb.dapla.secret.service.protobuf.DeleteSecretResponse;
import no.ssb.dapla.secret.service.protobuf.GetSecretRequest;
import no.ssb.dapla.secret.service.protobuf.GetSecretResponse;
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
    public void getSecret(GetSecretRequest request, StreamObserver<GetSecretResponse> responseObserver) {
        String secretId = request.getSecretId();
        repository.getSecret(secretId)
                .orTimeout(10, TimeUnit.SECONDS)
                .thenAccept(secret -> {
                    GetSecretResponse.Builder responseBuilder = GetSecretResponse.newBuilder();
                    if (secret != null) {
                        responseBuilder.setSecret(secret);
                    }
                    responseObserver.onNext(responseBuilder.build());
                    responseObserver.onCompleted();
                })
                .exceptionally(throwable -> {
                    LOG.error(String.format("Failed to get secret with id: %s", secretId), throwable);
                    responseObserver.onError(throwable);
                    return null;
                });
    }

    @Override
    public void createSecret(CreateSecretRequest request, StreamObserver<CreateSecretResponse> responseObserver) {
        String secretId = request.getSecret().getId();
        repository.createSecret(request.getSecret())
                .orTimeout(10, TimeUnit.SECONDS)
                .thenRun(() -> {
                    responseObserver.onNext(CreateSecretResponse.newBuilder().setSecretId(secretId).build());
                    responseObserver.onCompleted();
                })
                .exceptionally(throwable -> {
                    LOG.error(String.format("Failed to create secret with id: %s", secretId), throwable);
                    responseObserver.onError(throwable);
                    return null;
                });
    }

    @Override
    public void deleteSecret(DeleteSecretRequest request, StreamObserver<DeleteSecretResponse> responseObserver) {
        repository.deleteSecret(request.getSecretId())
                .orTimeout(10, TimeUnit.SECONDS)
                .thenAccept(rowsAffected -> {
                    responseObserver.onNext(DeleteSecretResponse.newBuilder().setRowsAffected(rowsAffected).build());
                    responseObserver.onCompleted();
                })
                .exceptionally(throwable -> {
                    LOG.error(String.format("Failed to delete secret with id: %s", request.getSecretId()), throwable);
                    responseObserver.onError(throwable);
                    return null;
                });
    }
}
