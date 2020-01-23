package no.ssb.dapla.secret;

import io.vertx.pgclient.PgPool;

public class SecretRepository {

    final PgPool pgPool;

    public SecretRepository(PgPool pgPool) {
        this.pgPool = pgPool;
    }
}
