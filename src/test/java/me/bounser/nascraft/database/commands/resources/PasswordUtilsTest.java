package me.bounser.nascraft.database.commands.resources;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordUtilsTest {

    @Test
    void hashPassword_producesNonNullBcryptHash() {
        String hash = PasswordUtils.hashPassword("correct horse battery staple");

        assertNotNull(hash);
        assertTrue(hash.startsWith("$2a$") || hash.startsWith("$2b$") || hash.startsWith("$2y$"),
            "hash should be in bcrypt format, was: " + hash);
    }

    @Test
    void hashPassword_producesDifferentHashesForSamePasswordDueToSalting() {
        String hashA = PasswordUtils.hashPassword("same-password");
        String hashB = PasswordUtils.hashPassword("same-password");

        assertNotEquals(hashA, hashB);
    }

    @Test
    void checkPassword_returnsTrueForMatchingPlaintext() {
        String hash = PasswordUtils.hashPassword("s3cret");

        assertTrue(PasswordUtils.checkPassword("s3cret", hash));
    }

    @Test
    void checkPassword_returnsFalseForMismatchedPlaintext() {
        String hash = PasswordUtils.hashPassword("s3cret");

        assertFalse(PasswordUtils.checkPassword("guess", hash));
    }

    @Test
    void checkPassword_returnsFalseForNullOrEmptyInputs() {
        String hash = PasswordUtils.hashPassword("s3cret");

        assertFalse(PasswordUtils.checkPassword(null, hash));
        assertFalse(PasswordUtils.checkPassword("", hash));
        assertFalse(PasswordUtils.checkPassword("s3cret", null));
        assertFalse(PasswordUtils.checkPassword("s3cret", ""));
    }

    @Test
    void hashPassword_rejectsNullOrEmptyInput() {
        assertThrows(IllegalArgumentException.class, () -> PasswordUtils.hashPassword(null));
        assertThrows(IllegalArgumentException.class, () -> PasswordUtils.hashPassword(""));
    }
}
