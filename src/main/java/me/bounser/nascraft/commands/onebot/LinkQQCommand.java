package me.bounser.nascraft.commands.onebot;

import me.bounser.nascraft.Nascraft;
import me.bounser.nascraft.commands.Command;
import me.bounser.nascraft.config.lang.Lang;
import me.bounser.nascraft.config.lang.Message;
import me.bounser.nascraft.onebot.OneBotBridge;
import me.bounser.nascraft.qqofficial.QqOfficialBridge;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * In-game command that creates a one-time code used to link a Minecraft account with QQ.
 *
 * <p>Works with both QQ bridges: the OneBot v11 bridge and the QQ official bot bridge. When both
 * are enabled the same code is registered on both, so the player only has to share one code.</p>
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

        OneBotBridge oneBot = OneBotBridge.getInstance();
        QqOfficialBridge official = QqOfficialBridge.getInstance();
        if (oneBot == null && official == null) return;

        UUID uuid = player.getUniqueId();

        if (args.length > 0 && (args[0].equalsIgnoreCase("unlink") || args[0].equals("解除"))) {
            boolean unlinked = false;

            if (oneBot != null && oneBot.getStore().getQQ(uuid) != null) {
                oneBot.getStore().unlink(uuid);
                unlinked = true;
            }
            if (official != null && official.getStore().getOpenId(uuid) != null) {
                official.getStore().unlink(uuid);
                unlinked = true;
            }

            Lang.get().message(player, unlinked ? Message.ONEBOT_UNLINKED : Message.ONEBOT_UNLINK_NONE);
            return;
        }

        if (oneBot != null) {
            Long qq = oneBot.getStore().getQQ(uuid);
            if (qq != null) {
                Lang.get().message(player, Message.ONEBOT_PLAYER_LINKED, "[QQ]", String.valueOf(qq));
                return;
            }
        }

        if (official != null) {
            String openId = official.getStore().getOpenId(uuid);
            if (openId != null) {
                Lang.get().message(player, Message.ONEBOT_PLAYER_LINKED, "[QQ]", shortOpenId(openId));
                return;
            }
        }

        String code = oneBot != null ? oneBot.createLinkCode(uuid) : null;
        if (official != null) {
            if (code == null) code = official.createLinkCode(uuid);
            else official.registerLinkCode(code, uuid);
        }

        Lang.get().message(player, Message.ONEBOT_LINK_CODE, "[CODE]", code == null ? "-" : code);
    }

    private static String shortOpenId(String openId) {
        if (openId == null) return "-";
        return openId.length() <= 8 ? openId : openId.substring(0, 8) + "...";
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        return args.length == 1 ? Collections.singletonList("unlink") : Collections.emptyList();
    }
}
