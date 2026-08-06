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
 * Anvil text-input prompt backed by invui's {@link AnvilWindow}.
 *
 * <p>Replaces the bundled anvilgui library, whose NMS version wrappers only go up to
 * 1.21.10 and hard-reference {@code org.bukkit.craftbukkit.v1_21_Rx.*} classes, so on
 * 1.21.11 they throw {@code NoClassDefFoundError}. invui's AnvilWindow uses the bundled
 * inventory-access (r26) which supports 1.21.11.</p>
 *
 * <p>A placeholder item is placed in the anvil's left slot: without it the rename field
 * is not editable in vanilla, and its display name doubles as the pre-filled input text
 * (the old anvilgui {@code .text(...)} behaviour).</p>
 */
public final class AnvilPrompt {

    private AnvilPrompt() {}

    public static Builder builder() { return new Builder(); }

    /** Outcome of a submitted input. */
    public static final class Result {
        private final boolean accepted;
        private final Runnable afterClose;
        private final String errorMessage;

        private Result(boolean accepted, Runnable afterClose, String errorMessage) {
            this.accepted = accepted;
            this.afterClose = afterClose;
            this.errorMessage = errorMessage;
        }

        /** Close the anvil (and optionally run an action afterwards). */
        public static Result accept() { return new Result(true, null, null); }
        public static Result accept(Runnable afterClose) { return new Result(true, afterClose, null); }

        /** Keep the anvil open and send {@code errorMessage} to the player in chat. */
        public static Result reject(String errorMessage) { return new Result(false, null, errorMessage); }
    }

    public static final class Builder {
        private String title = "";
        private String text = "";
        private Function<String, Result> onSubmit;

        public Builder title(String title) { this.title = title == null ? "" : title; return this; }

        /** Pre-filled input text (also shown on the left-slot placeholder item). */
        public Builder text(String text) { this.text = text == null ? "" : text; return this; }

        /** Called on submit with the input text. */
        public Builder onSubmit(Function<String, Result> onSubmit) { this.onSubmit = onSubmit; return this; }

        public void open(Player player) {
            // The anvil's rename field is only editable when something is in the left
            // slot; using the pre-filled text as its display name both enables typing
            // and preserves the old ".text(...)" placeholder behaviour.
            ItemStack placeholder = new ItemStack(Material.PAPER);
            ItemMeta meta = placeholder.getItemMeta();
            meta.setDisplayName(text.isEmpty() ? " " : text);
            placeholder.setItemMeta(meta);

            Gui gui = Gui.empty(2, 1);
            gui.setItem(0, new SimpleItem(placeholder));

            AnvilWindow[] holder = new AnvilWindow[1];

            AnvilWindow window = AnvilWindow.single()
                    .setTitle(title)
                    .setGui(gui)
                    .setCloseable(false)
                    .addRenameHandler(input -> {
                        if (onSubmit == null) return;
                        Result result = onSubmit.apply(input);
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
}
