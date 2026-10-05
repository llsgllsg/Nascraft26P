package me.bounser.nascraft.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 回归测试：SQLite 的连接池只有 1 条连接（见 SqliteDatabase#configureHikari），
 * 所以「在 queryConnection/withConnection 里面再要一次连接」会自己把自己锁死。
 *
 * <p>真实故障现场（Purpur 26.2 + Nascraft 26.3.1）：
 * <pre>
 *   BaseDatabase.getTopWorth                 ← 外层拿走池里唯一的连接
 *     PortfoliosWorth.getTopWorth
 *       PortfoliosManager.getPortfolio       ← 该玩家组合不在缓存里
 *         new Portfolio(...) -> retrievePortfolio
 *           BaseDatabase.queryConnection     ← 再要一条连接 → 等 30 秒超时
 * </pre>
 * 这段栈跑在服务器主线程（InventoryClickEvent），于是整个服务器卡死 30 秒，
 * 最后抛 SQLTransientConnectionException: Connection is not available。
 *
 * <p>下面用一个自建的最小数据库复现同样的嵌套结构。修复前这些测试会因为
 * 连接超时而失败（或直接超时），修复后应当秒过。
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

    @Test
    @Timeout(value = 20, unit = TimeUnit.SECONDS)
    void nestedQueryConnectionReusesTheOuterConnection(@TempDir Path dir) {
        TestDatabase db = newDatabase(dir);
        try {
            AtomicReference<Connection> outer = new AtomicReference<>();

            String result = db.queryConnection(outerConnection -> {
                outer.set(outerConnection);
                // 这就是 getTopWorth 里发生的事：外层还握着连接时再查一次库
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
    void outerConnectionIsReleasedAfterUse(@TempDir Path dir) throws Exception {
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

    @Test
    @Timeout(value = 20, unit = TimeUnit.SECONDS)
    void nestedQueryTransactionJoinsTheOuterOne(@TempDir Path dir) {
        TestDatabase db = newDatabase(dir);
        try {
            String result = db.queryTransaction(outerConnection -> {
                // 事务里再开一层：并入外层，不重复提交/回滚
                return db.queryTransaction(innerConnection -> {
                    assertSame(outerConnection, innerConnection,
                            "嵌套事务必须并入外层事务，复用同一条连接");
                    return "ok";
                });
            });
            assertEquals("ok", result);
        } finally {
            db.close();
        }
    }
}
