package me.bounser.nascraft.util;

import org.bukkit.Bukkit;

/**
 * Runtime detection of which invui generation the server needs.
 *
 * <p>Nascraft ships one jar for MC 1.21.11 and for Paper 26.1+. The two
 * generations of the GUI library are not source compatible, so the plugin
 * carries both backends and picks one at runtime:</p>
 *
 * <ul>
 *   <li>MC 1.21.x / Java 21 → invui 1.x ({@code xyz.xenondevs.invui.*});</li>
 *   <li>Paper 26.x / Java 25 → invui 2.x, pre-relocated to
 *       {@code me.bounser.libs.invui2.xyz.xenondevs.invui.*} so both copies can
 *       live in the same jar without package collisions.</li>
 * </ul>
 *
 * <p>The switch keys off the Minecraft version because Paper 26.x renamed a
 * CraftBukkit method that invui 1.x calls, while invui 2.x needs the Paper 26
 * internals. Paper's version scheme changed at that point: {@code 1.21.11} is
 * Java 21 and everything from {@code 26.1} on is Java 25.</p>
 */
public final class ServerCompat {

    private static final boolean INVUI2 = detectInvui2();

    private ServerCompat() {}

    /** @return {@code true} when the server must use the invui 2.x backend. */
    public static boolean isInvui2() {
        return INVUI2;
    }

    /** @return a short human readable reason for the selected backend (used in logs). */
    public static String describeBackend() {
        return "invui " + (INVUI2 ? "2.x (MC " + mcVersionFamily() + ")" : "1.x (MC " + mcVersionFamily() + ")");
    }

    private static String mcVersionFamily() {
        String raw = Bukkit.getBukkitVersion();
        return raw == null ? "unknown" : raw;
    }

    private static boolean detectInvui2() {
        try {
            String raw = Bukkit.getBukkitVersion();
            if (raw == null || raw.isEmpty()) {
                return false;
            }
            // "1.21.11-R0.1-SNAPSHOT" / "26.2-R0.1-SNAPSHOT" / "26.1.2-..."
            String numeric = raw.split("-", 2)[0];
            String[] parts = numeric.split("\\.");
            if (parts.length < 2) {
                return false;
            }
            int major = Integer.parseInt(parts[0]);
            if (major == 1) {
                // Everything up to and including 1.21.11 runs on the invui 1.x backend.
                return false;
            }
            // Paper switched to the 26.x versioning scheme, which also requires Java 25
            // and the invui 2.x API. Any other unknown major is treated as 26.x-like.
            return major >= 26;
        } catch (RuntimeException e) {
            // Never let version sniffing break plugin startup; the invui 1.x path is the
            // one that works on the widest range of old servers.
            return false;
        }
    }
}
