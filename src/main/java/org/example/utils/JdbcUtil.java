package org.example.util;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;

public class JdbcUtil {

    private JdbcUtil() {
    }

    public static Connection getConnection(DataSource dataSource) throws SQLException {
        return new TransactionAwareDataSourceProxy(dataSource).getConnection();
    }

    public static void rollbackQuietly(Connection conn) {
        if (conn != null) {
            try {
                conn.rollback();
            } catch (Exception ignored) {
            }
        }
    }

    public static void closeQuietly(AutoCloseable target) {
        if (target != null) {
            try {
                target.close();
            } catch (Exception ignored) {
            }
        }
    }
}
