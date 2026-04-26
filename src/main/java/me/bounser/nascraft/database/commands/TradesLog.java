package me.bounser.nascraft.database.commands;

import me.bounser.nascraft.config.Config;
import me.bounser.nascraft.database.commands.resources.NormalisedDate;
import me.bounser.nascraft.database.commands.resources.Trade;
import me.bounser.nascraft.formatter.RoundUtils;
import me.bounser.nascraft.market.MarketManager;
import me.bounser.nascraft.market.unit.Item;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TradesLog {

    public static void saveTrade(Connection connection, Trade trade) throws SQLException {
        String sql = "INSERT INTO trade_log (uuid, day, date, identifier, amount, value, buy, discord) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement prep = connection.prepareStatement(sql)) {
            prep.setString(1, trade.getUuid().toString());
            prep.setInt(2, NormalisedDate.getDays());
            prep.setString(3, NormalisedDate.formatDateTime(LocalDateTime.now()));
            prep.setString(4, trade.getItem().getIdentifier());
            prep.setInt(5, trade.getAmount());
            prep.setFloat(6, RoundUtils.round(trade.getValue()));
            prep.setBoolean(7, trade.isBuy());
            prep.setBoolean(8, trade.throughDiscord());
            prep.executeUpdate();
        }
    }

    public static List<Trade> retrieveTrades(Connection connection, UUID uuid, int offset, int limit)
            throws SQLException {
        if (uuid == null) return new ArrayList<>();
        String sql = "SELECT * FROM trade_log WHERE uuid = ? ORDER BY id DESC LIMIT " + limit + " OFFSET ?";
        return fetchTrades(connection, sql, stmt -> {
            stmt.setString(1, uuid.toString());
            stmt.setInt(2, offset);
        }, uuid);
    }

    public static List<Trade> retrieveTrades(Connection connection, UUID uuid, Item item, int offset, int limit)
            throws SQLException {
        if (uuid == null) return new ArrayList<>();
        String sql = "SELECT * FROM trade_log WHERE uuid = ? AND identifier = ? ORDER BY id DESC LIMIT " + limit + " OFFSET ?";
        return fetchTrades(connection, sql, stmt -> {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, item.getIdentifier());
            stmt.setInt(3, offset);
        }, null);
    }

    public static List<Trade> retrieveTrades(Connection connection, Item item, int offset, int limit)
            throws SQLException {
        String sql = "SELECT * FROM trade_log WHERE identifier = ? ORDER BY id DESC LIMIT " + limit + " OFFSET ?";
        return fetchTrades(connection, sql, stmt -> {
            stmt.setString(1, item.getIdentifier());
            stmt.setInt(2, offset);
        }, null);
    }

    public static List<Trade> retrieveLastTrades(Connection connection, int offset, int limit)
            throws SQLException {
        String sql = "SELECT * FROM trade_log ORDER BY id DESC LIMIT " + limit + " OFFSET ?";
        return fetchTrades(connection, sql, stmt -> stmt.setInt(1, offset), null);
    }

    public static void purgeHistory(Connection connection) throws SQLException {
        int offset = Config.getInstance().getDatabasePurgeDays();
        if (offset == -1) return;
        try (PreparedStatement prep = connection.prepareStatement(
                "DELETE FROM trade_log WHERE day < ?")) {
            prep.setInt(1, NormalisedDate.getDays() - offset);
            prep.executeUpdate();
        }
    }

    @FunctionalInterface
    private interface StatementBinder {
        void bind(PreparedStatement stmt) throws SQLException;
    }

    private static List<Trade> fetchTrades(Connection connection, String sql,
                                           StatementBinder binder, UUID uuidHint) throws SQLException {
        List<Trade> trades = new ArrayList<>();
        try (PreparedStatement prep = connection.prepareStatement(sql)) {
            binder.bind(prep);
            try (ResultSet rs = prep.executeQuery()) {
                while (rs.next()) {
                    UUID uuid = uuidHint != null ? uuidHint : UUID.fromString(rs.getString("uuid"));
                    trades.add(new Trade(
                            MarketManager.getInstance().getItem(rs.getString("identifier")),
                            NormalisedDate.parseDateTime(rs.getString("date")),
                            rs.getFloat("value"),
                            rs.getInt("amount"),
                            rs.getBoolean("buy"),
                            rs.getBoolean("discord"),
                            uuid
                    ));
                }
            }
        }
        return trades;
    }
}
