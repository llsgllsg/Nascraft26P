package me.bounser.nascraft.database.commands;

import me.bounser.nascraft.support.DatabaseTest;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class UserNamesDaoTest extends DatabaseTest {

    @Test
    void saveAndRetrieve_roundTripsPlayerName() throws java.sql.SQLException {
        UUID uuid = UUID.randomUUID();

        UserNames.saveOrUpdateNick(connection, uuid, "Bounser");

        assertEquals("Bounser", UserNames.getNameByUUID(connection, uuid));
    }

    @Test
    void saveOrUpdateNick_overwritesExistingName() throws java.sql.SQLException {
        UUID uuid = UUID.randomUUID();

        UserNames.saveOrUpdateNick(connection, uuid, "OldName");
        UserNames.saveOrUpdateNick(connection, uuid, "NewName");

        assertEquals("NewName", UserNames.getNameByUUID(connection, uuid));
    }

    @Test
    void getNameByUUID_returnsNullForUnknownPlayer() throws java.sql.SQLException {
        assertNull(UserNames.getNameByUUID(connection, UUID.randomUUID()));
    }

    @Test
    void getUUIDbyName_roundTripsUUID() throws java.sql.SQLException {
        UUID uuid = UUID.randomUUID();
        UserNames.saveOrUpdateNick(connection, uuid, "PlayerX");

        assertEquals(uuid.toString(), UserNames.getUUIDbyName(connection, "PlayerX"));
    }

    @Test
    void getUUIDbyName_returnsNullForUnknownName() throws java.sql.SQLException {
        assertNull(UserNames.getUUIDbyName(connection, "Ghost"));
    }
}
