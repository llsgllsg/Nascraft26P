package me.bounser.nascraft.database.commands;

import me.bounser.nascraft.support.DatabaseTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CredentialsDaoTest extends DatabaseTest {

    @Test
    void saveCredentials_persistsHashForLookup() {
        Credentials.saveCredentials(connection, "admin", "hash-abc");

        assertEquals("hash-abc", Credentials.getHashFromUserName(connection, "admin"));
    }

    @Test
    void getHashFromUserName_returnsNullForUnknownUser() {
        assertNull(Credentials.getHashFromUserName(connection, "missing"));
    }

    @Test
    void clearUserCredentials_removesHash() {
        Credentials.saveCredentials(connection, "admin", "hash-abc");
        Credentials.clearUserCredentials(connection, "admin");

        assertNull(Credentials.getHashFromUserName(connection, "admin"));
    }

    @Test
    void clearUserCredentials_doesNotAffectOtherUsers() {
        Credentials.saveCredentials(connection, "admin", "hash-abc");
        Credentials.saveCredentials(connection, "viewer", "hash-xyz");

        Credentials.clearUserCredentials(connection, "admin");

        assertEquals("hash-xyz", Credentials.getHashFromUserName(connection, "viewer"));
    }
}
