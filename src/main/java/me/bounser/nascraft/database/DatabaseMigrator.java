package me.bounser.nascraft.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;

public final class DatabaseMigrator {

    // Every table the plugin owns
    static final String[] TABLES = {
            "items", "prices_day", "prices_month", "prices_history",
            "portfolios", "portfolios_log", "portfolios_worth", "capacities",
            "discord_links", "trade_log", "cpi", "alerts", "flows",
            "limit_orders", "loans", "interests", "user_names", "balances",
            "money_supply", "discord"
    };

    private DatabaseMigrator() {}

    public static int copyAll(Connection src, Connection dst, SqlDialect targetDialect) throws SQLException {
        int total = 0;
        for (String table : TABLES) {
            total += copyTable(src, dst, table, targetDialect);
        }
        return total;
    }

    static int copyTable(Connection src, Connection dst, String table, SqlDialect dialect) throws SQLException {
        try (Statement st = src.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM " + table)) {

            ResultSetMetaData md = rs.getMetaData();
            int cols = md.getColumnCount();

            StringBuilder colList = new StringBuilder();
            StringBuilder placeholders = new StringBuilder();
            for (int i = 1; i <= cols; i++) {
                if (i > 1) {
                    colList.append(", ");
                    placeholders.append(", ");
                }
                colList.append(md.getColumnName(i));
                placeholders.append('?');
            }

            String insert = dialect.insertIgnoreInto() + " " + table
                    + " (" + colList + ") VALUES (" + placeholders + ")";

            int n = 0;
            try (PreparedStatement ps = dst.prepareStatement(insert)) {
                while (rs.next()) {
                    for (int i = 1; i <= cols; i++) ps.setObject(i, rs.getObject(i));
                    ps.addBatch();
                    if (++n % 500 == 0) ps.executeBatch();
                }
                ps.executeBatch();
            }
            return n;
        }
    }
}
