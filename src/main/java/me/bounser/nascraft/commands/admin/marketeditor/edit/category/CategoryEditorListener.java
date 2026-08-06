package me.bounser.nascraft.commands.admin.marketeditor.edit.category;

import me.bounser.nascraft.commands.admin.marketeditor.overview.MarketEditor;
import me.bounser.nascraft.config.lang.Lang;
import me.bounser.nascraft.config.lang.Message;
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
import java.util.Objects;

public class CategoryEditorListener implements Listener {

    private static CategoryEditorListener instance = null;

    public static CategoryEditorListener getInstance() { return instance == null ? new CategoryEditorListener() : instance; }

    @EventHandler
    public void onClickInventory(InventoryClickEvent event) {

        if (!event.getWhoClicked().hasPermission("nascraft.admin")) return;

        if (event.getView().getTopInventory().getSize() != 27 || !event.getView().getTitle().equals(Lang.get().message(Message.MARKETEDITOR_CATEGORY_EDITOR_TITLE)) || event.getCurrentItem() == null) return;

        Player player = (Player) event.getWhoClicked();

        if (Objects.equals(event.getClickedInventory(), event.getView().getTopInventory())) event.setCancelled(true);

        CategoryEditor categoryEditor = CategoryEditorManager.getInstance().getEditCategoryFromPlayer(player);

        switch (event.getRawSlot()) {

            case 9:
                categoryEditor.save();
                return;

            case 11:
                CategoryEditorManager.getInstance().clearEditing(player);
                new MarketEditor(player);
                return;

            case 17:
                ItemStack deletePanel = event.getCurrentItem();

                ItemMeta metaDelete = deletePanel.getItemMeta();

                if (metaDelete.getDisplayName().equals(Lang.get().message(Message.MARKETEDITOR_DELETE_CATEGORY))) {
                    metaDelete.setDisplayName(Lang.get().message(Message.MARKETEDITOR_CONFIRM));
                    deletePanel.setItemMeta(metaDelete);
                } else {
                    categoryEditor.removeCategory();
                }

                return;

            case 13:
                AnvilPrompt.builder()
                        .text(Lang.get().message(Message.MARKETEDITOR_CATEGORY_NAME_ANVIL_TEXT))
                        .title(Lang.get().message(Message.MARKETEDITOR_CATEGORY_NAME_ANVIL_TITLE))
                        .onSubmit(categoryName -> {

                            categoryEditor.setDisplayName(categoryName);

                            player.sendMessage(Lang.get().message(Message.MARKETEDITOR_CATEGORY_NAME_SET));
                            return AnvilPrompt.Result.accept(categoryEditor::open);

                        })
                        .open(player);

                return;

            case 14:

                if (event.getCursor() != null && !event.getCursor().getType().equals(Material.AIR)){
                    categoryEditor.setMaterial(event.getCursor().getType());

                    player.sendMessage(Lang.get().message(Message.MARKETEDITOR_CATEGORY_MATERIAL_SET)
                            .replace("[MAT]", event.getCursor().getType().toString().toLowerCase()));
                    categoryEditor.open();
                }
                return;
        }
    }

}
