package me.bounser.nascraft.database.commands;

import me.bounser.nascraft.support.DatabaseTest;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DiscordLinkDaoTest extends DatabaseTest {

    private static final String USER_ID = "123456789012345678";
    private static final String NICKNAME = "Bounser#0001";

    @Test
    void saveLink_roundTripsAllFields() {
        UUID uuid = UUID.randomUUID();

        DiscordLink.saveLink(connection, USER_ID, uuid, NICKNAME);

        assertEquals(uuid, DiscordLink.getUUID(connection, USER_ID));
        assertEquals(NICKNAME, DiscordLink.getNickname(connection, USER_ID));
        assertEquals(USER_ID, DiscordLink.getUserId(connection, uuid));
    }

    @Test
    void removeLink_clearsExistingEntry() {
        UUID uuid = UUID.randomUUID();
        DiscordLink.saveLink(connection, USER_ID, uuid, NICKNAME);

        DiscordLink.removeLink(connection, USER_ID);

        assertNull(DiscordLink.getUUID(connection, USER_ID));
        assertNull(DiscordLink.getNickname(connection, USER_ID));
        assertNull(DiscordLink.getUserId(connection, uuid));
    }

    @Test
    void getUUID_returnsNullForUnknownUser() {
        assertNull(DiscordLink.getUUID(connection, "000000000000000000"));
    }

    @Test
    void getNickname_returnsNullForUnknownUser() {
        assertNull(DiscordLink.getNickname(connection, "000000000000000000"));
    }

    @Test
    void getUserId_returnsNullForUnlinkedPlayer() {
        assertNull(DiscordLink.getUserId(connection, UUID.randomUUID()));
    }
}
