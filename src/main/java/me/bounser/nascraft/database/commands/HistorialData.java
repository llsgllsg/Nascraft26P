package me.bounser.nascraft.database.commands;

import me.bounser.nascraft.database.BaseDatabase;
import me.bounser.nascraft.market.unit.Item;
import me.bounser.nascraft.market.unit.stats.Instant;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.LinkedList;
import java.util.List;

public class HistorialData {

    private static String bucket(java.time.Instant now, ChronoUnit unit) {
        return now.truncatedTo(unit).toString();
    }

    private static void upsert(Connection connection, String table, String identifier,
                               String bucketStart, double price, double volume) throws SQLException {
        String sql = "INSERT INTO " + table
                + " (identifier, bucket_start, open, high, low, close, volume) VALUES (?,?,?,?,?,?,?) "
                + "ON CONFLICT(identifier, bucket_start) DO UPDATE SET "
                + "high=max(high, excluded.high), "
                + "low=min(low, excluded.low), "
                + "close=excluded.close, "
                + "volume=volume+excluded.volume";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, identifier);
            ps.setString(2, bucketStart);
            ps.setDouble(3, price);
            ps.setDouble(4, price);
            ps.setDouble(5, price);
            ps.setDouble(6, price);
            ps.setDouble(7, volume);
            ps.executeUpdate();
        }
    }

    public static void saveDayPrice(Connection connection, Item item, Instant instant) {
        try {
            String bucketStart = bucket(java.time.Instant.now(), ChronoUnit.MINUTES);
            upsert(connection, "prices_day", item.getIdentifier(), bucketStart,
                    instant.getPrice(), instant.getVolume());
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public static void saveMonthPrice(Connection connection, Item item, Instant instant) {
        try {
            String bucketStart = bucket(java.time.Instant.now(), ChronoUnit.HOURS);
            upsert(connection, "prices_month", item.getIdentifier(), bucketStart,
                    instant.getPrice(), instant.getVolume());
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public static void saveHistoryPrices(Connection connection, Item item, Instant instant) {
        try {
            String bucketStart = bucket(java.time.Instant.now(), ChronoUnit.DAYS);
            upsert(connection, "prices_history", item.getIdentifier(), bucketStart,
                    instant.getPrice(), instant.getVolume());
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private static List<Instant> readRange(Connection connection, String table, Item item,
                                           java.time.Instant from, int limit) {
        List<Instant> prices = new LinkedList<>();
        String sql = "SELECT bucket_start, close, volume FROM " + table
                + " WHERE identifier=? AND bucket_start >= ? ORDER BY bucket_start ASC"
                + (limit > 0 ? " LIMIT " + limit : "");
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, item.getIdentifier());
            ps.setString(2, from.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    java.time.Instant t = BaseDatabase.fromIso(rs.getString("bucket_start"));
                    if (t == null) continue;
                    LocalDateTime ldt = LocalDateTime.ofInstant(t, ZoneId.systemDefault());
                    prices.add(new Instant(ldt, rs.getDouble("close"), (int) rs.getDouble("volume")));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return prices;
    }

    public static List<Instant> getDayPrices(Connection connection, Item item) {
        java.time.Instant from = java.time.Instant.now().minus(24, ChronoUnit.HOURS);
        List<Instant> prices = readRange(connection, "prices_day", item, from, 0);
        if (prices.isEmpty()) {
            prices.add(new Instant(LocalDateTime.now().minusHours(24), 0, 0));
            prices.add(new Instant(LocalDateTime.now().minusMinutes(5), 0, 0));
        } else {
            prices.add(0, new Instant(LocalDateTime.now().minusHours(24), 0, 0));
        }
        prices.add(new Instant(LocalDateTime.now(), item.getPrice().getValue(), item.getVolume()));
        return prices;
    }

    public static List<Instant> getMonthPrices(Connection connection, Item item) {
        java.time.Instant from = java.time.Instant.now().minus(30, ChronoUnit.DAYS);
        List<Instant> prices = readRange(connection, "prices_month", item, from, 0);
        if (prices.isEmpty()) {
            prices.add(new Instant(LocalDateTime.now().minusDays(30), 0, 0));
            prices.add(new Instant(LocalDateTime.now().minusMinutes(5), 0, 0));
        } else {
            prices.add(0, new Instant(LocalDateTime.now().minusDays(30), 0, 0));
        }
        prices.add(new Instant(LocalDateTime.now(), item.getPrice().getValue(), item.getVolume()));
        return prices;
    }

    public static List<Instant> getYearPrices(Connection connection, Item item) {
        java.time.Instant from = java.time.Instant.now().minus(365, ChronoUnit.DAYS);
        List<Instant> prices = readRange(connection, "prices_history", item, from, 0);
        if (prices.isEmpty()) {
            prices.add(new Instant(LocalDateTime.now().minusDays(365), 0, 0));
            prices.add(new Instant(LocalDateTime.now().minusMinutes(5), 0, 0));
        }
        prices.add(new Instant(LocalDateTime.now(), item.getPrice().getValue(), item.getVolume()));
        return prices;
    }

    public static List<Instant> getAllPrices(Connection connection, Item item) {
        List<Instant> prices = new LinkedList<>();
        String sql = "SELECT bucket_start, close, volume FROM prices_history WHERE identifier=? ORDER BY bucket_start ASC";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, item.getIdentifier());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    java.time.Instant t = BaseDatabase.fromIso(rs.getString("bucket_start"));
                    if (t == null) continue;
                    LocalDateTime ldt = LocalDateTime.ofInstant(t, ZoneId.systemDefault());
                    prices.add(new Instant(ldt, rs.getDouble("close"), (int) rs.getDouble("volume")));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        if (prices.isEmpty()) {
            prices.add(new Instant(LocalDateTime.now().minusDays(30), 0, 0));
            prices.add(new Instant(LocalDateTime.now().minusMinutes(5), 0, 0));
        }
        prices.add(new Instant(LocalDateTime.now(), item.getPrice().getValue(), item.getVolume()));
        return prices;
    }

    public static Double getPriceOfDay(Connection connection, String identifier, int day) {
        // day is the plugin's "normalised day" counter; approximate via bucket_start range match
        java.time.Instant dayStart = java.time.LocalDate.of(2023, 1, 1)
                .plusDays(day).atStartOfDay().toInstant(ZoneOffset.UTC);
        String sql = "SELECT close FROM prices_history WHERE identifier=? AND bucket_start=?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, identifier);
            ps.setString(2, dayStart.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return 0.0;
                return rs.getDouble("close");
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
