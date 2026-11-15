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
 *
 * <p>QQ ids are stored as {@link String}: personal QQ bots (NapCat / Lagrange / go-cqhttp)
 * report numeric QQ numbers, while QQ official bot adapters report {@code openid} strings
 * such as {@code 0A1B2C3D4E5F...}. Keeping them as text means both work with the same file.</p>
 */
public class OneBotStore {

    public static final int MAX_ALERTS = 8;

    private final File file;

    /** Minecraft UUID -> QQ number / openid. */
    private final Map<UUID, String> links = new ConcurrentHashMap<>();

    /** QQ number / openid -> (item identifier -> signed threshold). */
    private final Map<String, Map<String, Double>> alerts = new ConcurrentHashMap<>();

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
                    String qq = String.valueOf(linkSection.get(key)).trim();
                    if (qq.isEmpty() || "null".equals(qq)) continue;
                    links.put(UUID.fromString(key), qq);
                } catch (Exception ignored) {
                    // Ignore malformed entries instead of failing the whole bridge.
                }
            }
        }

        ConfigurationSection alertSection = data.getConfigurationSection("alerts");
        if (alertSection != null) {
            for (String key : alertSection.getKeys(false)) {
                String qq = key == null ? "" : key.trim();
                if (qq.isEmpty()) continue;

                ConfigurationSection items = alertSection.getConfigurationSection(key);
                if (items == null) continue;

                Map<String, Double> thresholds = new HashMap<>();
                for (String item : items.getKeys(false))
                    thresholds.put(item, items.getDouble(item));

                if (!thresholds.isEmpty()) alerts.put(qq, thresholds);
            }
        }
    }

    public String getQQ(UUID uuid) { return links.get(uuid); }

    public UUID getUUID(String qq) {
        if (qq == null) return null;
        for (Map.Entry<UUID, String> entry : links.entrySet())
            if (qq.equals(entry.getValue())) return entry.getKey();
        return null;
    }

    public synchronized boolean link(UUID uuid, String qq) {
        if (qq == null || qq.isBlank()) return false;

        UUID current = getUUID(qq);
        if (current != null && !current.equals(uuid)) return false;

        links.put(uuid, qq);
        saveAsync();
        return true;
    }

    public synchronized void unlink(UUID uuid) {
        String qq = links.remove(uuid);
        if (qq != null) alerts.remove(qq);
        saveAsync();
    }

    public synchronized void unlink(String qq) {
        UUID uuid = getUUID(qq);
        if (uuid != null) links.remove(uuid);
        alerts.remove(qq);
        saveAsync();
    }

    public int alertCount(String qq) {
        Map<String, Double> thresholds = alerts.get(qq);
        return thresholds == null ? 0 : thresholds.size();
    }

    /**
     * Stores a signed threshold: a positive value alerts when the price rises to it,
     * a negative value alerts when the price falls to its absolute value.
     */
    public synchronized boolean setAlert(String qq, String identifier, double threshold) {
        if (qq == null || qq.isBlank()) return false;

        Map<String, Double> thresholds = alerts.computeIfAbsent(qq, key -> new HashMap<>());
        if (!thresholds.containsKey(identifier) && thresholds.size() >= MAX_ALERTS) return false;

        thresholds.put(identifier, threshold);
        saveAsync();
        return true;
    }

    public synchronized boolean removeAlert(String qq, String identifier) {
        if (qq == null) return false;

        Map<String, Double> thresholds = alerts.get(qq);
        if (thresholds == null || thresholds.remove(identifier) == null) return false;

        if (thresholds.isEmpty()) alerts.remove(qq);
        saveAsync();
        return true;
    }

    public Map<String, Double> getAlerts(String qq) {
        Map<String, Double> thresholds = alerts.get(qq);
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
            if (plugin != null) plugin.getLogger().warning("[OneBot] 保存 onebot.yml 失败: " + e.getMessage());
        }
    }
}
