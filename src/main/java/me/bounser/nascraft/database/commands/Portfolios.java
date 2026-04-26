package me.bounser.nascraft.database.commands;

import me.bounser.nascraft.config.Config;
import me.bounser.nascraft.market.MarketManager;
import me.bounser.nascraft.market.unit.Item;

import java.sql.*;
import java.util.LinkedHashMap;
import java.util.UUID;

public class Portfolios {

    public static void updateItemPortfolio(Connection connection, UUID uuid, Item item, int quantity)
            throws SQLException {
        String sql = "INSERT INTO portfolios (uuid, identifier, amount) VALUES (?, ?, ?) " +
                     "ON CONFLICT(uuid, identifier) DO UPDATE SET amount = excluded.amount";
        try (PreparedStatement prep = connection.prepareStatement(sql)) {
            prep.setString(1, uuid.toString());
            prep.setString(2, item.getIdentifier());
            prep.setInt(3, quantity);
            prep.executeUpdate();
        }
    }

    public static void removeItemPortfolio(Connection connection, UUID uuid, Item item) throws SQLException {
        try (PreparedStatement prep = connection.prepareStatement(
                "DELETE FROM portfolios WHERE uuid = ? AND identifier = ?")) {
            prep.setString(1, uuid.toString());
            prep.setString(2, item.getIdentifier());
            prep.executeUpdate();
        }
    }

    public static void clearPortfolio(Connection connection, UUID uuid) throws SQLException {
        try (PreparedStatement prep = connection.prepareStatement(
                "DELETE FROM portfolios WHERE uuid = ?")) {
            prep.setString(1, uuid.toString());
            prep.executeUpdate();
        }
    }

    public static void updateCapacity(Connection connection, UUID uuid, int capacity) throws SQLException {
        String sql = "INSERT INTO capacities (uuid, capacity) VALUES (?, ?) " +
                     "ON CONFLICT(uuid) DO UPDATE SET capacity = excluded.capacity";
        try (PreparedStatement prep = connection.prepareStatement(sql)) {
            prep.setString(1, uuid.toString());
            prep.setInt(2, capacity);
            prep.executeUpdate();
        }
    }

    public static LinkedHashMap<Item, Integer> retrievePortfolio(Connection connection, UUID uuid)
            throws SQLException {
        LinkedHashMap<Item, Integer> content = new LinkedHashMap<>();
        try (PreparedStatement prep = connection.prepareStatement(
                "SELECT identifier, amount FROM portfolios WHERE uuid = ?")) {
            prep.setString(1, uuid.toString());
            try (ResultSet rs = prep.executeQuery()) {
                while (rs.next()) {
                    Item item = MarketManager.getInstance().getItem(rs.getString("identifier"));
                    if (item != null) {
                        content.put(item, rs.getInt("amount"));
                    }
                }
            }
        }
        return content;
    }

    public static int retrieveCapacity(Connection connection, UUID uuid) throws SQLException {
        try (PreparedStatement prep = connection.prepareStatement(
                "SELECT capacity FROM capacities WHERE uuid = ?")) {
            prep.setString(1, uuid.toString());
            try (ResultSet rs = prep.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("capacity");
                }
            }
        }
        int defaultSlots = Config.getInstance().getDefaultSlots();
        try (PreparedStatement prep = connection.prepareStatement(
                "INSERT OR IGNORE INTO capacities (uuid, capacity) VALUES (?, ?)")) {
            prep.setString(1, uuid.toString());
            prep.setInt(2, defaultSlots);
            prep.executeUpdate();
        }
        return defaultSlots;
    }
}
