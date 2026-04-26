package me.bounser.nascraft.database.commands;

import me.bounser.nascraft.database.commands.resources.NormalisedDate;
import me.bounser.nascraft.market.unit.Item;

import java.sql.*;
import java.util.HashMap;
import java.util.UUID;

public class PortfoliosLog {

    public static void logContribution(Connection connection, UUID uuid, Item item, int amount)
            throws SQLException {
        int today = NormalisedDate.getDays();
        String selectSql = "SELECT contribution, amount, day FROM portfolios_log " +
                           "WHERE uuid = ? AND identifier = ? ORDER BY day DESC LIMIT 1";
        try (PreparedStatement prep = connection.prepareStatement(selectSql)) {
            prep.setString(1, uuid.toString());
            prep.setString(2, item.getIdentifier());
            try (ResultSet rs = prep.executeQuery()) {
                boolean hasRow = rs.next();
                int prevAmount = hasRow ? rs.getInt("amount") : 0;
                double prevContrib = hasRow ? rs.getDouble("contribution") : 0;

                if (hasRow && rs.getInt("day") == today) {
                    try (PreparedStatement upd = connection.prepareStatement(
                            "UPDATE portfolios_log SET contribution = ?, amount = ? " +
                            "WHERE uuid = ? AND identifier = ? AND day = ?")) {
                        upd.setDouble(1, item.getPrice().getValue() * amount + prevContrib);
                        upd.setInt(2, amount + prevAmount);
                        upd.setString(3, uuid.toString());
                        upd.setString(4, item.getIdentifier());
                        upd.setInt(5, today);
                        upd.executeUpdate();
                    }
                } else {
                    insertLogRow(connection, uuid, item, today,
                            prevAmount + amount,
                            item.getPrice().getValue() * amount + prevContrib);
                }
            }
        }
    }

    public static void logWithdraw(Connection connection, UUID uuid, Item item, int amount)
            throws SQLException {
        int today = NormalisedDate.getDays();
        String selectSql = "SELECT contribution, amount, day FROM portfolios_log " +
                           "WHERE uuid = ? AND identifier = ? ORDER BY day DESC LIMIT 1";
        try (PreparedStatement prep = connection.prepareStatement(selectSql)) {
            prep.setString(1, uuid.toString());
            prep.setString(2, item.getIdentifier());
            try (ResultSet rs = prep.executeQuery()) {
                if (!rs.next()) return;

                int storedAmount = rs.getInt("amount");
                double storedContrib = rs.getDouble("contribution");
                int storedDay = rs.getInt("day");

                if (storedAmount <= amount) {
                    try (PreparedStatement del = connection.prepareStatement(
                            "DELETE FROM portfolios_log WHERE uuid = ? AND identifier = ? AND day = ?")) {
                        del.setString(1, uuid.toString());
                        del.setString(2, item.getIdentifier());
                        del.setInt(3, storedDay);
                        del.executeUpdate();
                    }
                } else if (storedDay == today) {
                    double newContrib = storedContrib * (storedAmount - amount) / (double) storedAmount;
                    try (PreparedStatement upd = connection.prepareStatement(
                            "UPDATE portfolios_log SET amount = ?, contribution = ? " +
                            "WHERE uuid = ? AND identifier = ? AND day = ?")) {
                        upd.setInt(1, storedAmount - amount);
                        upd.setDouble(2, newContrib);
                        upd.setString(3, uuid.toString());
                        upd.setString(4, item.getIdentifier());
                        upd.setInt(5, today);
                        upd.executeUpdate();
                    }
                } else {
                    double newContrib = storedContrib * (storedAmount - amount) / (double) storedAmount;
                    insertLogRow(connection, uuid, item, today, storedAmount - amount, newContrib);
                }
            }
        }
    }

    private static void insertLogRow(Connection connection, UUID uuid, Item item,
                                     int day, int amount, double contribution) throws SQLException {
        try (PreparedStatement ins = connection.prepareStatement(
                "INSERT INTO portfolios_log (uuid, identifier, amount, contribution, day) VALUES (?, ?, ?, ?, ?)")) {
            ins.setString(1, uuid.toString());
            ins.setString(2, item.getIdentifier());
            ins.setInt(3, amount);
            ins.setDouble(4, contribution);
            ins.setInt(5, day);
            ins.executeUpdate();
        }
    }

    public static HashMap<Integer, Double> getContributionChangeEachDay(Connection connection, UUID uuid)
            throws SQLException {
        HashMap<Integer, Double> dayAndContribution = new HashMap<>();
        try (PreparedStatement prep = connection.prepareStatement(
                "SELECT contribution, day FROM portfolios_log WHERE uuid = ? ORDER BY day DESC")) {
            prep.setString(1, uuid.toString());
            try (ResultSet rs = prep.executeQuery()) {
                while (rs.next()) {
                    int day = rs.getInt("day");
                    double contrib = rs.getDouble("contribution");
                    dayAndContribution.merge(day, contrib, Double::sum);
                }
            }
        }
        return dayAndContribution;
    }

    public static HashMap<Integer, HashMap<String, Integer>> getCompositionEachDay(Connection connection, UUID uuid)
            throws SQLException {
        HashMap<Integer, HashMap<String, Integer>> dayAndComposition = new HashMap<>();
        try (PreparedStatement prep = connection.prepareStatement(
                "SELECT identifier, amount, day FROM portfolios_log WHERE uuid = ? ORDER BY day DESC")) {
            prep.setString(1, uuid.toString());
            try (ResultSet rs = prep.executeQuery()) {
                while (rs.next()) {
                    int day = rs.getInt("day");
                    dayAndComposition.computeIfAbsent(day, k -> new HashMap<>())
                                     .put(rs.getString("identifier"), rs.getInt("amount"));
                }
            }
        }
        return dayAndComposition;
    }

    public static int getFirstDay(Connection connection, UUID uuid) throws SQLException {
        try (PreparedStatement prep = connection.prepareStatement(
                "SELECT MIN(day) AS first_day FROM portfolios_log WHERE uuid = ?")) {
            prep.setString(1, uuid.toString());
            try (ResultSet rs = prep.executeQuery()) {
                return rs.next() ? rs.getInt("first_day") : NormalisedDate.getDays();
            }
        }
    }
}
