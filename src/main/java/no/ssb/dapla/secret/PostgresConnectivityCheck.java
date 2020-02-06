package no.ssb.dapla.secret;

import io.vertx.pgclient.PgPool;
import no.ssb.dapla.readiness.ReadinessCheck;

import java.util.concurrent.CompletableFuture;

public class PostgresConnectivityCheck implements ReadinessCheck {

    private final PgPool pgPool;
    private final String host;
    private final int port;

    public PostgresConnectivityCheck(PgPool pgPool, String host, int port) {
        this.pgPool = pgPool;
        this.host = host;
        this.port = port;
    }

    @Override
    public CompletableFuture<Boolean> check() {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        pgPool.query("SELECT 1", ar -> {
            if (ar.succeeded()) {
                future.complete(true);
            } else {
                future.completeExceptionally(new RuntimeException(String.format("Unable to connect to %s:%d", host, port)));
            }
        });
        return future;
    }
}
