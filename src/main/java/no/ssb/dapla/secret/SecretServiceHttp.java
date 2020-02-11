package no.ssb.dapla.secret;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import io.helidon.common.http.Http;
import io.helidon.webserver.Handler;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;
import io.helidon.webserver.Service;
import no.ssb.dapla.auth.dataset.protobuf.AccessCheckRequest;
import no.ssb.dapla.auth.dataset.protobuf.AccessCheckResponse;
import no.ssb.dapla.auth.dataset.protobuf.AuthServiceGrpc.AuthServiceFutureStub;
import no.ssb.dapla.secret.service.protobuf.DatasetState;
import no.ssb.dapla.secret.service.protobuf.Privilege;
import no.ssb.dapla.secret.service.protobuf.Secret;
import no.ssb.dapla.secret.service.protobuf.Valuation;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

public class SecretServiceHttp implements Service {

    private static final Logger LOG = LoggerFactory.getLogger(SecretServiceHttp.class);

    final SecretRepository repository;
    final AuthServiceFutureStub authService;

    public SecretServiceHttp(SecretRepository repository, AuthServiceFutureStub authService) {
        this.repository = repository;
        this.authService = authService;
    }

    @Override
    public void update(Routing.Rules rules) {
        rules.get("/{secretId}", this::httpGet);
        rules.post("/{secretId}", Handler.create(Secret.class, this::httpPost));
        rules.delete("/{secretId}", this::httpDelete);
    }

    void httpGet(ServerRequest request, ServerResponse response) {
        String secretId = request.path().param("secretId");
        repository.getSecret(secretId)
                .orTimeout(10, TimeUnit.SECONDS)
                .thenAccept(pseudoKey -> {
                    if (pseudoKey == null) {
                        response.status(Http.Status.NOT_FOUND_404).send();
                        return;
                    }
                    response.status(Http.Status.OK_200).send(pseudoKey);
                })
                .exceptionally(throwable -> {
                    LOG.error(String.format("Failed to get secret with id: %s", secretId), throwable);
                    response.status(Http.Status.INTERNAL_SERVER_ERROR_500).send(throwable.getMessage());
                    return null;
                });
    }

    void httpDelete(ServerRequest request, ServerResponse response) {
        String secretId = request.path().param("secretId");
        repository.deleteSecret(secretId)
                .orTimeout(10, TimeUnit.SECONDS)
                .thenAccept((rowsAffected) -> {
                    if (rowsAffected < 1) {
                        response.status(Http.Status.NOT_FOUND_404).send();
                        return;
                    }
                    response.status(Http.Status.OK_200).send();
                })
                .exceptionally(throwable -> {
                    LOG.error(String.format("Failed to delete secret with id: %s", secretId), throwable);
                    response.status(Http.Status.INTERNAL_SERVER_ERROR_500).send(throwable.getMessage());
                    return null;
                });
    }

    void httpPost(ServerRequest request, ServerResponse response, Secret secret) {

        // Extract required query parameters
        Optional<String> maybeNamespace = request.queryParams().first("namespace");
        if (maybeNamespace.isEmpty()) {
            response.status(Http.Status.BAD_REQUEST_400).send("Missing required query parameter 'namespace'");
            return;
        }
        String namespace = maybeNamespace.get();

        Optional<String> maybeState = request.queryParams().first("state");
        if (maybeState.isEmpty()) {
            response.status(Http.Status.BAD_REQUEST_400).send("Missing required query parameter 'state'");
            return;
        }
        DatasetState state = DatasetState.valueOf(maybeState.get());

        Optional<String> maybeValuation = request.queryParams().first("valuation");
        if (maybeValuation.isEmpty()) {
            response.status(Http.Status.BAD_REQUEST_400).send("Missing required query parameter 'valuation'");
            return;
        }
        Valuation valuation = Valuation.valueOf(maybeValuation.get());

        Optional<String> maybePrivilege = request.queryParams().first("privilege");
        if (maybePrivilege.isEmpty()) {
            response.status(Http.Status.BAD_REQUEST_400).send("Missing required query parameter 'privilege'");
            return;
        }
        Privilege privilege = Privilege.valueOf(maybePrivilege.get());

        String secretId = request.path().param("secretId");

        AccessCheckRequest accessCheckRequest = AccessCheckRequest.newBuilder()
                .setUserId("") //TODO: Extract user id from token
                .setNamespace(namespace)
                .setState(state.name())
                .setValuation(valuation.name())
                .setPrivilege(privilege.name())
                .build();

        ListenableFuture<AccessCheckResponse> hasAccessFuture = authService.hasAccess(accessCheckRequest); //TODO: Acquire client token and send as part of request

        Futures.addCallback(hasAccessFuture, new FutureCallback<>() {
            @Override
            public void onSuccess(@Nullable AccessCheckResponse result) {
                if (result != null && result.getAllowed()) {
                    repository.getSecret(secretId)
                            .orTimeout(10, TimeUnit.SECONDS)
                            .thenAccept(s -> {
                                if (s != null) {
                                    response.status(Http.Status.OK_200).send(s);
                                    return;
                                }
                                Secret generated = SecretGenerator.generate(secretId, secret.getType());
                                repository.createSecret(generated)
                                        .orTimeout(10, TimeUnit.SECONDS)
                                        .thenRun(() -> {
                                            response.headers().add("Location", String.format("/secret/%s", secretId));
                                            response.status(Http.Status.CREATED_201).send(generated);
                                        })
                                        .exceptionally(throwable -> {
                                            LOG.error("Failed to create secret", throwable);
                                            response.status(Http.Status.INTERNAL_SERVER_ERROR_500).send(throwable.getMessage());
                                            return null;
                                        });
                            })
                            .exceptionally(throwable -> {
                                LOG.error("Failed to get secret", throwable);
                                response.status(Http.Status.INTERNAL_SERVER_ERROR_500).send(throwable.getMessage());
                                return null;
                            });
                }
                response.status(Http.Status.FORBIDDEN_403).send();
            }
            @Override
            public void onFailure(Throwable t) {
                LOG.error("Failed to do access check", t);
                response.status(Http.Status.INTERNAL_SERVER_ERROR_500).send(t.getMessage());
            }
        }, MoreExecutors.directExecutor());
    }
}
