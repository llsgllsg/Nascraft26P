package me.bounser.nascraft.util;

import org.bukkit.entity.Player;
import xyz.xenondevs.invui.gui.Gui;
import xyz.xenondevs.invui.window.AnvilWindow;

import java.util.function.Function;

/**
 * Anvil text-input prompt backed by invui's {@link AnvilWindow} (invui 2.x API,
 * used by the MC 26.2 target). Same public API as the invui 1.x version in
 * src/main/java so all call sites compile against both targets.
 *
 * <p>invui 2.x exposes {@code setTextFieldAlwaysEnabled(true)}, so the rename field
 * is editable without a placeholder item in the left slot (unlike the 1.x variant).
 * There is no initial-text / pre-fill support, so {@link Builder#text(String)} is a
 * no-op kept only for API compatibility.</p>
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

        /** Pre-filled input text — not supported by invui 2.x; kept for API compatibility. */
        public Builder text(String text) { this.text = text == null ? "" : text; return this; }

        /** Called on submit with the input text. */
        public Builder onSubmit(Function<String, Result> onSubmit) { this.onSubmit = onSubmit; return this; }

        public void open(Player player) {

            AnvilWindow[] holder = new AnvilWindow[1];

            AnvilWindow window = AnvilWindow.builder()
                    .setTitle(title)
                    // Anvil has 4 slots: left(0), right(1), middle(2), output(3).
                    // GUI must have exactly 4 columns to avoid
                    // ArrayIndexOutOfBoundsException when the player clicks slot 2.
                    .setUpperGui(Gui.empty(1, 4))
                    .setTextFieldAlwaysEnabled(true)
                    .setResultAlwaysValid(true)
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
