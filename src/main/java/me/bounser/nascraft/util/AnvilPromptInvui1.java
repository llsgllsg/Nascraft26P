package me.bounser.nascraft.util;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import xyz.xenondevs.invui.gui.Gui;
import xyz.xenondevs.invui.item.impl.SimpleItem;
import xyz.xenondevs.invui.window.AnvilWindow;

import java.util.function.Function;

/**
 * invui 1.x implementation of {@link AnvilPrompt}, used on MC 1.21.11.
 *
 * <p>A placeholder item is placed in the anvil's left slot: without it the rename
 * field is not editable in vanilla, and its display name doubles as the pre-filled
 * input text (the old anvilgui {@code .text(...)} behaviour).</p>
 */
final class AnvilPromptInvui1 {

    private AnvilPromptInvui1() {}

    static void open(Player player, String title, String text, Function<String, AnvilPrompt.Result> onSubmit) {
        // The anvil's rename field is only editable when something is in the left
        // slot; using the pre-filled text as its display name both enables typing
        // and preserves the old ".text(...)" placeholder behaviour.
        ItemStack placeholder = new ItemStack(Material.PAPER);
        ItemMeta meta = placeholder.getItemMeta();
        meta.setDisplayName(text.isEmpty() ? " " : text);
        placeholder.setItemMeta(meta);

        // Anvil has 4 slots: left(0), right(1), middle(2), output(3).
        // GUI must have exactly 4 columns (1 row × 4 cols) to avoid
        // ArrayIndexOutOfBoundsException when the player clicks slot 2.
        Gui gui = Gui.empty(1, 4);
        gui.setItem(0, new SimpleItem(placeholder));

        AnvilWindow[] holder = new AnvilWindow[1];

        AnvilWindow window = AnvilWindow.single()
                .setTitle(title)
                .setGui(gui)
                .setCloseable(false)
                .addRenameHandler(input -> {
                    if (onSubmit == null) return;
                    AnvilPrompt.Result result = onSubmit.apply(input);
                    if (result == null) return;
                    if (result.accepted) {
                        AnvilWindow open = holder[0];
                        if (open != null) open.close();
                        if (result.afterClose != null) result.afterClose.run();
                    } else if (result.errorMessage != null && !result.errorMessage.isEmpty()) {
                        player.sendMessage(result.errorMessage);
                    }
                })
                .build(player);

        holder[0] = window;
        window.open();
    }
}
