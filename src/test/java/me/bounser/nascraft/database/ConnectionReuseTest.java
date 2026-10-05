package me.bounser.nascraft.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import me.bounser.nascraft.portfolio.Portfolio;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.objenesis.ObjenesisStd;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 回归测试：SQLite 的连接池只有 1 条连接（见 SqliteDatabase#configureHikari），
 * 所以「在 queryConnection/withConnection 里面再要一次连接」会自己把自己锁死。
 *
 * <p>真实故障现场（Purpur 26.2 + Nascraft 26.3.1）：
 * <pre>
 *   BaseDatabase.getTopWorth                 ← 外层拿走池里唯一的连接
 *     PortfoliosWorth.getTopWorth
 *       PortfoliosManager.getPortfolio       ← 该组合不在缓存里
 *         new Portfolio(...) -> retrievePortfolio
 *           BaseDatabase.queryConnection     ← 再要一条连接 → 等 30 秒超时
 * </pre>
 * 这段栈跑在服务器主线程（InventoryClickEvent），于是整个服务器卡死 30 秒，
 * 最后抛出 SQLTransientConnectionException: Connection is not available。
 *
 * <p>第一组测试直接复现嵌套结构；第二组走真实的 getTopWorth 调用链。
 * 修复前它们会因连接超时而失败，修复后应当秒过。
 */
class ConnectionReuseTest {

    /** 只保留连接管理逻辑，绕开建表和迁移，避免依赖插件运行时环境。 */
    private static final class TestDatabase extends BaseDatabase {
        TestDatabase(HikariDataSource dataSource) {
            this.dataSource = dataSource;
        }

        @Override
        protected void configureHikari(HikariConfig config) {
            // 测试里自己建池，不走这里
        }

        @Override
        protected void onConnectionInit(Connection connection) {
        }

        @Override
        protected void runMigrations(Connection connection) {
        }

        @Override
        public void createTables() {
        }
    }

    /** 与 SqliteDatabase 完全一致的池配置：只有 1 条连接，超时 30 秒。 */
    private static TestDatabase newDatabase(Path dir) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:" + dir.resolve("reuse-test.db").toAbsolutePath()
                + "?journal_mode=WAL");
        config.setDriverClassName("org.sqlite.JDBC");
        config.setMaximumPoolSize(1);
        config.setConnectionTimeout(30_000);
        config.setPoolName("Nascraft-Test");
        return new TestDatabase(new HikariDataSource(config));
    }

    /**
     * 把测试用的数据库装进 DatabaseManager 单例，让 Portfolio 构造时查到它。
     *
     * <p>这里刻意不用 Mockito 的 mockStatic：静态 mock 需要动态挂 java agent，
     * 在 GitHub Actions 上会以 MockitoException / IllegalArgumentException 失败，
     * 而本地却可能是好的。Objenesis 绕过构造函数直接造实例，再反射填两个私有字段，
     * 行为完全确定，不需要任何 agent 或字节码增强。
     */
    private static void installDatabaseManager(Database database) throws Exception {
        DatabaseManager manager = new ObjenesisStd().newInstance(DatabaseManager.class);

        Field databaseField = DatabaseManager.class.getDeclaredField("database");
        databaseField.setAccessible(true);
        databaseField.set(manager, database);

        Field instanceField = DatabaseManager.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        instanceField.set(null, manager);
    }

    // ------------------------------------------------------------------
    // 1. 直接验证连接复用
    // ------------------------------------------------------------------

    @Test
    @Timeout(value = 20, unit = TimeUnit.SECONDS)
    void nestedQueryConnectionReusesTheOuterConnection(@TempDir Path dir) {
        TestDatabase db = newDatabase(dir);
        try {
            AtomicReference<Connection> outer = new AtomicReference<>();

            String result = db.queryConnection(outerConnection -> {
                outer.set(outerConnection);
                // 这正是 getTopWorth 里发生的事：外层还握着连接时再查一次库
                return db.queryConnection(innerConnection -> {
                    assertSame(outerConnection, innerConnection,
                            "嵌套的 queryConnection 必须复用外层连接，不能向只有 1 条连接的池再申请");
                    return "ok";
                });
            });

            assertEquals("ok", result);
            assertNotNull(outer.get());
        } finally {
            db.close();
        }
    }

    @Test
    @Timeout(value = 20, unit = TimeUnit.SECONDS)
    void withConnectionNestedInsideQueryConnectionReusesConnection(@TempDir Path dir) {
        TestDatabase db = newDatabase(dir);
        try {
            db.queryConnection(outerConnection -> {
                db.withConnection(innerConnection -> assertSame(outerConnection, innerConnection,
                        "queryConnection 里调 withConnection 也必须复用连接"));
                return null;
            });
        } finally {
            db.close();
        }
    }

    @Test
    @Timeout(value = 20, unit = TimeUnit.SECONDS)
    void nestedQueryTransactionJoinsTheOuterOne(@TempDir Path dir) {
        TestDatabase db = newDatabase(dir);
        try {
            String result = db.queryTransaction(outerConnection ->
                    // 事务里再开一层：并入外层，不重复提交/回滚
                    db.queryTransaction(innerConnection -> {
                        assertSame(outerConnection, innerConnection,
                                "嵌套事务必须并入外层事务，复用同一条连接");
                        return "ok";
                    }));
            assertEquals("ok", result);
        } finally {
            db.close();
        }
    }

    @Test
    @Timeout(value = 20, unit = TimeUnit.SECONDS)
    void outerConnectionIsReleasedAfterUse(@TempDir Path dir) {
        TestDatabase db = newDatabase(dir);
        try {
            // 用完之后必须归还，否则第二次顶层调用也会卡住
            db.queryConnection(Connection::getAutoCommit);
            db.queryConnection(Connection::getAutoCommit);
            db.queryConnection(Connection::getAutoCommit);
        } finally {
            db.close();
        }
    }

    // ------------------------------------------------------------------
    // 2. 走真实的 getTopWorth 调用链（玩家打开「资产榜单」时的路径）
    // ------------------------------------------------------------------

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void getTopWorthDoesNotDeadlockWhenPortfolioIsNotCached(@TempDir Path dir) throws Exception {
        TestDatabase db = newDatabase(dir);
        UUID uuid = UUID.randomUUID();

        db.withConnection(c -> {
            try (Statement s = c.createStatement()) {
                s.execute("CREATE TABLE portfolios_worth (id INTEGER PRIMARY KEY, uuid VARCHAR(36) NOT NULL, "
                        + "day INT NOT NULL, worth DOUBLE NOT NULL, UNIQUE (uuid, day))");
                s.execute("CREATE TABLE portfolios (uuid VARCHAR(36) NOT NULL, identifier TEXT NOT NULL, "
                        + "amount INT NOT NULL DEFAULT 0, PRIMARY KEY (uuid, identifier))");
                s.execute("CREATE TABLE capacities (uuid VARCHAR(36) PRIMARY KEY, capacity INT NOT NULL)");
            }
        });
        db.withConnection(c -> {
            try (PreparedStatement p = c.prepareStatement(
                    "INSERT INTO portfolios_worth (uuid, day, worth) VALUES (?, ?, ?)")) {
                p.setString(1, uuid.toString());
                p.setInt(2, 1);
                p.setDouble(3, 123.0);
                p.executeUpdate();
            }
            // 有容量记录，Portfolio 构造就不会走到需要 Bukkit Config 的默认值分支
            try (PreparedStatement p = c.prepareStatement(
                    "INSERT INTO capacities (uuid, capacity) VALUES (?, ?)")) {
                p.setString(1, uuid.toString());
                p.setInt(2, 5);
                p.executeUpdate();
            }
        });

        installDatabaseManager(db);
        try {
            // 修复前：这里会卡满 30 秒连接超时，再抛 SQLTransientConnectionException
            HashMap<UUID, Portfolio> top = db.getTopWorth(10);

            assertTrue(top.containsKey(uuid), "应返回刚写入的那个组合");
            // Portfolio 构造里还要查一次 capacities，能走到这里说明嵌套查询也成功了
            assertEquals(5, top.get(uuid).getCapacity());
        } finally {
            db.close();
        }
    }
}
