package me.bounser.nascraft.database.commands;

import me.bounser.nascraft.database.SqlDialect;
import me.bounser.nascraft.database.SqlDialects;

import java.sql.*;
import java.util.UUID;

public class UserNames {

    public static String getNameByUUID(Connection connection, UUID uuid) throws SQLException {
        try (PreparedStatement prep = connection.prepareStatement(
                "SELECT name FROM user_names WHERE uuid = ?")) {
            prep.setString(1, uuid.toString());
            try (ResultSet rs = prep.executeQuery()) {
                return rs.next() ? rs.getString("name") : null;
            }
        }
    }

    public static String getUUIDbyName(Connection connection, String name) throws SQLException {
        try (PreparedStatement prep = connection.prepareStatement(
                "SELECT uuid FROM user_names WHERE name = ?")) {
            prep.setString(1, name);
            try (ResultSet rs = prep.executeQuery()) {
                return rs.next() ? rs.getString("uuid") : null;
            }
        }
    }

    public static void saveOrUpdateNick(Connection connection, UUID uuid, String name) throws SQLException {
        SqlDialect d = SqlDialects.current();
        try (PreparedStatement prep = connection.prepareStatement(
                "INSERT INTO user_names (uuid, name) VALUES (?, ?)" +
                d.onConflictUpdate("uuid") + "name = " + d.inserted("name"))) {
            prep.setString(1, uuid.toString());
            prep.setString(2, name);
            prep.executeUpdate();
        }
    }
}
