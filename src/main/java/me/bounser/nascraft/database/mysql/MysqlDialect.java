package me.bounser.nascraft.database.mysql;

import me.bounser.nascraft.database.SqlDialect;

public final class MysqlDialect implements SqlDialect {

    @Override
    public String inserted(String column) { return "VALUES(" + column + ")"; }

    @Override
    public String greatest(String a, String b) { return "GREATEST(" + a + ", " + b + ")"; }

    @Override
    public String least(String a, String b) { return "LEAST(" + a + ", " + b + ")"; }

    @Override
    public String onConflictUpdate(String conflictColumns) {
        return " ON DUPLICATE KEY UPDATE ";
    }

    @Override
    public String insertIgnoreInto() { return "INSERT IGNORE INTO"; }

    @Override
    public String replaceInto() { return "REPLACE INTO"; }
}
