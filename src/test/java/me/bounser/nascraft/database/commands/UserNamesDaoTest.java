package me.bounser.nascraft.database.commands;

import me.bounser.nascraft.support.DatabaseTest;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class UserNamesDaoTest extends DatabaseTest {

    @Test
    void saveAndRetrieve_roundTripsPlayerName() {
        UUID uuid = UUID.randomUUID();

        UserNames.saveOrUpdateNick(connection, uuid, "Bounser");

        assertEquals("Bounser", UserNames.getNameByUUID(connection, uuid));
    }

    @Test
    void saveOrUpdateNick_overwritesExistingName() {
        UUID uuid = UUID.randomUUID();

        UserNames.saveOrUpdateNick(connection, uuid, "OldName");
        UserNames.saveOrUpdateNick(connection, uuid, "NewName");

        assertEquals("NewName", UserNames.getNameByUUID(connection, uuid));
    }

    @Test
    void getNameByUUID_returnsNullForUnknownPlayer() {
        assertNull(UserNames.getNameByUUID(connection, UUID.randomUUID()));
    }

    @Test
    void getUUIDbyName_roundTripsUUID() {
        UUID uuid = UUID.randomUUID();
        UserNames.saveOrUpdateNick(connection, uuid, "PlayerX");

        assertEquals(uuid.toString(), UserNames.getUUIDbyName(connection, "PlayerX"));
    }

    @Test
    void getUUIDbyName_returnsNullForUnknownName() {
        assertNull(UserNames.getUUIDbyName(connection, "Ghost"));
    }
}
