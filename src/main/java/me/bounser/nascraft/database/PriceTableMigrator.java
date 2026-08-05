package me.bounser.nascraft.database;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Migrates legacy price tables to the current OHLC-bucket schema.
 *
 * <p>Databases created by Nascraft 1.9.2 (and earlier) have
 * {@code prices_day} / {@code prices_month} / {@code prices_history} tables
 * that predate the {@code bucket_start} column (they used {@code date} /
 * {@code price} / {@code volume} instead). {@code CREATE TABLE IF NOT EXISTS}
 * cannot add the missing column to an existing table, which is why the plugin
 * crashed with {@code no such column: bucket_start}. This migrator rebuilds
 * those tables in place, mapping the old rows onto the new schema.</p>
 */
public final class PriceTableMigrator {

    private static final String[] PRICE_TABLES =
            {"prices_day", "prices_month", "prices_history"};

    private PriceTableMigrator() {
    }

    /**
     * Rebuilds any legacy price tables present on the given connection.
     * Idempotent: tables that already have the new schema are left untouched.
     */
    public static void migrate(Connection connection) throws SQLException {
        for (String table : PRICE_TABLES) {
            Set<String> cols = lowerColumnNames(connection, table);
            // Missing table (fresh install) or already-new schema: nothing to do.
            if (cols.isEmpty() || cols.contains("bucket_start")) {
                continue;
            }

            String temp = table + "_migrated";

            createPriceTable(connection, temp);
            copyRows(connection, table, temp, cols, bucketUnit(table));
            dropTable(connection, table);
            renameTable(connection, temp, table);
        }
    }

    /**
     * The granularity the current plugin writes into each table (see
     * {@code HistorialData}: prices_day -> minutes, prices_month -> hours,
     * prices_history -> days). Migrated buckets are truncated to the same unit.
     */
    private static ChronoUnit bucketUnit(String table) {
        return switch (table) {
            case "prices_day" -> ChronoUnit.MINUTES;
            case "prices_month" -> ChronoUnit.HOURS;
            default -> ChronoUnit.DAYS;
        };
    }

    private static Set<String> lowerColumnNames(Connection connection, String table)
            throws SQLException {
        DatabaseMetaData meta = connection.getMetaData();
        Set<String> cols = new HashSet<>();
        try (ResultSet rs = meta.getColumns(connection.getCatalog(), null, table, null)) {
            while (rs.next()) {
                cols.add(rs.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));
            }
        }
        return cols;
    }

    private static void createPriceTable(Connection connection, String table)
            throws SQLException {
        try (Statement s = connection.createStatement()) {
            // Clean up after a previously interrupted migration, if any.
            s.execute("DROP TABLE IF EXISTS " + table);
            s.execute("CREATE TABLE " + table + " (" +
                    "identifier TEXT NOT NULL, " +
                    "bucket_start TEXT NOT NULL, " +
                    "open REAL NOT NULL, " +
                    "high REAL NOT NULL, " +
                    "low REAL NOT NULL, " +
                    "close REAL NOT NULL, " +
                    "volume REAL NOT NULL DEFAULT 0, " +
                    "PRIMARY KEY (identifier, bucket_start))");
        }
    }

    private static void copyRows(Connection connection, String src, String dst, Set<String> cols,
                                 ChronoUnit unit) throws SQLException {
        boolean hasDay = cols.contains("day");
        boolean hasDate = cols.contains("date");
        if (!hasDay && !hasDate) {
            // No usable timestamp column; leave the new table empty rather
            // than inventing bucket_start values.
            return;
        }

        String priceCol = cols.contains("close") ? "close" : (cols.contains("price") ? "price" : null);
        boolean hasVolume = cols.contains("volume");
        String tsCol = hasDay ? "day" : "date";

        String select = "SELECT identifier, " + tsCol +
                (priceCol != null ? ", " + priceCol : ", 0") +
                (hasVolume ? ", volume" : ", 0") +
                " FROM " + src + " ORDER BY " + tsCol;

        // Aggregate the source rows into one OHLCV row per (identifier, bucket_start),
        // mirroring the plugin's own upsert: open = first price, high = max,
        // low = min, close = last price, volume = sum. Legacy tables can hold
        // several rows that map to the same bucket (duplicate or sub-bucket
        // snapshots); the new schema's PRIMARY KEY (identifier, bucket_start)
        // rejects them otherwise.
        Map<Bucket, double[]> buckets = new LinkedHashMap<>();

        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(select)) {
            while (rs.next()) {
                Instant instant = hasDay
                        ? dayToBucket(rs.getInt(2))
                        : toInstant(rs.getString(2));
                if (instant == null) {
                    continue;
                }
                String bucket = instant.truncatedTo(unit).toString();

                double price = priceCol != null ? rs.getDouble(3) : 0.0;
                double volume = hasVolume ? rs.getDouble(4) : 0.0;

                Bucket key = new Bucket(rs.getString(1), bucket);
                double[] v = buckets.get(key);
                if (v == null) {
                    buckets.put(key, new double[]{price, price, price, price, volume});
                } else {
                    v[1] = Math.max(v[1], price); // high
                    v[2] = Math.min(v[2], price); // low
                    v[3] = price;                 // close = last
                    v[4] += volume;
                }
            }
        }

        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO " + dst +
                        " (identifier, bucket_start, open, high, low, close, volume) " +
                        "VALUES (?,?,?,?,?,?,?)")) {
            for (Map.Entry<Bucket, double[]> e : buckets.entrySet()) {
                double[] v = e.getValue();
                ps.setString(1, e.getKey().identifier());
                ps.setString(2, e.getKey().start());
                ps.setDouble(3, v[0]);
                ps.setDouble(4, v[1]);
                ps.setDouble(5, v[2]);
                ps.setDouble(6, v[3]);
                ps.setDouble(7, v[4]);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private static void dropTable(Connection connection, String table) throws SQLException {
        try (Statement s = connection.createStatement()) {
            s.execute("DROP TABLE " + table);
        }
    }

    private static void renameTable(Connection connection, String from, String to)
            throws SQLException {
        try (Statement s = connection.createStatement()) {
            s.execute("ALTER TABLE " + from + " RENAME TO " + to);
        }
    }

    /**
     * Maps the plugin's "normalised day" counter (2023-01-01 + day) to the
     * UTC-midnight instant of that day.
     */
    private static Instant dayToBucket(int day) {
        return LocalDate.of(2023, 1, 1)
                .plusDays(day)
                .atStartOfDay()
                .toInstant(ZoneOffset.UTC);
    }

    /**
     * Normalises the various date-string formats the legacy code wrote into
     * the {@code date} column into an instant. Returns null when the value
     * cannot be parsed.
     */
    private static Instant toInstant(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        String value = raw.trim();

        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalDateTime.parse(value)
                    .atZone(ZoneId.systemDefault())
                    .toInstant();
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalDateTime.parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                    .atZone(ZoneId.systemDefault())
                    .toInstant();
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalDate.parse(value)
                    .atStartOfDay(ZoneId.systemDefault())
                    .toInstant();
        } catch (DateTimeParseException ignored) {
        }
        return null;
    }

    /**
     * Composite key of one price bucket: an item on a specific start instant.
     */
    private record Bucket(String identifier, String start) {
    }
}
