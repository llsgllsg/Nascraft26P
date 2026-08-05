package me.bounser.nascraft.database.commands;

import me.bounser.nascraft.database.SqlDialect;
import me.bounser.nascraft.database.SqlDialects;

import java.sql.*;
import java.util.HashMap;
import java.util.UUID;

public class Debt {

    public static void increaseDebt(Connection connection, UUID uuid, double amount) throws SQLException {
        SqlDialect d = SqlDialects.current();
        String sql = "INSERT INTO loans (uuid, debt) VALUES (?, ?)" +
                     d.onConflictUpdate("uuid") + "debt = debt + " + d.inserted("debt");
        try (PreparedStatement prep = connection.prepareStatement(sql)) {
            prep.setString(1, uuid.toString());
            prep.setDouble(2, amount);
            prep.executeUpdate();
        }
    }

    // Must be called within a transaction (wrapped by SqliteDatabase.decreaseDebt).
    public static void decreaseDebt(Connection connection, UUID uuid, double amount) throws SQLException {
        double currentDebt;
        try (PreparedStatement prep = connection.prepareStatement(
                "SELECT debt FROM loans WHERE uuid = ?")) {
            prep.setString(1, uuid.toString());
            try (ResultSet rs = prep.executeQuery()) {
                if (!rs.next()) return;
                currentDebt = rs.getDouble("debt");
            }
        }

        if (currentDebt - amount <= 0) {
            try (PreparedStatement prep = connection.prepareStatement(
                    "DELETE FROM loans WHERE uuid = ?")) {
                prep.setString(1, uuid.toString());
                prep.executeUpdate();
            }
        } else {
            try (PreparedStatement prep = connection.prepareStatement(
                    "UPDATE loans SET debt = ? WHERE uuid = ?")) {
                prep.setDouble(1, currentDebt - amount);
                prep.setString(2, uuid.toString());
                prep.executeUpdate();
            }
        }
    }

    public static double getDebt(Connection connection, UUID uuid) throws SQLException {
        try (PreparedStatement prep = connection.prepareStatement(
                "SELECT debt FROM loans WHERE uuid = ?")) {
            prep.setString(1, uuid.toString());
            try (ResultSet rs = prep.executeQuery()) {
                return rs.next() ? rs.getDouble("debt") : 0;
            }
        }
    }

    public static HashMap<UUID, Double> getUUIDAndDebt(Connection connection) throws SQLException {
        HashMap<UUID, Double> debtors = new HashMap<>();
        try (PreparedStatement prep = connection.prepareStatement(
                "SELECT uuid, debt FROM loans WHERE debt > 0");
             ResultSet rs = prep.executeQuery()) {
            while (rs.next()) {
                debtors.put(UUID.fromString(rs.getString("uuid")), rs.getDouble("debt"));
            }
        }
        return debtors;
    }

    public static void addInterestPaid(Connection connection, UUID uuid, double interest) throws SQLException {
        SqlDialect d = SqlDialects.current();
        String sql = "INSERT INTO interests (uuid, paid) VALUES (?, ?)" +
                     d.onConflictUpdate("uuid") + "paid = paid + " + d.inserted("paid");
        try (PreparedStatement prep = connection.prepareStatement(sql)) {
            prep.setString(1, uuid.toString());
            prep.setDouble(2, interest);
            prep.executeUpdate();
        }
    }

    public static HashMap<UUID, Double> getUUIDAndInterestsPaid(Connection connection) throws SQLException {
        HashMap<UUID, Double> payers = new HashMap<>();
        try (PreparedStatement prep = connection.prepareStatement(
                "SELECT uuid, paid FROM interests");
             ResultSet rs = prep.executeQuery()) {
            while (rs.next()) {
                payers.put(UUID.fromString(rs.getString("uuid")), rs.getDouble("paid"));
            }
        }
        return payers;
    }

    public static double getInterestsPaid(Connection connection, UUID uuid) throws SQLException {
        try (PreparedStatement prep = connection.prepareStatement(
                "SELECT paid FROM interests WHERE uuid = ?")) {
            prep.setString(1, uuid.toString());
            try (ResultSet rs = prep.executeQuery()) {
                return rs.next() ? rs.getDouble("paid") : 0;
            }
        }
    }

    public static double getAllOutstandingDebt(Connection connection) throws SQLException {
        try (PreparedStatement prep = connection.prepareStatement(
                "SELECT SUM(debt) AS total FROM loans");
             ResultSet rs = prep.executeQuery()) {
            return rs.next() ? rs.getDouble("total") : 0;
        }
    }

    public static double getAllInterestsPaid(Connection connection) throws SQLException {
        try (PreparedStatement prep = connection.prepareStatement(
                "SELECT SUM(paid) AS total FROM interests");
             ResultSet rs = prep.executeQuery()) {
            return rs.next() ? rs.getDouble("total") : 0;
        }
    }
}
