package me.bounser.nascraft.database;

public interface SqlDialect {

    /**
     * <li>SQLite: {@code excluded.<column>}</li>
     * <li>MySQL:  {@code VALUES(<column>)}</li>
     */
    String inserted(String column);

    /** Scalar maximum of two SQL expressions ({@code max} vs {@code GREATEST}). */
    String greatest(String a, String b);

    /** Scalar minimum of two SQL expressions ({@code min} vs {@code LEAST}). */
    String least(String a, String b);

    /**
     * SQLite: {@code  ON CONFLICT(<cols>) DO UPDATE SET }
     * MySQL:  {@code  ON DUPLICATE KEY UPDATE } (the columns are implicit
     */
    String onConflictUpdate(String conflictColumns);

    /** Insert-or-skip-on-duplicate prefix: {@code INSERT OR IGNORE INTO} / {@code INSERT IGNORE INTO}. */
    String insertIgnoreInto();

    /** Insert-or-overwrite prefix: {@code INSERT OR REPLACE INTO} / {@code REPLACE INTO}. */
    String replaceInto();
}
