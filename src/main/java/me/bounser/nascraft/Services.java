package me.bounser.nascraft;

import me.bounser.nascraft.config.Config;
import me.bounser.nascraft.database.DatabaseManager;
import me.bounser.nascraft.discord.DiscordBot;
import me.bounser.nascraft.discord.linking.LinkManager;
import me.bounser.nascraft.inventorygui.MarketMenuManager;
import me.bounser.nascraft.managers.DebtManager;
import me.bounser.nascraft.managers.GraphManager;
import me.bounser.nascraft.managers.ImagesManager;
import me.bounser.nascraft.managers.MoneyManager;
import me.bounser.nascraft.managers.TasksManager;
import me.bounser.nascraft.managers.currencies.CurrenciesManager;
import me.bounser.nascraft.market.MarketManager;
import me.bounser.nascraft.market.limitorders.LimitOrdersManager;
import me.bounser.nascraft.portfolio.PortfoliosManager;
import me.bounser.nascraft.sellwand.WandsManager;

public class Services {

    private static Services instance = new Services();

    public static Services get() { return instance; }

    public static void set(Services services) {
        if (services == null) throw new IllegalArgumentException("services");
        instance = services;
    }

    public static void reset() { instance = new Services(); }

    public Config config() { return Config.getInstance(); }

    public DatabaseManager database() { return DatabaseManager.get(); }

    public MarketManager market() { return MarketManager.getInstance(); }

    public DebtManager debt() { return DebtManager.getInstance(); }

    public TasksManager tasks() { return TasksManager.getInstance(); }

    public MoneyManager money() { return MoneyManager.getInstance(); }

    public ImagesManager images() { return ImagesManager.getInstance(); }

    public GraphManager graphs() { return GraphManager.getInstance(); }

    public PortfoliosManager portfolios() { return PortfoliosManager.getInstance(); }

    public LimitOrdersManager limitOrders() { return LimitOrdersManager.getInstance(); }

    public CurrenciesManager currencies() { return CurrenciesManager.getInstance(); }

    public WandsManager wands() { return WandsManager.getInstance(); }

    public LinkManager links() { return LinkManager.getInstance(); }

    public DiscordBot discord() { return DiscordBot.getInstance(); }

    public MarketMenuManager marketMenu() { return MarketMenuManager.getInstance(); }
}
