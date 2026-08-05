package me.bounser.nascraft.api.events;

import me.bounser.nascraft.market.unit.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class SellItemEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private boolean cancelled;

    private Player player;
    private Item item;
    private float amount;

    public SellItemEvent(Player player, Item item, float amount) {
        cancelled = false;

        this.player = player;
        this.item = item;
        this.amount = amount;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    public Player getPlayer() {
        return player;
    }

    public Item getItem() {
        return item;
    }

    public float getAmount() {
        return amount;
    }
}
