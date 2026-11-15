package me.bounser.nascraft.util;

import me.bounser.libs.invui2.xyz.xenondevs.invui.gui.Gui;
import me.bounser.libs.invui2.xyz.xenondevs.invui.window.AnvilWindow;
import org.bukkit.entity.Player;

import java.util.function.Function;

/**
 * invui 2.x implementation of {@link AnvilPrompt}, used on Paper 26.x.
 *
 * <p>invui 2.x exposes {@code setTextFieldAlwaysEnabled(true)}, so the rename field
 * is editable without a placeholder item in the left slot (unlike the 1.x variant).
 * There is no initial-text / pre-fill support, so the {@code text} argument is
 * intentionally unused.</p>
 */
final class AnvilPromptInvui2 {

    private AnvilPromptInvui2() {}

    static void open(Player player, String title, String text, Function<String, AnvilPrompt.Result> onSubmit) {

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
