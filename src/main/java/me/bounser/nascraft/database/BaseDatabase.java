package me.bounser.nascraft.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import me.bounser.nascraft.Nascraft;
import me.bounser.nascraft.chart.cpi.CPIInstant;
import me.bounser.nascraft.database.commands.*;
import me.bounser.nascraft.database.commands.resources.DayInfo;
import me.bounser.nascraft.database.commands.resources.NormalisedDate;
import me.bounser.nascraft.database.commands.resources.Trade;
import me.bounser.nascraft.market.MarketManager;
import me.bounser.nascraft.market.unit.Item;
import me.bounser.nascraft.market.unit.stats.Instant;
import me.bounser.nascraft.portfolio.Portfolio;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public abstract class BaseDatabase implements Database {

    protected HikariDataSource dataSource;

    // Configure the Hikari pool (JDBC URL, driver, credentials, pool size).
    protected abstract void configureHikari(HikariConfig config);

    // Per-connection initialisation run once at startup (e.g. SQLite PRAGMAs).
    protected abstract void onConnectionInit(Connection connection) throws SQLException;

    // Create/upgrade the schema. Run once at startup on a pooled connection.
    protected abstract void runMigrations(Connection connection) throws SQLException;

    @Override
    public void connect() {
        HikariConfig config = new HikariConfig();
        configureHikari(config);
        dataSource = new HikariDataSource(config);

        try (Connection connection = dataSource.getConnection()) {
            onConnectionInit(connection);
            runMigrations(connection);
        } catch (SQLException e) {
            Nascraft.getInstance().getLogger().warning("Database init failed: " + e.getMessage());
            throw new RuntimeException(e);
        }

        createTables();
    }

    @Override
    public void disconnect() {
        try {
            saveEverything();
        } finally {
            close();
        }
    }

    @Override
    public boolean isConnected() {
        return dataSource != null && !dataSource.isClosed();
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    // Execute a statement, logging (but not throwing on) failures. Used by DDL.
    protected void safeExec(Connection connection, String sql) {
        try (Statement s = connection.createStatement()) {
            s.execute(sql);
        } catch (SQLException e) {
            Nascraft.getInstance().getLogger().warning("SQL migration warning: " + e.getMessage());
        }
    }

    @FunctionalInterface
    public interface SqlConsumer {
        void accept(Connection connection) throws SQLException;
    }

    @FunctionalInterface
    public interface SqlFunction<T> {
        T apply(Connection connection) throws SQLException;
    }

    public void withConnection(SqlConsumer action) {
        try (Connection connection = dataSource.getConnection()) {
            action.accept(connection);
        } catch (SQLException e) {
            throw fail("withConnection failed", e);
        }
    }

    public <T> T queryConnection(SqlFunction<T> action) {
        try (Connection connection = dataSource.getConnection()) {
            return action.apply(connection);
        } catch (SQLException e) {
            throw fail("queryConnection failed", e);
        }
    }

    public <T> T queryTransaction(SqlFunction<T> action) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                T result = action.apply(connection);
                connection.commit();
                return result;
            } catch (SQLException e) {
                try { connection.rollback(); } catch (SQLException rb) {
                    Nascraft.getInstance().getLogger().log(Level.SEVERE, "Rollback failed", rb);
                }
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw fail("queryTransaction failed", e);
        }
    }

    public void withTransaction(SqlConsumer action) {
        queryTransaction(conn -> { action.accept(conn); return null; });
    }

    private static DatabaseException fail(String context, SQLException e) {
        Nascraft.getInstance().getLogger().log(Level.SEVERE, context + ": " + e.getMessage(), e);
        return new DatabaseException(context, e);
    }

    public static String toIso(java.time.Instant instant) {
        return instant == null ? null : instant.toString();
    }

    public static java.time.Instant fromIso(String iso) {
        if (iso == null) return null;
        try {
            return java.time.Instant.parse(iso);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    // ------------------------------------------------------------------
    // Maintenance
    // ------------------------------------------------------------------

    @Override
    public void saveEverything() {
        if (dataSource == null || dataSource.isClosed()) return;
        for (Item item : MarketManager.getInstance().getAllParentItems()) {
            withConnection(c -> ItemProperties.saveItem(c, item));
        }
    }

    public void purgeOldData() {
        withConnection(conn -> {
            try (PreparedStatement p1 = conn.prepareStatement("DELETE FROM trade_log WHERE day < ?")) {
                p1.setInt(1, NormalisedDate.getDays() - 90);
                p1.executeUpdate();
            }
            try (PreparedStatement p2 = conn.prepareStatement("DELETE FROM prices_day WHERE bucket_start < ?")) {
                p2.setString(1, java.time.Instant.now().minusSeconds(2L * 86400).toString());
                p2.executeUpdate();
            }
            try (PreparedStatement p3 = conn.prepareStatement("DELETE FROM prices_month WHERE bucket_start < ?")) {
                p3.setString(1, java.time.Instant.now().minusSeconds(31L * 86400).toString());
                p3.executeUpdate();
            }
        });
        purgeHistory();
        purgeAlerts();
    }

    // ------------------------------------------------------------------
    // Discord links (native linking)
    // ------------------------------------------------------------------

    @Override
    public void saveLink(String userId, UUID uuid, String nickname) {
        withConnection(c -> DiscordLink.saveLink(c, userId, uuid, nickname));
    }

    @Override
    public void removeLink(String userId) {
        withConnection(c -> DiscordLink.removeLink(c, userId));
    }

    @Override
    public UUID getUUID(String userId) {
        return queryConnection(c -> DiscordLink.getUUID(c, userId));
    }

    @Override
    public String getNickname(String userId) {
        return queryConnection(c -> DiscordLink.getNickname(c, userId));
    }

    @Override
    public String getUserId(UUID uuid) {
        return queryConnection(c -> DiscordLink.getUserId(c, uuid));
    }

    // ------------------------------------------------------------------
    // Historical prices
    // ------------------------------------------------------------------

    @Override
    public void saveDayPrice(Item item, Instant instant) {
        withConnection(c -> HistorialData.saveDayPrice(c, item, instant));
    }

    @Override
    public void saveMonthPrice(Item item, Instant instant) {
        withConnection(c -> HistorialData.saveMonthPrice(c, item, instant));
    }

    @Override
    public void saveHistoryPrices(Item item, Instant instant) {
        withConnection(c -> HistorialData.saveHistoryPrices(c, item, instant));
    }

    @Override
    public List<Instant> getDayPrices(Item item) {
        return queryConnection(c -> HistorialData.getDayPrices(c, item));
    }

    @Override
    public List<Instant> getMonthPrices(Item item) {
        return queryConnection(c -> HistorialData.getMonthPrices(c, item));
    }

    @Override
    public List<Instant> getYearPrices(Item item) {
        return queryConnection(c -> HistorialData.getYearPrices(c, item));
    }

    @Override
    public List<Instant> getAllPrices(Item item) {
        return queryConnection(c -> HistorialData.getAllPrices(c, item));
    }

    @Override
    public Double getPriceOfDay(String identifier, int day) {
        return queryConnection(c -> HistorialData.getPriceOfDay(c, identifier, day));
    }

    // ------------------------------------------------------------------
    // Item properties
    // ------------------------------------------------------------------

    @Override
    public void saveItem(Item item) {
        withConnection(c -> ItemProperties.saveItem(c, item));
    }

    @Override
    public void retrieveItem(Item item) {
        withConnection(c -> ItemProperties.retrieveItem(c, item));
    }

    @Override
    public void retrieveItems() {
        withConnection(ItemProperties::retrieveItems);
    }

    @Override
    public float retrieveLastPrice(Item item) {
        return queryConnection(c -> ItemProperties.retrieveLastPrice(c, item));
    }

    // ------------------------------------------------------------------
    // Trade log
    // ------------------------------------------------------------------

    @Override
    public void saveTrade(Trade trade) {
        withConnection(c -> TradesLog.saveTrade(c, trade));
    }

    @Override
    public List<Trade> retrieveTrades(UUID uuid, int offset, int limit) {
        return queryConnection(c -> TradesLog.retrieveTrades(c, uuid, offset, limit));
    }

    @Override
    public List<Trade> retrieveTrades(UUID uuid, Item item, int offset, int limit) {
        return queryConnection(c -> TradesLog.retrieveTrades(c, uuid, item, offset, limit));
    }

    @Override
    public List<Trade> retrieveTrades(Item item, int offset, int limit) {
        return queryConnection(c -> TradesLog.retrieveTrades(c, item, offset, limit));
    }

    @Override
    public List<Trade> retrieveTrades(int offset, int limit) {
        return queryConnection(c -> TradesLog.retrieveLastTrades(c, offset, limit));
    }

    @Override
    public void purgeHistory() {
        withConnection(TradesLog::purgeHistory);
    }

    // ------------------------------------------------------------------
    // Portfolios
    // ------------------------------------------------------------------

    @Override
    public void updateItemPortfolio(UUID uuid, Item item, int quantity) {
        withConnection(c -> Portfolios.updateItemPortfolio(c, uuid, item, quantity));
    }

    @Override
    public void removeItemPortfolio(UUID uuid, Item item) {
        withConnection(c -> Portfolios.removeItemPortfolio(c, uuid, item));
    }

    @Override
    public void clearPortfolio(UUID uuid) {
        withConnection(c -> Portfolios.clearPortfolio(c, uuid));
    }

    @Override
    public void updateCapacity(UUID uuid, int capacity) {
        withConnection(c -> Portfolios.updateCapacity(c, uuid, capacity));
    }

    @Override
    public LinkedHashMap<Item, Integer> retrievePortfolio(UUID uuid) {
        return queryConnection(c -> Portfolios.retrievePortfolio(c, uuid));
    }

    @Override
    public int retrieveCapacity(UUID uuid) {
        return queryConnection(c -> Portfolios.retrieveCapacity(c, uuid));
    }

    // ------------------------------------------------------------------
    // Debt / interests
    // ------------------------------------------------------------------

    @Override
    public void increaseDebt(UUID uuid, Double debt) {
        withConnection(c -> Debt.increaseDebt(c, uuid, debt));
    }

    @Override
    public void decreaseDebt(UUID uuid, Double debt) {
        withTransaction(c -> Debt.decreaseDebt(c, uuid, debt));
    }

    @Override
    public double getDebt(UUID uuid) {
        return queryConnection(c -> Debt.getDebt(c, uuid));
    }

    @Override
    public HashMap<UUID, Double> getUUIDAndDebt() {
        return queryConnection(Debt::getUUIDAndDebt);
    }

    @Override
    public void addInterestPaid(UUID uuid, Double interest) {
        withConnection(c -> Debt.addInterestPaid(c, uuid, interest));
    }

    @Override
    public HashMap<UUID, Double> getUUIDAndInterestsPaid() {
        return queryConnection(Debt::getUUIDAndInterestsPaid);
    }

    @Override
    public double getInterestsPaid(UUID uuid) {
        return queryConnection(c -> Debt.getInterestsPaid(c, uuid));
    }

    @Override
    public double getAllOutstandingDebt() {
        return queryConnection(Debt::getAllOutstandingDebt);
    }

    @Override
    public double getAllInterestsPaid() {
        return queryConnection(Debt::getAllInterestsPaid);
    }

    // ------------------------------------------------------------------
    // Portfolio worth / leaderboard
    // ------------------------------------------------------------------

    @Override
    public void saveOrUpdateWorth(UUID uuid, int day, double worth) {
        withConnection(c -> PortfoliosWorth.saveOrUpdateWorth(c, uuid, day, worth));
    }

    @Override
    public void saveOrUpdateWorthToday(UUID uuid, double worth) {
        withConnection(c -> PortfoliosWorth.saveOrUpdateWorthToday(c, uuid, worth));
    }

    @Override
    public HashMap<UUID, Portfolio> getTopWorth(int n) {
        return queryConnection(c -> PortfoliosWorth.getTopWorth(c, n));
    }

    @Override
    public double getLatestWorth(UUID uuid) {
        return queryConnection(c -> PortfoliosWorth.getLatestWorth(c, uuid));
    }

    // ------------------------------------------------------------------
    // Portfolio contribution log
    // ------------------------------------------------------------------

    @Override
    public void logContribution(UUID uuid, Item item, int amount) {
        withTransaction(c -> PortfoliosLog.logContribution(c, uuid, item, amount));
    }

    @Override
    public void logWithdraw(UUID uuid, Item item, int amount) {
        withTransaction(c -> PortfoliosLog.logWithdraw(c, uuid, item, amount));
    }

    @Override
    public HashMap<Integer, Double> getContributionChangeEachDay(UUID uuid) {
        return queryConnection(c -> PortfoliosLog.getContributionChangeEachDay(c, uuid));
    }

    @Override
    public HashMap<Integer, HashMap<String, Integer>> getCompositionEachDay(UUID uuid) {
        return queryConnection(c -> PortfoliosLog.getCompositionEachDay(c, uuid));
    }

    @Override
    public int getFirstDay(UUID uuid) {
        return queryConnection(c -> PortfoliosLog.getFirstDay(c, uuid));
    }

    // ------------------------------------------------------------------
    // CPI / statistics
    // ------------------------------------------------------------------

    @Override
    public void saveCPIValue(float indexValue) {
        withConnection(c -> Statistics.saveCPI(c, indexValue));
    }

    @Override
    public List<CPIInstant> getCPIHistory() {
        return queryConnection(Statistics::getAllCPI);
    }

    @Override
    public List<Instant> getPriceAgainstCPI(Item item) {
        return queryConnection(c -> Statistics.getPriceAgainstCPI(c, item));
    }

    @Override
    public void addTransaction(double newFlow, double effectiveTaxes) {
        withConnection(c -> Statistics.addTransaction(c, newFlow, effectiveTaxes));
    }

    @Override
    public List<DayInfo> getDayInfos() {
        return queryConnection(Statistics::getDayInfos);
    }

    @Override
    public double getAllTaxesCollected() {
        return queryConnection(Statistics::getAllTaxesCollected);
    }

    // ------------------------------------------------------------------
    // Alerts
    // ------------------------------------------------------------------

    @Override
    public void addAlert(String userid, Item item, double price) {
        withConnection(c -> Alerts.addAlert(c, userid, item, price));
    }

    @Override
    public void removeAlert(String userid, Item item) {
        withConnection(c -> Alerts.removeAlert(c, userid, item));
    }

    @Override
    public void retrieveAlerts() {
        withConnection(Alerts::retrieveAlerts);
    }

    @Override
    public void removeAllAlerts(String userid) {
        withConnection(c -> Alerts.removeAllAlerts(c, userid));
    }

    @Override
    public void purgeAlerts() {
        withConnection(Alerts::purgeAlerts);
    }

    // ------------------------------------------------------------------
    // Limit orders
    // ------------------------------------------------------------------

    @Override
    public void addLimitOrder(UUID uuid, LocalDateTime expiration, Item item, int type, double price, int amount) {
        withConnection(c -> LimitOrders.addLimitOrder(c, uuid, expiration, item, type, price, amount));
    }

    @Override
    public void updateLimitOrder(UUID uuid, Item item, int completed, double cost) {
        withConnection(c -> LimitOrders.updateLimitOrder(c, uuid, item, completed, cost));
    }

    @Override
    public void removeLimitOrder(String uuid, String identifier) {
        withConnection(c -> LimitOrders.removeLimitOrder(c, uuid, identifier));
    }

    @Override
    public void retrieveLimitOrders() {
        withConnection(LimitOrders::retrieveLimitOrders);
    }

    // ------------------------------------------------------------------
    // User names
    // ------------------------------------------------------------------

    @Override
    public String getNameByUUID(UUID uuid) {
        return queryConnection(c -> UserNames.getNameByUUID(c, uuid));
    }

    @Override
    public void saveOrUpdateName(UUID uuid, String name) {
        withConnection(c -> UserNames.saveOrUpdateNick(c, uuid, name));
    }

    @Override
    public String getUUIDbyName(String name) {
        return queryConnection(c -> UserNames.getUUIDbyName(c, name));
    }

    // ------------------------------------------------------------------
    // Balances / money supply
    // ------------------------------------------------------------------

    @Override
    public void updateBalance(UUID uuid) {
        withConnection(c -> Balances.updateBalance(c, uuid));
    }

    @Override
    public Map<Integer, Double> getMoneySupplyHistory() {
        return queryConnection(Balances::getMoneySupplyHistory);
    }

    // ------------------------------------------------------------------
    // Discord (DiscordSRV linking)
    // ------------------------------------------------------------------

    @Override
    public void saveDiscordLink(UUID uuid, String userid, String nickname) {
        withConnection(c -> Discord.saveDiscordLink(c, uuid, userid, nickname));
    }

    @Override
    public void removeDiscordLink(UUID uuid) {
        withConnection(c -> Discord.removeLink(c, uuid));
    }

    @Override
    public String getDiscordUserId(UUID uuid) {
        return queryConnection(c -> Discord.getDiscordUserId(c, uuid));
    }

    @Override
    public UUID getUUIDFromUserid(String userid) {
        return queryConnection(c -> Discord.getUUIDFromUserid(c, userid));
    }

    @Override
    public String getNicknameFromUserId(String userid) {
        return queryConnection(c -> Discord.getNicknameFromUserId(c, userid));
    }
}
