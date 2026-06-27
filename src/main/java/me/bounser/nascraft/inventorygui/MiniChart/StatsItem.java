package me.bounser.nascraft.inventorygui.MiniChart;

import me.bounser.nascraft.chart.price.ChartType;
import me.bounser.nascraft.config.lang.Lang;
import me.bounser.nascraft.config.lang.Message;
import me.bounser.nascraft.formatter.Formatter;
import me.bounser.nascraft.formatter.Style;
import me.bounser.nascraft.market.unit.Item;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import xyz.xenondevs.invui.item.ItemProvider;
import xyz.xenondevs.invui.item.impl.Item;

import java.util.ArrayList;
import java.util.List;

public class StatsItem implements Item {

    private Item item;
    private ChartType type;

    public StatsItem(Item item, ChartType type) {
        this.item = item;
        this.type = type;
    }

    @Override
    public ItemProvider getItemProvider() {

        Component title = MiniMessage.miniMessage().deserialize(Lang.get().message(Message.GUI_INFO_STATISTICS_NAME).replace("[ITEM-NAME]", item.getTaggedName()));

        String loreBase = Lang.get().message(Message.GUI_INFO_STATISTICS_LORE);

        loreBase = loreBase.replace("[OPTION]", Lang.get().message(Message.valueOf("GUI_INFO_TIMEFRAME_OPTION_" + (type.ordinal() + 1))));

        double high, low, change;

        switch (type) {

            case DAY:
                high = item.getPrice().getDayHigh();
                low = item.getPrice().getChartDayLow();
                change = item.getPrice().getDayChange();
                break;

            case MONTH:
                high = item.getPrice().getMonthHigh();
                low = item.getPrice().getMonthLow();
                change = item.getPrice().getMonthChange();
                break;

            case YEAR:
                high = item.getPrice().getYearHigh();
                low = item.getPrice().getYearLow();
                change = item.getPrice().getYearChange();
                break;

            case ALL:
                high = item.getPrice().getAllHigh();
                low = item.getPrice().getAllLow();
                change = item.getPrice().getAllChange();
                break;

            default:
                high = 0; low = 0; change = 0;
        }

        if (change > 0) {
            loreBase = loreBase.replace("[CHANGE]", Lang.get().message(Message.GUI_INFO_POSITIVE_CHANGE).replace("[CHANGE]", "" + Formatter.roundToDecimals(change*100, 2)));
        } else {
            loreBase = loreBase.replace("[CHANGE]", Lang.get().message(Message.GUI_INFO_NEGATIVE_CHANGE).replace("[CHANGE]", "" + Formatter.roundToDecimals(change*100, 2)));
        }

        loreBase = loreBase.replace("[HIGH]", Formatter.format(item.getCurrency(), high, Style.ROUND_BASIC));
        loreBase = loreBase.replace("[LOW]", Formatter.format(item.getCurrency(), low, Style.ROUND_BASIC));
        loreBase = loreBase.replace("[PRICE]", Formatter.format(item.getCurrency(), item.getPrice().getValue(), Style.ROUND_BASIC));

        List<String> lore = new ArrayList<>();

        for (String line : loreBase.split("\\n")) {
            Component componentLine = MiniMessage.miniMessage().deserialize(line);
            lore.add(LegacyComponentSerializer.legacySection().serialize(componentLine));
        }

        ItemStack stack = new ItemStack(item.getItemStack().getType());
        var meta = stack.getItemMeta();
        meta.setDisplayName(LegacyComponentSerializer.legacySection().serialize(title));
        meta.setLore(lore);
        stack.setItemMeta(meta);

        return player -> stack;
    }

    public void setChartType(ChartType chartType) {
        this.type = chartType;
        notifyWindows();
    }

    @Override
    public void handleClick(@NotNull ClickType clickType, @NotNull Player player, @NotNull InventoryClickEvent inventoryClickEvent) {
        // 点击处理逻辑（如有需要）
    }
}
