package me.bounser.nascraft.onebot;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import me.bounser.nascraft.Nascraft;
import me.bounser.nascraft.config.Config;
import me.bounser.nascraft.scheduler.FoliaScheduler;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Receives OneBot v11 event reports over HTTP ("HTTP 上报") and dispatches chat commands.
 *
 * <p>The server is intentionally tiny and dependency free: it uses the JDK's built-in
 * {@link HttpServer}. Events are parsed with {@link YamlConfiguration} because a JSON
 * document is also valid YAML; this avoids adding a JSON library just for the bridge.</p>
 */
public class OneBotWebhook {

    private HttpServer server;
    private ExecutorService executor;

    public void start() throws IOException {
        Config config = Config.getInstance();

        String host = config.getOneBotWebhookHost();
        int port = config.getOneBotWebhookPort();

        String path = config.getOneBotWebhookPath();
        if (path == null || path.isEmpty()) path = "/onebot";
        if (!path.startsWith("/")) path = "/" + path;

        try {
            server = HttpServer.create(new InetSocketAddress(host, port), 0);
        } catch (java.net.BindException e) {
            throw new IOException("端口 " + host + ":" + port + " 已被占用"
                    + "（常见原因：另一个 QQ 机器人插件/OneBot 适配器的 Webhook 也监听同一端口，"
                    + "或者上一次的服务器进程还没退出）；请修改 config.yml 的 onebot.webhook.port（例如 8766）"
                    + "并同步修改机器人上报地址，或者把 onebot.webhook.enabled 设为 false。", e);
        }
        server.createContext(path, this::handle);

        executor = Executors.newFixedThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "Nascraft-OneBot-Webhook");
            thread.setDaemon(true);
            return thread;
        });
        server.setExecutor(executor);
        server.start();

        Nascraft.getInstance().getLogger().info("[OneBot] Webhook 监听 http://" + host + ":" + port + path);
    }

    public void stop() {
        if (server != null) server.stop(0);
        if (executor != null) executor.shutdown();
    }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                respond(exchange, 405);
                return;
            }

            if (!authorized(exchange)) {
                respond(exchange, 403);
                return;
            }

            byte[] body = exchange.getRequestBody().readAllBytes();
            if (body.length == 0 || body.length > 1024 * 1024) {
                respond(exchange, 400);
                return;
            }

            YamlConfiguration json;
            try {
                json = new YamlConfiguration();
                json.loadFromString(new String(body, StandardCharsets.UTF_8));
            } catch (Exception e) {
                respond(exchange, 400);
                return;
            }

            // Acknowledge first: OneBot implementations expect a fast 200.
            respond(exchange, 200);

            if (!"message".equals(json.getString("post_type"))) return;
            if (!Config.getInstance().getOneBotCommandsEnabled()) return;

            // Ids may be numeric (personal QQ bots) or openid strings (QQ official bot adapters).
            String selfId = json.getString("self_id", "");
            String userId = json.getString("user_id", "");
            if (userId == null || userId.isBlank() || userId.equals(selfId)) return;

            String messageType = json.getString("message_type", "");
            String groupId = null;

            if ("group".equals(messageType)) {
                String group = json.getString("group_id", "");
                if (group == null || group.isBlank() || "0".equals(group)) return;

                List<String> whitelist = Config.getInstance().getOneBotGroupWhitelist();
                if (!whitelist.isEmpty() && !whitelist.contains(group)) return;
                groupId = group;
            } else if (!"private".equals(messageType)) {
                return;
            }

            String raw = json.getString("raw_message", "");
            if (raw == null) raw = "";

            // Strip CQ codes (at-mentions, images, replies...) and keep the plain text.
            String text = raw.replaceAll("\\[CQ:[^\\]]*\\]", "").trim();
            if (text.isEmpty()) return;

            String finalGroup = groupId;
            FoliaScheduler.runGlobal(Nascraft.getInstance(), () ->
                    OneBotCommands.handle(userId, finalGroup, text));
        } catch (Exception e) {
            Nascraft plugin = Nascraft.getInstance();
            if (plugin != null) plugin.getLogger().warning("[OneBot] 处理上报失败: " + e.getMessage());
        }
    }

    private boolean authorized(HttpExchange exchange) {
        String secret = Config.getInstance().getOneBotWebhookSecret();
        if (secret == null || secret.isEmpty() || secret.equalsIgnoreCase("SECRET")) return true;

        String authorization = exchange.getRequestHeaders().getFirst("Authorization");
        if (authorization != null
                && (authorization.equals("Bearer " + secret) || authorization.equals(secret)))
            return true;

        String query = exchange.getRequestURI().getQuery();
        if (query == null) return false;

        for (String pair : query.split("&")) {
            String[] parts = pair.split("=", 2);
            if (parts.length != 2) continue;
            if (!"access_token".equals(parts[0]) && !"secret".equals(parts[0])) continue;

            try {
                if (secret.equals(URLDecoder.decode(parts[1], StandardCharsets.UTF_8))) return true;
            } catch (IllegalArgumentException ignored) {
                // Malformed percent-encoding: treat as unauthorized.
            }
        }
        return false;
    }

    private void respond(HttpExchange exchange, int status) throws IOException {
        byte[] payload = "{}".getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, payload.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(payload);
        }
    }
}
