package me.bounser.nascraft.inventorygui.Portfolio;

import me.bounser.nascraft.Nascraft;
import me.bounser.nascraft.chart.portfolio.PortfolioCompositionChart;
import me.bounser.nascraft.chart.portfolio.PortfolioEvolutionChart;
import me.bounser.nascraft.config.lang.Lang;
import me.bounser.nascraft.config.lang.Message;
import me.bounser.nascraft.inventorygui.MenuPage;
import me.bounser.nascraft.portfolio.Portfolio;
import me.bounser.nascraft.scheduler.FoliaScheduler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.map.MapPalette;
import org.bukkit.metadata.FixedMetadataValue;
import xyz.xenondevs.inventoryaccess.map.MapPatch;
import xyz.xenondevs.invui.gui.Gui;
import xyz.xenondevs.invui.gui.structure.Structure;
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

        Structure structure = new Structure(
                "I C")
                .addIngredient('I', modeItem)
                .addIngredient('C', stats);

        Gui gui = Gui.builder()
                .setStructure(structure)
                .build();

        CartographyWindow window = CartographyWindow.builder()
                .setViewer(player)
                .setTitle(title)
                .setGui(gui)
                .build();

        window.setCloseHandler(() -> reopenPortfolioInventory(player));

        window.updateMap(getMapPatchComposition(portfolio));
        window.open();
    }

    private void reopenPortfolioInventory(Player player) {
        Component title = MiniMessage.miniMessage().deserialize(Lang.get().message(Message.PORTFOLIO_TITLE));
        String legacyTitle = LegacyComponentSerializer.legacySection().serialize(title);
        Inventory inventory = Bukkit.createInventory(player, 45, legacyTitle);
        player.openInventory(inventory);
        player.setMetadata("NascraftPortfolio", new FixedMetadataValue(Nascraft.getInstance(), false));
        PortfolioInventory.getInstance().updatePortfolioInventory(player);
        FoliaScheduler.runAtEntityLater(Nascraft.getInstance(), player, () -> {
            Component delayedTitle = MiniMessage.miniMessage().deserialize(Lang.get().message(Message.PORTFOLIO_TITLE));
            String delayedLegacyTitle = LegacyComponentSerializer.legacySection().serialize(delayedTitle);
            Inventory delayedInventory = Bukkit.createInventory(player, 45, delayedLegacyTitle);
            player.openInventory(delayedInventory);
            player.setMetadata("NascraftPortfolio", new FixedMetadataValue(Nascraft.getInstance(), false));
            PortfolioInventory.getInstance().updatePortfolioInventory(player);
        }, 1L);
    }

    @Override
    public void close() {
        // 如有需要可添加关闭逻辑
    }

    @Override
    public void update() {
        // 如有需要可添加更新逻辑
    }

    public static MapPatch getMapPatchComposition(Portfolio portfolio) {
        BufferedImage graphImage = PortfolioCompositionChart.getImage(portfolio, 128, 128);
        return new MapPatch(0, 0, 128, 128, MapPalette.imageToBytes(graphImage));
    }

    public static MapPatch getMapPatchEvolution(Portfolio portfolio) {
        BufferedImage graphImage = PortfolioEvolutionChart.getImage(portfolio, 128, 128);
        return new MapPatch(0, 0, 128, 128, MapPalette.imageToBytes(graphImage));
    }
}
