package me.bounser.nascraft.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import me.bounser.nascraft.Nascraft;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.logging.Level;

public abstract class BaseDatabase implements Database {

    protected HikariDataSource dataSource;

    protected abstract void configureHikari(HikariConfig config);

    protected abstract void runMigrations(Connection connection) throws SQLException;

    protected abstract void onConnectionInit(Connection connection) throws SQLException;

    @Override
    public void connect() {
        HikariConfig config = new HikariConfig();
        configureHikari(config);
        dataSource = new HikariDataSource(config);

        try (Connection connection = dataSource.getConnection()) {
            onConnectionInit(connection);
            runMigrations(connection);
        } catch (SQLException e) {
            Nascraft.getInstance().getLogger().warning("Database init failed: " + e.getMessage());
            throw new RuntimeException(e);
        }

        createTables();
    }

    @Override
    public void disconnect() {
        try {
            saveEverything();
        } finally {
            close();
        }
    }

    @Override
    public boolean isConnected() {
        return dataSource != null && !dataSource.isClosed();
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    @FunctionalInterface
    public interface SqlConsumer {
        void accept(Connection connection) throws SQLException;
    }

    @FunctionalInterface
    public interface SqlFunction<T> {
        T apply(Connection connection) throws SQLException;
    }

    public void withConnection(SqlConsumer action) {
        try (Connection connection = dataSource.getConnection()) {
            action.accept(connection);
        } catch (SQLException e) {
            throw fail("withConnection failed", e);
        }
    }

    public <T> T queryConnection(SqlFunction<T> action) {
        try (Connection connection = dataSource.getConnection()) {
            return action.apply(connection);
        } catch (SQLException e) {
            throw fail("queryConnection failed", e);
        }
    }

    public <T> T queryTransaction(SqlFunction<T> action) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                T result = action.apply(connection);
                connection.commit();
                return result;
            } catch (SQLException e) {
                try { connection.rollback(); } catch (SQLException rb) {
                    Nascraft.getInstance().getLogger().log(Level.SEVERE, "Rollback failed", rb);
                }
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw fail("queryTransaction failed", e);
        }
    }

    public void withTransaction(SqlConsumer action) {
        queryTransaction(conn -> { action.accept(conn); return null; });
    }

    private static DatabaseException fail(String context, SQLException e) {
        Nascraft.getInstance().getLogger().log(Level.SEVERE, context + ": " + e.getMessage(), e);
        return new DatabaseException(context, e);
    }

    public static String toIso(Instant instant) {
        return instant == null ? null : instant.toString();
    }

    public static Instant fromIso(String iso) {
        if (iso == null) return null;
        try {
            return Instant.parse(iso);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
