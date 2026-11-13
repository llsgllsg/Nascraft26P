package me.bounser.nascraft.onebot;

import me.bounser.nascraft.Nascraft;
import org.bukkit.configuration.file.YamlConfiguration;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Thin OneBot v11 HTTP API client.
 *
 * <p>Only what Nascraft needs is implemented: sending text to a private user or a group.
 * JSON is built by hand (the message is escaped properly) so the plugin does not need
 * to bundle a JSON library.</p>
 */
public class OneBotClient {

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private final String apiUrl;
    private final String accessToken;
    private final AtomicLong lastWarning = new AtomicLong();

    public OneBotClient(String apiUrl, String accessToken) {
        String url = apiUrl == null ? "" : apiUrl.trim();
        while (url.endsWith("/")) url = url.substring(0, url.length() - 1);
        this.apiUrl = url;
        this.accessToken = accessToken == null ? "" : accessToken.trim();
    }

    public void sendPrivate(long userId, String message) {
        send("send_private_msg", "{\"user_id\":" + userId + ",\"message\":" + segment(message) + "}");
    }

    public void sendGroup(long groupId, String message) {
        send("send_group_msg", "{\"group_id\":" + groupId + ",\"message\":" + segment(message) + "}");
    }

    public String getApiUrl() { return apiUrl; }

    private String segment(String message) {
        return "[{\"type\":\"text\",\"data\":{\"text\":\"" + escape(message) + "\"}}]";
    }

    private void send(String action, String body) {
        if (apiUrl.isEmpty()) return;

        HttpRequest request;
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(apiUrl + "/" + action))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));

            if (!accessToken.isEmpty() && !accessToken.equalsIgnoreCase("TOKEN"))
                builder.header("Authorization", "Bearer " + accessToken);

            request = builder.build();
        } catch (IllegalArgumentException e) {
            warn("OneBot API 地址无效: " + apiUrl);
            return;
        }

        http.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> handleResponse(action, response))
                .exceptionally(throwable -> {
                    warn("无法连接 OneBot（" + action + "）：" + throwable.getMessage());
                    return null;
                });
    }

    private void handleResponse(String action, HttpResponse<String> response) {
        if (response.statusCode() != 200) {
            warn("OneBot " + action + " 返回 HTTP " + response.statusCode() + "：" + abbreviate(response.body()));
            return;
        }

        String body = response.body();
        if (body == null || body.isBlank()) return;

        try {
            YamlConfiguration json = new YamlConfiguration();
            json.loadFromString(body);
            int retcode = json.getInt("retcode", 0);
            String status = json.getString("status", "");
            if (retcode != 0 || (!status.isEmpty() && !"ok".equalsIgnoreCase(status)))
                warn("OneBot " + action + " 返回异常：" + abbreviate(body));
        } catch (Exception ignored) {
            if (!body.contains("\"retcode\":0") && !body.contains("\"retcode\": 0"))
                warn("OneBot " + action + " 返回异常：" + abbreviate(body));
        }
    }

    private void warn(String message) {
        long now = System.currentTimeMillis();
        long last = lastWarning.get();
        if (last != 0L && now - last < 60_000L) return;
        if (!lastWarning.compareAndSet(last, now)) return;

        Nascraft plugin = Nascraft.getInstance();
        if (plugin != null) plugin.getLogger().warning("[OneBot] " + message);
    }

    private static String abbreviate(String text) {
        if (text == null) return "";
        return text.length() > 300 ? text.substring(0, 300) + "..." : text;
    }

    static String escape(String text) {
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
}
