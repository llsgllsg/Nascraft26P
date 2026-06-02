package me.bounser.nascraft.database.sqlite;

import me.bounser.nascraft.database.SqlDialect;

public final class SqliteDialect implements SqlDialect {

    @Override
    public String inserted(String column) { return "excluded." + column; }

    @Override
    public String greatest(String a, String b) { return "max(" + a + ", " + b + ")"; }

    @Override
    public String least(String a, String b) { return "min(" + a + ", " + b + ")"; }

    @Override
    public String onConflictUpdate(String conflictColumns) {
        return " ON CONFLICT(" + conflictColumns + ") DO UPDATE SET ";
    }

    @Override
    public String insertIgnoreInto() { return "INSERT OR IGNORE INTO"; }

    @Override
    public String replaceInto() { return "INSERT OR REPLACE INTO"; }
}
