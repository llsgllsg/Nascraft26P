package me.bounser.nascraft.database.commands;

import me.bounser.nascraft.database.SqlDialect;
import me.bounser.nascraft.database.SqlDialects;
import me.bounser.nascraft.database.commands.resources.NormalisedDate;
import me.bounser.nascraft.portfolio.Portfolio;
import me.bounser.nascraft.portfolio.PortfoliosManager;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

public class PortfoliosWorth {

    public static void saveOrUpdateWorth(Connection connection, UUID uuid, int day, double worth)
            throws SQLException {
        SqlDialect d = SqlDialects.current();
        String sql = "INSERT INTO portfolios_worth (uuid, day, worth) VALUES (?, ?, ?)" +
                     d.onConflictUpdate("uuid, day") + "worth = " + d.inserted("worth");
        try (PreparedStatement prep = connection.prepareStatement(sql)) {
            prep.setString(1, uuid.toString());
            prep.setInt(2, day);
            prep.setDouble(3, worth);
            prep.executeUpdate();
        }
    }

    public static void saveOrUpdateWorthToday(Connection connection, UUID uuid, double worth)
            throws SQLException {
        saveOrUpdateWorth(connection, uuid, NormalisedDate.getDays(), worth);
    }

    public static HashMap<UUID, Portfolio> getTopWorth(Connection connection, int n) throws SQLException {
        LinkedHashMap<UUID, Portfolio> result = new LinkedHashMap<>();
        String sql = "SELECT uuid, worth FROM portfolios_worth " +
                     "WHERE (uuid, day) IN (SELECT uuid, MAX(day) FROM portfolios_worth GROUP BY uuid) " +
                     "ORDER BY worth DESC LIMIT ?";

        // 先把结果集读干净，再去构造 Portfolio。
        // getPortfolio 会再查一次库；如果还在遍历结果集的时候就查，等于在这条连接上
        // 叠一条语句（SQLite 的池只有 1 条连接，历史上就是在这里卡死 30 秒的）。
        List<UUID> uuids = new ArrayList<>();
        try (PreparedStatement prep = connection.prepareStatement(sql)) {
            prep.setInt(1, n);
            try (ResultSet rs = prep.executeQuery()) {
                while (rs.next()) uuids.add(UUID.fromString(rs.getString("uuid")));
            }
        }

        for (UUID uuid : uuids) {
            result.put(uuid, PortfoliosManager.getInstance().getPortfolio(uuid));
        }
        return result;
    }

    public static double getLatestWorth(Connection connection, UUID uuid) throws SQLException {
        try (PreparedStatement prep = connection.prepareStatement(
                "SELECT worth FROM portfolios_worth WHERE uuid = ? ORDER BY day DESC LIMIT 1")) {
            prep.setString(1, uuid.toString());
            try (ResultSet rs = prep.executeQuery()) {
                return rs.next() ? rs.getDouble("worth") : 0;
            }
        }
    }
}
