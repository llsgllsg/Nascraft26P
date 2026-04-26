package me.bounser.nascraft.database.commands;

import java.sql.*;
import java.util.UUID;

public class DiscordLink {

    public static void saveLink(Connection connection, String userId, UUID uuid, String nickname)
            throws SQLException {
        try (PreparedStatement prep = connection.prepareStatement(
                "INSERT OR REPLACE INTO discord_links (userid, uuid, nickname) VALUES (?, ?, ?)")) {
            prep.setString(1, userId);
            prep.setString(2, uuid.toString());
            prep.setString(3, nickname);
            prep.executeUpdate();
        }
    }

    public static void removeLink(Connection connection, String userId) throws SQLException {
        try (PreparedStatement prep = connection.prepareStatement(
                "DELETE FROM discord_links WHERE userid = ?")) {
            prep.setString(1, userId);
            prep.executeUpdate();
        }
    }

    public static UUID getUUID(Connection connection, String userId) throws SQLException {
        try (PreparedStatement prep = connection.prepareStatement(
                "SELECT uuid FROM discord_links WHERE userid = ?")) {
            prep.setString(1, userId);
            try (ResultSet rs = prep.executeQuery()) {
                return rs.next() ? UUID.fromString(rs.getString("uuid")) : null;
            }
        }
    }

    public static String getNickname(Connection connection, String userId) throws SQLException {
        try (PreparedStatement prep = connection.prepareStatement(
                "SELECT nickname FROM discord_links WHERE userid = ?")) {
            prep.setString(1, userId);
            try (ResultSet rs = prep.executeQuery()) {
                return rs.next() ? rs.getString("nickname") : null;
            }
        }
    }

    public static String getUserId(Connection connection, UUID uuid) throws SQLException {
        try (PreparedStatement prep = connection.prepareStatement(
                "SELECT userid FROM discord_links WHERE uuid = ?")) {
            prep.setString(1, uuid.toString());
            try (ResultSet rs = prep.executeQuery()) {
                return rs.next() ? rs.getString("userid") : null;
            }
        }
    }
}
