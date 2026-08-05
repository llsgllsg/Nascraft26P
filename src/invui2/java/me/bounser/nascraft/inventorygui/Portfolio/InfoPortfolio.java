package me.bounser.nascraft.inventorygui.Portfolio;

import me.bounser.nascraft.Nascraft;
import me.bounser.nascraft.chart.portfolio.PortfolioCompositionChart;
import me.bounser.nascraft.chart.portfolio.PortfolioEvolutionChart;
import me.bounser.nascraft.config.lang.Lang;
import me.bounser.nascraft.config.lang.Message;
import me.bounser.nascraft.inventorygui.MenuPage;
import me.bounser.nascraft.portfolio.Portfolio;
import me.bounser.nascraft.scheduler.FoliaScheduler;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.map.MapPalette;
import org.bukkit.metadata.FixedMetadataValue;
import xyz.xenondevs.invui.gui.Gui;
import xyz.xenondevs.invui.gui.Structure;
import xyz.xenondevs.invui.window.CartographyWindow;

import java.awt.image.BufferedImage;

public class InfoPortfolio implements MenuPage {

    private Player player;
    private Portfolio portfolio;
    private ModeItem modeItem;

    public InfoPortfolio(Portfolio portfolio, Player player) {
        this.portfolio = portfolio;
        this.player = player;

        open();
    }

    @Override
    public void open() {
        Component title = MiniMessage.miniMessage().deserialize(Lang.get().message(Message.PORTFOLIO_COMPOSITION_TITLE));

        this.modeItem = new ModeItem(portfolio);
        PortfolioStatsItem stats = new PortfolioStatsItem(portfolio, player, modeItem);

        // Cartography input gui must be 1x2 -> one column, two rows.
        Structure structure = new Structure("I", "C")
                .addIngredient('I', modeItem)
                .addIngredient('C', stats);

        Gui gui = Gui.of(structure);

        CartographyWindow window = CartographyWindow.builder()
                .setViewer(player)
                .setTitle(LegacyComponentSerializer.legacySection().serialize(title))
                .setInputGui(gui)
                .build();

        window.addCloseHandler(reason -> {
            Component reopenedTitle = MiniMessage.miniMessage().deserialize(Lang.get().message(Message.PORTFOLIO_TITLE));

            Inventory inventory = Bukkit.createInventory(player, 45, LegacyComponentSerializer.legacySection().serialize(reopenedTitle));
            player.openInventory(inventory);
            player.setMetadata("NascraftPortfolio", new FixedMetadataValue(Nascraft.getInstance(),false));

            PortfolioInventory.getInstance().updatePortfolioInventory(player);

            FoliaScheduler.runAtEntityLater(Nascraft.getInstance(), player, () -> {
                Component retryTitle = MiniMessage.miniMessage().deserialize(Lang.get().message(Message.PORTFOLIO_TITLE));

                Inventory retryInventory = Bukkit.createInventory(player, 45, LegacyComponentSerializer.legacySection().serialize(retryTitle));
                player.openInventory(retryInventory);
                player.setMetadata("NascraftPortfolio", new FixedMetadataValue(Nascraft.getInstance(),false));

                PortfolioInventory.getInstance().updatePortfolioInventory(player);

            }, 1L);
        });

        window.applyPatch(getMapPatchComposition(portfolio));

        window.open();
    }

    @Override
    public void close() {

    }

    @Override
    public void update() {

    }

    public static CartographyWindow.MapPatch getMapPatchComposition(Portfolio portfolio) {

        BufferedImage graphImage = PortfolioCompositionChart.getImage(portfolio, 128, 128);

        return new CartographyWindow.MapPatch(0, 0, 128, 128, MapPalette.imageToBytes(graphImage));
    }

    public static CartographyWindow.MapPatch getMapPatchEvolution(Portfolio portfolio) {

        BufferedImage graphImage = PortfolioEvolutionChart.getImage(portfolio, 128, 128);

        return new CartographyWindow.MapPatch(0, 0, 128, 128, MapPalette.imageToBytes(graphImage));
    }
}
