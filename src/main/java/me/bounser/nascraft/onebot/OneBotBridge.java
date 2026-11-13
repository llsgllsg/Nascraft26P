package me.bounser.nascraft.onebot;

import me.bounser.nascraft.Nascraft;
import me.bounser.nascraft.config.Config;
import me.bounser.nascraft.config.lang.Lang;
import me.bounser.nascraft.config.lang.Message;
import me.bounser.nascraft.database.commands.resources.Trade;
import me.bounser.nascraft.formatter.Formatter;
import me.bounser.nascraft.formatter.Style;
import me.bounser.nascraft.market.MarketManager;
import me.bounser.nascraft.market.unit.Item;
import me.bounser.nascraft.scheduler.FoliaScheduler;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lifecycle, trade log buffering and price alerts for the OneBot v11 bridge.
 */
public class OneBotBridge {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private static OneBotBridge instance;

    private final OneBotClient client;
    private final OneBotStore store;

    private OneBotWebhook webhook;

    private final Object bufferLock = new Object();
    private final StringBuilder tradeBuffer = new StringBuilder();

    private final Map<String, PendingLink> pendingLinks = new ConcurrentHashMap<>();

    public static OneBotBridge getInstance() { return instance; }

    public static void enable() {
        if (instance != null) return;
        instance = new OneBotBridge();
    }

    public static void disable() {
        if (instance == null) return;
        instance.shutdown();
        instance = null;
    }

    private OneBotBridge() {
        Config config = Config.getInstance();
        client = new OneBotClient(config.getOneBotApiUrl(), config.getOneBotAccessToken());
        store = new OneBotStore(Nascraft.getInstance().getDataFolder());

        if (config.getOneBotWebhookEnabled()) {
            webhook = new OneBotWebhook();
            try {
                webhook.start();
            } catch (IOException | RuntimeException e) {
                Nascraft.getInstance().getLogger().warning("[OneBot] 无法启动 Webhook：" + e.getMessage());
                webhook = null;
            }
        }

        Nascraft.getInstance().getLogger().info("[OneBot] 已启用，API: " + client.getApiUrl()
                + (webhook == null ? "" : "，Webhook 已监听"));
    }

    private void shutdown() {
        if (webhook != null) webhook.stop();
        store.saveNow();
        synchronized (bufferLock) {
            tradeBuffer.setLength(0);
        }
    }

    public OneBotStore getStore() { return store; }

    public OneBotClient getClient() { return client; }

    public String createLinkCode(UUID uuid) {
        long now = System.currentTimeMillis();
        pendingLinks.entrySet().removeIf(entry -> entry.getValue().expiresAt() < now);

        String code;
        do {
            code = String.format("%06d", new Random().nextInt(1_000_000));
        } while (pendingLinks.containsKey(code));

        pendingLinks.put(code, new PendingLink(uuid, now + 10L * 60L * 1000L));
        return code;
    }

    public UUID redeemCode(String code) {
        PendingLink pending = pendingLinks.remove(code);
        if (pending == null || pending.expiresAt() < System.currentTimeMillis()) return null;
        return pending.uuid();
    }

    public void sendTradeLog(Trade trade) {
        if (!Config.getInstance().getOneBotLogEnabled()) return;
        if (Config.getInstance().getOneBotLogGroups().isEmpty()) return;

        String message = Lang.get().message(Message.ONEBOT_TRADE_LINE)
                .replace("[TIME]", LocalDateTime.now().format(TIME_FORMAT))
                .replace("[PLAYER]", nameOf(trade.getUuid()))
                .replace("[ACTION]", trade.isBuy()
                        ? Lang.get().message(Message.DISCORD_LOG_BUY)
                        : Lang.get().message(Message.DISCORD_LOG_SELL))
                .replace("[AMOUNT]", String.valueOf(trade.getAmount()))
                .replace("[ITEM]", trade.getItem().getName())
                .replace("[WORTH]", Formatter.plainFormat(trade.getItem().getCurrency(), trade.getValue(), Style.ROUND_BASIC));

        boolean flushNow = false;
        synchronized (bufferLock) {
            tradeBuffer.append(message).append('\n');
            if (tradeBuffer.length() > 1000) flushNow = true;
        }
        if (flushNow) flush();
    }

    public void flush() {
        String payload;
        synchronized (bufferLock) {
            if (tradeBuffer.length() == 0) return;
            payload = tradeBuffer.toString().trim();
            tradeBuffer.setLength(0);
        }

        String plain = stripColour(payload);
        for (long groupId : Config.getInstance().getOneBotLogGroups())
            client.sendGroup(groupId, plain);
    }

    public void checkAlerts() {
        if (!Config.getInstance().getOneBotAlertsEnabled()) return;
        FoliaScheduler.runGlobal(Nascraft.getInstance(), this::checkAlertsNow);
    }

    private void checkAlertsNow() {
        for (Map.Entry<Long, Map<String, Double>> entry : store.copyAlerts().entrySet()) {
            long qq = entry.getKey();

            for (Map.Entry<String, Double> alert : entry.getValue().entrySet()) {
                Item item = MarketManager.getInstance().getItem(alert.getKey());
                if (item == null) continue;

                double threshold = alert.getValue();
                double price = item.getPrice().getValue();
                boolean up = threshold >= 0;
                boolean reached = up ? price >= threshold : price <= -threshold;
                if (!reached) continue;

                String message = Lang.get().message(Message.ONEBOT_ALERT_TRIGGERED)
                        .replace("[ITEM]", item.getName())
                        .replace("[PRICE]", Formatter.plainFormat(item.getCurrency(), price, Style.ROUND_BASIC))
                        .replace("[TARGET]", Formatter.plainFormat(item.getCurrency(), Math.abs(threshold), Style.ROUND_BASIC));

                client.sendPrivate(qq, stripColour(message));
                store.removeAlert(qq, alert.getKey());
            }
        }
    }

    public static String stripColour(String text) {
        return text == null ? "" : text.replaceAll("(?i)§[0-9a-fk-orx]", "");
    }

    public static String nameOf(UUID uuid) {
        if (uuid == null) return "-";

        Player player = Bukkit.getPlayer(uuid);
        if (player != null) return player.getName();

        OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
        String name = offline.getName();
        return name != null ? name : uuid.toString().substring(0, 8);
    }

    private record PendingLink(UUID uuid, long expiresAt) { }
}
