package me.bounser.nascraft.support;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public final class InMemoryDatabase {

    private InMemoryDatabase() {
    }

    public static Connection openWithSchema() throws SQLException {
        Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        applySchema(connection);
        return connection;
    }

    public static void applySchema(Connection connection) throws SQLException {
        try (Statement st = connection.createStatement()) {
            for (String ddl : SCHEMA) {
                st.execute(ddl);
            }
        }
    }

    private static final String[] SCHEMA = {
        "CREATE TABLE items (" +
            "identifier TEXT PRIMARY KEY, " +
            "lastprice DOUBLE, " +
            "lowest DOUBLE, " +
            "highest DOUBLE, " +
            "stock DOUBLE DEFAULT 0, " +
            "taxes DOUBLE)",

        "CREATE TABLE prices_day (" +
            "identifier TEXT NOT NULL, " +
            "bucket_start TEXT NOT NULL, " +
            "open REAL NOT NULL, " +
            "high REAL NOT NULL, " +
            "low REAL NOT NULL, " +
            "close REAL NOT NULL, " +
            "volume REAL NOT NULL DEFAULT 0, " +
            "PRIMARY KEY (identifier, bucket_start))",

        "CREATE TABLE prices_month (" +
            "identifier TEXT NOT NULL, " +
            "bucket_start TEXT NOT NULL, " +
            "open REAL NOT NULL, " +
            "high REAL NOT NULL, " +
            "low REAL NOT NULL, " +
            "close REAL NOT NULL, " +
            "volume REAL NOT NULL DEFAULT 0, " +
            "PRIMARY KEY (identifier, bucket_start))",

        "CREATE TABLE prices_history (" +
            "identifier TEXT NOT NULL, " +
            "bucket_start TEXT NOT NULL, " +
            "open REAL NOT NULL, " +
            "high REAL NOT NULL, " +
            "low REAL NOT NULL, " +
            "close REAL NOT NULL, " +
            "volume REAL NOT NULL DEFAULT 0, " +
            "PRIMARY KEY (identifier, bucket_start))",

        "CREATE TABLE portfolios (" +
            "uuid VARCHAR(36) NOT NULL, " +
            "identifier TEXT, " +
            "amount INT)",

        "CREATE TABLE portfolios_log (" +
            "id INTEGER PRIMARY KEY, " +
            "uuid VARCHAR(36) NOT NULL, " +
            "day INT, " +
            "identifier TEXT, " +
            "amount INT, " +
            "contribution DOUBLE)",

        "CREATE TABLE portfolios_worth (" +
            "id INTEGER PRIMARY KEY, " +
            "uuid VARCHAR(36) NOT NULL, " +
            "day INT, " +
            "worth DOUBLE)",

        "CREATE TABLE capacities (" +
            "uuid VARCHAR(36) PRIMARY KEY, " +
            "capacity INT)",

        "CREATE TABLE discord_links (" +
            "userid VARCHAR(18) NOT NULL, " +
            "uuid VARCHAR(36) NOT NULL, " +
            "nickname TEXT NOT NULL)",

        "CREATE TABLE trade_log (" +
            "id INTEGER PRIMARY KEY, " +
            "uuid VARCHAR(36) NOT NULL, " +
            "day INT NOT NULL, " +
            "date TEXT NOT NULL, " +
            "identifier TEXT NOT NULL, " +
            "amount INT NOT NULL, " +
            "value TEXT NOT NULL, " +
            "buy INT NOT NULL, " +
            "discord INT NOT NULL)",

        "CREATE TABLE cpi (" +
            "day INT NOT NULL, " +
            "date TEXT NOT NULL, " +
            "value DOUBLE NOT NULL)",

        "CREATE TABLE alerts (" +
            "day INT NOT NULL, " +
            "userid TEXT NOT NULL, " +
            "identifier TEXT NOT NULL, " +
            "price DOUBLE NOT NULL)",

        "CREATE TABLE flows (" +
            "day INT PRIMARY KEY, " +
            "flow DOUBLE NOT NULL, " +
            "taxes DOUBLE NOT NULL, " +
            "operations INT NOT NULL, " +
            "UNIQUE(day))",

        "CREATE TABLE limit_orders (" +
            "id INTEGER PRIMARY KEY, " +
            "expiration TEXT NOT NULL, " +
            "uuid VARCHAR(36) NOT NULL, " +
            "identifier TEXT NOT NULL, " +
            "type INT NOT NULL, " +
            "price DOUBLE NOT NULL, " +
            "to_complete INT NOT NULL, " +
            "completed INT NOT NULL, " +
            "cost INT NOT NULL)",

        "CREATE TABLE loans (" +
            "id INTEGER PRIMARY KEY, " +
            "uuid VARCHAR(36) NOT NULL UNIQUE, " +
            "debt DOUBLE NOT NULL)",

        "CREATE TABLE interests (" +
            "id INTEGER PRIMARY KEY, " +
            "uuid VARCHAR(36) NOT NULL UNIQUE, " +
            "paid DOUBLE NOT NULL)",

        "CREATE TABLE user_names (" +
            "id INTEGER PRIMARY KEY, " +
            "uuid VARCHAR(36) NOT NULL UNIQUE, " +
            "name TEXT NOT NULL)",

        "CREATE TABLE balances (" +
            "id INTEGER PRIMARY KEY, " +
            "uuid VARCHAR(36) NOT NULL UNIQUE, " +
            "balance DOUBLE NOT NULL)",

        "CREATE TABLE money_supply (" +
            "day INT PRIMARY KEY, " +
            "supply DOUBLE NOT NULL)",

        "CREATE TABLE web_credentials (" +
            "name TEXT NOT NULL, " +
            "pass TEXT NOT NULL)",

        "CREATE TABLE player_stats (" +
            "day INT NOT NULL, " +
            "uuid VARCHAR(36) NOT NULL, " +
            "balance DOUBLE NOT NULL, " +
            "portfolio DOUBLE NOT NULL, " +
            "debt DOUBLE NOT NULL)",

        "CREATE TABLE discord (" +
            "userid VARCHAR(18) NOT NULL, " +
            "uuid VARCHAR(36) NOT NULL, " +
            "nickname TEXT NOT NULL)"
    };
}
