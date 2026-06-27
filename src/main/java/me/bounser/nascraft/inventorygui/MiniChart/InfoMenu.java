package me.bounser.nascraft.inventorygui.MiniChart;

import me.bounser.nascraft.Nascraft;
import me.bounser.nascraft.chart.price.ChartType;
import me.bounser.nascraft.chart.price.ItemChartReduced;
import me.bounser.nascraft.config.lang.Lang;
import me.bounser.nascraft.config.lang.Message;
import me.bounser.nascraft.inventorygui.BuySellMenu;
import me.bounser.nascraft.inventorygui.MarketMenuManager;
import me.bounser.nascraft.inventorygui.MenuPage;
import me.bounser.nascraft.market.unit.Item;
import me.bounser.nascraft.scheduler.FoliaScheduler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.bukkit.map.MapPalette;
import xyz.xenondevs.inventoryaccess.map.MapPatch;
import xyz.xenondevs.invui.gui.Gui;
import xyz.xenondevs.invui.gui.structure.Structure;
import xyz.xenondevs.invui.window.Window;  // ✅ v2: 使用 Window 代替 CartographyWindow

import java.awt.image.BufferedImage;
import java.util.Arrays;

public class InfoMenu implements MenuPage {

    private final Player player;
    private final Item item;

    public InfoMenu(Player player, Item item) {
        this.player = player;
        this.item = item;
        open();
    }

    @Override
    public void open() {
        Component title = MiniMessage.miniMessage().deserialize(Lang.get().message(Message.GUI_INFO_TITLE));

        StatsItem statsItem = new StatsItem(item, ChartType.DAY);
        TimeFrameItem timeFrameItem = new TimeFrameItem(item, statsItem);

        Structure structure = new Structure(
                "I C")
                .addIngredient('I', statsItem)
                .addIngredient('C', timeFrameItem);

        // ✅ v2: Gui.normal() 改为 Gui.builder()
        Gui gui = Gui.builder()
                .setStructure(structure)
                .build();

        // ✅ v2: CartographyWindow.single() 改为 Window.cartography()
        // ✅ setTitle 直接接受 Component，无需再转换
        Window window = Window.cartography()
                .setViewer(player)
                .setTitle(title)
                .setGui(gui)
                .build();

        // ✅ v2: setCloseHandlers(List) 改为 setCloseHandler(Runnable)
        window.setCloseHandler(() -> {
            MarketMenuManager.getInstance().setMenuOfPlayer(player, new BuySellMenu(player, item));
            FoliaScheduler.runAtEntityLater(Nascraft.getInstance(), player, () -> {
                MarketMenuManager.getInstance().setMenuOfPlayer(player, new BuySellMenu(player, item));
            }, 1L);
        });

        window.updateMap(getMapPatch(item, ChartType.DAY));
        window.open();
    }

    @Override
    public void close() {
        // 如有需要可添加关闭逻辑
    }

    @Override
    public void update() {
        // 如有需要可添加更新逻辑
    }

    public static MapPatch getMapPatch(Item item, ChartType type) {
        BufferedImage graphImage = ItemChartReduced.getImage(item, type);
        return new MapPatch(0, 0, 128, 128, MapPalette.imageToBytes(graphImage));
    }
}
