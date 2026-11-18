package me.bounser.nascraft.qqofficial;

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

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lifecycle, event dispatch, trade log buffering and price alerts for the QQ official bot bridge.
 *
 * <p>See <a href="https://bot.q.qq.com/wiki/develop/api-v2/">https://bot.q.qq.com/wiki/develop/api-v2/</a>
 * for the protocol reference.</p>
 */
public class QqOfficialBridge {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private static QqOfficialBridge instance;

    private final QqOfficialApi api;
    private final QqOfficialStore store;
    private final QqOfficialClient client;

    private final Object bufferLock = new Object();
    private final StringBuilder tradeBuffer = new StringBuilder();
    private final Map<String, PendingLink> pendingLinks = new ConcurrentHashMap<>();

    public static QqOfficialBridge getInstance() { return instance; }

    public static void enable() {
        if (instance != null) return;
        instance = new QqOfficialBridge();
    }

    public static void disable() {
        if (instance == null) return;
        instance.shutdown();
        instance = null;
    }

    private static String mask(String value) {
        if (value == null || value.isBlank()) return "(未配置)";
        String trimmed = value.trim();
        if (trimmed.length() <= 4) return "****";
        return trimmed.substring(0, 4) + "****";
    }

    private QqOfficialBridge() {
        Config config = Config.getInstance();
        String apiBase = config.getQqOfficialSandbox()
                ? QqOfficialApi.SANDBOX_BASE
                : QqOfficialApi.PRODUCTION_BASE;
        if (config.getQqOfficialApiBase() != null && !config.getQqOfficialApiBase().isBlank())
            apiBase = config.getQqOfficialApiBase();

        api = new QqOfficialApi(
                config.getQqOfficialAppId(),
                config.getQqOfficialClientSecret(),
                apiBase,
                config.getQqOfficialTokenUrl());
        store = new QqOfficialStore(Nascraft.getInstance().getDataFolder());

        if (!api.isConfigured()) {
            Nascraft.getInstance().getLogger().warning("[QQ Official] 未配置 app-id / client-secret，QQ 官方机器人不会连接。");
            client = new QqOfficialClient(api, config.getQqOfficialIntents());
            return;
        }

        client = new QqOfficialClient(api, config.getQqOfficialIntents());
        client.start();

        Nascraft.getInstance().getLogger().info("[QQ Official] 已启用，AppID: "
                + mask(config.getQqOfficialAppId()) + "，API: " + api.getApiBase()
                + (config.getQqOfficialSandbox() ? "（沙箱）" : "")
                + "（主动连接 QQ 网关，不占用本地端口）");
    }

    private void shutdown() {
        client.stop();
        store.saveNow();
        synchronized (bufferLock) {
            tradeBuffer.setLength(0);
        }
    }

    public QqOfficialStore getStore() { return store; }

    public QqOfficialApi getApi() { return api; }

    public QqOfficialClient getClient() { return client; }

    /** Creates a one-time code that a QQ user can redeem with the {@code 绑定} command. */
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

    /** Registers a code generated elsewhere, so both QQ bridges can share a single code. */
    public void registerLinkCode(String code, UUID uuid) {
        if (code == null || uuid == null) return;
        pendingLinks.put(code, new PendingLink(uuid, System.currentTimeMillis() + 10L * 60L * 1000L));
    }

    /** Called from the websocket thread whenever the gateway dispatches an event. */
    public void onDispatch(String type, Map<String, Object> data) {
        Config config = Config.getInstance();

        switch (type) {
            case "GROUP_AT_MESSAGE_CREATE" -> {
                String groupOpenId = Json.string(data, "group_openid");
                String memberOpenId = Json.string(data, "author", "member_openid");
                if (memberOpenId == null) memberOpenId = Json.string(data, "author", "id");
                String eventId = Json.string(data, "id");
                String content = Json.string(data, "content");
                if (groupOpenId == null || memberOpenId == null || content == null) return;

                if (!config.getQqOfficialCommandsEnabled()) return;
                List<String> whitelist = config.getQqOfficialGroupWhitelist();
                if (!whitelist.isEmpty() && !whitelist.contains(groupOpenId)) return;

                String openId = memberOpenId;
                String text = normalize(content);
                deliver(() -> QqOfficialCommands.handle(openId, groupOpenId, eventId, text, true));
            }
            case "C2C_MESSAGE_CREATE" -> {
                String userOpenId = Json.string(data, "author", "user_openid");
                if (userOpenId == null) userOpenId = Json.string(data, "author", "id");
                String eventId = Json.string(data, "id");
                String content = Json.string(data, "content");
                if (userOpenId == null || content == null) return;

                if (!config.getQqOfficialCommandsEnabled()) return;

                String openId = userOpenId;
                String text = normalize(content);
                deliver(() -> QqOfficialCommands.handle(openId, null, eventId, text, false));
            }
            default -> {
                // Other dispatches (FRIEND_ADD, GROUP_ADD_ROBOT, ...) do not need handling yet.
            }
        }
    }

    private void deliver(Runnable task) {
        Nascraft plugin = Nascraft.getInstance();
        if (plugin == null || !plugin.isEnabled()) return;
        FoliaScheduler.runGlobal(plugin, task);
    }

    private static String normalize(String content) {
        String text = content.trim();
        // Defensive: strip a leading mention token such as "<@!123456>" if the platform keeps it.
        while (text.startsWith("<@")) {
            int end = text.indexOf('>');
            if (end < 0) break;
            text = text.substring(end + 1).trim();
        }
        return text;
    }

    public void sendTradeLog(Trade trade) {
        if (!Config.getInstance().getQqOfficialLogEnabled()) return;
        if (Config.getInstance().getQqOfficialLogGroups().isEmpty()) return;

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
        for (String groupOpenId : Config.getInstance().getQqOfficialLogGroups())
            api.sendGroupMessage(groupOpenId, plain, null);
    }

    public void checkAlerts() {
        if (!Config.getInstance().getQqOfficialAlertsEnabled()) return;
        FoliaScheduler.runGlobal(Nascraft.getInstance(), this::checkAlertsNow);
    }

    private void checkAlertsNow() {
        for (Map.Entry<String, Map<String, Double>> entry : store.copyAlerts().entrySet()) {
            String openId = entry.getKey();

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

                // Accounts linked by mentioning the bot in a group only have a group-scoped
                // member_openid, which cannot receive C2C messages: push the alert back into that
                // group and at-mention the member instead.
                String groupOpenId = store.getGroup(openId);

                if (groupOpenId != null) {
                    String mention = "<qqbot-at-user id=\"" + openId + "\" /> ";
                    api.sendGroupMessage(groupOpenId, mention + stripColour(message), null);
                } else {
                    api.sendC2CMessage(openId, stripColour(message), null);
                }

                store.removeAlert(openId, alert.getKey());
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
