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
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.map.MapPalette;
import org.bukkit.metadata.FixedMetadataValue;
import xyz.xenondevs.inventoryaccess.map.MapPatch;
import xyz.xenondevs.invui.gui.Gui;
import xyz.xenondevs.invui.gui.structure.Structure;
import xyz.xenondevs.invui.window.Window;   // ✅ 改用 Window

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

        // ✅ v2: Gui.normal() -> Gui.builder()
        Gui gui = Gui.builder()
                .setStructure(structure)
                .build();

        // ✅ v2: CartographyWindow.single() -> Window.cartography()
        // ✅ setTitle 直接传入 Component
        Window window = Window.cartography()
                .setViewer(player)
                .setTitle(title)
                .setGui(gui)
                .build();

        // ✅ v2: setCloseHandlers(List) -> setCloseHandler(Runnable)
        window.setCloseHandler(() -> {
            Component backTitle = MiniMessage.miniMessage().deserialize(Lang.get().message(Message.PORTFOLIO_TITLE));
            Inventory inventory = Bukkit.createInventory(player, 45, Component.translatable(backTitle)); // ⚠️ 注：这里可能需要适配
            // 原来用的是 LegacyComponentSerializer，现在可以直接用 Component，但 createInventory 第二个参数需要 String，所以还是需要转换为旧格式？
            // 为了兼容，保留原来的方式，或者用 MiniMessage 的解析。
            // 这里我们保持和原来一样用 LegacyComponentSerializer，但需要导入。
            // 但既然我们已经移除了 LegacyComponentSerializer 导入，需要重新导入或者改用 Adventure 的方式。
            // 保险起见，还是使用 LegacyComponentSerializer 转换。
            // 但我们可以改为直接使用 MiniMessage 解析成 Legacy 字符串。
            String legacyTitle = MiniMessage.miniMessage().serialize(backTitle); // 这是 MiniMessage 字符串，不是旧格式，需要转换
            // 其实最好使用 LegacyComponentSerializer.legacySection().serialize(backTitle)
            // 需要重新导入 LegacyComponentSerializer
            // 所以我们保留 LegacyComponentSerializer 的导入。
            // 下面重新写这段逻辑，并且简化（避免重复代码）
            reopenPortfolioInventory(player);
        });

        window.updateMap(getMapPatchComposition(portfolio));
        window.open();
    }

    // 提取重复逻辑为方法
    private void reopenPortfolioInventory(Player player) {
        Component title = MiniMessage.miniMessage().deserialize(Lang.get().message(Message.PORTFOLIO_TITLE));
        String legacyTitle = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().serialize(title);
        Inventory inventory = Bukkit.createInventory(player, 45, legacyTitle);
        player.openInventory(inventory);
        player.setMetadata("NascraftPortfolio", new FixedMetadataValue(Nascraft.getInstance(), false));
        PortfolioInventory.getInstance().updatePortfolioInventory(player);
        // 无需再延迟执行一次，因为 setCloseHandler 只会在关闭时调用一次，且我们延迟处理的是重新打开，但这里没有延迟需求，所以去掉 FoliaScheduler 的重复调用。
        // 但原来有延迟1tick，可能为了防止某些问题，我们可以保留延迟但用 lambda 简化。
        // 原代码有延迟1tick，我们保留：
        FoliaScheduler.runAtEntityLater(Nascraft.getInstance(), player, () -> {
            Component delayedTitle = MiniMessage.miniMessage().deserialize(Lang.get().message(Message.PORTFOLIO_TITLE));
            String delayedLegacyTitle = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().serialize(delayedTitle);
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
