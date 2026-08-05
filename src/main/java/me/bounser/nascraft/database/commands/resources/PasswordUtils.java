package me.bounser.nascraft.database.commands.resources;

import org.mindrot.jbcrypt.BCrypt;

/**
 * Small bcrypt wrapper used for credential hashing.
 */
public final class PasswordUtils {

    private PasswordUtils() {
    }

    /**
     * Hashes the given plaintext password with a fresh salt.
     *
     * @param password plaintext password, must not be null or empty
     * @return bcrypt hash (prefixed with {@code $2a$/$2b$/$2y$})
     * @throws IllegalArgumentException if the password is null or empty
     */
    public static String hashPassword(String password) {
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("Password must not be null or empty");
        }
        return BCrypt.hashpw(password, BCrypt.gensalt());
    }

    /**
     * Verifies a plaintext password against a bcrypt hash.
     *
     * @param password plaintext password to check
     * @param hash     bcrypt hash to verify against
     * @return true if the password matches, false on any mismatch or malformed input
     */
    public static boolean checkPassword(String password, String hash) {
        if (password == null || password.isEmpty() || hash == null || hash.isEmpty()) {
            return false;
        }
        try {
            return BCrypt.checkpw(password, hash);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
