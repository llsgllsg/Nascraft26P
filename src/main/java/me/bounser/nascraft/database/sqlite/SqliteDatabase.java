package me.bounser.nascraft.database.sqlite;

import com.zaxxer.hikari.HikariConfig;
import me.bounser.nascraft.Nascraft;
import me.bounser.nascraft.chart.cpi.CPIInstant;
import me.bounser.nascraft.database.BaseDatabase;
import me.bounser.nascraft.database.commands.*;
import me.bounser.nascraft.database.commands.resources.DayInfo;
import me.bounser.nascraft.database.commands.resources.NormalisedDate;
import me.bounser.nascraft.database.commands.resources.Trade;
import me.bounser.nascraft.web.dto.PlayerStatsDTO;
import me.bounser.nascraft.market.MarketManager;
import me.bounser.nascraft.market.unit.Item;
import me.bounser.nascraft.market.unit.stats.Instant;
import me.bounser.nascraft.portfolio.Portfolio;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.Collections;
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
                "identifier TEXT, " +
                "amount INT)");

        safeExec(connection, "CREATE TABLE IF NOT EXISTS portfolios_log (" +
                "id INTEGER PRIMARY KEY, " +
                "uuid VARCHAR(36) NOT NULL, " +
                "day INT, " +
                "identifier TEXT, " +
                "amount INT, " +
                "contribution DOUBLE)");

        safeExec(connection, "CREATE TABLE IF NOT EXISTS portfolios_worth (" +
                "id INTEGER PRIMARY KEY, " +
                "uuid VARCHAR(36) NOT NULL, " +
                "day INT, " +
                "worth DOUBLE)");

        safeExec(connection, "CREATE TABLE IF NOT EXISTS capacities (" +
                "uuid VARCHAR(36) PRIMARY KEY, " +
                "capacity INT)");

        safeExec(connection, "CREATE TABLE IF NOT EXISTS discord_links (" +
                "userid VARCHAR(18) NOT NULL, " +
                "uuid VARCHAR(36) NOT NULL, " +
                "nickname TEXT NOT NULL)");

        safeExec(connection, "CREATE TABLE IF NOT EXISTS trade_log (" +
                "id INTEGER PRIMARY KEY, " +
                "uuid VARCHAR(36) NOT NULL, " +
                "day INT NOT NULL, " +
                "date TEXT NOT NULL, " +
                "identifier TEXT NOT NULL, " +
                "amount INT NOT NULL, " +
                "value TEXT NOT NULL, " +
                "buy INT NOT NULL, " +
                "discord INT NOT NULL)");

        safeExec(connection, "CREATE TABLE IF NOT EXISTS cpi (" +
                "day INT NOT NULL, " +
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
                "operations INT NOT NULL, " +
                "UNIQUE(day))");

        safeExec(connection, "CREATE TABLE IF NOT EXISTS limit_orders (" +
                "id INTEGER PRIMARY KEY, " +
                "expiration TEXT NOT NULL, " +
                "uuid VARCHAR(36) NOT NULL, " +
                "identifier TEXT NOT NULL, " +
                "type INT NOT NULL, " +
                "price DOUBLE NOT NULL, " +
                "to_complete INT NOT NULL, " +
                "completed INT NOT NULL, " +
                "cost INT NOT NULL)");

        safeExec(connection, "CREATE TABLE IF NOT EXISTS loans (" +
                "id INTEGER PRIMARY KEY, " +
                "uuid VARCHAR(36) NOT NULL, " +
                "debt DOUBLE NOT NULL)");

        safeExec(connection, "CREATE TABLE IF NOT EXISTS interests (" +
                "id INTEGER PRIMARY KEY, " +
                "uuid VARCHAR(36) NOT NULL, " +
                "paid DOUBLE NOT NULL)");

        safeExec(connection, "CREATE TABLE IF NOT EXISTS user_names (" +
                "id INTEGER PRIMARY KEY, " +
                "uuid VARCHAR(36) NOT NULL, " +
                "name TEXT NOT NULL)");

        safeExec(connection, "CREATE TABLE IF NOT EXISTS balances (" +
                "id INTEGER PRIMARY KEY, " +
                "uuid VARCHAR(36) NOT NULL, " +
                "balance DOUBLE NOT NULL)");

        safeExec(connection, "CREATE TABLE IF NOT EXISTS money_supply (" +
                "day INT PRIMARY KEY, " +
                "supply DOUBLE NOT NULL)");

        safeExec(connection, "CREATE TABLE IF NOT EXISTS web_credentials (" +
                "name TEXT NOT NULL, " +
                "pass TEXT NOT NULL)");

        safeExec(connection, "CREATE TABLE IF NOT EXISTS player_stats (" +
                "day INT NOT NULL, " +
                "uuid VARCHAR(36) NOT NULL, " +
                "balance DOUBLE NOT NULL, " +
                "portfolio DOUBLE NOT NULL, " +
                "debt DOUBLE NOT NULL)");

        safeExec(connection, "CREATE TABLE IF NOT EXISTS discord (" +
                "userid VARCHAR(18) NOT NULL, " +
                "uuid VARCHAR(36) NOT NULL, " +
                "nickname TEXT NOT NULL)");
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
            try (Statement s = conn.createStatement()) {
                s.executeUpdate("DELETE FROM trade_log WHERE day < " + (NormalisedDate.getDays() - 90));
                s.executeUpdate("DELETE FROM prices_day WHERE bucket_start < '"
                        + java.time.Instant.now().minusSeconds(2L * 86400).toString() + "'");
                s.executeUpdate("DELETE FROM prices_month WHERE bucket_start < '"
                        + java.time.Instant.now().minusSeconds(31L * 86400).toString() + "'");
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
        return withConnection(c -> DiscordLink.getUUID(c, userId), null);
    }

    @Override
    public String getNickname(String userId) {
        return withConnection(c -> DiscordLink.getNickname(c, userId), null);
    }

    @Override
    public String getUserId(UUID uuid) {
        return withConnection(c -> DiscordLink.getUserId(c, uuid), null);
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
        return withConnection(c -> HistorialData.getDayPrices(c, item), Collections.emptyList());
    }

    @Override
    public List<Instant> getMonthPrices(Item item) {
        return withConnection(c -> HistorialData.getMonthPrices(c, item), Collections.emptyList());
    }

    @Override
    public List<Instant> getYearPrices(Item item) {
        return withConnection(c -> HistorialData.getYearPrices(c, item), Collections.emptyList());
    }

    @Override
    public List<Instant> getAllPrices(Item item) {
        return withConnection(c -> HistorialData.getAllPrices(c, item), Collections.emptyList());
    }

    @Override
    public Double getPriceOfDay(String identifier, int day) {
        return withConnection(c -> HistorialData.getPriceOfDay(c, identifier, day), 0.0);
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
        return withConnection(c -> ItemProperties.retrieveLastPrice(c, item), 0f);
    }

    @Override
    public void saveTrade(Trade trade) {
        withConnection(c -> TradesLog.saveTrade(c, trade));
    }

    @Override
    public List<Trade> retrieveTrades(UUID uuid, int offset, int limit) {
        return withConnection(c -> TradesLog.retrieveTrades(c, uuid, offset, limit), Collections.emptyList());
    }

    @Override
    public List<Trade> retrieveTrades(UUID uuid, Item item, int offset, int limit) {
        return withConnection(c -> TradesLog.retrieveTrades(c, uuid, item, offset, limit), Collections.emptyList());
    }

    @Override
    public List<Trade> retrieveTrades(Item item, int offset, int limit) {
        return withConnection(c -> TradesLog.retrieveTrades(c, item, offset, limit), Collections.emptyList());
    }

    @Override
    public List<Trade> retrieveTrades(int offset, int limit) {
        return withConnection(c -> TradesLog.retrieveLastTrades(c, offset, limit), Collections.emptyList());
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
        return withConnection(c -> Portfolios.retrievePortfolio(c, uuid), new LinkedHashMap<>());
    }

    @Override
    public int retrieveCapacity(UUID uuid) {
        return withConnection(c -> Portfolios.retrieveCapacity(c, uuid), 0);
    }

    @Override
    public void increaseDebt(UUID uuid, Double debt) {
        withConnection(c -> Debt.increaseDebt(c, uuid, debt));
    }

    @Override
    public void decreaseDebt(UUID uuid, Double debt) {
        withConnection(c -> Debt.decreaseDebt(c, uuid, debt));
    }

    @Override
    public double getDebt(UUID uuid) {
        return withConnection(c -> Debt.getDebt(c, uuid), 0d);
    }

    @Override
    public HashMap<UUID, Double> getUUIDAndDebt() {
        return withConnection(Debt::getUUIDAndDebt, null);
    }

    @Override
    public void addInterestPaid(UUID uuid, Double interest) {
        withConnection(c -> Debt.addInterestPaid(c, uuid, interest));
    }

    @Override
    public HashMap<UUID, Double> getUUIDAndInterestsPaid() {
        return withConnection(Debt::getUUIDAndInterestsPaid, null);
    }

    @Override
    public double getInterestsPaid(UUID uuid) {
        return withConnection(c -> Debt.getInterestsPaid(c, uuid), 0d);
    }

    @Override
    public double getAllOutstandingDebt() {
        return withConnection(Debt::getAllOutstandingDebt, 0d);
    }

    @Override
    public double getAllInterestsPaid() {
        return withConnection(Debt::getAllInterestsPaid, 0d);
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
        return withConnection(c -> PortfoliosWorth.getTopWorth(c, n), null);
    }

    @Override
    public double getLatestWorth(UUID uuid) {
        return withConnection(c -> PortfoliosWorth.getLatestWorth(c, uuid), 0d);
    }

    @Override
    public void logContribution(UUID uuid, Item item, int amount) {
        withConnection(c -> PortfoliosLog.logContribution(c, uuid, item, amount));
    }

    @Override
    public void logWithdraw(UUID uuid, Item item, int amount) {
        withConnection(c -> PortfoliosLog.logWithdraw(c, uuid, item, amount));
    }

    @Override
    public HashMap<Integer, Double> getContributionChangeEachDay(UUID uuid) {
        return withConnection(c -> PortfoliosLog.getContributionChangeEachDay(c, uuid), null);
    }

    @Override
    public HashMap<Integer, HashMap<String, Integer>> getCompositionEachDay(UUID uuid) {
        return withConnection(c -> PortfoliosLog.getCompositionEachDay(c, uuid), null);
    }

    @Override
    public int getFirstDay(UUID uuid) {
        return withConnection(c -> PortfoliosLog.getFirstDay(c, uuid), NormalisedDate.getDays());
    }

    @Override
    public void saveCPIValue(float indexValue) {
        withConnection(c -> Statistics.saveCPI(c, indexValue));
    }

    @Override
    public List<CPIInstant> getCPIHistory() {
        return withConnection(Statistics::getAllCPI, Collections.emptyList());
    }

    @Override
    public List<Instant> getPriceAgainstCPI(Item item) {
        return withConnection(c -> Statistics.getPriceAgainstCPI(c, item), Collections.emptyList());
    }

    @Override
    public void addTransaction(double newFlow, double effectiveTaxes) {
        withConnection(c -> Statistics.addTransaction(c, newFlow, effectiveTaxes));
    }

    @Override
    public List<DayInfo> getDayInfos() {
        return withConnection(Statistics::getDayInfos, Collections.emptyList());
    }

    @Override
    public double getAllTaxesCollected() {
        return withConnection(Statistics::getAllTaxesCollected, 0d);
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
        return withConnection(c -> UserNames.getNameByUUID(c, uuid), " ");
    }

    @Override
    public void saveOrUpdateName(UUID uuid, String name) {
        withConnection(c -> UserNames.saveOrUpdateNick(c, uuid, name));
    }

    @Override
    public String getUUIDbyName(String name) {
        return withConnection(c -> UserNames.getUUIDbyName(c, name), null);
    }

    @Override
    public void updateBalance(UUID uuid) {
        withConnection(c -> Balances.updateBalance(c, uuid));
    }

    @Override
    public Map<Integer, Double> getMoneySupplyHistory() {
        return withConnection(Balances::getMoneySupplyHistory, Collections.emptyMap());
    }

    @Override
    public void storeCredentials(String userName, String hash) {
        withConnection(c -> Credentials.saveCredentials(c, userName, hash));
    }

    @Override
    public String retrieveHash(String userName) {
        return withConnection(c -> Credentials.getHashFromUserName(c, userName), null);
    }

    @Override
    public void clearUserCredentials(String userName) {
        withConnection(c -> Credentials.clearUserCredentials(c, userName));
    }

    @Override
    public void saveOrUpdatePlayerStats(UUID uuid) {
        withConnection(c -> PlayerStats.saveOrUpdatePlayerStats(c, uuid));
    }

    @Override
    public List<PlayerStatsDTO> getAllPlayerStats(UUID uuid) {
        return withConnection(c -> PlayerStats.getAllPlayerStats(c, uuid), Collections.emptyList());
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
        return withConnection(c -> Discord.getDiscordUserId(c, uuid), null);
    }

    @Override
    public UUID getUUIDFromUserid(String userid) {
        return withConnection(c -> Discord.getUUIDFromUserid(c, userid), null);
    }

    @Override
    public String getNicknameFromUserId(String userid) {
        return withConnection(c -> Discord.getNicknameFromUserId(c, userid), null);
    }
}
