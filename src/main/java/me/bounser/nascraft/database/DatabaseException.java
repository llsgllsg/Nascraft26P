package me.bounser.nascraft.database;

import java.sql.SQLException;

public class DatabaseException extends RuntimeException {

    public DatabaseException(String message, SQLException cause) {
        super(message, cause);
    }

    public DatabaseException(SQLException cause) {
        super(cause.getMessage(), cause);
    }
}
