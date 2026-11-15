package me.bounser.nascraft.qqofficial;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Small dependency free JSON reader/writer used by the QQ official bot bridge.
 *
 * <p>The plugin only talks to a handful of QQ open platform endpoints, so pulling in a full
 * JSON library is not worth it. This reader covers the subset of JSON produced by the
 * platform (objects, arrays, strings, numbers, booleans and null).</p>
 */
public final class Json {

    private Json() {
    }

    public static Object parse(String text) {
        if (text == null) return null;
        Parser parser = new Parser(text);
        Object value = parser.readValue();
        parser.skipWhitespace();
        return value;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> object(String text) {
        Object value = parse(text);
        if (value instanceof Map<?, ?> map) return (Map<String, Object>) map;
        return new LinkedHashMap<>();
    }

    public static String write(Object value) {
        StringBuilder builder = new StringBuilder();
        writeValue(builder, value);
        return builder.toString();
    }

    public static String escape(String text) {
        if (text == null) return "";

        StringBuilder builder = new StringBuilder(text.length() + 16);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"' -> builder.append("\\\"");
                case '\\' -> builder.append("\\\\");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                case '\b' -> builder.append("\\b");
                case '\f' -> builder.append("\\f");
                default -> {
                    if (c < 0x20) builder.append(String.format("\\u%04x", (int) c));
                    else builder.append(c);
                }
            }
        }
        return builder.toString();
    }

    public static Object get(Object node, String... path) {
        Object current = node;
        for (String key : path) {
            if (!(current instanceof Map<?, ?> map)) return null;
            current = map.get(key);
        }
        return current;
    }

    public static String string(Object node, String... path) {
        Object value = get(node, path);
        return value == null ? null : String.valueOf(value);
    }

    public static long number(Object node, long fallback, String... path) {
        Object value = get(node, path);
        if (value instanceof Number number) return number.longValue();
        if (value instanceof String text) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException ignored) {
                // not a number
            }
        }
        return fallback;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> map(Object node, String... path) {
        Object value = get(node, path);
        if (value instanceof Map<?, ?> map) return (Map<String, Object>) map;
        return new LinkedHashMap<>();
    }

    @SuppressWarnings("unchecked")
    public static List<Object> list(Object node, String... path) {
        Object value = get(node, path);
        if (value instanceof List<?> list) return (List<Object>) list;
        return new ArrayList<>();
    }

    static void writeValue(StringBuilder builder, Object value) {
        if (value == null) {
            builder.append("null");
        } else if (value instanceof Map<?, ?> map) {
            builder.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) builder.append(',');
                first = false;
                builder.append('"').append(escape(String.valueOf(entry.getKey()))).append("\":");
                writeValue(builder, entry.getValue());
            }
            builder.append('}');
        } else if (value instanceof Iterable<?> iterable) {
            builder.append('[');
            boolean first = true;
            for (Object element : iterable) {
                if (!first) builder.append(',');
                first = false;
                writeValue(builder, element);
            }
            builder.append(']');
        } else if (value instanceof Number number) {
            if (number instanceof Double || number instanceof Float) {
                double d = number.doubleValue();
                if (d == Math.rint(d) && !Double.isInfinite(d) && Math.abs(d) < 1e15) builder.append((long) d);
                else builder.append(number);
            } else {
                builder.append(number);
            }
        } else if (value instanceof Boolean bool) {
            builder.append(bool);
        } else {
            builder.append('"').append(escape(String.valueOf(value))).append('"');
        }
    }

    private static final class Parser {

        private final String text;
        private int pos;

        private Parser(String text) {
            this.text = text;
        }

        private Object readValue() {
            skipWhitespace();
            if (pos >= text.length()) return null;

            char c = text.charAt(pos);
            return switch (c) {
                case '{' -> readObject();
                case '[' -> readArray();
                case '"' -> readString();
                case 't' -> readLiteral("true", Boolean.TRUE);
                case 'f' -> readLiteral("false", Boolean.FALSE);
                case 'n' -> readLiteral("null", null);
                default -> readNumber();
            };
        }

        private Map<String, Object> readObject() {
            Map<String, Object> result = new LinkedHashMap<>();
            pos++; // {
            skipWhitespace();
            if (peek() == '}') {
                pos++;
                return result;
            }
            while (pos < text.length()) {
                skipWhitespace();
                String key = readString();
                skipWhitespace();
                if (peek() == ':') pos++;
                Object value = readValue();
                result.put(key, value);
                skipWhitespace();
                char c = peek();
                if (c == ',') {
                    pos++;
                    continue;
                }
                if (c == '}') {
                    pos++;
                    break;
                }
                break;
            }
            return result;
        }

        private List<Object> readArray() {
            List<Object> result = new ArrayList<>();
            pos++; // [
            skipWhitespace();
            if (peek() == ']') {
                pos++;
                return result;
            }
            while (pos < text.length()) {
                result.add(readValue());
                skipWhitespace();
                char c = peek();
                if (c == ',') {
                    pos++;
                    continue;
                }
                if (c == ']') {
                    pos++;
                    break;
                }
                break;
            }
            return result;
        }

        private String readString() {
            if (peek() != '"') return "";
            pos++;
            StringBuilder builder = new StringBuilder();
            while (pos < text.length()) {
                char c = text.charAt(pos++);
                if (c == '"') break;
                if (c != '\\') {
                    builder.append(c);
                    continue;
                }
                if (pos >= text.length()) break;
                char escaped = text.charAt(pos++);
                switch (escaped) {
                    case 'n' -> builder.append('\n');
                    case 'r' -> builder.append('\r');
                    case 't' -> builder.append('\t');
                    case 'b' -> builder.append('\b');
                    case 'f' -> builder.append('\f');
                    case 'u' -> {
                        if (pos + 4 <= text.length()) {
                            builder.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16));
                            pos += 4;
                        }
                    }
                    default -> builder.append(escaped);
                }
            }
            return builder.toString();
        }

        private Object readNumber() {
            int start = pos;
            while (pos < text.length()) {
                char c = text.charAt(pos);
                if ((c >= '0' && c <= '9') || c == '-' || c == '+' || c == '.' || c == 'e' || c == 'E') pos++;
                else break;
            }
            String raw = text.substring(start, pos);
            if (raw.isEmpty()) return null;
            if (raw.indexOf('.') >= 0 || raw.indexOf('e') >= 0 || raw.indexOf('E') >= 0) {
                try {
                    return Double.parseDouble(raw);
                } catch (NumberFormatException e) {
                    return null;
                }
            }
            try {
                return Long.parseLong(raw);
            } catch (NumberFormatException e) {
                return raw;
            }
        }

        private Object readLiteral(String literal, Object value) {
            if (text.startsWith(literal, pos)) {
                pos += literal.length();
                return value;
            }
            pos++;
            return null;
        }

        private char peek() {
            return pos < text.length() ? text.charAt(pos) : '\0';
        }

        private void skipWhitespace() {
            while (pos < text.length()) {
                char c = text.charAt(pos);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') pos++;
                else break;
            }
        }
    }
}
