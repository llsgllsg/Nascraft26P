package me.bounser.nascraft.inventorygui.Portfolio;

import me.bounser.nascraft.inventorygui.MenuPage;
import me.bounser.nascraft.portfolio.Portfolio;
import me.bounser.nascraft.util.ServerCompat;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.logging.Level;

/**
 * Entry point for the portfolio composition window.
 *
 * <p>Only the construction is version dependent: the actual window is built by
 * {@link InfoPortfolioInvui1} (MC 1.21.11) or {@link InfoPortfolioInvui2}
 * (Paper 26.x). Call sites keep using
 * {@code new InfoPortfolio(portfolio, player)}.</p>
 */
public class InfoPortfolio implements MenuPage {

    private final MenuPage delegate;

    public InfoPortfolio(Portfolio portfolio, Player player) {
        this.delegate = create(portfolio, player);
    }

    private static MenuPage create(Portfolio portfolio, Player player) {
        if (ServerCompat.isInvui2()) {
            try {
                return new InfoPortfolioInvui2(portfolio, player);
            } catch (LinkageError | RuntimeException e) {
                Bukkit.getLogger().log(Level.SEVERE,
                        "[Nascraft] Failed to open the portfolio menu with the invui 2.x backend " +
                                "on " + Bukkit.getBukkitVersion() + " (Java " +
                                System.getProperty("java.version") + ")", e);
                throw e;
            }
        }
        return new InfoPortfolioInvui1(portfolio, player);
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
