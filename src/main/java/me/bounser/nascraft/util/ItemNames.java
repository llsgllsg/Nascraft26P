package me.bounser.nascraft.util;

import me.bounser.nascraft.config.Config;
import me.bounser.nascraft.qqofficial.Json;
import org.bukkit.Material;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Official Minecraft translations used as the default market alias of every item.
 *
 * <p>The language tables under {@code /translations/names-<lang>.json} are generated from the
 * vanilla client language files, so an item called {@code diamond} is displayed and searchable as
 * {@code 钻石} without having to write an {@code alias} into {@code items.yml}. A configured alias
 * always wins. When a translation is missing the caller falls back to the historic
 * {@code Diamond Ore} style name.</p>
 */
public final class ItemNames {

    private static final String DEFAULT_LANGUAGE = "zh_cn";

    private static final Map<String, Map<String, String>> CACHE = new ConcurrentHashMap<>();

    private ItemNames() {}

    /**
     * Returns the official translated name of the item with the given market identifier, or
     * {@code null} when the identifier is not a valid material or has no translation.
     */
    public static String nameOf(String identifier) {

        if (identifier == null || identifier.isBlank()) return null;

        Material material = resolveMaterial(identifier);
        if (material == null) return null;

        Map<String, String> names = namesForCurrentLanguage();
        if (names.isEmpty()) return null;

        String name = names.get(material.getKey().getKey());
        if (name != null) return name;

        return names.get(identifier.toLowerCase(Locale.ROOT));
    }

    public static void clearCache() {
        CACHE.clear();
    }

    private static Material resolveMaterial(String identifier) {
        try {
            return Material.matchMaterial(identifier.replaceAll("\\d", "").toUpperCase(Locale.ROOT));
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static Map<String, String> namesForCurrentLanguage() {
        String language = currentLanguage();
        return CACHE.computeIfAbsent(language, ItemNames::load);
    }

    private static String currentLanguage() {
        try {
            Config config = Config.getInstance();
            if (config != null) {
                String selected = config.getSelectedLanguage();
                if (selected != null && !selected.isBlank())
                    return selected.toLowerCase(Locale.ROOT);
            }
        } catch (RuntimeException ignored) {
            // Config may not be ready yet; fall back to the shipped default.
        }
        return DEFAULT_LANGUAGE;
    }

    private static Map<String, String> load(String language) {
        Map<String, String> names = read("names-" + language + ".json");
        if (!names.isEmpty()) return names;

        if (!DEFAULT_LANGUAGE.equals(language)) {
            names = read("names-" + DEFAULT_LANGUAGE + ".json");
            if (!names.isEmpty()) return names;
        }

        return Collections.emptyMap();
    }

    private static Map<String, String> read(String fileName) {
        String path = "/translations/" + fileName;

        try (InputStream stream = ItemNames.class.getResourceAsStream(path)) {
            if (stream == null) return Collections.emptyMap();

            String text = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            Map<String, Object> raw = Json.object(text);

            Map<String, String> names = new HashMap<>(raw.size());
            for (Map.Entry<String, Object> entry : raw.entrySet())
                if (entry.getValue() instanceof String value) names.put(entry.getKey(), value);

            return names;
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }
}
