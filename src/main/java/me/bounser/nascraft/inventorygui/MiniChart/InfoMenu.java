package me.bounser.nascraft.inventorygui.MiniChart;

import me.bounser.nascraft.inventorygui.MenuPage;
import me.bounser.nascraft.market.unit.Item;
import me.bounser.nascraft.util.ServerCompat;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.logging.Level;

/**
 * Entry point for the item chart window.
 *
 * <p>Only the construction is version dependent: the heavy lifting lives in
 * {@link InfoMenuInvui1} (MC 1.21.11) and {@link InfoMenuInvui2} (Paper 26.x).
 * Call sites keep using {@code new InfoMenu(player, item)} and never see which
 * GUI backend is active.</p>
 */
public class InfoMenu implements MenuPage {

    private final MenuPage delegate;

    public InfoMenu(Player player, Item item) {
        this.delegate = create(player, item);
    }

    private static MenuPage create(Player player, Item item) {
        if (ServerCompat.isInvui2()) {
            try {
                return new InfoMenuInvui2(player, item);
            } catch (LinkageError | RuntimeException e) {
                // A broken invui 2.x backend on a 26.x server: make the failure obvious
                // instead of dying with a bare NoClassDefFoundError.
                Bukkit.getLogger().log(Level.SEVERE,
                        "[Nascraft] Failed to open the item chart menu with the invui 2.x backend " +
                                "on " + Bukkit.getBukkitVersion() + " (Java " +
                                System.getProperty("java.version") + ")", e);
                throw e;
            }
        }
        return new InfoMenuInvui1(player, item);
    }

    @Override
    public void open() {
        delegate.open();
    }

    @Override
    public void close() {
        delegate.close();
    }

    @Override
    public void update() {
        delegate.update();
    }
}
