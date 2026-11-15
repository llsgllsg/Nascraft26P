package me.bounser.nascraft.qqofficial;

import me.bounser.nascraft.Nascraft;
import me.bounser.nascraft.scheduler.FoliaScheduler;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Persistence for the QQ official bot bridge (account links and price alerts).
 *
 * <p>QQ open platform identifies users by an opaque {@code openid} string, so unlike the OneBot
 * store everything is keyed by that string. Data lives in {@code plugins/Nascraft/qqofficial.yml}
 * to keep the main database schema untouched.</p>
 */
public class QqOfficialStore {

    public static final int MAX_ALERTS = 8;

    private final File file;

    /** Minecraft UUID -> QQ openid. */
    private final Map<UUID, String> links = new ConcurrentHashMap<>();

    /** QQ openid -> (item identifier -> signed threshold). */
    private final Map<String, Map<String, Double>> alerts = new ConcurrentHashMap<>();

    public QqOfficialStore(File dataFolder) {
        this.file = new File(dataFolder, "qqofficial.yml");
        load();
    }

    private void load() {
        if (!file.exists()) return;

        YamlConfiguration data = YamlConfiguration.loadConfiguration(file);

        ConfigurationSection linkSection = data.getConfigurationSection("links");
        if (linkSection != null) {
            for (String key : linkSection.getKeys(false)) {
                String openId = String.valueOf(linkSection.get(key));
                if (openId.isBlank() || "null".equals(openId)) continue;
                try {
                    links.put(UUID.fromString(key), openId);
                } catch (IllegalArgumentException ignored) {
                    // Ignore malformed entries instead of failing the whole bridge.
                }
            }
        }

        ConfigurationSection alertSection = data.getConfigurationSection("alerts");
        if (alertSection != null) {
            for (String openId : alertSection.getKeys(false)) {
                ConfigurationSection items = alertSection.getConfigurationSection(openId);
                if (items == null) continue;

                Map<String, Double> thresholds = new HashMap<>();
                for (String item : items.getKeys(false))
                    thresholds.put(item, items.getDouble(item));

                if (!thresholds.isEmpty()) alerts.put(openId, thresholds);
            }
        }
    }

    public String getOpenId(UUID uuid) { return links.get(uuid); }

    public UUID getUUID(String openId) {
        if (openId == null) return null;
        for (Map.Entry<UUID, String> entry : links.entrySet())
            if (entry.getValue().equals(openId)) return entry.getKey();
        return null;
    }

    public synchronized boolean link(UUID uuid, String openId) {
        UUID current = getUUID(openId);
        if (current != null && !current.equals(uuid)) return false;

        links.put(uuid, openId);
        saveAsync();
        return true;
    }

    public synchronized void unlink(UUID uuid) {
        String openId = links.remove(uuid);
        if (openId != null) alerts.remove(openId);
        saveAsync();
    }

    public synchronized void unlink(String openId) {
        UUID uuid = getUUID(openId);
        if (uuid != null) links.remove(uuid);
        alerts.remove(openId);
        saveAsync();
    }

    public int alertCount(String openId) {
        Map<String, Double> thresholds = alerts.get(openId);
        return thresholds == null ? 0 : thresholds.size();
    }

    /**
     * Stores a signed threshold: a positive value alerts when the price rises to it,
     * a negative value alerts when the price falls to its absolute value.
     */
    public synchronized boolean setAlert(String openId, String identifier, double threshold) {
        Map<String, Double> thresholds = alerts.computeIfAbsent(openId, key -> new HashMap<>());
        if (!thresholds.containsKey(identifier) && thresholds.size() >= MAX_ALERTS) return false;

        thresholds.put(identifier, threshold);
        saveAsync();
        return true;
    }

    public synchronized boolean removeAlert(String openId, String identifier) {
        Map<String, Double> thresholds = alerts.get(openId);
        if (thresholds == null || thresholds.remove(identifier) == null) return false;

        if (thresholds.isEmpty()) alerts.remove(openId);
        saveAsync();
        return true;
    }

    public Map<String, Double> getAlerts(String openId) {
        Map<String, Double> thresholds = alerts.get(openId);
        return thresholds == null ? Collections.emptyMap() : new HashMap<>(thresholds);
    }

    public Map<String, Map<String, Double>> copyAlerts() {
        Map<String, Map<String, Double>> copy = new HashMap<>();
        for (Map.Entry<String, Map<String, Double>> entry : alerts.entrySet())
            copy.put(entry.getKey(), new HashMap<>(entry.getValue()));
        return copy;
    }

    public void saveAsync() {
        Nascraft plugin = Nascraft.getInstance();
        if (plugin == null || !plugin.isEnabled()) {
            saveNow();
            return;
        }
        FoliaScheduler.runAsync(plugin, this::saveNow);
    }

    public synchronized void saveNow() {
        try {
            YamlConfiguration data = new YamlConfiguration();

            ConfigurationSection linkSection = data.createSection("links");
            for (Map.Entry<UUID, String> entry : links.entrySet())
                linkSection.set(entry.getKey().toString(), entry.getValue());

            ConfigurationSection alertSection = data.createSection("alerts");
            for (Map.Entry<String, Map<String, Double>> entry : alerts.entrySet()) {
                ConfigurationSection items = alertSection.createSection(entry.getKey());
                for (Map.Entry<String, Double> threshold : entry.getValue().entrySet())
                    items.set(threshold.getKey(), threshold.getValue());
            }

            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs())
                return;

            data.save(file);
        } catch (IOException e) {
            Nascraft plugin = Nascraft.getInstance();
            if (plugin != null) plugin.getLogger().warning("[QQ Official] 保存 qqofficial.yml 失败: " + e.getMessage());
        }
    }
}
