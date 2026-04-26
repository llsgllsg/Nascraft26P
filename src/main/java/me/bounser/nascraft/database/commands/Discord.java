package me.bounser.nascraft.database.commands;

import java.sql.*;
import java.util.UUID;

public class Discord {

    public static void saveDiscordLink(Connection connection, UUID uuid, String userId, String nickname)
            throws SQLException {
        try (PreparedStatement prep = connection.prepareStatement(
                "INSERT OR REPLACE INTO discord (userid, uuid, nickname) VALUES (?, ?, ?)")) {
            prep.setString(1, userId);
            prep.setString(2, uuid.toString());
            prep.setString(3, nickname);
            prep.executeUpdate();
        }
    }

    public static void removeLink(Connection connection, UUID uuid) throws SQLException {
        try (PreparedStatement prep = connection.prepareStatement(
                "DELETE FROM discord WHERE uuid = ?")) {
            prep.setString(1, uuid.toString());
            prep.executeUpdate();
        }
    }

    public static UUID getUUIDFromUserid(Connection connection, String userId) throws SQLException {
        try (PreparedStatement prep = connection.prepareStatement(
                "SELECT uuid FROM discord WHERE userid = ?")) {
            prep.setString(1, userId);
            try (ResultSet rs = prep.executeQuery()) {
                return rs.next() ? UUID.fromString(rs.getString("uuid")) : null;
            }
        }
    }

    public static String getDiscordUserId(Connection connection, UUID uuid) throws SQLException {
        try (PreparedStatement prep = connection.prepareStatement(
                "SELECT userid FROM discord WHERE uuid = ?")) {
            prep.setString(1, uuid.toString());
            try (ResultSet rs = prep.executeQuery()) {
                return rs.next() ? rs.getString("userid") : null;
            }
        }
    }

    public static String getNicknameFromUserId(Connection connection, String userId) throws SQLException {
        try (PreparedStatement prep = connection.prepareStatement(
                "SELECT nickname FROM discord WHERE userid = ?")) {
            prep.setString(1, userId);
            try (ResultSet rs = prep.executeQuery()) {
                return rs.next() ? rs.getString("nickname") : null;
            }
        }
    }
}
