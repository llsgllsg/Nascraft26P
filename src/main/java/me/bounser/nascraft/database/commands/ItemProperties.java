package me.bounser.nascraft.database.commands;

import me.bounser.nascraft.config.Config;
import me.bounser.nascraft.database.SqlDialect;
import me.bounser.nascraft.database.SqlDialects;
import me.bounser.nascraft.market.MarketManager;
import me.bounser.nascraft.market.unit.Item;

import java.sql.*;

public class ItemProperties {

    public static void saveItem(Connection connection, Item item) throws SQLException {
        SqlDialect d = SqlDialects.current();
        String sql = "INSERT INTO items (identifier, lastprice, lowest, highest, stock, taxes, version) VALUES (?, ?, ?, ?, ?, ?, ?)" +
                     d.onConflictUpdate("identifier") +
                     "lastprice = " + d.inserted("lastprice") + ", " +
                     "lowest    = " + d.inserted("lowest") + ", " +
                     "highest   = " + d.inserted("highest") + ", " +
                     "stock     = " + d.inserted("stock") + ", " +
                     "taxes     = " + d.inserted("taxes") + ", " +
                     "version   = " + d.inserted("version");
        try (PreparedStatement prep = connection.prepareStatement(sql)) {
            prep.setString(1, item.getIdentifier());
            prep.setDouble(2, item.getPrice().getValue());
            prep.setDouble(3, item.getPrice().getHistoricalLow());
            prep.setDouble(4, item.getPrice().getHistoricalHigh());
            prep.setDouble(5, item.getPrice().getStock());
            prep.setDouble(6, item.getCollectedTaxes());
            prep.setLong(7, item.getPrice().getVersion());
            prep.executeUpdate();
        }
    }

    public static void retrieveItem(Connection connection, Item item) throws SQLException {
        try (PreparedStatement prep = connection.prepareStatement(
                "SELECT lowest, highest, stock, taxes, version FROM items WHERE identifier = ?")) {
            prep.setString(1, item.getIdentifier());
            try (ResultSet rs = prep.executeQuery()) {
                if (rs.next()) {
                    item.getPrice().restoreFromSaved(rs.getDouble("stock"), rs.getLong("version"));
                    item.getPrice().setHistoricalHigh(rs.getFloat("highest"));
                    item.getPrice().setHistoricalLow(rs.getFloat("lowest"));
                    item.setCollectedTaxes(rs.getFloat("taxes"));
                } else {
                    float initialPrice = Config.getInstance().getInitialPrice(item.getIdentifier());
                    item.getPrice().setStock(0);
                    item.getPrice().setHistoricalHigh(initialPrice);
                    item.getPrice().setHistoricalLow(initialPrice);
                    item.setCollectedTaxes(0);
                    try (PreparedStatement ins = connection.prepareStatement(
                            "INSERT INTO items (identifier, lastprice, lowest, highest, stock, taxes, version) " +
                            "VALUES (?, ?, ?, ?, 0, 0, 0)")) {
                        ins.setString(1, item.getIdentifier());
                        ins.setFloat(2, initialPrice);
                        ins.setFloat(3, initialPrice);
                        ins.setFloat(4, initialPrice);
                        ins.executeUpdate();
                    }
                }
            }
        }
    }

    public static float retrieveLastPrice(Connection connection, Item item) throws SQLException {
        try (PreparedStatement prep = connection.prepareStatement(
                "SELECT lastprice FROM items WHERE identifier = ?")) {
            prep.setString(1, item.getIdentifier());
            try (ResultSet rs = prep.executeQuery()) {
                return rs.next() ? rs.getFloat("lastprice")
                                 : Config.getInstance().getInitialPrice(item.getIdentifier());
            }
        }
    }

    public static void retrieveItems(Connection connection) throws SQLException {
        try (PreparedStatement prep = connection.prepareStatement(
                "SELECT identifier, stock, version FROM items");
             ResultSet rs = prep.executeQuery()) {
            while (rs.next()) {
                Item item = MarketManager.getInstance().getItem(rs.getString("identifier"));
                if (item != null && item.isParent()) {
                    item.getPrice().restoreFromSaved(rs.getDouble("stock"), rs.getLong("version"));
                }
            }
        }
    }
}
