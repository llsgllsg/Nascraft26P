package me.bounser.nascraft.qqofficial;

import me.bounser.nascraft.Nascraft;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/**
 * Thin wrapper around the QQ open platform HTTP API (v2 message API + gateway discovery).
 *
 * <p>Access tokens are valid for two hours. They are cached and refreshed lazily shortly before
 * they expire. All message sending is asynchronous so a slow QQ API never blocks the server
 * thread.</p>
 */
public final class QqOfficialApi {

    /** Production API base, as documented by the QQ open platform. */
    public static final String PRODUCTION_BASE = "https://api.bot.qq.com";
    /** Sandbox API base, used while the bot is still under review. */
    public static final String SANDBOX_BASE = "https://sandbox.api.sgroup.qq.com";

    private static final String PRIMARY_TOKEN_URL = "https://api.bot.qq.com/app/getAppAccessToken";
    private static final String LEGACY_TOKEN_URL = "https://bots.qq.com/app/getAppAccessToken";

    private static final int MESSAGE_TYPE_TEXT = 0;

    private final Logger logger = Logger.getLogger("Nascraft");
    private final String appId;
    private final String clientSecret;
    private final String apiBase;
    private final String tokenUrl;
    private final HttpClient http;
    private final AtomicInteger messageSequence = new AtomicInteger();

    private volatile String accessToken;
    private volatile long tokenExpiresAt;

    public QqOfficialApi(String appId, String clientSecret, String apiBase, String tokenUrl) {
        this.appId = appId == null ? "" : appId.trim();
        this.clientSecret = clientSecret == null ? "" : clientSecret.trim();
        this.apiBase = apiBase == null || apiBase.isBlank() ? PRODUCTION_BASE : trimTrailingSlash(apiBase.trim());
        this.tokenUrl = tokenUrl == null || tokenUrl.isBlank() ? PRIMARY_TOKEN_URL : tokenUrl.trim();
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public boolean isConfigured() {
        return !appId.isEmpty() && !clientSecret.isEmpty();
    }

    public String getApiBase() {
        return apiBase;
    }

    /**
     * Returns a valid access token, refreshing it when necessary.
     *
     * @throws IllegalStateException when no token can be obtained
     */
    public String accessToken() {
        String cached = accessToken;
        if (cached != null && System.currentTimeMillis() < tokenExpiresAt) return cached;

        synchronized (this) {
            if (accessToken != null && System.currentTimeMillis() < tokenExpiresAt) return accessToken;

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("appId", appId);
            body.put("clientSecret", clientSecret);
            String payload = Json.write(body);

            RuntimeException failure = null;
            for (String url : tokenUrls()) {
                try {
                    HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                            .timeout(Duration.ofSeconds(15))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(payload))
                            .build();

                    HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
                    if (response.statusCode() < 200 || response.statusCode() >= 300) {
                        failure = new IllegalStateException("token request to " + url + " returned "
                                + response.statusCode() + ": " + shorten(response.body()));
                        continue;
                    }

                    Map<String, Object> json = Json.object(response.body());
                    String token = Json.string(json, "access_token");
                    if (token == null || token.isBlank()) {
                        failure = new IllegalStateException("token response has no access_token: "
                                + shorten(response.body()));
                        continue;
                    }

                    long expiresIn = Json.number(json, 7200L, "expires_in");
                    if (expiresIn <= 0) expiresIn = 7200L;
                    // Refresh 60 seconds early; the platform keeps the previous token valid for that window.
                    accessToken = token;
                    tokenExpiresAt = System.currentTimeMillis() + (expiresIn - 60L) * 1000L;
                    return token;
                } catch (Exception e) {
                    failure = new IllegalStateException("token request to " + url + " failed: " + e.getMessage(), e);
                }
            }

            throw failure != null ? failure : new IllegalStateException("no token endpoint configured");
        }
    }

    /** Resolves the websocket gateway URL through {@code GET /gateway}. */
    public String gatewayUrl() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(apiBase + "/gateway"))
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", "QQBot " + accessToken())
                .header("X-Union-Appid", appId)
                .GET()
                .build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            if (response.statusCode() == 401) invalidateToken();
            throw new IllegalStateException("GET /gateway returned " + response.statusCode()
                    + ": " + shorten(response.body()));
        }

        String url = Json.string(Json.object(response.body()), "url");
        if (url == null || url.isBlank()) throw new IllegalStateException("gateway response has no url");
        return url;
    }

    /** Sends a passive (reply) or proactive message to a group. */
    public void sendGroupMessage(String groupOpenId, String content, String eventId) {
        Map<String, Object> body = messageBody(content, eventId);
        post("/v2/groups/" + groupOpenId + "/messages", body, "group message");
    }

    /** Sends a passive (reply) or proactive message to a single user. */
    public void sendC2CMessage(String userOpenId, String content, String eventId) {
        Map<String, Object> body = messageBody(content, eventId);
        post("/v2/users/" + userOpenId + "/messages", body, "c2c message");
    }

    private Map<String, Object> messageBody(String content, String eventId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("msg_type", MESSAGE_TYPE_TEXT);
        body.put("content", content == null ? "" : content);
        if (eventId != null && !eventId.isBlank()) {
            body.put("msg_id", eventId);
            body.put("msg_seq", messageSequence.incrementAndGet());
        }
        return body;
    }

    private void post(String path, Map<String, Object> body, String what) {
        try {
            String token = accessToken();
            HttpRequest request = HttpRequest.newBuilder(URI.create(apiBase + path))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "QQBot " + token)
                    .header("X-Union-Appid", appId)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(Json.write(body)))
                    .build();

            http.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenAccept(response -> {
                int code = response.statusCode();
                if (code >= 200 && code < 300) return;

                if (code == 401) invalidateToken();
                logger.warning("[QQ Official] " + what + " failed (" + code + "): " + shorten(response.body()));
            }).exceptionally(error -> {
                logger.warning("[QQ Official] " + what + " failed: " + error.getMessage());
                return null;
            });
        } catch (RuntimeException e) {
            logger.warning("[QQ Official] " + what + " failed: " + e.getMessage());
        }
    }

    private void invalidateToken() {
        accessToken = null;
        tokenExpiresAt = 0L;
    }

    private List<String> tokenUrls() {
        List<String> urls = new ArrayList<>(2);
        urls.add(tokenUrl);
        for (String fallback : new String[]{PRIMARY_TOKEN_URL, LEGACY_TOKEN_URL})
            if (!fallback.equals(tokenUrl)) urls.add(fallback);
        return urls;
    }

    private static String trimTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private static String shorten(String text) {
        if (text == null) return "";
        String compact = text.replace('\n', ' ').replace('\r', ' ').trim();
        return compact.length() <= 300 ? compact : compact.substring(0, 300) + "...";
    }
}
