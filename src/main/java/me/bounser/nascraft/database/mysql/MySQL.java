package me.bounser.nascraft.database.mysql;

import com.zaxxer.hikari.HikariConfig;
import me.bounser.nascraft.database.BaseDatabase;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * MySQL / MariaDB backend — the shared store for cross-server (multi-instance) setups
 */
public class MySQL extends BaseDatabase {

    private final String host;
    private final int port;
    private final String database;
    private final String username;
    private final String password;

    public MySQL(String host, int port, String database, String username, String password) {
        this.host = host;
        this.port = port;
        this.database = database;
        this.username = username;
        this.password = password;
    }

    @Override
    protected void configureHikari(HikariConfig config) {
        String url = "jdbc:mysql://" + host + ":" + port + "/" + database
                + "?useUnicode=true&characterEncoding=utf8&useSSL=false"
                + "&allowPublicKeyRetrieval=true&serverTimezone=UTC&autoReconnect=true";
        config.setJdbcUrl(url);
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        config.setUsername(username);
        config.setPassword(password);

        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(30_000);
        config.setPoolName("Nascraft-MySQL");
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
    }

    @Override
    protected void onConnectionInit(Connection connection) throws SQLException {
        // No per-connection setup required for MySQL.
    }

    @Override
    protected void runMigrations(Connection connection) throws SQLException {
        createAllTables(connection);
        addColumnIfMissing(connection, "items", "version", "BIGINT NOT NULL DEFAULT 0");
    }

    @Override
    public void createTables() {
        withConnection((SqlConsumer) this::createAllTables);
    }

    private static final String OPTS = " ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";

    private void createAllTables(Connection connection) throws SQLException {
        safeExec(connection, "CREATE TABLE IF NOT EXISTS items (" +
                "identifier VARCHAR(190) PRIMARY KEY, " +
                "lastprice DOUBLE, " +
                "lowest DOUBLE, " +
                "highest DOUBLE, " +
                "stock DOUBLE DEFAULT 0, " +
                "taxes DOUBLE, " +
                "version BIGINT NOT NULL DEFAULT 0" + OPTS);

        for (String table : new String[]{"prices_day", "prices_month", "prices_history"}) {
            safeExec(connection, "CREATE TABLE IF NOT EXISTS " + table + " (" +
                    "identifier VARCHAR(190) NOT NULL, " +
                    "bucket_start VARCHAR(40) NOT NULL, " +
                    "open DOUBLE NOT NULL, " +
                    "high DOUBLE NOT NULL, " +
                    "low DOUBLE NOT NULL, " +
                    "close DOUBLE NOT NULL, " +
                    "volume DOUBLE NOT NULL DEFAULT 0, " +
                    "PRIMARY KEY (identifier, bucket_start)" + OPTS);
        }

        safeExec(connection, "CREATE TABLE IF NOT EXISTS portfolios (" +
                "uuid VARCHAR(36) NOT NULL, " +
                "identifier VARCHAR(190) NOT NULL, " +
                "amount INT NOT NULL DEFAULT 0, " +
                "PRIMARY KEY (uuid, identifier)" + OPTS);

        safeExec(connection, "CREATE TABLE IF NOT EXISTS portfolios_log (" +
                "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                "uuid VARCHAR(36) NOT NULL, " +
                "day INT NOT NULL, " +
                "identifier VARCHAR(190) NOT NULL, " +
                "amount INT NOT NULL, " +
                "contribution DOUBLE NOT NULL, " +
                "INDEX idx_portfolios_log_uuid (uuid)" + OPTS);

        safeExec(connection, "CREATE TABLE IF NOT EXISTS portfolios_worth (" +
                "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                "uuid VARCHAR(36) NOT NULL, " +
                "day INT NOT NULL, " +
                "worth DOUBLE NOT NULL, " +
                "UNIQUE KEY uk_portfolios_worth (uuid, day), " +
                "INDEX idx_portfolios_worth_uuid (uuid)" + OPTS);

        safeExec(connection, "CREATE TABLE IF NOT EXISTS capacities (" +
                "uuid VARCHAR(36) PRIMARY KEY, " +
                "capacity INT NOT NULL" + OPTS);

        safeExec(connection, "CREATE TABLE IF NOT EXISTS discord_links (" +
                "userid VARCHAR(32) PRIMARY KEY, " +
                "uuid VARCHAR(36) NOT NULL, " +
                "nickname VARCHAR(255) NOT NULL" + OPTS);

        safeExec(connection, "CREATE TABLE IF NOT EXISTS trade_log (" +
                "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                "uuid VARCHAR(36) NOT NULL, " +
                "day INT NOT NULL, " +
                "date VARCHAR(40) NOT NULL, " +
                "identifier VARCHAR(190) NOT NULL, " +
                "amount INT NOT NULL, " +
                "value DOUBLE NOT NULL, " +
                "buy INT NOT NULL, " +
                "discord INT NOT NULL, " +
                "INDEX idx_trade_log_uuid (uuid), " +
                "INDEX idx_trade_log_identifier (identifier)" + OPTS);

        safeExec(connection, "CREATE TABLE IF NOT EXISTS cpi (" +
                "day INT PRIMARY KEY, " +
                "date VARCHAR(40) NOT NULL, " +
                "value DOUBLE NOT NULL" + OPTS);

        safeExec(connection, "CREATE TABLE IF NOT EXISTS alerts (" +
                "day INT NOT NULL, " +
                "userid VARCHAR(64) NOT NULL, " +
                "identifier VARCHAR(190) NOT NULL, " +
                "price DOUBLE NOT NULL" + OPTS);

        safeExec(connection, "CREATE TABLE IF NOT EXISTS flows (" +
                "day INT PRIMARY KEY, " +
                "flow DOUBLE NOT NULL, " +
                "taxes DOUBLE NOT NULL, " +
                "operations INT NOT NULL" + OPTS);

        safeExec(connection, "CREATE TABLE IF NOT EXISTS limit_orders (" +
                "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                "expiration VARCHAR(40) NOT NULL, " +
                "uuid VARCHAR(36) NOT NULL, " +
                "identifier VARCHAR(190) NOT NULL, " +
                "type INT NOT NULL, " +
                "price DOUBLE NOT NULL, " +
                "to_complete INT NOT NULL, " +
                "completed INT NOT NULL, " +
                "cost DOUBLE NOT NULL" + OPTS);

        safeExec(connection, "CREATE TABLE IF NOT EXISTS loans (" +
                "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                "uuid VARCHAR(36) NOT NULL UNIQUE, " +
                "debt DOUBLE NOT NULL" + OPTS);

        safeExec(connection, "CREATE TABLE IF NOT EXISTS interests (" +
                "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                "uuid VARCHAR(36) NOT NULL UNIQUE, " +
                "paid DOUBLE NOT NULL" + OPTS);

        safeExec(connection, "CREATE TABLE IF NOT EXISTS user_names (" +
                "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                "uuid VARCHAR(36) NOT NULL UNIQUE, " +
                "name VARCHAR(255) NOT NULL" + OPTS);

        safeExec(connection, "CREATE TABLE IF NOT EXISTS balances (" +
                "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                "uuid VARCHAR(36) NOT NULL UNIQUE, " +
                "balance DOUBLE NOT NULL" + OPTS);

        safeExec(connection, "CREATE TABLE IF NOT EXISTS money_supply (" +
                "day INT PRIMARY KEY, " +
                "supply DOUBLE NOT NULL" + OPTS);

        safeExec(connection, "CREATE TABLE IF NOT EXISTS discord (" +
                "userid VARCHAR(32) PRIMARY KEY, " +
                "uuid VARCHAR(36) NOT NULL, " +
                "nickname VARCHAR(255) NOT NULL" + OPTS);
    }
}
