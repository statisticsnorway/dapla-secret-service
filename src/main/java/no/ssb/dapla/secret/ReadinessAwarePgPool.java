package no.ssb.dapla.secret;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.SqlConnection;
import io.vertx.sqlclient.SqlResult;
import io.vertx.sqlclient.Transaction;
import io.vertx.sqlclient.Tuple;
import no.ssb.dapla.readiness.Readiness;

import java.util.List;
import java.util.stream.Collector;

public class ReadinessAwarePgPool implements PgPool {

    private final PgPool delegate;
    private final Readiness readiness;

    public ReadinessAwarePgPool(PgPool delegate, Readiness readiness) {
        this.delegate = delegate;
        this.readiness = readiness;
    }

    Handler<AsyncResult<RowSet<Row>>> handleRowSet(Handler<AsyncResult<RowSet<Row>>> handler) {
        return ar -> {
            readiness.set(ar.succeeded());
            handler.handle(ar);
        };
    }

    <R> Handler<AsyncResult<SqlResult<R>>> handleSqlResult(Handler<AsyncResult<SqlResult<R>>> handler) {
        return ar -> {
            readiness.set(ar.succeeded());
            handler.handle(ar);
        };
    }

    @Override
    public PgPool preparedQuery(String sql, Handler<AsyncResult<RowSet<Row>>> handler) {
        return delegate.preparedQuery(sql, handleRowSet(handler));
    }

    @Override
    public <R> PgPool preparedQuery(String sql, Collector<Row, ?, R> collector, Handler<AsyncResult<SqlResult<R>>> handler) {
        return delegate.preparedQuery(sql, collector, handleSqlResult(handler));
    }

    @Override
    public PgPool query(String sql, Handler<AsyncResult<RowSet<Row>>> handler) {
        return delegate.query(sql, handleRowSet(handler));
    }

    @Override
    public <R> PgPool query(String sql, Collector<Row, ?, R> collector, Handler<AsyncResult<SqlResult<R>>> handler) {
        return delegate.query(sql, collector, handleSqlResult(handler));
    }

    @Override
    public PgPool preparedQuery(String sql, Tuple arguments, Handler<AsyncResult<RowSet<Row>>> handler) {
        return delegate.preparedQuery(sql, arguments, handleRowSet(handler));
    }

    @Override
    public <R> PgPool preparedQuery(String sql, Tuple arguments, Collector<Row, ?, R> collector, Handler<AsyncResult<SqlResult<R>>> handler) {
        return delegate.preparedQuery(sql, arguments, collector, handleSqlResult(handler));
    }

    @Override
    public PgPool preparedBatch(String sql, List<Tuple> batch, Handler<AsyncResult<RowSet<Row>>> handler) {
        return delegate.preparedBatch(sql, batch, handleRowSet(handler));
    }

    @Override
    public <R> PgPool preparedBatch(String sql, List<Tuple> batch, Collector<Row, ?, R> collector, Handler<AsyncResult<SqlResult<R>>> handler) {
        return delegate.preparedBatch(sql, batch, collector, handleSqlResult(handler));
    }

    @Override
    public void getConnection(Handler<AsyncResult<SqlConnection>> handler) {
        delegate.getConnection(handler);
    }

    @Override
    public void begin(Handler<AsyncResult<Transaction>> handler) {
        delegate.begin(handler);
    }

    @Override
    public void close() {
        delegate.close();
    }
}
