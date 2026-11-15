package me.bounser.nascraft.util;

import org.bukkit.entity.Player;

import java.util.function.Function;

/**
 * Anvil text-input prompt with a version independent API.
 *
 * <p>The implementation is chosen at {@link Builder#open(Player)} time: MC 1.21.11
 * uses {@link AnvilPromptInvui1} (invui 1.x {@code AnvilWindow.single()}), Paper
 * 26.x uses {@link AnvilPromptInvui2} (invui 2.x {@code AnvilWindow.builder()}).
 * Both backends present the same {@link Result} contract, so every existing call
 * site keeps working unchanged.</p>
 */
public final class AnvilPrompt {

    private AnvilPrompt() {}

    public static Builder builder() { return new Builder(); }

    /** Outcome of a submitted input. */
    public static final class Result {
        final boolean accepted;
        final Runnable afterClose;
        final String errorMessage;

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

        /**
         * Pre-filled input text. Supported by the invui 1.x backend through a
         * placeholder item in the anvil's left slot; invui 2.x has no pre-fill
         * support, so the text is ignored there (the field is always editable).
         */
        public Builder text(String text) { this.text = text == null ? "" : text; return this; }

        /** Called on submit with the input text. */
        public Builder onSubmit(Function<String, Result> onSubmit) { this.onSubmit = onSubmit; return this; }

        public void open(Player player) {
            if (ServerCompat.isInvui2()) {
                AnvilPromptInvui2.open(player, title, text, onSubmit);
            } else {
                AnvilPromptInvui1.open(player, title, text, onSubmit);
            }
        }
    }
}
