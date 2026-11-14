package me.bounser.nascraft.updatechecker;

import me.bounser.nascraft.config.lang.Lang;
import me.bounser.nascraft.config.lang.Message;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * 在 OP 进服时提醒新版本。只提示有管理权限的玩家，避免普通玩家被打扰。
 */
public class UpdateNotifyListener implements Listener {

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!UpdateChecker.isUpdateAvailable()) {
            return;
        }

        Player player = event.getPlayer();
        if (!player.isOp() && !player.hasPermission("nascraft.admin")) {
            return;
        }

        String message = Lang.get().message(Message.UPDATE_AVAILABLE);
        if (message.startsWith("Lang section not found")) {
            // 服务器沿用了旧语言文件时给出兜底文本，避免只看到报错。
            message = "§8[§6Nascraft§8] §e发现新版本 §6" + UpdateChecker.getLatestVersion()
                    + "§e（当前 §7" + UpdateChecker.getCurrentVersion() + "§e）。下载：§b" + UpdateChecker.getLatestUrl();
        } else {
            message = message
                    .replace("[NEW]", UpdateChecker.getLatestVersion())
                    .replace("[CURRENT]", UpdateChecker.getCurrentVersion())
                    .replace("[URL]", UpdateChecker.getLatestUrl());
        }

        player.sendMessage(message);
    }
}
