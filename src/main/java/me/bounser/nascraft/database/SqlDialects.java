package me.bounser.nascraft.database;

import me.bounser.nascraft.database.sqlite.SqliteDialect;

public final class SqlDialects {

    private static volatile SqlDialect current = new SqliteDialect();

    private SqlDialects() {}

    public static SqlDialect current() { return current; }

    public static void set(SqlDialect dialect) { current = dialect; }
}
