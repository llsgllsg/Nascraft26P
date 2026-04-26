package me.bounser.nascraft.database.sqlite;

import com.zaxxer.hikari.HikariConfig;
import me.bounser.nascraft.Nascraft;
import me.bounser.nascraft.chart.cpi.CPIInstant;
import me.bounser.nascraft.database.BaseDatabase;
import me.bounser.nascraft.database.commands.*;
import me.bounser.nascraft.database.commands.resources.DayInfo;
import me.bounser.nascraft.database.commands.resources.NormalisedDate;
import me.bounser.nascraft.database.commands.resources.Trade;
import me.bounser.nascraft.market.MarketManager;
import me.bounser.nascraft.market.unit.Item;
import me.bounser.nascraft.market.unit.stats.Instant;
import me.bounser.nascraft.portfolio.Portfolio;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SqliteDatabase extends BaseDatabase {

    private final File dataFolder;
    private final File dbFile;

    public SqliteDatabase(File dataFolder) {
        this.dataFolder = dataFolder;
        File dataDir = new File(dataFolder, "data");
        if (!dataDir.exists()) dataDir.mkdirs();
        this.dbFile = new File(dataDir, "sqlite.db");
    }

    @Override
    protected void configureHikari(HikariConfig config) {
        String url = "jdbc:sqlite:" + dbFile.getAbsolutePath()
                + "?foreign_keys=on&journal_mode=WAL&synchronous=NORMAL";
        config.setJdbcUrl(url);
        config.setDriverClassName("org.sqlite.JDBC");

        config.setMaximumPoolSize(1);
        config.setConnectionTimeout(30_000);
        config.setPoolName("Nascraft-SQLite");
    }

    @Override
    protected void onConnectionInit(Connection connection) throws SQLException {
        try (Statement s = connection.createStatement()) {
            s.execute("PRAGMA foreign_keys=ON");
            s.execute("PRAGMA journal_mode=WAL");
            s.execute("PRAGMA synchronous=NORMAL");
        }
    }

    @Override
    protected void runMigrations(Connection connection) throws SQLException {
        createAllTables(connection);
        addMissingIndexes(connection);
    }

    @Override
    public void createTables() {
        withConnection((SqlConsumer) this::createAllTables);
    }

    private void createAllTables(Connection connection) throws SQLException {
        safeExec(connection, "CREATE TABLE IF NOT EXISTS items (" +
                "identifier TEXT PRIMARY KEY, " +
                "lastprice DOUBLE, " +
                "lowest DOUBLE, " +
                "highest DOUBLE, " +
                "stock DOUBLE DEFAULT 0, " +
                "taxes DOUBLE)");

        safeExec(connection, "CREATE TABLE IF NOT EXISTS prices_day (" +
                "identifier TEXT NOT NULL, " +
                "bucket_start TEXT NOT NULL, " +
                "open REAL NOT NULL, " +
                "high REAL NOT NULL, " +
                "low REAL NOT NULL, " +
                "close REAL NOT NULL, " +
                "volume REAL NOT NULL DEFAULT 0, " +
                "PRIMARY KEY (identifier, bucket_start))");

        safeExec(connection, "CREATE TABLE IF NOT EXISTS prices_month (" +
                "identifier TEXT NOT NULL, " +
                "bucket_start TEXT NOT NULL, " +
                "open REAL NOT NULL, " +
                "high REAL NOT NULL, " +
                "low REAL NOT NULL, " +
                "close REAL NOT NULL, " +
                "volume REAL NOT NULL DEFAULT 0, " +
                "PRIMARY KEY (identifier, bucket_start))");

        safeExec(connection, "CREATE TABLE IF NOT EXISTS prices_history (" +
                "identifier TEXT NOT NULL, " +
                "bucket_start TEXT NOT NULL, " +
                "open REAL NOT NULL, " +
                "high REAL NOT NULL, " +
                "low REAL NOT NULL, " +
                "close REAL NOT NULL, " +
                "volume REAL NOT NULL DEFAULT 0, " +
                "PRIMARY KEY (identifier, bucket_start))");

        safeExec(connection, "CREATE TABLE IF NOT EXISTS portfolios (" +
                "uuid VARCHAR(36) NOT NULL, " +
                "identifier TEXT NOT NULL, " +
                "amount INT NOT NULL DEFAULT 0, " +
                "PRIMARY KEY (uuid, identifier))");

        safeExec(connection, "CREATE TABLE IF NOT EXISTS portfolios_log (" +
                "id INTEGER PRIMARY KEY, " +
                "uuid VARCHAR(36) NOT NULL, " +
                "day INT NOT NULL, " +
                "identifier TEXT NOT NULL, " +
                "amount INT NOT NULL, " +
                "contribution DOUBLE NOT NULL)");

        safeExec(connection, "CREATE TABLE IF NOT EXISTS portfolios_worth (" +
                "id INTEGER PRIMARY KEY, " +
                "uuid VARCHAR(36) NOT NULL, " +
                "day INT NOT NULL, " +
                "worth DOUBLE NOT NULL, " +
                "UNIQUE (uuid, day))");

        safeExec(connection, "CREATE TABLE IF NOT EXISTS capacities (" +
                "uuid VARCHAR(36) PRIMARY KEY, " +
                "capacity INT NOT NULL)");

        safeExec(connection, "CREATE TABLE IF NOT EXISTS discord_links (" +
                "userid VARCHAR(18) PRIMARY KEY, " +
                "uuid VARCHAR(36) NOT NULL, " +
                "nickname TEXT NOT NULL)");

        safeExec(connection, "CREATE TABLE IF NOT EXISTS trade_log (" +
                "id INTEGER PRIMARY KEY, " +
                "uuid VARCHAR(36) NOT NULL, " +
                "day INT NOT NULL, " +
                "date TEXT NOT NULL, " +
                "identifier TEXT NOT NULL, " +
                "amount INT NOT NULL, " +
                "value REAL NOT NULL, " +
                "buy INT NOT NULL, " +
                "discord INT NOT NULL)");

        safeExec(connection, "CREATE TABLE IF NOT EXISTS cpi (" +
                "day INT PRIMARY KEY, " +
                "date TEXT NOT NULL, " +
                "value DOUBLE NOT NULL)");

        safeExec(connection, "CREATE TABLE IF NOT EXISTS alerts (" +
                "day INT NOT NULL, " +
                "userid TEXT NOT NULL, " +
                "identifier TEXT NOT NULL, " +
                "price DOUBLE NOT NULL)");

        safeExec(connection, "CREATE TABLE IF NOT EXISTS flows (" +
                "day INT PRIMARY KEY, " +
                "flow DOUBLE NOT NULL, " +
                "taxes DOUBLE NOT NULL, " +
                "operations INT NOT NULL)");

        safeExec(connection, "CREATE TABLE IF NOT EXISTS limit_orders (" +
                "id INTEGER PRIMARY KEY, " +
                "expiration TEXT NOT NULL, " +
                "uuid VARCHAR(36) NOT NULL, " +
                "identifier TEXT NOT NULL, " +
                "type INT NOT NULL, " +
                "price DOUBLE NOT NULL, " +
                "to_complete INT NOT NULL, " +
                "completed INT NOT NULL, " +
                "cost DOUBLE NOT NULL)");

        safeExec(connection, "CREATE TABLE IF NOT EXISTS loans (" +
                "id INTEGER PRIMARY KEY, " +
                "uuid VARCHAR(36) NOT NULL UNIQUE, " +
                "debt DOUBLE NOT NULL)");

        safeExec(connection, "CREATE TABLE IF NOT EXISTS interests (" +
                "id INTEGER PRIMARY KEY, " +
                "uuid VARCHAR(36) NOT NULL UNIQUE, " +
                "paid DOUBLE NOT NULL)");

        safeExec(connection, "CREATE TABLE IF NOT EXISTS user_names (" +
                "id INTEGER PRIMARY KEY, " +
                "uuid VARCHAR(36) NOT NULL UNIQUE, " +
                "name TEXT NOT NULL)");

        safeExec(connection, "CREATE TABLE IF NOT EXISTS balances (" +
                "id INTEGER PRIMARY KEY, " +
                "uuid VARCHAR(36) NOT NULL UNIQUE, " +
                "balance DOUBLE NOT NULL)");

        safeExec(connection, "CREATE TABLE IF NOT EXISTS money_supply (" +
                "day INT PRIMARY KEY, " +
                "supply DOUBLE NOT NULL)");

        safeExec(connection, "CREATE TABLE IF NOT EXISTS discord (" +
                "userid VARCHAR(18) PRIMARY KEY, " +
                "uuid VARCHAR(36) NOT NULL, " +
                "nickname TEXT NOT NULL)");
    }

    private void addMissingIndexes(Connection connection) {
        // Unique indexes on existing installs that predate the schema improvements above.
        // CREATE UNIQUE INDEX IF NOT EXISTS is idempotent and safe to run every startup.
        safeExec(connection, "CREATE UNIQUE INDEX IF NOT EXISTS idx_loans_uuid ON loans(uuid)");
        safeExec(connection, "CREATE UNIQUE INDEX IF NOT EXISTS idx_interests_uuid ON interests(uuid)");
        safeExec(connection, "CREATE UNIQUE INDEX IF NOT EXISTS idx_portfolios_pk ON portfolios(uuid, identifier)");
        safeExec(connection, "CREATE UNIQUE INDEX IF NOT EXISTS idx_portfolios_worth_uk ON portfolios_worth(uuid, day)");
        safeExec(connection, "CREATE UNIQUE INDEX IF NOT EXISTS idx_discord_links_userid ON discord_links(userid)");
        safeExec(connection, "CREATE UNIQUE INDEX IF NOT EXISTS idx_discord_userid ON discord(userid)");
        safeExec(connection, "CREATE UNIQUE INDEX IF NOT EXISTS idx_user_names_uuid ON user_names(uuid)");
        safeExec(connection, "CREATE UNIQUE INDEX IF NOT EXISTS idx_balances_uuid ON balances(uuid)");
        safeExec(connection, "CREATE UNIQUE INDEX IF NOT EXISTS idx_cpi_day ON cpi(day)");
        // Performance indexes
        safeExec(connection, "CREATE INDEX IF NOT EXISTS idx_trade_log_uuid ON trade_log(uuid)");
        safeExec(connection, "CREATE INDEX IF NOT EXISTS idx_trade_log_identifier ON trade_log(identifier)");
        safeExec(connection, "CREATE INDEX IF NOT EXISTS idx_portfolios_log_uuid ON portfolios_log(uuid)");
        safeExec(connection, "CREATE INDEX IF NOT EXISTS idx_portfolios_worth_uuid ON portfolios_worth(uuid)");
    }

    private void safeExec(Connection connection, String sql) {
        try (Statement s = connection.createStatement()) {
            s.execute(sql);
        } catch (SQLException e) {
            Nascraft.getInstance().getLogger().warning("SQL migration warning: " + e.getMessage());
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

    @Override
    public void saveEverything() {
        if (dataSource == null || dataSource.isClosed()) return;
        for (Item item : MarketManager.getInstance().getAllParentItems()) {
            withConnection(c -> ItemProperties.saveItem(c, item));
        }
    }

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

    @Override
    public void updateBalance(UUID uuid) {
        withConnection(c -> Balances.updateBalance(c, uuid));
    }

    @Override
    public Map<Integer, Double> getMoneySupplyHistory() {
        return queryConnection(Balances::getMoneySupplyHistory);
    }

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
