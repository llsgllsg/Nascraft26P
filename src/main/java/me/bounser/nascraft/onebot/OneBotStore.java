package me.bounser.nascraft.onebot;

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
 * Persistence for the OneBot bridge (links and price alerts).
 *
 * <p>Data is kept in {@code plugins/Nascraft/onebot.yml} instead of the main database
 * so enabling the bridge does not require a schema migration.</p>
 */
public class OneBotStore {

    public static final int MAX_ALERTS = 8;

    private final File file;

    /** Minecraft UUID -> QQ number. */
    private final Map<UUID, Long> links = new ConcurrentHashMap<>();

    /** QQ number -> (item identifier -> signed threshold). */
    private final Map<Long, Map<String, Double>> alerts = new ConcurrentHashMap<>();

    public OneBotStore(File dataFolder) {
        this.file = new File(dataFolder, "onebot.yml");
        load();
    }

    private void load() {
        if (!file.exists()) return;

        YamlConfiguration data = YamlConfiguration.loadConfiguration(file);

        ConfigurationSection linkSection = data.getConfigurationSection("links");
        if (linkSection != null) {
            for (String key : linkSection.getKeys(false)) {
                try {
                    links.put(UUID.fromString(key), Long.parseLong(String.valueOf(linkSection.get(key))));
                } catch (Exception ignored) {
                    // Ignore malformed entries instead of failing the whole bridge.
                }
            }
        }

        ConfigurationSection alertSection = data.getConfigurationSection("alerts");
        if (alertSection != null) {
            for (String key : alertSection.getKeys(false)) {
                long qq;
                try {
                    qq = Long.parseLong(key);
                } catch (NumberFormatException e) {
                    continue;
                }

                ConfigurationSection items = alertSection.getConfigurationSection(key);
                if (items == null) continue;

                Map<String, Double> thresholds = new HashMap<>();
                for (String item : items.getKeys(false))
                    thresholds.put(item, items.getDouble(item));

                if (!thresholds.isEmpty()) alerts.put(qq, thresholds);
            }
        }
    }

    public Long getQQ(UUID uuid) { return links.get(uuid); }

    public UUID getUUID(long qq) {
        for (Map.Entry<UUID, Long> entry : links.entrySet())
            if (entry.getValue() == qq) return entry.getKey();
        return null;
    }

    public synchronized boolean link(UUID uuid, long qq) {
        UUID current = getUUID(qq);
        if (current != null && !current.equals(uuid)) return false;

        links.put(uuid, qq);
        saveAsync();
        return true;
    }

    public synchronized void unlink(UUID uuid) {
        Long qq = links.remove(uuid);
        if (qq != null) alerts.remove(qq);
        saveAsync();
    }

    public synchronized void unlink(long qq) {
        UUID uuid = getUUID(qq);
        if (uuid != null) links.remove(uuid);
        alerts.remove(qq);
        saveAsync();
    }

    public int alertCount(long qq) {
        Map<String, Double> thresholds = alerts.get(qq);
        return thresholds == null ? 0 : thresholds.size();
    }

    /**
     * Stores a signed threshold: a positive value alerts when the price rises to it,
     * a negative value alerts when the price falls to its absolute value.
     */
    public synchronized boolean setAlert(long qq, String identifier, double threshold) {
        Map<String, Double> thresholds = alerts.computeIfAbsent(qq, key -> new HashMap<>());
        if (!thresholds.containsKey(identifier) && thresholds.size() >= MAX_ALERTS) return false;

        thresholds.put(identifier, threshold);
        saveAsync();
        return true;
    }

    public synchronized boolean removeAlert(long qq, String identifier) {
        Map<String, Double> thresholds = alerts.get(qq);
        if (thresholds == null || thresholds.remove(identifier) == null) return false;

        if (thresholds.isEmpty()) alerts.remove(qq);
        saveAsync();
        return true;
    }

    public Map<String, Double> getAlerts(long qq) {
        Map<String, Double> thresholds = alerts.get(qq);
        return thresholds == null ? Collections.emptyMap() : new HashMap<>(thresholds);
    }

    public Map<Long, Map<String, Double>> copyAlerts() {
        Map<Long, Map<String, Double>> copy = new HashMap<>();
        for (Map.Entry<Long, Map<String, Double>> entry : alerts.entrySet())
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
            for (Map.Entry<UUID, Long> entry : links.entrySet())
                linkSection.set(entry.getKey().toString(), entry.getValue());

            ConfigurationSection alertSection = data.createSection("alerts");
            for (Map.Entry<Long, Map<String, Double>> entry : alerts.entrySet()) {
                ConfigurationSection items = alertSection.createSection(String.valueOf(entry.getKey()));
                for (Map.Entry<String, Double> threshold : entry.getValue().entrySet())
                    items.set(threshold.getKey(), threshold.getValue());
            }

            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs())
                return;

            data.save(file);
        } catch (IOException e) {
            Nascraft plugin = Nascraft.getInstance();
            if (plugin != null) plugin.getLogger().warning("[OneBot] 保存 onebot.yml 失败: " + e.getMessage());
        }
    }
}
