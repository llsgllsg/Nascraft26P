package me.bounser.nascraft.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converts legacy {@code &a} / {@code §a} colour codes into MiniMessage tags.
 *
 * <p>Most of the bundled language files were migrated to MiniMessage, but a few sections (the
 * OneBot / QQ replies notably) still use the old ampersand codes. Feeding those to
 * {@code MiniMessage.deserialize} prints the codes literally ("&a请在 10 分钟内…"), so every
 * legacy string is normalised here first.</p>
 */
public final class LegacyColours {

    private static final Pattern HEX = Pattern.compile("(?i)[&§]#([0-9a-f]{6})");
    private static final Pattern CODE = Pattern.compile("(?i)[&§]([0-9a-fk-or])");

    private static final String[] NAMES = {
            "black", "dark_blue", "dark_green", "dark_aqua", "dark_red",
            "dark_purple", "gold", "gray", "dark_gray", "blue",
            "green", "aqua", "red", "light_purple", "yellow", "white"
    };

    private LegacyColours() {}

    public static String toMiniMessage(String input) {

        if (input == null || input.isEmpty()) return input;
        if (input.indexOf('&') < 0 && input.indexOf('§') < 0) return input;

        String text = replaceHex(input);

        Matcher matcher = CODE.matcher(text);
        StringBuilder builder = new StringBuilder(text.length() + 32);

        while (matcher.find()) {

            String code = matcher.group(1).toLowerCase();
            char symbol = code.charAt(0);

            String tag;

            if (symbol >= '0' && symbol <= '9') {
                tag = "<color:" + NAMES[symbol - '0'] + ">";
            } else if (symbol >= 'a' && symbol <= 'f') {
                tag = "<color:" + NAMES[symbol - 'a' + 10] + ">";
            } else {
                tag = switch (symbol) {
                    case 'k' -> "<obfuscated>";
                    case 'l' -> "<bold>";
                    case 'm' -> "<strikethrough>";
                    case 'n' -> "<underlined>";
                    case 'o' -> "<italic>";
                    default -> "<reset>";
                };
            }

            matcher.appendReplacement(builder, Matcher.quoteReplacement(tag));
        }

        matcher.appendTail(builder);
        return builder.toString();
    }

    private static String replaceHex(String input) {
        Matcher matcher = HEX.matcher(input);
        StringBuilder builder = new StringBuilder(input.length() + 16);

        while (matcher.find())
            matcher.appendReplacement(builder, Matcher.quoteReplacement("<color:#" + matcher.group(1) + ">"));

        matcher.appendTail(builder);
        return builder.toString();
    }
}
