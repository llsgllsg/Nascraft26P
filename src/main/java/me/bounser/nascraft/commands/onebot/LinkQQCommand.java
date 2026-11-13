package me.bounser.nascraft.commands.onebot;

import me.bounser.nascraft.Nascraft;
import me.bounser.nascraft.commands.Command;
import me.bounser.nascraft.config.lang.Lang;
import me.bounser.nascraft.config.lang.Message;
import me.bounser.nascraft.onebot.OneBotBridge;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;

/**
 * In-game command that creates a one-time code used to link a Minecraft account with QQ.
 */
public class LinkQQCommand extends Command {

    public LinkQQCommand() {
        super(
                "linkqq",
                new String[]{"qq"},
                "Link a minecraft account with QQ",
                "nascraft.qq.linkable"
        );
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            Nascraft.getInstance().getLogger().info(ChatColor.RED + "Command not available through console.");
            return;
        }

        if (!player.hasPermission("nascraft.qq.linkable")) {
            Lang.get().message(player, Message.NO_PERMISSION);
            return;
        }

        OneBotBridge bridge = OneBotBridge.getInstance();
        if (bridge == null) return;

        if (args.length > 0 && (args[0].equalsIgnoreCase("unlink") || args[0].equals("解除"))) {
            if (bridge.getStore().getQQ(player.getUniqueId()) == null) {
                Lang.get().message(player, Message.ONEBOT_UNLINK_NONE);
                return;
            }

            bridge.getStore().unlink(player.getUniqueId());
            Lang.get().message(player, Message.ONEBOT_UNLINKED);
            return;
        }

        Long qq = bridge.getStore().getQQ(player.getUniqueId());
        if (qq != null) {
            Lang.get().message(player, Message.ONEBOT_PLAYER_LINKED, "[QQ]", String.valueOf(qq));
            return;
        }

        String code = bridge.createLinkCode(player.getUniqueId());
        Lang.get().message(player, Message.ONEBOT_LINK_CODE, "[CODE]", code);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        return args.length == 1 ? Collections.singletonList("unlink") : Collections.emptyList();
    }
}
