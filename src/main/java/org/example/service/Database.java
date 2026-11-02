package org.example.service;

import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import java.sql.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import javax.sql.DataSource;

/** Pooled JDBC; all live mutations share an atomic transaction and a cross-process lease. */
@Component
public class Database {
    private final DataSource pool;
    private final DataSource transactional;
    private final TransactionTemplate transaction;
    private final ReentrantLock liveLock = new ReentrantLock(true);
    public Database(DataSource pool) {
        this.pool = pool;
        this.transactional = new TransactionAwareDataSourceProxy(pool);
        this.transaction = new TransactionTemplate(new DataSourceTransactionManager(pool));
        transaction.setTimeout(30);
    }
    public Connection open() throws SQLException { return transactional.getConnection(); }
    public <T> T write(Supplier<T> action) {
        if (liveLock.isHeldByCurrentThread()) return action.get();
        boolean locked = false;
        try {
            locked = liveLock.tryLock(5, TimeUnit.SECONDS);
            if (!locked) throw new IllegalStateException("调度操作繁忙，请稍后重试");
            try (Connection lease = pool.getConnection()) {
                String lockName = "transport-live-" + lease.getCatalog();
                try (PreparedStatement statement = lease.prepareStatement("SELECT GET_LOCK(?, 5)")) {
                    statement.setString(1, lockName);
                    try (ResultSet result = statement.executeQuery()) {
                        if (!result.next() || result.getInt(1) != 1) throw new IllegalStateException("另一个调度进程正在操作，请稍后重试");
                    }
                }
                try { return transaction.execute(status -> action.get()); }
                finally {
                    try (PreparedStatement statement = lease.prepareStatement("SELECT RELEASE_LOCK(?)")) {
                        statement.setString(1, lockName); statement.execute();
                    }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); throw new IllegalStateException("调度已取消", e);
        } catch (SQLException e) { throw new IllegalStateException("数据库连接或操作失败", e); }
        finally { if (locked) liveLock.unlock(); }
    }
    public void write(Runnable action) { write(() -> { action.run(); return null; }); }
}
