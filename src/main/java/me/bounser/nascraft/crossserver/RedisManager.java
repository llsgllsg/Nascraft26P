package me.bounser.nascraft.crossserver;

import me.bounser.nascraft.Nascraft;
import me.bounser.nascraft.config.Config;
import me.bounser.nascraft.market.MarketManager;
import me.bounser.nascraft.market.unit.Item;
import me.bounser.nascraft.scheduler.FoliaScheduler;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.JedisPubSub;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Cross-server market synchronisation over Redis pub/sub.
 */
public final class RedisManager {

    private static final String CHANNEL = "nascraft:asset_update";
    private static final String VERSION_KEY_PREFIX = "nascraft:version:";

    private final Nascraft plugin;
    private final String serverId;
    private final Logger logger;

    private volatile JedisPool pool;
    private Thread subscriberThread;

    public RedisManager(Nascraft plugin, String serverId) {
        this.plugin = plugin;
        this.serverId = serverId;
        this.logger = plugin.getLogger();
    }

    public String getServerId() { return serverId; }

    public boolean isConnected() { return pool != null; }

    public void connect() {
        Config config = Config.getInstance();
        String host = config.getRedisHost();
        int port = config.getRedisPort();
        String password = config.getRedisPassword();

        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(8);
        poolConfig.setMinIdle(2);
        poolConfig.setTestOnBorrow(true);
        poolConfig.setTestWhileIdle(true);

        pool = (password == null || password.isBlank())
                ? new JedisPool(poolConfig, host, port, 3_000)
                : new JedisPool(poolConfig, host, port, 3_000, password);

        // Verify the connection before declaring success.
        try (var jedis = pool.getResource()) {
            jedis.ping();
        }

        startSubscriber();
        logger.info("[Redis] Connected to " + host + ":" + port + " as node '" + serverId + "'.");
    }

    public void disconnect() {
        if (subscriberThread != null) subscriberThread.interrupt();
        if (pool != null) {
            pool.close();
            pool = null;
        }
        logger.info("[Redis] Disconnected.");
    }

    public long nextGlobalVersion(String identifier) {
        JedisPool p = pool;
        if (p == null) return -1;
        try (var jedis = p.getResource()) {
            return jedis.incr(VERSION_KEY_PREFIX + identifier);
        } catch (Exception e) {
            logger.warning("[Redis] INCR version failed for '" + identifier + "': " + e.getMessage());
            return -1;
        }
    }

    public void seedVersionIfNeeded(String identifier, long minVersion) {
        JedisPool p = pool;
        if (p == null) return;
        try (var jedis = p.getResource()) {
            String key = VERSION_KEY_PREFIX + identifier;
            String current = jedis.get(key);
            long currentVal = current != null ? Long.parseLong(current) : 0;
            if (minVersion > currentVal) {
                jedis.set(key, String.valueOf(minVersion));
            }
        } catch (Exception e) {
            logger.warning("[Redis] seed version failed for '" + identifier + "': " + e.getMessage());
        }
    }

    public void publishAssetUpdate(String identifier, double stock, long version) {
        JedisPool p = pool;
        if (p == null) return;
        String payload = new AssetUpdate(identifier, stock, version, serverId).toJson();
        try (var jedis = p.getResource()) {
            jedis.publish(CHANNEL, payload);
        } catch (Exception e) {
            logger.warning("[Redis] publish failed for '" + identifier + "': " + e.getMessage());
        }
    }

    private void startSubscriber() {
        subscriberThread = Thread.ofVirtual()
                .name("nascraft-redis-subscriber")
                .start(this::subscribeLoop);
    }

    private void subscribeLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            JedisPool p = pool;
            if (p == null) break;
            try (var jedis = p.getResource()) {
                jedis.subscribe(new JedisPubSub() {
                    @Override
                    public void onMessage(String channel, String message) {
                        if (CHANNEL.equals(channel)) handleAssetUpdate(message);
                    }
                }, CHANNEL);
            } catch (Exception e) {
                if (Thread.currentThread().isInterrupted()) break;
                logger.warning("[Redis] Subscriber dropped, reconnecting in 5s: " + e.getMessage());
                try {
                    Thread.sleep(5_000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    private void handleAssetUpdate(String json) {
        AssetUpdate update = AssetUpdate.parse(json);
        if (update == null) {
            logger.warning("[Redis] Malformed asset-update packet: " + json);
            return;
        }

        // Ignore packets we published ourselves.
        if (serverId.equals(update.serverId())) return;

        // Apply on the main/global thread to keep market state single-threaded.
        FoliaScheduler.runGlobal(plugin, () -> {
            Item item = MarketManager.getInstance().getItem(update.identifier());
            if (item == null) return;
            boolean applied = item.getPrice().applyRemoteState(update.stock(), update.version());
            if (applied) {
                logger.log(Level.FINE, "[Redis] Applied remote state for '" + update.identifier()
                        + "' v" + update.version() + " from '" + update.serverId() + "'.");
            }
        });
    }
}
