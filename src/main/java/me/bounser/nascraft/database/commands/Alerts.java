package me.bounser.nascraft.database.commands;

import me.bounser.nascraft.config.Config;
import me.bounser.nascraft.database.commands.resources.NormalisedDate;
import me.bounser.nascraft.discord.alerts.DiscordAlerts;
import me.bounser.nascraft.market.unit.Item;

import java.sql.*;

public class Alerts {

    public static void addAlert(Connection connection, String userid, Item item, double price)
            throws SQLException {
        try (PreparedStatement prep = connection.prepareStatement(
                "INSERT INTO alerts (day, userid, identifier, price) VALUES (?, ?, ?, ?)")) {
            prep.setInt(1, NormalisedDate.getDays());
            prep.setString(2, userid);
            prep.setString(3, item.getIdentifier());
            prep.setDouble(4, price);
            prep.executeUpdate();
        }
    }

    public static void removeAlert(Connection connection, String userid, Item item) throws SQLException {
        try (PreparedStatement prep = connection.prepareStatement(
                "DELETE FROM alerts WHERE userid = ? AND identifier = ?")) {
            prep.setString(1, userid);
            prep.setString(2, item.getIdentifier());
            prep.executeUpdate();
        }
    }

    public static void retrieveAlerts(Connection connection) throws SQLException {
        try (PreparedStatement prep = connection.prepareStatement(
                "SELECT userid, identifier, price FROM alerts");
             ResultSet rs = prep.executeQuery()) {
            while (rs.next()) {
                DiscordAlerts.getInstance().setAlert(
                        rs.getString("userid"), rs.getString("identifier"), rs.getDouble("price"));
            }
        }
    }

    public static void removeAllAlerts(Connection connection, String userId) throws SQLException {
        try (PreparedStatement prep = connection.prepareStatement(
                "DELETE FROM alerts WHERE userid = ?")) {
            prep.setString(1, userId);
            prep.executeUpdate();
        }
    }

    public static void purgeAlerts(Connection connection) throws SQLException {
        int expiration = Config.getInstance().getAlertsDaysUntilExpired();
        try (PreparedStatement prep = connection.prepareStatement(
                "DELETE FROM alerts WHERE day < ?")) {
            prep.setInt(1, NormalisedDate.getDays() - expiration);
            prep.executeUpdate();
        }
    }
}
