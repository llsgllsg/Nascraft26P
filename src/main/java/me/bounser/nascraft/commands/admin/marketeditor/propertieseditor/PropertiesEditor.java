package me.bounser.nascraft.commands.admin.marketeditor.propertieseditor;

import me.bounser.nascraft.config.lang.Lang;
import me.bounser.nascraft.config.lang.Message;
import me.bounser.nascraft.formatter.Formatter;
import me.bounser.nascraft.formatter.Style;
import me.bounser.nascraft.market.MarketManager;
import me.bounser.nascraft.market.resources.Category;
import me.bounser.nascraft.market.unit.Item;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PropertiesEditor {

    private int verticalOffset, horizontalOffset;

    private Player player;


    public PropertiesEditor(Player player) {

        this.player = player;

        verticalOffset = 0;
        horizontalOffset = 0;

        open();
    }

    public void open() {
        Inventory inventory = Bukkit.createInventory(player, 54, Lang.get().message(Message.MARKETEDITOR_TITLE));

        insertFillingPanes(inventory);
        insertArrows(inventory);
        insertHelpHead(inventory);
        insertButtons(inventory);
        insertItems(inventory);

        player.openInventory(inventory);
    }

    public void insertFillingPanes(Inventory inventory) {

        ItemStack blackFiller = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta metaBlack = blackFiller.getItemMeta();
        metaBlack.setDisplayName(" ");
        blackFiller.setItemMeta(metaBlack);

        for(int i : new int[]{1, 2, 3, 5, 6, 47, 48, 50, 51}) {
            inventory.setItem(i, blackFiller);
        }

        ItemStack closeButton = new ItemStack(Material.RED_STAINED_GLASS_PANE);
        ItemMeta meta = closeButton.getItemMeta();
        meta.setDisplayName(Lang.get().message(Message.MARKETEDITOR_CLOSE));
        closeButton.setItemMeta(meta);

        inventory.setItem(8, closeButton);
    }

    public void insertArrows(Inventory inventory) {

        ItemStack arrow = new ItemStack(Material.ARROW);
        ItemMeta meta = arrow.getItemMeta();
        meta.setDisplayName(Lang.get().message(Message.MARKETEDITOR_SCROLL_UP));
        arrow.setItemMeta(meta);

        inventory.setItem(0, arrow);

        meta.setDisplayName(Lang.get().message(Message.MARKETEDITOR_SCROLL_DOWN));
        arrow.setItemMeta(meta);

        inventory.setItem(45, arrow);

        meta.setDisplayName(Lang.get().message(Message.MARKETEDITOR_SCROLL_LEFT));
        arrow.setItemMeta(meta);

        inventory.setItem(52, arrow);

        meta.setDisplayName(Lang.get().message(Message.MARKETEDITOR_SCROLL_RIGHT));
        arrow.setItemMeta(meta);

        inventory.setItem(53, arrow);
    }

    public void insertHelpHead(Inventory inventory) {

        ItemStack info = new ItemStack(Material.CHEST);
        ItemMeta meta = info.getItemMeta();
        meta.setDisplayName(Lang.get().message(Message.MARKETEDITOR_HEADER));
        meta.setLore(Arrays.asList(
                Lang.get().message(Message.MARKETEDITOR_HEADER_LORE_1),
                Lang.get().message(Message.MARKETEDITOR_HEADER_LORE_2)
        ));
        info.setItemMeta(meta);

        inventory.setItem(4, info);
    }

    public void insertButtons(Inventory inventory) {

        ItemStack newItem = new ItemStack(Material.HOPPER);
        ItemMeta metaNewItem = newItem.getItemMeta();
        metaNewItem.setDisplayName(Lang.get().message(Message.MARKETEDITOR_ADD_ITEM));
        metaNewItem.setLore(Arrays.asList(
                Lang.get().message(Message.MARKETEDITOR_ADD_ITEM_LORE_1),
                Lang.get().message(Message.MARKETEDITOR_ADD_ITEM_LORE_2)
        ));
        newItem.setItemMeta(metaNewItem);

        inventory.setItem(49, newItem);

        ItemStack newCategory = new ItemStack(Material.WRITABLE_BOOK);
        ItemMeta newCategoryItemMeta = newCategory.getItemMeta();
        newCategoryItemMeta.setDisplayName(Lang.get().message(Message.MARKETEDITOR_NEW_CATEGORY));
        newCategoryItemMeta.setLore(Arrays.asList(
                Lang.get().message(Message.MARKETEDITOR_NEW_CATEGORY_LORE)
        ));
        newCategory.setItemMeta(newCategoryItemMeta);

        inventory.setItem(46, newCategory);

        ItemStack enabled;
        ItemMeta metaEnabled;

        if (MarketManager.getInstance().getActive()) {
            enabled = new ItemStack(Material.LIME_DYE);

            metaEnabled = enabled.getItemMeta();
            metaEnabled.setDisplayName(Lang.get().message(Message.MARKETEDITOR_MARKET_ACTIVE));
            metaEnabled.setLore(Arrays.asList(
                    Lang.get().message(Message.MARKETEDITOR_MARKET_ACTIVE_LORE_1),
                    Lang.get().message(Message.MARKETEDITOR_MARKET_ACTIVE_LORE_2)
            ));

        } else {
            enabled = new ItemStack(Material.RED_DYE);

            metaEnabled = enabled.getItemMeta();
            metaEnabled.setDisplayName(Lang.get().message(Message.MARKETEDITOR_MARKET_STOPPED));
            metaEnabled.setLore(Arrays.asList(
                    Lang.get().message(Message.MARKETEDITOR_MARKET_STOPPED_LORE_1),
                    Lang.get().message(Message.MARKETEDITOR_MARKET_STOPPED_LORE_2)
            ));
        }

        enabled.setItemMeta(metaEnabled);

        inventory.setItem(7, enabled);
    }

    public void insertItems(Inventory inventory) {

        List<Category> categories = new ArrayList<>();

        for (int i = 0; i <= 3; i++)
            categories.add(MarketManager.getInstance().getCategories().get(i + verticalOffset));

        int j = 0;

        for (Category category : categories) {

            ItemStack categoryItemStack = new ItemStack(category.getMaterial());

            ItemMeta CategoryMeta = categoryItemStack.getItemMeta();

            CategoryMeta.setDisplayName(Lang.get().message(Message.MARKETEDITOR_CATEGORY_LABEL) + category.getDisplayName());
            CategoryMeta.setLore(Arrays.asList(Lang.get().message(Message.MARKETEDITOR_IDENTIFIER_LABEL) + ChatColor.GOLD + category.getIdentifier(),
                    "", Lang.get().message(Message.MARKETEDITOR_CLICK_TO_EDIT)));

            categoryItemStack.setItemMeta(CategoryMeta);

            inventory.setItem(9 + 9*j, categoryItemStack);

            List<Item> items = new ArrayList<>();

            if (horizontalOffset < category.getNumberOfItems())
                items = new ArrayList<>(category.getItems().subList(horizontalOffset, category.getNumberOfItems()));

            while (items.size() < 9)
                items.add(null);

            for (int k = 1; k <= 8; k++) {

                Item item = items.get(k-1);

                if (item == null) {
                    inventory.clear((j+1)*9 + k);
                } else {
                    ItemStack itemStack = item.getItemStack();

                    ItemMeta meta = itemStack.getItemMeta();

                    meta.setDisplayName(Lang.get().message(Message.MARKETEDITOR_ALIAS_LABEL) + item.getName());

                    meta.setLore(Arrays.asList(
                            Lang.get().message(Message.MARKETEDITOR_INITIAL_PRICE_LABEL) + ChatColor.GREEN + Formatter.format(item.getCurrency(), item.getPrice().getInitialValue(), Style.ROUND_BASIC),
                            Lang.get().message(Message.MARKETEDITOR_ELASTICITY_LABEL) + ChatColor.GREEN + item.getPrice().getElasticity(),
                            Lang.get().message(Message.MARKETEDITOR_NOISE_LABEL) + ChatColor.GREEN + item.getPrice().getNoiseIntensity(),
                            Lang.get().message(Message.MARKETEDITOR_SUPPORT_LABEL) + ChatColor.GREEN + item.getPrice().getSupport(),
                            Lang.get().message(Message.MARKETEDITOR_RESISTANCE_LABEL) + ChatColor.GREEN + item.getPrice().getResistance(),
                            " ",
                            Lang.get().message(Message.MARKETEDITOR_CLICK_TO_EDIT)
                    ));

                    itemStack.setItemMeta(meta);

                    inventory.setItem(((j+1)*9) + k, itemStack);
                }
            }
            j++;
        }
    }

    public void increaseVerticalOffset() {
        if (MarketManager.getInstance().getCategories().size() - 4 > verticalOffset)
            verticalOffset++;

    }

    public void increaseHorizontalOffset() {
        int biggestCategory = 0;

        for (Category category : MarketManager.getInstance().getCategories())
            if (category.getNumberOfItems() > biggestCategory) biggestCategory = category.getNumberOfItems();

        if (horizontalOffset < biggestCategory-8) horizontalOffset++;
    }

    public void decreaseVerticalOffset() { if (verticalOffset > 0) verticalOffset--; }

    public void decreaseHorizontalOffset() {

        if (horizontalOffset > 0)
            horizontalOffset--;

    }

    public int getVerticalOffset() { return verticalOffset; }

    public int getHorizontalOffset() { return horizontalOffset; }

}
