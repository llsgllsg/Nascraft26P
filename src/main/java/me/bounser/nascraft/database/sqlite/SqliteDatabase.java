package me.bounser.nascraft.database.sqlite;

import com.zaxxer.hikari.HikariConfig;
import me.bounser.nascraft.database.BaseDatabase;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class SqliteDatabase extends BaseDatabase {

    private final File dataFolder;
    private final File dbFile;

    public SqliteDatabase(File dataFolder) {
        this.dataFolder = dataFolder;
        File dataDir = new File(dataFolder, "data");
        if (!dataDir.exists()) dataDir.mkdirs();
        this.dbFile = new File(dataDir, "sqlite.db");
    }

    @Override
    protected void configureHikari(HikariConfig config) {
        String url = "jdbc:sqlite:" + dbFile.getAbsolutePath()
                + "?foreign_keys=on&journal_mode=WAL&synchronous=NORMAL";
        config.setJdbcUrl(url);
        config.setDriverClassName("org.sqlite.JDBC");

        config.setMaximumPoolSize(1);
        config.setConnectionTimeout(30_000);
        config.setPoolName("Nascraft-SQLite");
    }

    @Override
    protected void onConnectionInit(Connection connection) throws SQLException {
        try (Statement s = connection.createStatement()) {
            s.execute("PRAGMA foreign_keys=ON");
            s.execute("PRAGMA journal_mode=WAL");
            s.execute("PRAGMA synchronous=NORMAL");
        }
    }

    @Override
    protected void runMigrations(Connection connection) throws SQLException {
        createAllTables(connection);
        addMissingIndexes(connection);
    }

    @Override
    public void createTables() {
        withConnection((SqlConsumer) this::createAllTables);
    }

    private void createAllTables(Connection connection) throws SQLException {
        safeExec(connection, "CREATE TABLE IF NOT EXISTS items (" +
                "identifier TEXT PRIMARY KEY, " +
                "lastprice DOUBLE, " +
                "lowest DOUBLE, " +
                "highest DOUBLE, " +
                "stock DOUBLE DEFAULT 0, " +
                "taxes DOUBLE)");

        safeExec(connection, "CREATE TABLE IF NOT EXISTS prices_day (" +
                "identifier TEXT NOT NULL, " +
                "bucket_start TEXT NOT NULL, " +
                "open REAL NOT NULL, " +
                "high REAL NOT NULL, " +
                "low REAL NOT NULL, " +
                "close REAL NOT NULL, " +
                "volume REAL NOT NULL DEFAULT 0, " +
                "PRIMARY KEY (identifier, bucket_start))");

        safeExec(connection, "CREATE TABLE IF NOT EXISTS prices_month (" +
                "identifier TEXT NOT NULL, " +
                "bucket_start TEXT NOT NULL, " +
                "open REAL NOT NULL, " +
                "high REAL NOT NULL, " +
                "low REAL NOT NULL, " +
                "close REAL NOT NULL, " +
                "volume REAL NOT NULL DEFAULT 0, " +
                "PRIMARY KEY (identifier, bucket_start))");

        safeExec(connection, "CREATE TABLE IF NOT EXISTS prices_history (" +
                "identifier TEXT NOT NULL, " +
                "bucket_start TEXT NOT NULL, " +
                "open REAL NOT NULL, " +
                "high REAL NOT NULL, " +
                "low REAL NOT NULL, " +
                "close REAL NOT NULL, " +
                "volume REAL NOT NULL DEFAULT 0, " +
                "PRIMARY KEY (identifier, bucket_start))");

        safeExec(connection, "CREATE TABLE IF NOT EXISTS portfolios (" +
                "uuid VARCHAR(36) NOT NULL, " +
                "identifier TEXT NOT NULL, " +
                "amount INT NOT NULL DEFAULT 0, " +
                "PRIMARY KEY (uuid, identifier))");

        safeExec(connection, "CREATE TABLE IF NOT EXISTS portfolios_log (" +
                "id INTEGER PRIMARY KEY, " +
                "uuid VARCHAR(36) NOT NULL, " +
                "day INT NOT NULL, " +
                "identifier TEXT NOT NULL, " +
                "amount INT NOT NULL, " +
                "contribution DOUBLE NOT NULL)");

        safeExec(connection, "CREATE TABLE IF NOT EXISTS portfolios_worth (" +
                "id INTEGER PRIMARY KEY, " +
                "uuid VARCHAR(36) NOT NULL, " +
                "day INT NOT NULL, " +
                "worth DOUBLE NOT NULL, " +
                "UNIQUE (uuid, day))");

        safeExec(connection, "CREATE TABLE IF NOT EXISTS capacities (" +
                "uuid VARCHAR(36) PRIMARY KEY, " +
                "capacity INT NOT NULL)");

        safeExec(connection, "CREATE TABLE IF NOT EXISTS discord_links (" +
                "userid VARCHAR(18) PRIMARY KEY, " +
                "uuid VARCHAR(36) NOT NULL, " +
                "nickname TEXT NOT NULL)");

        safeExec(connection, "CREATE TABLE IF NOT EXISTS trade_log (" +
                "id INTEGER PRIMARY KEY, " +
                "uuid VARCHAR(36) NOT NULL, " +
                "day INT NOT NULL, " +
                "date TEXT NOT NULL, " +
                "identifier TEXT NOT NULL, " +
                "amount INT NOT NULL, " +
                "value REAL NOT NULL, " +
                "buy INT NOT NULL, " +
                "discord INT NOT NULL)");

        safeExec(connection, "CREATE TABLE IF NOT EXISTS cpi (" +
                "day INT PRIMARY KEY, " +
                "date TEXT NOT NULL, " +
                "value DOUBLE NOT NULL)");

        safeExec(connection, "CREATE TABLE IF NOT EXISTS alerts (" +
                "day INT NOT NULL, " +
                "userid TEXT NOT NULL, " +
                "identifier TEXT NOT NULL, " +
                "price DOUBLE NOT NULL)");

        safeExec(connection, "CREATE TABLE IF NOT EXISTS flows (" +
                "day INT PRIMARY KEY, " +
                "flow DOUBLE NOT NULL, " +
                "taxes DOUBLE NOT NULL, " +
                "operations INT NOT NULL)");

        safeExec(connection, "CREATE TABLE IF NOT EXISTS limit_orders (" +
                "id INTEGER PRIMARY KEY, " +
                "expiration TEXT NOT NULL, " +
                "uuid VARCHAR(36) NOT NULL, " +
                "identifier TEXT NOT NULL, " +
                "type INT NOT NULL, " +
                "price DOUBLE NOT NULL, " +
                "to_complete INT NOT NULL, " +
                "completed INT NOT NULL, " +
                "cost DOUBLE NOT NULL)");

        safeExec(connection, "CREATE TABLE IF NOT EXISTS loans (" +
                "id INTEGER PRIMARY KEY, " +
                "uuid VARCHAR(36) NOT NULL UNIQUE, " +
                "debt DOUBLE NOT NULL)");

        safeExec(connection, "CREATE TABLE IF NOT EXISTS interests (" +
                "id INTEGER PRIMARY KEY, " +
                "uuid VARCHAR(36) NOT NULL UNIQUE, " +
                "paid DOUBLE NOT NULL)");

        safeExec(connection, "CREATE TABLE IF NOT EXISTS user_names (" +
                "id INTEGER PRIMARY KEY, " +
                "uuid VARCHAR(36) NOT NULL UNIQUE, " +
                "name TEXT NOT NULL)");

        safeExec(connection, "CREATE TABLE IF NOT EXISTS balances (" +
                "id INTEGER PRIMARY KEY, " +
                "uuid VARCHAR(36) NOT NULL UNIQUE, " +
                "balance DOUBLE NOT NULL)");

        safeExec(connection, "CREATE TABLE IF NOT EXISTS money_supply (" +
                "day INT PRIMARY KEY, " +
                "supply DOUBLE NOT NULL)");

        safeExec(connection, "CREATE TABLE IF NOT EXISTS discord (" +
                "userid VARCHAR(18) PRIMARY KEY, " +
                "uuid VARCHAR(36) NOT NULL, " +
                "nickname TEXT NOT NULL)");
    }

    private void addMissingIndexes(Connection connection) {
        // Unique indexes on existing installs that predate the schema improvements above.
        // CREATE UNIQUE INDEX IF NOT EXISTS is idempotent and safe to run every startup.
        safeExec(connection, "CREATE UNIQUE INDEX IF NOT EXISTS idx_loans_uuid ON loans(uuid)");
        safeExec(connection, "CREATE UNIQUE INDEX IF NOT EXISTS idx_interests_uuid ON interests(uuid)");
        safeExec(connection, "CREATE UNIQUE INDEX IF NOT EXISTS idx_portfolios_pk ON portfolios(uuid, identifier)");
        safeExec(connection, "CREATE UNIQUE INDEX IF NOT EXISTS idx_portfolios_worth_uk ON portfolios_worth(uuid, day)");
        safeExec(connection, "CREATE UNIQUE INDEX IF NOT EXISTS idx_discord_links_userid ON discord_links(userid)");
        safeExec(connection, "CREATE UNIQUE INDEX IF NOT EXISTS idx_discord_userid ON discord(userid)");
        safeExec(connection, "CREATE UNIQUE INDEX IF NOT EXISTS idx_user_names_uuid ON user_names(uuid)");
        safeExec(connection, "CREATE UNIQUE INDEX IF NOT EXISTS idx_balances_uuid ON balances(uuid)");
        safeExec(connection, "CREATE UNIQUE INDEX IF NOT EXISTS idx_cpi_day ON cpi(day)");
        // Performance indexes
        safeExec(connection, "CREATE INDEX IF NOT EXISTS idx_trade_log_uuid ON trade_log(uuid)");
        safeExec(connection, "CREATE INDEX IF NOT EXISTS idx_trade_log_identifier ON trade_log(identifier)");
        safeExec(connection, "CREATE INDEX IF NOT EXISTS idx_portfolios_log_uuid ON portfolios_log(uuid)");
        safeExec(connection, "CREATE INDEX IF NOT EXISTS idx_portfolios_worth_uuid ON portfolios_worth(uuid)");
    }
}
