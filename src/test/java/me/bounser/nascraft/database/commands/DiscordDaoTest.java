package me.bounser.nascraft.database.commands;

import me.bounser.nascraft.support.DatabaseTest;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DiscordDaoTest extends DatabaseTest {

    private static final String USER_ID = "987654321098765432";
    private static final String NICKNAME = "Player#4242";

    @Test
    void saveDiscordLink_roundTripsAllFields() throws java.sql.SQLException {
        UUID uuid = UUID.randomUUID();

        Discord.saveDiscordLink(connection, uuid, USER_ID, NICKNAME);

        assertEquals(uuid, Discord.getUUIDFromUserid(connection, USER_ID));
        assertEquals(USER_ID, Discord.getDiscordUserId(connection, uuid));
        assertEquals(NICKNAME, Discord.getNicknameFromUserId(connection, USER_ID));
    }

    @Test
    void removeLink_clearsExistingEntry() throws java.sql.SQLException {
        UUID uuid = UUID.randomUUID();
        Discord.saveDiscordLink(connection, uuid, USER_ID, NICKNAME);

        Discord.removeLink(connection, uuid);

        assertNull(Discord.getDiscordUserId(connection, uuid));
        assertNull(Discord.getUUIDFromUserid(connection, USER_ID));
    }

    @Test
    void getDiscordUserId_returnsNullForUnlinkedPlayer() throws java.sql.SQLException {
        assertNull(Discord.getDiscordUserId(connection, UUID.randomUUID()));
    }

    @Test
    void getUUIDFromUserid_returnsNullForUnknownUser() throws java.sql.SQLException {
        assertNull(Discord.getUUIDFromUserid(connection, "000000000000000000"));
    }

    @Test
    void getNicknameFromUserId_returnsNullForUnknownUser() throws java.sql.SQLException {
        assertNull(Discord.getNicknameFromUserId(connection, "000000000000000000"));
    }
}
