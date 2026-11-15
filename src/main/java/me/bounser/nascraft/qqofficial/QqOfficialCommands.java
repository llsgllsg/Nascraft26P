package me.bounser.nascraft.qqofficial;

import me.bounser.nascraft.config.lang.Lang;
import me.bounser.nascraft.config.lang.Message;
import me.bounser.nascraft.formatter.Formatter;
import me.bounser.nascraft.formatter.Style;
import me.bounser.nascraft.market.MarketManager;
import me.bounser.nascraft.market.unit.Item;
import me.bounser.nascraft.market.unit.Price;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Chat commands handled from QQ through the official bot bridge.
 *
 * <p>{@link #handle(String, String, String, String, boolean)} is always called on the server's
 * global thread. Replies are sent as passive replies (msg_id = incoming event id) so they stay
 * inside the five minute passive reply window.</p>
 */
public final class QqOfficialCommands {

    private QqOfficialCommands() {
    }

    public static void handle(String openId, String groupOpenId, String eventId, String text, boolean group) {
        QqOfficialBridge bridge = QqOfficialBridge.getInstance();
        if (bridge == null) return;

        String[] args = text.trim().split("\\s+");
        if (args.length == 0 || args[0].isEmpty()) return;

        String command = args[0].toLowerCase(Locale.ROOT);
        switch (command) {
            case "帮助", "help", "菜单", "?", "？" -> reply(groupOpenId, openId, eventId, group, Lang.get().message(Message.ONEBOT_HELP));
            case "行情", "价格", "price" -> handlePrice(groupOpenId, openId, eventId, group, args);
            case "提醒", "alert" -> handleAlertSet(openId, groupOpenId, eventId, group, args);
            case "提醒列表", "alerts" -> handleAlertList(openId, groupOpenId, eventId, group);
            case "取消提醒", "unalert" -> handleAlertRemove(openId, groupOpenId, eventId, group, args);
            case "绑定", "bind" -> handleBind(openId, groupOpenId, eventId, group, args);
            case "解绑", "unlink" -> handleUnlink(openId, groupOpenId, eventId, group);
            default -> reply(groupOpenId, openId, eventId, group, Lang.get().message(Message.ONEBOT_HELP));
        }
    }

    private static void handlePrice(String groupOpenId, String openId, String eventId, boolean group, String[] args) {
        if (args.length < 2) {
            reply(groupOpenId, openId, eventId, group, Lang.get().message(Message.ONEBOT_HELP));
            return;
        }

        Item item = findItem(join(args, 1, args.length));
        if (item == null) {
            reply(groupOpenId, openId, eventId, group, Lang.get().message(Message.ONEBOT_ITEM_NOT_FOUND));
            return;
        }

        Price price = item.getPrice();
        String message = Lang.get().message(Message.ONEBOT_PRICE)
                .replace("[ITEM]", item.getName())
                .replace("[PRICE]", Formatter.plainFormat(item.getCurrency(), price.getValue(), Style.ROUND_BASIC))
                .replace("[STOCK]", String.format(Locale.ROOT, "%.0f", price.getStock()))
                .replace("[CHANGE]", String.format(Locale.ROOT, "%+.2f%%", price.getDayChange() * 100f));

        reply(groupOpenId, openId, eventId, group, message);
    }

    private static void handleAlertSet(String openId, String groupOpenId, String eventId, boolean group, String[] args) {
        if (args.length < 3) {
            reply(groupOpenId, openId, eventId, group, Lang.get().message(Message.ONEBOT_HELP));
            return;
        }

        Item item = findItem(join(args, 1, args.length - 1));
        if (item == null) {
            reply(groupOpenId, openId, eventId, group, Lang.get().message(Message.ONEBOT_ITEM_NOT_FOUND));
            return;
        }

        double target;
        try {
            target = Double.parseDouble(args[args.length - 1].replace(",", "."));
        } catch (NumberFormatException e) {
            reply(groupOpenId, openId, eventId, group, Lang.get().message(Message.ONEBOT_INVALID_PRICE));
            return;
        }

        if (!(target > 0)) {
            reply(groupOpenId, openId, eventId, group, Lang.get().message(Message.ONEBOT_INVALID_PRICE));
            return;
        }

        double current = item.getPrice().getValue();
        double threshold = target < current ? -target : target;
        String operator = threshold >= 0 ? "≥" : "≤";

        boolean stored = QqOfficialBridge.getInstance().getStore()
                .setAlert(openId, item.getIdentifier(), threshold);

        if (!stored) {
            reply(groupOpenId, openId, eventId, group, Lang.get().message(Message.ONEBOT_ALERT_LIMIT));
            return;
        }

        String message = Lang.get().message(Message.ONEBOT_ALERT_SET)
                .replace("[ITEM]", item.getName())
                .replace("[OP]", operator)
                .replace("[PRICE]", Formatter.plainFormat(item.getCurrency(), target, Style.ROUND_BASIC));

        reply(groupOpenId, openId, eventId, group, message);
    }

    private static void handleAlertList(String openId, String groupOpenId, String eventId, boolean group) {
        Map<String, Double> alerts = QqOfficialBridge.getInstance().getStore().getAlerts(openId);
        if (alerts.isEmpty()) {
            reply(groupOpenId, openId, eventId, group, Lang.get().message(Message.ONEBOT_ALERT_LIST_EMPTY));
            return;
        }

        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, Double> alert : alerts.entrySet()) {
            Item item = MarketManager.getInstance().getItem(alert.getKey());
            if (item == null) continue;

            double threshold = alert.getValue();
            String operator = threshold >= 0 ? "≥" : "≤";

            if (builder.length() > 0) builder.append('\n');
            builder.append(Lang.get().message(Message.ONEBOT_ALERT_LIST_ITEM)
                    .replace("[ITEM]", item.getName())
                    .replace("[OP]", operator)
                    .replace("[PRICE]", Formatter.plainFormat(item.getCurrency(), Math.abs(threshold), Style.ROUND_BASIC)));
        }

        if (builder.length() == 0) {
            reply(groupOpenId, openId, eventId, group, Lang.get().message(Message.ONEBOT_ALERT_LIST_EMPTY));
            return;
        }

        reply(groupOpenId, openId, eventId, group, builder.toString());
    }

    private static void handleAlertRemove(String openId, String groupOpenId, String eventId, boolean group, String[] args) {
        if (args.length < 2) {
            reply(groupOpenId, openId, eventId, group, Lang.get().message(Message.ONEBOT_HELP));
            return;
        }

        Item item = findItem(join(args, 1, args.length));
        if (item == null) {
            reply(groupOpenId, openId, eventId, group, Lang.get().message(Message.ONEBOT_ITEM_NOT_FOUND));
            return;
        }

        boolean removed = QqOfficialBridge.getInstance().getStore()
                .removeAlert(openId, item.getIdentifier());

        reply(groupOpenId, openId, eventId, group,
                Lang.get().message(removed ? Message.ONEBOT_ALERT_REMOVED : Message.ONEBOT_ALERT_NOT_FOUND));
    }

    private static void handleBind(String openId, String groupOpenId, String eventId, boolean group, String[] args) {
        if (args.length < 2) {
            reply(groupOpenId, openId, eventId, group, Lang.get().message(Message.ONEBOT_HELP));
            return;
        }

        QqOfficialBridge bridge = QqOfficialBridge.getInstance();
        QqOfficialStore store = bridge.getStore();

        UUID existing = store.getUUID(openId);
        if (existing != null) {
            reply(groupOpenId, openId, eventId, group, Lang.get().message(Message.ONEBOT_QQ_TAKEN)
                    .replace("[PLAYER]", QqOfficialBridge.nameOf(existing)));
            return;
        }

        UUID uuid = bridge.redeemCode(args[1]);
        if (uuid == null) {
            reply(groupOpenId, openId, eventId, group, Lang.get().message(Message.ONEBOT_LINK_FAILED));
            return;
        }

        if (!store.link(uuid, openId)) {
            reply(groupOpenId, openId, eventId, group, Lang.get().message(Message.ONEBOT_QQ_TAKEN)
                    .replace("[PLAYER]", QqOfficialBridge.nameOf(store.getUUID(openId))));
            return;
        }

        reply(groupOpenId, openId, eventId, group, Lang.get().message(Message.ONEBOT_LINK_OK)
                .replace("[PLAYER]", QqOfficialBridge.nameOf(uuid))
                .replace("[QQ]", shortOpenId(openId)));
    }

    private static void handleUnlink(String openId, String groupOpenId, String eventId, boolean group) {
        QqOfficialBridge bridge = QqOfficialBridge.getInstance();
        UUID uuid = bridge.getStore().getUUID(openId);

        if (uuid == null) {
            reply(groupOpenId, openId, eventId, group, Lang.get().message(Message.ONEBOT_NOT_LINKED));
            return;
        }

        bridge.getStore().unlink(uuid);
        reply(groupOpenId, openId, eventId, group, Lang.get().message(Message.ONEBOT_UNLINKED));
    }

    private static Item findItem(String query) {
        return MarketManager.getInstance().getItemByQuery(query);
    }

    private static void reply(String groupOpenId, String openId, String eventId, boolean group, String message) {
        QqOfficialBridge bridge = QqOfficialBridge.getInstance();
        if (bridge == null) return;

        String plain = QqOfficialBridge.stripColour(message);
        if (group && groupOpenId != null) bridge.getApi().sendGroupMessage(groupOpenId, plain, eventId);
        else bridge.getApi().sendC2CMessage(openId, plain, eventId);
    }

    private static String shortOpenId(String openId) {
        if (openId == null) return "-";
        return openId.length() <= 8 ? openId : openId.substring(0, 8) + "...";
    }

    private static String join(String[] args, int from, int to) {
        StringBuilder builder = new StringBuilder();
        for (int i = from; i < to; i++) {
            if (builder.length() > 0) builder.append(' ');
            builder.append(args[i]);
        }
        return builder.toString();
    }
}
