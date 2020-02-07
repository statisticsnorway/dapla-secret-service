package no.ssb.dapla.secret;

import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowIterator;
import io.vertx.sqlclient.Tuple;
import no.ssb.dapla.secret.service.protobuf.PseudoKey;
import no.ssb.helidon.media.protobuf.ProtobufJsonUtils;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class SecretRepository {

    final PgPool pgClient;

    public SecretRepository(PgPool pgClient) {
        this.pgClient = pgClient;
    }

    public CompletableFuture<Void> createKey(String id, PseudoKey key) {
        JsonObject value = (JsonObject) Json.decodeValue(ProtobufJsonUtils.toString(key));
        CompletableFuture<Void> future = new CompletableFuture<>();
        pgClient.preparedQuery(
                "INSERT INTO secret (id, document) VALUES($1, $2) ON CONFLICT (id) DO NOTHING",
                Tuple.tuple().addString(id).addValue(value),
                asyncResult -> {
                    if (asyncResult.failed()) {
                        future.completeExceptionally(asyncResult.cause());
                        return;
                    }
                    future.complete(null);
                });
        return future.orTimeout(5, TimeUnit.SECONDS);
    }

    public CompletableFuture<PseudoKey> getKey(String id) {
        CompletableFuture<PseudoKey> future = new CompletableFuture<>();
        pgClient.preparedQuery(
                "SELECT id, document FROM secret WHERE id = $1",
                Tuple.tuple().addString(id),
                asyncResult -> {
                    if (asyncResult.failed()) {
                        future.completeExceptionally(asyncResult.cause());
                        return;
                    }
                    RowIterator<Row> iterator = asyncResult.result().iterator();
                    if (!iterator.hasNext()) {
                        future.complete(null);
                        return;
                    }
                    JsonObject document = iterator.next().get(JsonObject.class, 1);
                    PseudoKey pseudoKey = ProtobufJsonUtils.toPojo(Json.encode(document), PseudoKey.class);
                    future.complete(pseudoKey);
                }
        );
        return future.orTimeout(5, TimeUnit.SECONDS);
    }

    public CompletableFuture<Integer> deleteKey(String id) {
        CompletableFuture<Integer> future = new CompletableFuture<>();
        pgClient.preparedQuery("DELETE FROM secret WHERE id = $1",
                Tuple.tuple().addString(id),
                asyncResult -> {
                    if (asyncResult.failed()) {
                        future.completeExceptionally(asyncResult.cause());
                        return;
                    }
                    future.complete(asyncResult.result().rowCount());
                }
        );
        return future.orTimeout(5, TimeUnit.SECONDS);
    }

    CompletableFuture<Void> deleteAllKeys() {
        CompletableFuture<Void> future = new CompletableFuture<>();
        pgClient.query("TRUNCATE TABLE secret",
                asyncResult -> {
                    if (asyncResult.failed()) {
                        future.completeExceptionally(asyncResult.cause());
                        return;
                    }
                    future.complete(null);
                }
        );
        return future.orTimeout(5, TimeUnit.SECONDS);
    }
}
