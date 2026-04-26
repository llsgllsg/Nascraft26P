package me.bounser.nascraft.database.commands;

import me.bounser.nascraft.market.MarketManager;
import me.bounser.nascraft.market.limitorders.LimitOrder;
import me.bounser.nascraft.market.limitorders.LimitOrdersManager;
import me.bounser.nascraft.market.limitorders.OrderType;
import me.bounser.nascraft.market.unit.Item;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.UUID;

public class LimitOrders {

    public static void addLimitOrder(Connection connection, UUID uuid, LocalDateTime expiration,
                                     Item item, int type, double price, int amount) throws SQLException {
        try (PreparedStatement prep = connection.prepareStatement(
                "INSERT INTO limit_orders (expiration, uuid, identifier, type, price, to_complete, completed, cost) " +
                "VALUES (?, ?, ?, ?, ?, ?, 0, 0)")) {
            prep.setString(1, expiration.toString());
            prep.setString(2, uuid.toString());
            prep.setString(3, item.getIdentifier());
            prep.setInt(4, type);
            prep.setDouble(5, price);
            prep.setInt(6, amount);
            prep.executeUpdate();
        }
    }

    public static void updateLimitOrder(Connection connection, UUID uuid, Item item,
                                        int completed, double cost) throws SQLException {
        try (PreparedStatement prep = connection.prepareStatement(
                "UPDATE limit_orders SET completed = ?, cost = ? WHERE uuid = ? AND identifier = ?")) {
            prep.setInt(1, completed);
            prep.setDouble(2, cost);
            prep.setString(3, uuid.toString());
            prep.setString(4, item.getIdentifier());
            prep.executeUpdate();
        }
    }

    public static void removeLimitOrder(Connection connection, String uuid, String identifier)
            throws SQLException {
        try (PreparedStatement prep = connection.prepareStatement(
                "DELETE FROM limit_orders WHERE uuid = ? AND identifier = ?")) {
            prep.setString(1, uuid);
            prep.setString(2, identifier);
            prep.executeUpdate();
        }
    }

    public static void retrieveLimitOrders(Connection connection) throws SQLException {
        try (PreparedStatement prep = connection.prepareStatement(
                "SELECT expiration, uuid, identifier, type, cost, price, to_complete, completed FROM limit_orders");
             ResultSet rs = prep.executeQuery()) {
            while (rs.next()) {
                LimitOrdersManager.getInstance().registerLimitOrder(new LimitOrder(
                        UUID.fromString(rs.getString("uuid")),
                        MarketManager.getInstance().getItem(rs.getString("identifier")),
                        LocalDateTime.parse(rs.getString("expiration")),
                        rs.getInt("to_complete"),
                        rs.getInt("completed"),
                        rs.getDouble("price"),
                        rs.getDouble("cost"),
                        rs.getInt("type") == 1 ? OrderType.LIMIT_BUY : OrderType.LIMIT_SELL
                ));
            }
        }
    }
}
