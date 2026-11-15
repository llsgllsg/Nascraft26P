package me.bounser.nascraft.qqofficial;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Websocket client for the QQ official bot gateway.
 *
 * <p>Implements the documented handshake (Hello / Identify / Resume), heartbeats and the
 * reconnect rules. Events are handed to {@link QqOfficialBridge} which runs them on the
 * server thread.</p>
 */
public final class QqOfficialClient {

    private static final Logger LOGGER = Logger.getLogger("Nascraft");

    private static final int OP_DISPATCH = 0;
    private static final int OP_HEARTBEAT = 1;
    private static final int OP_IDENTIFY = 2;
    private static final int OP_RESUME = 6;
    private static final int OP_RECONNECT = 7;
    private static final int OP_INVALID_SESSION = 9;
    private static final int OP_HELLO = 10;
    private static final int OP_HEARTBEAT_ACK = 11;

    private final QqOfficialApi api;
    private final int intents;
    private final HttpClient http;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "Nascraft-QQOfficial-heartbeat");
        thread.setDaemon(true);
        return thread;
    });

    private volatile boolean running;
    private volatile WebSocket socket;
    private volatile CountDownLatch disconnected = new CountDownLatch(1);
    private volatile String sessionId;
    private volatile long sequence = -1;
    private volatile long heartbeatInterval = 45_000L;
    private volatile ScheduledFuture<?> heartbeatTask;
    private volatile Thread worker;

    public QqOfficialClient(QqOfficialApi api, int intents) {
        this.api = api;
        this.intents = intents <= 0 ? (1 << 25) : intents;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    public boolean isRunning() {
        return running;
    }

    public void start() {
        if (running) return;
        running = true;
        worker = new Thread(this::runLoop, "Nascraft-QQOfficial-WS");
        worker.setDaemon(true);
        worker.start();
    }

    public void stop() {
        if (!running) return;
        running = false;
        cancelHeartbeat();
        closeSocket();
        Thread thread = worker;
        if (thread != null) {
            try {
                thread.join(5000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        scheduler.shutdownNow();
    }

    private void runLoop() {
        AtomicInteger attempts = new AtomicInteger();
        while (running) {
            try {
                connectOnce();
                attempts.set(0);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                if (!running) return;
                LOGGER.warning("[QQ Official] 网关连接失败: " + describe(e));
            }
            if (!running) return;

            long seconds = Math.min(60L, 1L << Math.min(6, attempts.getAndIncrement()));
            try {
                Thread.sleep(seconds * 1000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void connectOnce() throws Exception {
        String url = api.gatewayUrl();
        if (url == null || url.isBlank()) throw new IllegalStateException("empty gateway url");

        CountDownLatch latch = new CountDownLatch(1);
        disconnected = latch;

        CompletableFuture<WebSocket> future = http.newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .buildAsync(URI.create(url), new GatewayListener());
        WebSocket webSocket = future.get(25L, TimeUnit.SECONDS);
        // onOpen 里通常会先一步把 socket 赋好；这里兜底，避免 Hello 先到、socket 还是 null
        if (socket == null) socket = webSocket;
        LOGGER.info("[QQ Official] 已连接 QQ 网关: " + url);

        latch.await();
        socket = null;
    }

    private void dispatch(Map<String, Object> payload) {
        String type = Json.string(payload, "t");
        if (type == null) return;

        if ("READY".equals(type)) {
            String newSession = Json.string(payload, "d", "session_id");
            if (newSession != null) sessionId = newSession;
            LOGGER.info("[QQ Official] 鉴权成功（session=" + sessionId + "）");
            return;
        }

        if ("RESUMED".equals(type)) {
            LOGGER.info("[QQ Official] 会话已恢复");
            return;
        }

        Map<String, Object> data = Json.map(payload, "d");
        QqOfficialBridge bridge = QqOfficialBridge.getInstance();
        if (bridge != null) bridge.onDispatch(type, data);
    }

    private void handlePayload(String text) {
        Map<String, Object> payload;
        try {
            payload = Json.object(text);
        } catch (RuntimeException e) {
            LOGGER.fine("[QQ Official] 无法解析网关消息: " + text);
            return;
        }

        int op = (int) Json.number(payload, -1, "op");
        long s = Json.number(payload, -1, "s");
        if (s >= 0) sequence = s;

        switch (op) {
            case OP_HELLO -> onHello(payload);
            case OP_HEARTBEAT_ACK -> { /* expected once per interval */ }
            case OP_HEARTBEAT -> sendRaw("{\"op\":1,\"d\":" + (sequence >= 0 ? sequence : "null") + "}");
            case OP_RECONNECT -> {
                LOGGER.info("[QQ Official] 服务端要求重连");
                cancelHeartbeat();
                closeSocket();
            }
            case OP_INVALID_SESSION -> {
                LOGGER.warning("[QQ Official] 会话失效，将重新鉴权");
                sessionId = null;
                sequence = -1;
                cancelHeartbeat();
                closeSocket();
            }
            case OP_DISPATCH -> dispatch(payload);
            default -> LOGGER.fine("[QQ Official] 未处理的 opcode: " + op);
        }
    }

    private void onHello(Map<String, Object> payload) {
        long interval = Json.number(payload, 45_000L, "d", "heartbeat_interval");
        heartbeatInterval = Math.max(5_000L, interval);
        startHeartbeat();
        LOGGER.info("[QQ Official] 收到 Hello（心跳 " + heartbeatInterval + "ms），开始"
                + (sessionId != null && sequence >= 0 ? "恢复会话" : "鉴权"));

        if (sessionId != null && sequence >= 0) sendResume();
        else sendIdentify();
    }

    private void sendIdentify() {
        Map<String, Object> data = new LinkedHashMap<>();
        String token;
        try {
            token = api.accessToken();
        } catch (RuntimeException e) {
            LOGGER.warning("[QQ Official] 获取 access_token 失败: " + e.getMessage());
            closeSocket();
            return;
        }

        data.put("token", "QQBot " + token);
        data.put("intents", intents);
        data.put("shard", List.of(0, 1));
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("$os", System.getProperty("os.name", "linux"));
        properties.put("$browser", "nascraft");
        properties.put("$device", "nascraft");
        data.put("properties", properties);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("op", OP_IDENTIFY);
        payload.put("d", data);
        if (sendRaw(Json.write(payload)))
            LOGGER.info("[QQ Official] 已发送 Identify（intents=" + intents + "）");
        else
            LOGGER.warning("[QQ Official] 网关尚未就绪，Identify 未能发出");
    }

    private void sendResume() {
        Map<String, Object> data = new LinkedHashMap<>();
        String token;
        try {
            token = api.accessToken();
        } catch (RuntimeException e) {
            LOGGER.warning("[QQ Official] 获取 access_token 失败: " + e.getMessage());
            sessionId = null;
            sequence = -1;
            closeSocket();
            return;
        }

        data.put("token", "QQBot " + token);
        data.put("session_id", sessionId);
        data.put("seq", sequence);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("op", OP_RESUME);
        payload.put("d", data);
        if (sendRaw(Json.write(payload)))
            LOGGER.info("[QQ Official] 已发送 Resume（seq=" + sequence + "）");
        else
            LOGGER.warning("[QQ Official] 网关尚未就绪，Resume 未能发出");
    }

    private void startHeartbeat() {
        cancelHeartbeat();
        heartbeatTask = scheduler.scheduleAtFixedRate(() -> {
            if (!running || socket == null) return;
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("op", OP_HEARTBEAT);
            payload.put("d", sequence >= 0 ? sequence : null);
            sendRaw(Json.write(payload));
        }, heartbeatInterval, heartbeatInterval, TimeUnit.MILLISECONDS);
    }

    private void cancelHeartbeat() {
        ScheduledFuture<?> task = heartbeatTask;
        if (task != null) {
            task.cancel(false);
            heartbeatTask = null;
        }
    }

    private boolean sendRaw(String text) {
        WebSocket webSocket = socket;
        if (webSocket == null) return false;
        try {
            webSocket.sendText(text, true).exceptionally(error -> {
                LOGGER.fine("[QQ Official] 发送网关消息失败: " + error.getMessage());
                return null;
            });
            return true;
        } catch (RuntimeException e) {
            LOGGER.fine("[QQ Official] 发送网关消息失败: " + e.getMessage());
            return false;
        }
    }

    private void closeSocket() {
        WebSocket webSocket = socket;
        if (webSocket == null) return;
        try {
            webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "bye");
        } catch (RuntimeException ignored) {
            // already closing
        }
        try {
            webSocket.abort();
        } catch (RuntimeException ignored) {
            // already closed
        }
    }

    private static String closeHint(int statusCode) {
        return switch (statusCode) {
            case 4004 -> " —— access_token 无效 / AppID 与 AppSecret 不匹配";
            case 4006 -> " —— 会话无效，将重新鉴权";
            case 4009 -> " —— 未在时限内完成鉴权（通常是 Identify 没发出去，或同一个机器人被别处占用）";
            case 4013 -> " —— intents 无效";
            case 4014 -> " —— intents 未被允许（去 q.qq.com 勾选对应事件后重新申请）";
            default -> "";
        };
    }

    private static String describe(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null && cause.getCause() != cause) cause = cause.getCause();
        String message = cause.getMessage();
        return cause.getClass().getSimpleName() + (message == null ? "" : ": " + message);
    }

    private final class GatewayListener implements WebSocket.Listener {

        private final StringBuilder buffer = new StringBuilder();

        @Override
        public void onOpen(WebSocket webSocket) {
            // 必须先登记 socket 再 request：Hello 帧可能在 buildAsync 返回之前就到达，
            // 那时 sendIdentify() 里的 sendRaw() 会因为 socket 还是 null 而把鉴权帧丢掉，
            // 表现就是「已连接 QQ 网关」之后没有「鉴权成功」，约 120s 后被 4009 Session timed out 踢下线。
            socket = webSocket;
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            buffer.append(data);
            if (last) {
                String text = buffer.toString();
                buffer.setLength(0);
                try {
                    handlePayload(text);
                } catch (RuntimeException e) {
                    LOGGER.log(Level.WARNING, "[QQ Official] 处理网关消息时出错", e);
                }
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            if (running) LOGGER.info("[QQ Official] 网关连接关闭（" + statusCode + "）" + reason
                    + closeHint(statusCode));
            if (statusCode == 4004 || statusCode == 4006 || statusCode == 4009) {
                // 鉴权失败 / 会话失效 / 未在时限内完成鉴权：丢掉旧会话，下次重连重新 Identify
                sessionId = null;
                sequence = -1;
            }
            cancelHeartbeat();
            disconnected.countDown();
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            if (running) LOGGER.warning("[QQ Official] 网关错误: " + describe(error));
            cancelHeartbeat();
            disconnected.countDown();
        }
    }
}
