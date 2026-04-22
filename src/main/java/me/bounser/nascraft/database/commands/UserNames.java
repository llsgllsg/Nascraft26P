package me.bounser.nascraft.database.commands;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class UserNames {

    public static String getNameByUUID(Connection connection, UUID uuid) {
        String sql = "SELECT name FROM user_names WHERE uuid=?;";
        try (PreparedStatement prep = connection.prepareStatement(sql)) {
            prep.setString(1, uuid.toString());
            try (ResultSet resultSet = prep.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getString("name");
                }
                return null;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public static String getUUIDbyName(Connection connection, String name) {
        String sql = "SELECT uuid FROM user_names WHERE name=?;";
        try (PreparedStatement prep = connection.prepareStatement(sql)) {
            prep.setString(1, name);
            try (ResultSet resultSet = prep.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getString("uuid");
                }
                return null;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public static void saveOrUpdateNick(Connection connection, UUID uuid, String name) {
        String selectSql = "SELECT id FROM user_names WHERE uuid=?;";
        boolean exists;
        try (PreparedStatement prep = connection.prepareStatement(selectSql)) {
            prep.setString(1, uuid.toString());
            try (ResultSet resultSet = prep.executeQuery()) {
                exists = resultSet.next();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        String sql = exists
                ? "UPDATE user_names SET name=? WHERE uuid=?;"
                : "INSERT INTO user_names (uuid, name) VALUES (?,?);";
        try (PreparedStatement prep = connection.prepareStatement(sql)) {
            if (exists) {
                prep.setString(1, name);
                prep.setString(2, uuid.toString());
            } else {
                prep.setString(1, uuid.toString());
                prep.setString(2, name);
            }
            prep.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

}
