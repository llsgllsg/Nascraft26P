package me.bounser.nascraft.inventorygui.Portfolio;

import me.bounser.nascraft.portfolio.Portfolio;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import xyz.xenondevs.invui.item.ItemProvider;
import xyz.xenondevs.invui.item.impl.Item;

public class ModeItem implements Item {

    private Portfolio portfolio;
    private PortfolioChartType type;

    public ModeItem(Portfolio portfolio) {
        this.portfolio = portfolio;
        this.type = PortfolioChartType.COMPOSITION;
    }

    @Override
    public ItemProvider getItemProvider() {
        ItemStack stack = new ItemStack(Material.BOOK);
        return player -> stack;
    }

    @Override
    public void handleClick(@NotNull ClickType clickType, @NotNull Player player, @NotNull InventoryClickEvent inventoryClickEvent) {
        // 点击逻辑（可保留注释中的切换功能，但需实现）
    }

    public PortfolioChartType getPortfolioChartType() {
        return type;
    }
}
