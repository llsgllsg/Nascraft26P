package me.bounser.nascraft.onebot;

import me.bounser.nascraft.config.lang.Lang;
import me.bounser.nascraft.config.lang.Message;
import me.bounser.nascraft.formatter.Formatter;
import me.bounser.nascraft.formatter.Style;
import me.bounser.nascraft.market.MarketManager;
import me.bounser.nascraft.market.unit.Item;
import me.bounser.nascraft.market.unit.Price;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Chat commands handled from QQ through the OneBot v11 bridge.
 *
 * <p>{@link #handle(long, Long, String)} is always called on the server's global thread.</p>
 */
public final class OneBotCommands {

    private OneBotCommands() { }

    public static void handle(long userId, Long groupId, String text) {
        OneBotBridge bridge = OneBotBridge.getInstance();
        if (bridge == null) return;

        String[] args = text.trim().split("\\s+");
        if (args.length == 0 || args[0].isEmpty()) return;

        String command = args[0].toLowerCase(Locale.ROOT);
        switch (command) {
            case "帮助", "help", "菜单", "?", "？" -> reply(groupId, userId, Lang.get().message(Message.ONEBOT_HELP));
            case "行情", "价格", "price" -> handlePrice(groupId, userId, args);
            case "提醒", "alert" -> handleAlertSet(userId, groupId, args);
            case "提醒列表", "alerts" -> handleAlertList(userId, groupId);
            case "取消提醒", "unalert" -> handleAlertRemove(userId, groupId, args);
            case "绑定", "bind" -> handleBind(userId, groupId, args);
            case "解绑", "unlink" -> handleUnlink(userId, groupId);
            default -> reply(groupId, userId, Lang.get().message(Message.ONEBOT_HELP));
        }
    }

    private static void handlePrice(Long groupId, long userId, String[] args) {
        if (args.length < 2) {
            reply(groupId, userId, Lang.get().message(Message.ONEBOT_HELP));
            return;
        }

        Item item = findItem(join(args, 1, args.length));
        if (item == null) {
            reply(groupId, userId, Lang.get().message(Message.ONEBOT_ITEM_NOT_FOUND));
            return;
        }

        Price price = item.getPrice();
        String message = Lang.get().message(Message.ONEBOT_PRICE)
                .replace("[ITEM]", item.getName())
                .replace("[PRICE]", Formatter.plainFormat(item.getCurrency(), price.getValue(), Style.ROUND_BASIC))
                .replace("[STOCK]", String.format(Locale.ROOT, "%.0f", price.getStock()))
                .replace("[CHANGE]", String.format(Locale.ROOT, "%+.2f%%", price.getDayChange() * 100f));

        reply(groupId, userId, message);
    }

    private static void handleAlertSet(long userId, Long groupId, String[] args) {
        if (args.length < 3) {
            reply(groupId, userId, Lang.get().message(Message.ONEBOT_HELP));
            return;
        }

        Item item = findItem(join(args, 1, args.length - 1));
        if (item == null) {
            reply(groupId, userId, Lang.get().message(Message.ONEBOT_ITEM_NOT_FOUND));
            return;
        }

        double target;
        try {
            target = Double.parseDouble(args[args.length - 1].replace(",", "."));
        } catch (NumberFormatException e) {
            reply(groupId, userId, Lang.get().message(Message.ONEBOT_INVALID_PRICE));
            return;
        }

        if (!(target > 0)) {
            reply(groupId, userId, Lang.get().message(Message.ONEBOT_INVALID_PRICE));
            return;
        }

        double current = item.getPrice().getValue();
        double threshold = target < current ? -target : target;
        String operator = threshold >= 0 ? "≥" : "≤";

        boolean stored = OneBotBridge.getInstance().getStore()
                .setAlert(userId, item.getIdentifier(), threshold);

        if (!stored) {
            reply(groupId, userId, Lang.get().message(Message.ONEBOT_ALERT_LIMIT));
            return;
        }

        String message = Lang.get().message(Message.ONEBOT_ALERT_SET)
                .replace("[ITEM]", item.getName())
                .replace("[OP]", operator)
                .replace("[PRICE]", Formatter.plainFormat(item.getCurrency(), target, Style.ROUND_BASIC));

        reply(groupId, userId, message);
    }

    private static void handleAlertList(long userId, Long groupId) {
        Map<String, Double> alerts = OneBotBridge.getInstance().getStore().getAlerts(userId);
        if (alerts.isEmpty()) {
            reply(groupId, userId, Lang.get().message(Message.ONEBOT_ALERT_LIST_EMPTY));
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
            reply(groupId, userId, Lang.get().message(Message.ONEBOT_ALERT_LIST_EMPTY));
            return;
        }

        reply(groupId, userId, builder.toString());
    }

    private static void handleAlertRemove(long userId, Long groupId, String[] args) {
        if (args.length < 2) {
            reply(groupId, userId, Lang.get().message(Message.ONEBOT_HELP));
            return;
        }

        Item item = findItem(join(args, 1, args.length));
        if (item == null) {
            reply(groupId, userId, Lang.get().message(Message.ONEBOT_ITEM_NOT_FOUND));
            return;
        }

        boolean removed = OneBotBridge.getInstance().getStore()
                .removeAlert(userId, item.getIdentifier());

        reply(groupId, userId,
                Lang.get().message(removed ? Message.ONEBOT_ALERT_REMOVED : Message.ONEBOT_ALERT_NOT_FOUND));
    }

    private static void handleBind(long userId, Long groupId, String[] args) {
        if (args.length < 2) {
            reply(groupId, userId, Lang.get().message(Message.ONEBOT_HELP));
            return;
        }

        OneBotBridge bridge = OneBotBridge.getInstance();
        OneBotStore store = bridge.getStore();

        java.util.UUID existing = store.getUUID(userId);
        if (existing != null) {
            reply(groupId, userId, Lang.get().message(Message.ONEBOT_QQ_TAKEN)
                    .replace("[PLAYER]", OneBotBridge.nameOf(existing)));
            return;
        }

        java.util.UUID uuid = bridge.redeemCode(args[1]);
        if (uuid == null) {
            reply(groupId, userId, Lang.get().message(Message.ONEBOT_LINK_FAILED));
            return;
        }

        if (!store.link(uuid, userId)) {
            reply(groupId, userId, Lang.get().message(Message.ONEBOT_QQ_TAKEN)
                    .replace("[PLAYER]", OneBotBridge.nameOf(store.getUUID(userId))));
            return;
        }

        reply(groupId, userId, Lang.get().message(Message.ONEBOT_LINK_OK)
                .replace("[PLAYER]", OneBotBridge.nameOf(uuid))
                .replace("[QQ]", String.valueOf(userId)));
    }

    private static void handleUnlink(long userId, Long groupId) {
        OneBotBridge bridge = OneBotBridge.getInstance();
        java.util.UUID uuid = bridge.getStore().getUUID(userId);

        if (uuid == null) {
            reply(groupId, userId, Lang.get().message(Message.ONEBOT_NOT_LINKED));
            return;
        }

        bridge.getStore().unlink(uuid);
        reply(groupId, userId, Lang.get().message(Message.ONEBOT_UNLINKED));
    }

    private static Item findItem(String query) {
        MarketManager market = MarketManager.getInstance();

        Item item = market.getItem(query);
        if (item != null) return item;

        String lower = query.toLowerCase(Locale.ROOT);
        List<Item> parents = market.getAllParentItems();

        for (Item candidate : parents)
            if (candidate.getIdentifier().equalsIgnoreCase(lower)
                    || candidate.getName().toLowerCase(Locale.ROOT).equals(lower))
                return candidate;

        for (Item candidate : parents)
            if (candidate.getName().toLowerCase(Locale.ROOT).contains(lower))
                return candidate;

        return null;
    }

    private static void reply(Long groupId, long userId, String message) {
        String plain = OneBotBridge.stripColour(message);
        if (groupId == null) OneBotBridge.getInstance().getClient().sendPrivate(userId, plain);
        else OneBotBridge.getInstance().getClient().sendGroup(groupId, plain);
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
