package me.bounser.nascraft.crossserver;

public record AssetUpdate(String identifier, double stock, long version, String serverId) {

    public String toJson() {
        return "{\"id\":\"" + identifier + "\",\"s\":" + stock
                + ",\"v\":" + version + ",\"sid\":\"" + serverId + "\"}";
    }

    public static AssetUpdate parse(String json) {
        if (json == null) return null;
        try {
            String id  = extractString(json, "\"id\":");
            String s   = extractRaw(json, "\"s\":");
            String v   = extractRaw(json, "\"v\":");
            String sid = extractString(json, "\"sid\":");
            if (id == null || sid == null || s == null || v == null) return null;
            return new AssetUpdate(id, Double.parseDouble(s), Long.parseLong(v), sid);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String extractString(String json, String key) {
        int ki = json.indexOf(key);
        if (ki < 0) return null;
        int start = json.indexOf('"', ki + key.length());
        if (start < 0) return null;
        int end = json.indexOf('"', start + 1);
        if (end < 0) return null;
        return json.substring(start + 1, end);
    }

    private static String extractRaw(String json, String key) {
        int ki = json.indexOf(key);
        if (ki < 0) return null;
        int start = ki + key.length();
        while (start < json.length() && json.charAt(start) == ' ') start++;
        int end = start;
        while (end < json.length() && json.charAt(end) != ',' && json.charAt(end) != '}') end++;
        if (end == start) return null;
        return json.substring(start, end).trim();
    }
}
