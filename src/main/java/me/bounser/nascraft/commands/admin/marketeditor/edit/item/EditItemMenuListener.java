package me.bounser.nascraft.commands.admin.marketeditor.edit.item;

import me.bounser.nascraft.commands.admin.marketeditor.overview.MarketEditorManager;
import me.bounser.nascraft.config.lang.Lang;
import me.bounser.nascraft.config.lang.Message;
import me.bounser.nascraft.managers.currencies.CurrenciesManager;
import me.bounser.nascraft.managers.currencies.Currency;
import me.bounser.nascraft.market.MarketManager;
import me.bounser.nascraft.market.resources.Category;
import me.bounser.nascraft.util.AnvilPrompt;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class EditItemMenuListener implements Listener {

    @EventHandler
    public void onClickInventory(InventoryClickEvent event) {

        if (!event.getWhoClicked().hasPermission("nascraft.admin")) return;

        if (event.getView().getTopInventory().getSize() != 27 || !event.getView().getTitle().equals(Lang.get().message(Message.MARKETEDITOR_ITEM_TITLE))) return;

        Player player = (Player) event.getWhoClicked();

        if (Objects.equals(event.getClickedInventory(), event.getView().getTopInventory())) event.setCancelled(true);

        switch (event.getRawSlot()) {

            case 11:
                EditorManager.getInstance().clearEditing(player);
                MarketEditorManager.getInstance().getMarketEditorFromPlayer(player).open();
                break;

            case 9:
                EditorManager.getInstance().getEditItemMenuFromPlayer(player).save();
                break;

            case 10:

                ItemStack newItem = event.getCursor();

                assert newItem != null;
                if (newItem.getType() == Material.AIR || newItem.getAmount() == 0) {
                    player.sendMessage(Lang.get().message(Message.MARKETEDITOR_INVALID_ITEM));
                    return;
                }

                newItem.setAmount(1);

                EditorManager.getInstance().getEditItemMenuFromPlayer(player).setItemStack(newItem);
                EditorManager.getInstance().getEditItemMenuFromPlayer(player).open();

                break;

            case 17:

                ItemStack deletePanel = event.getCurrentItem();

                ItemMeta metaDelete = deletePanel.getItemMeta();

                if (metaDelete.getDisplayName().equals(Lang.get().message(Message.MARKETEDITOR_DELETE_ITEM))) {
                    metaDelete.setDisplayName(Lang.get().message(Message.MARKETEDITOR_CONFIRM));
                    deletePanel.setItemMeta(metaDelete);
                } else {
                    EditorManager.getInstance().getEditItemMenuFromPlayer(player).removeItem();
                    player.sendMessage(Lang.get().message(Message.MARKETEDITOR_ITEM_DELETED));
                }
                break;

            case 4:

                openAnvil(player,
                        Lang.get().message(Message.MARKETEDITOR_INITIAL_PRICE_SET),
                        Lang.get().message(Message.MARKETEDITOR_INITIAL_PRICE_ANVIL_TITLE),
                        "initialprice");

                break;

            case 5:
                AnvilPrompt.builder()
                        .text(Lang.get().message(Message.MARKETEDITOR_ALIAS_ANVIL_TEXT))
                        .title(Lang.get().message(Message.MARKETEDITOR_ALIAS_ANVIL_TITLE))
                        .onSubmit(input -> {
                            EditorManager.getInstance().getEditItemMenuFromPlayer(player).setAlias(input);
                            player.sendMessage(Lang.get().message(Message.MARKETEDITOR_ALIAS_SET));
                            return AnvilPrompt.Result.accept(() -> EditorManager.getInstance().getEditItemMenuFromPlayer(player).open());
                        })
                        .open(player);
                break;

            case 6:

                List<Currency> currencies = CurrenciesManager.getInstance().getCurrencies();

                int index = currencies.indexOf(EditorManager.getInstance().getEditItemMenuFromPlayer(player).getCurrency());

                Currency currency = currencies.get((index + 1 == (currencies.size())) ? 0 : index + 1);

                EditorManager.getInstance().getEditItemMenuFromPlayer(player).setCurrency(currency);

                EditorManager.getInstance().getEditItemMenuFromPlayer(player).insertOptions(event.getInventory());

                break;

            case 13:

                openAnvil(
                        player,
                        Lang.get().message(Message.MARKETEDITOR_ELASTICITY_SET),
                        Lang.get().message(Message.MARKETEDITOR_ELASTICITY_ANVIL_TITLE),
                        "elasticity");
                break;

            case 14:

                openAnvil(
                        player,
                        Lang.get().message(Message.MARKETEDITOR_NOISE_SET),
                        Lang.get().message(Message.MARKETEDITOR_NOISE_ANVIL_TITLE),
                        "noiseintensity");
                break;

            case 22:

                openAnvil(
                        player,
                        Lang.get().message(Message.MARKETEDITOR_SUPPORT_SET),
                        Lang.get().message(Message.MARKETEDITOR_SUPPORT_ANVIL_TITLE),
                        "support");
                break;

            case 23:

                openAnvil(
                        player,
                        Lang.get().message(Message.MARKETEDITOR_RESISTANCE_SET),
                        Lang.get().message(Message.MARKETEDITOR_RESISTANCE_ANVIL_TITLE),
                        "resistance");
                break;

            case 15:

                AnvilPrompt.builder()
                        .text(Lang.get().message(Message.MARKETEDITOR_CATEGORY_ANVIL_TEXT))
                        .title(Lang.get().message(Message.MARKETEDITOR_CATEGORY_ANVIL_TITLE))
                        .onSubmit(categoryReference -> {

                            Category selectedCategory = null;

                            for (Category category : MarketManager.getInstance().getCategories())
                                if (category.getIdentifier().equalsIgnoreCase(categoryReference) || category.getDisplayName().equalsIgnoreCase(categoryReference))
                                    selectedCategory = category;

                            if (selectedCategory == null) {

                                return AnvilPrompt.Result.reject(Lang.get().message(Message.MARKETEDITOR_CATEGORY_NOT_RECOGNIZED));

                            } else {
                                EditorManager.getInstance().getEditItemMenuFromPlayer(player).setCategory(selectedCategory);
                                player.sendMessage(Lang.get().message(Message.MARKETEDITOR_CATEGORY_SET));
                                return AnvilPrompt.Result.accept(() -> EditorManager.getInstance().getEditItemMenuFromPlayer(player).open());
                            }

                        })
                        .open(player);
                break;
        }
    }

    public void openAnvil(Player player, String setupedCorrectly, String title, String type) {

        AnvilPrompt.builder()
                .title(title)
                .onSubmit(input -> {
                    try {
                        float value = Float.parseFloat(input);

                        if (value < 0)
                            return AnvilPrompt.Result.reject(Lang.get().message(Message.MARKETEDITOR_CANNOT_BE_NEGATIVE));

                        player.sendMessage(setupedCorrectly);

                        switch (type) {

                            case "initialprice":
                                EditorManager.getInstance().getEditItemMenuFromPlayer(player).setInitialPrice(value);
                                break;
                            case "elasticity":
                                EditorManager.getInstance().getEditItemMenuFromPlayer(player).setElasticity(value);
                                break;
                            case "noiseintensity":
                                EditorManager.getInstance().getEditItemMenuFromPlayer(player).setNoiseIntensity(value);
                                break;
                            case "support":
                                EditorManager.getInstance().getEditItemMenuFromPlayer(player).setSupport(value);
                                break;
                            case "resistance":
                                EditorManager.getInstance().getEditItemMenuFromPlayer(player).setResistance(value);
                                break;

                        }

                        return AnvilPrompt.Result.accept(() -> EditorManager.getInstance().getEditItemMenuFromPlayer(player).open());
                    } catch (NumberFormatException e) {
                        return AnvilPrompt.Result.reject(Lang.get().message(Message.MARKETEDITOR_NOT_VALID_FORMAT));
                    }
                })
                .open(player);

    }

}
