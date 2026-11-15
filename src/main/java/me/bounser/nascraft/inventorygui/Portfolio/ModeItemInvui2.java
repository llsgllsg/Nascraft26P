package me.bounser.nascraft.inventorygui.Portfolio;

import me.bounser.nascraft.portfolio.Portfolio;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import me.bounser.libs.invui2.xyz.xenondevs.invui.Click;
import me.bounser.libs.invui2.xyz.xenondevs.invui.item.AbstractItem;
import me.bounser.libs.invui2.xyz.xenondevs.invui.item.ItemBuilder;
import me.bounser.libs.invui2.xyz.xenondevs.invui.item.ItemProvider;

public class ModeItemInvui2 extends AbstractItem {

    private Portfolio portfolio;
    private PortfolioChartType type;

    public ModeItemInvui2(Portfolio portfolio) {
        this.portfolio = portfolio;
        this.type = PortfolioChartType.COMPOSITION;
    }

    @Override
    public ItemProvider getItemProvider(Player viewer) {
        return new ItemBuilder(new ItemStack(Material.BOOK));
    }

    @Override
    public void handleClick(@NotNull ClickType clickType, @NotNull Player player, @NotNull Click click) {

        /*
        if (type.equals(PortfolioChartType.COMPOSITION)) type = PortfolioChartType.EVOLUTION;
        else type = PortfolioChartType.COMPOSITION;

        CartographyWindow window = (CartographyWindow) getWindows().iterator().next();

        switch (type) {
            case COMPOSITION -> window.updateMap(InfoPortfolioInvui2.getMapPatchComposition(portfolio));
            case EVOLUTION -> window.updateMap(InfoPortfolioInvui2.getMapPatchEvolution(portfolio));
        }

        notifyWindows(); */
    }

    public PortfolioChartType getPortfolioChartType() {
        return type;
    }
}
