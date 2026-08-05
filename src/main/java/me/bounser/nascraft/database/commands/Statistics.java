package me.bounser.nascraft.database.commands;

import me.bounser.nascraft.chart.cpi.CPIInstant;
import me.bounser.nascraft.database.SqlDialect;
import me.bounser.nascraft.database.SqlDialects;
import me.bounser.nascraft.database.commands.resources.DayInfo;
import me.bounser.nascraft.database.commands.resources.NormalisedDate;
import me.bounser.nascraft.market.unit.Item;
import me.bounser.nascraft.market.unit.stats.Instant;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Statistics {

    public static void saveCPI(Connection connection, float value) throws SQLException {
        int today = NormalisedDate.getDays();
        // Insert-or-ignore keeps the first value recorded per day
        try (PreparedStatement prep = connection.prepareStatement(
                SqlDialects.current().insertIgnoreInto() + " cpi (day, date, value) VALUES (?, ?, ?)")) {
            prep.setInt(1, today);
            prep.setString(2, LocalDateTime.now().toString());
            prep.setFloat(3, value);
            prep.executeUpdate();
        }
    }

    public static List<CPIInstant> getAllCPI(Connection connection) throws SQLException {
        List<CPIInstant> cpiInstants = new ArrayList<>();
        try (PreparedStatement prep = connection.prepareStatement("SELECT value, date FROM cpi");
             ResultSet rs = prep.executeQuery()) {
            while (rs.next()) {
                cpiInstants.add(new CPIInstant(rs.getFloat("value"), LocalDateTime.parse(rs.getString("date"))));
            }
        }
        return cpiInstants;
    }

    public static List<Instant> getPriceAgainstCPI(Connection connection, Item item) throws SQLException {
        int minDay;
        try (PreparedStatement prep = connection.prepareStatement("SELECT MIN(day) AS min_day FROM cpi");
             ResultSet rs = prep.executeQuery()) {
            if (!rs.next() || rs.getObject("min_day") == null) {
                return Collections.singletonList(new Instant(LocalDateTime.now(), item.getPrice().getValue(), 0));
            }
            minDay = rs.getInt("min_day");
        }

        if (NormalisedDate.getDays() - 30 < minDay) {
            return HistorialData.getMonthPrices(connection, item);
        }
        return HistorialData.getAllPrices(connection, item);
    }

    public static void addTransaction(Connection connection, double newFlow, double effectiveTaxes)
            throws SQLException {
        SqlDialect d = SqlDialects.current();
        String sql = "INSERT INTO flows (day, flow, taxes, operations) VALUES (?, ?, ?, 1)" +
                     d.onConflictUpdate("day") +
                     "flow = flow + " + d.inserted("flow") + ", " +
                     "taxes = taxes + " + d.inserted("taxes") + ", " +
                     "operations = operations + 1";
        try (PreparedStatement prep = connection.prepareStatement(sql)) {
            prep.setInt(1, NormalisedDate.getDays());
            prep.setDouble(2, newFlow);
            prep.setDouble(3, Math.abs(effectiveTaxes));
            prep.executeUpdate();
        }
    }

    public static List<DayInfo> getDayInfos(Connection connection) throws SQLException {
        List<DayInfo> dayInfos = new ArrayList<>();
        try (PreparedStatement prep = connection.prepareStatement("SELECT day, flow, taxes FROM flows");
             ResultSet rs = prep.executeQuery()) {
            while (rs.next()) {
                dayInfos.add(new DayInfo(rs.getInt("day"), rs.getDouble("flow"), rs.getDouble("taxes")));
            }
        }
        return dayInfos;
    }

    public static double getAllTaxesCollected(Connection connection) throws SQLException {
        try (PreparedStatement prep = connection.prepareStatement(
                "SELECT taxes FROM flows ORDER BY day DESC LIMIT 1");
             ResultSet rs = prep.executeQuery()) {
            return rs.next() ? rs.getDouble("taxes") : 0;
        }
    }
}
