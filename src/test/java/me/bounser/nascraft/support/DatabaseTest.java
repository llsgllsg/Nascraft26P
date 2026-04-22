package me.bounser.nascraft.support;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.sql.Connection;
import java.sql.SQLException;

public abstract class DatabaseTest {

    protected Connection connection;

    @BeforeEach
    void openDatabase() throws SQLException {
        connection = InMemoryDatabase.openWithSchema();
    }

    @AfterEach
    void closeDatabase() throws SQLException {
        if (connection != null) {
            connection.close();
        }
    }
}
