package me.bounser.nascraft.inventorygui.MiniChart;

import me.bounser.nascraft.chart.price.ChartType;
import me.bounser.nascraft.config.lang.Lang;
import me.bounser.nascraft.config.lang.Message;
import me.bounser.nascraft.market.unit.Item;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import xyz.xenondevs.invui.item.ItemProvider;
import xyz.xenondevs.invui.item.impl.Item;
import xyz.xenondevs.invui.window.CartographyWindow;

import java.util.ArrayList;
import java.util.List;

public class TimeFrameItem implements Item {

    private final Item item;
    private final StatsItem statsItem;

    private ChartType chartType;

    private int index = 0;

    public TimeFrameItem(Item item, StatsItem statsItem) {
        chartType = ChartType.DAY;
        this.item = item;
        this.statsItem = statsItem;
    }

    @Override
    public ItemProvider getItemProvider() {

        Component title = MiniMessage.miniMessage().deserialize(Lang.get().message(Message.GUI_INFO_TIMEFRAME_NAME));

        List<String> lore = new ArrayList<>();

        for (String line : Lang.get().message(Message.GUI_INFO_TIMEFRAME_LORE_BEFORE).split("\\n")) {
            Component componentLine = MiniMessage.miniMessage().deserialize(line);
            lore.add(LegacyComponentSerializer.legacySection().serialize(componentLine));
        }

        String segments = "";
        for (ChartType type : ChartType.values()) {

            String chartType;

            if (type.ordinal() == index) {
                chartType = Lang.get().message(Message.GUI_INFO_TIMEFRAME_LORE_SELECTED_SEGMENT).replace("[OPTION]", Lang.get().message(Message.valueOf("GUI_INFO_TIMEFRAME_OPTION_" + (type.ordinal() + 1))));
            } else {
                chartType = Lang.get().message(Message.GUI_INFO_TIMEFRAME_LORE_UNSELECTED_SEGMENT).replace("[OPTION]", Lang.get().message(Message.valueOf("GUI_INFO_TIMEFRAME_OPTION_" + (type.ordinal() + 1))));
            }

            segments += chartType;
        }

        for (String line : segments.split("\\n")) {
            Component componentLine = MiniMessage.miniMessage().deserialize(line);
            lore.add(LegacyComponentSerializer.legacySection().serialize(componentLine));
        }

        for (String line : Lang.get().message(Message.GUI_INFO_TIMEFRAME_LORE_AFTER).split("\\n")) {
            Component componentLine = MiniMessage.miniMessage().deserialize(line);
            lore.add(LegacyComponentSerializer.legacySection().serialize(componentLine));
        }

        ItemStack stack = new ItemStack(Material.CLOCK);
        var meta = stack.getItemMeta();
        meta.setDisplayName(LegacyComponentSerializer.legacySection().serialize(title));
        meta.setLore(lore);
        stack.setItemMeta(meta);

        return player -> stack;
    }

    @Override
    public void handleClick(@NotNull ClickType clickType, @NotNull Player player, @NotNull InventoryClickEvent inventoryClickEvent) {

        index++;

        if (index == 4) index = 0;

        chartType = ChartType.values()[index];

        CartographyWindow window = (CartographyWindow) getWindows().iterator().next();

        window.updateMap(InfoMenu.getMapPatch(item, chartType));

        statsItem.setChartType(chartType);

        notifyWindows();
    }

    public ChartType getChartType() { return chartType; }

}
