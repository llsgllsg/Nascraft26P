package me.bounser.nascraft.premium;

import me.bounser.nascraft.Nascraft;

/**
 * Stand-in for the proprietary premium loader of Nascraft.
 *
 * <p>The paid premium module is not part of this distribution, so every
 * hook is a no-op. Replacing this class with the real implementation is all
 * that is needed to enable premium features.</p>
 */
public final class PremiumLoader {

    private PremiumLoader() {
    }

    public static void enable(Nascraft plugin) {
        // No-op: premium module not bundled.
    }

    public static void disable() {
        // No-op: premium module not bundled.
    }
}
