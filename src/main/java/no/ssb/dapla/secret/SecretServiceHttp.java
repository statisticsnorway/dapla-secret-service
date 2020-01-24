package no.ssb.dapla.secret;

import io.helidon.common.http.Http;
import io.helidon.webserver.Handler;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;
import io.helidon.webserver.Service;
import no.ssb.dapla.secret.service.protobuf.PseudoKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

public class SecretServiceHttp implements Service {

    private static final Logger LOG = LoggerFactory.getLogger(SecretServiceHttp.class);

    final SecretRepository repository;

    public SecretServiceHttp(SecretRepository repository) {
        this.repository = repository;
    }

    @Override
    public void update(Routing.Rules rules) {
        rules.get("/{secretId}", this::httpGet);
        rules.put("/{secretId}", Handler.create(PseudoKey.class, this::httpPut));
        rules.delete("/{secretId}", this::httpDelete);
    }

    void httpGet(ServerRequest request, ServerResponse response) {
        String secretId = request.path().param("secretId");
        repository.getKey(secretId)
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

    void httpPut(ServerRequest request, ServerResponse response, PseudoKey pseudoKey) {
        String secretId = request.path().param("secretId");
        repository.createOrUpdateKey(secretId, pseudoKey)
                .orTimeout(10, TimeUnit.SECONDS)
                .thenRun(() -> {
                    response.headers().add("Location", String.format("/secret/%s", secretId));
                    response.status(Http.Status.CREATED_201).send();
                })
                .exceptionally(throwable -> {
                    LOG.error("Failed to create secret", throwable);
                    response.status(Http.Status.INTERNAL_SERVER_ERROR_500).send(throwable.getMessage());
                    return null;
                });
    }

    void httpDelete(ServerRequest request, ServerResponse response) {
        String secretId = request.path().param("secretId");
        repository.deleteKey(secretId)
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
}
