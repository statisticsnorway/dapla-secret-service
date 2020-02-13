package no.ssb.dapla.secret;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;
import io.grpc.Status;
import io.grpc.StatusException;
import io.grpc.stub.StreamObserver;
import no.ssb.dapla.auth.dataset.protobuf.AccessCheckRequest;
import no.ssb.dapla.auth.dataset.protobuf.AccessCheckResponse;
import no.ssb.dapla.auth.dataset.protobuf.AuthServiceGrpc.AuthServiceFutureStub;
import no.ssb.dapla.catalog.protobuf.CatalogServiceGrpc.CatalogServiceFutureStub;
import no.ssb.dapla.catalog.protobuf.Dataset;
import no.ssb.dapla.catalog.protobuf.GetByNameDatasetRequest;
import no.ssb.dapla.catalog.protobuf.GetByNameDatasetResponse;
import no.ssb.dapla.catalog.protobuf.SecretPseudoConfigItem;
import no.ssb.dapla.secret.service.protobuf.CreateOrGetSecretsRequest;
import no.ssb.dapla.secret.service.protobuf.CreateOrGetSecretsResponse;
import no.ssb.dapla.secret.service.protobuf.CreateSecretRequest;
import no.ssb.dapla.secret.service.protobuf.CreateSecretResponse;
import no.ssb.dapla.secret.service.protobuf.DeleteSecretRequest;
import no.ssb.dapla.secret.service.protobuf.DeleteSecretResponse;
import no.ssb.dapla.secret.service.protobuf.GetSecretsRequest;
import no.ssb.dapla.secret.service.protobuf.GetSecretsResponse;
import no.ssb.dapla.secret.service.protobuf.Secret;
import no.ssb.dapla.secret.service.protobuf.SecretRef;
import no.ssb.dapla.secret.service.protobuf.SecretServiceGrpc.SecretServiceImplBase;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SecretServiceGrpc extends SecretServiceImplBase {

    private static final Logger LOG = LoggerFactory.getLogger(SecretServiceGrpc.class);

    final SecretRepository repository;
    final AuthServiceFutureStub authService;
    final CatalogServiceFutureStub catalogService;

    public SecretServiceGrpc(SecretRepository repository, AuthServiceFutureStub authService, CatalogServiceFutureStub catalogService) {
        this.repository = repository;
        this.authService = authService;
        this.catalogService = catalogService;
    }

    @Override
    public void getSecrets(GetSecretsRequest request, StreamObserver<GetSecretsResponse> responseObserver) {
        String userId = "userId"; //TODO: Extract from request
        String datasetPath = request.getDatasetPath();
        getDatasetMetaByPath(datasetPath)
                .orTimeout(10, TimeUnit.SECONDS)
                .thenAccept(dataset -> {
                    hasAccess(userId, datasetPath, dataset.getState().name(), dataset.getValuation().name())
                            .thenAccept(hasAccess -> {
                                if (!hasAccess) {
                                    responseObserver.onError(new StatusException(Status.PERMISSION_DENIED));
                                    return;
                                }
                                getSecrets(dataset.getPseudoConfig().getSecretsList())
                                        .thenAccept(secrets -> {
                                            responseObserver.onNext(GetSecretsResponse.newBuilder().addAllSecrets(secrets).build());
                                            responseObserver.onCompleted();
                                        });
                            });
                })
                .exceptionally(throwable -> {
                    LOG.error("Failed during getSecrets", throwable);
                    responseObserver.onError(new StatusException(Status.fromThrowable(throwable)));
                    return null;
                });
    }

    private CompletableFuture<Set<Secret>> getSecrets(List<SecretPseudoConfigItem> pseudoConfigItems) {
        CompletableFuture<Set<Secret>> future = new CompletableFuture<>();
        repository.getSecrets(pseudoConfigItems.stream().map(SecretPseudoConfigItem::getId).toArray(String[]::new))
                .thenAccept(future::complete)
                .exceptionally(throwable -> {
                    future.completeExceptionally(throwable);
                    return null;
                });
        return future;
    }

    @Override
    public void createOrGetSecrets(CreateOrGetSecretsRequest request, StreamObserver<CreateOrGetSecretsResponse> responseObserver) {
        String userId = "userId"; //TODO: Extract from request
        String datasetPath = request.getDatasetPath();
        String datasetState = request.getDatasetState();
        String datasetValuation = request.getDatasetValuation();
        getDatasetMetaByPath(datasetPath)
                .orTimeout(10, TimeUnit.SECONDS)
                .thenAccept(dataset -> {
                    hasAccess(userId, datasetPath, datasetState, datasetValuation)
                            .thenAccept(hasAccess -> {
                                if (!hasAccess) {
                                    responseObserver.onError(new StatusException(Status.PERMISSION_DENIED));
                                    return;
                                }
                                createOrGetSecrets(request.getSecretRefsList())
                                        .thenAccept(secrets -> {
                                            responseObserver.onNext(CreateOrGetSecretsResponse.newBuilder().addAllSecrets(secrets).build());
                                            responseObserver.onCompleted();
                                        });
                            });
                })
                .exceptionally(throwable -> {
                    LOG.error("Failed during createOrGetSecrets", throwable);
                    responseObserver.onError(new StatusException(Status.fromThrowable(throwable)));
                    return null;
                });
    }

    private CompletableFuture<Dataset> getDatasetMetaByPath(String datasetPath) {

        GetByNameDatasetRequest request = GetByNameDatasetRequest.newBuilder().addAllName(List.of(datasetPath.split("/"))).build();
        CompletableFuture<Dataset> future = new CompletableFuture<>();

        Futures.addCallback(catalogService.getByName(request), new FutureCallback<>() {
            @Override
            public void onSuccess(@Nullable GetByNameDatasetResponse result) {
                if (result == null || !result.hasDataset()) {
                    future.completeExceptionally(new StatusException(Status.NOT_FOUND));
                    return;
                }
                future.complete(result.getDataset());
            }

            @Override
            public void onFailure(Throwable t) {
                future.completeExceptionally(new StatusException(Status.fromThrowable(t)));
            }
        }, MoreExecutors.directExecutor());

        return future;
    }

    private CompletableFuture<Boolean> hasAccess(String userId, String datasetPath, String datasetState, String datasetValuation) {

        AccessCheckRequest request = AccessCheckRequest.newBuilder()
                .setUserId(userId)
                .setNamespace(datasetPath)
                .setState(datasetState)
                .setValuation(datasetValuation)
                .setPrivilege("PSEUDONYMIZE")
                .build();
        CompletableFuture<Boolean> future = new CompletableFuture<>();

        Futures.addCallback(authService.hasAccess(request), new FutureCallback<>() {
            @Override
            public void onSuccess(@Nullable AccessCheckResponse result) {
                if (result == null || !result.getAllowed()) {
                    future.completeExceptionally(new StatusException(Status.PERMISSION_DENIED));
                    return;
                }
                future.complete(true);
            }

            @Override
            public void onFailure(Throwable t) {
                future.completeExceptionally(new StatusException(Status.fromThrowable(t)));
            }
        }, MoreExecutors.directExecutor());

        return future;
    }

    private CompletableFuture<Set<Secret>> createOrGetSecrets(List<SecretRef> secretRefs) {
        CompletableFuture<Set<Secret>> future = new CompletableFuture<>();
        repository.getSecrets(secretRefs.stream().map(SecretRef::getId).toArray(String[]::new))
                .thenAccept(existingSecrets -> {

                    HashSet<SecretRef> existingSecretRefs = existingSecrets
                            .stream()
                            .map(secret -> SecretRef.newBuilder().setId(secret.getId()).setType(secret.getType()).build())
                            .collect(Collectors.toCollection(HashSet::new));

                    List<Secret> secretsToCreate = secretRefs
                            .stream()
                            .filter(secretRef -> !existingSecretRefs.contains(secretRef))
                            .map(secretRef -> SecretGenerator.generate(secretRef.getId(), secretRef.getType()))
                            .collect(Collectors.toList());

                    repository.createSecrets(secretsToCreate.toArray(Secret[]::new))
                            .thenRun(() -> {
                                future.complete(Stream.concat(existingSecrets.stream(), secretsToCreate.stream()).collect(Collectors.toSet()));
                            })
                            .exceptionally(throwable -> {
                                future.completeExceptionally(throwable);
                                return null;
                            });
                })
                .exceptionally(throwable -> {
                    future.completeExceptionally(throwable);
                    return null;
                });
        return future;
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
