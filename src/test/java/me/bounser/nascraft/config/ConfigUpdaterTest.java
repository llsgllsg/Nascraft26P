package me.bounser.nascraft.config;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers the config schema upgrade: version comparison, backup creation, comment-preserving
 * text merge of missing keys and the YamlConfiguration fallback helpers.
 */
class ConfigUpdaterTest {

    private static final Logger LOGGER = Logger.getLogger("ConfigUpdaterTest");

    /** Bundled default config (as it would ship inside the jar). */
    private static final String DEFAULTS = """
            # Nascraft default config
            # 默认配置说明
            config-version: 2

            # 语言设置
            language: 'en_US'

            # 顶级键
            alpha: 1

            # 市场设置
            market:
              # 是否启用
              enabled: true
              # 手续费
              fee: 0.05
              # 允许的世界
              worlds:
                - world
                - world_nether

            # 列表键
            auto-features:
              - one
              - two
            """;

    /** Old config written by a previous plugin version: version 1, user values/comments. */
    private static final String LEGACY = """
            # 用户自己的头部注释
            config-version: 1

            # 用户改过的语言
            language: 'zh_CN'

            # 用户自定义键
            my-custom-key: keep-me

            # 用户自己写的市场设置
            market:
              enabled: false
              # 用户自己的注释：自定义键
              custom-option: 42
            """;

    private static InputStream stream(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void upgradesLegacyFileAndReportsMissingKeys(@TempDir Path dataFolder) throws IOException {
        Path config = dataFolder.resolve("config.yml");
        Files.writeString(config, LEGACY, StandardCharsets.UTF_8);

        ConfigUpdater.UpgradeResult result =
                ConfigUpdater.upgradeConfig(dataFolder.toFile(), stream(DEFAULTS), LOGGER);

        assertEquals(ConfigUpdater.Status.UPGRADED, result.status());
        assertEquals(1, result.oldVersion());
        assertEquals(2, result.newVersion());
        assertTrue(result.commentsPreserved());
        assertTrue(result.addedKeys().containsAll(
                List.of("alpha", "auto-features", "market.fee", "market.worlds")));
        assertFalse(result.addedKeys().contains("language"));

        String merged = Files.readString(config, StandardCharsets.UTF_8);
        YamlConfiguration parsed = new YamlConfiguration();
        assertDoesNotThrow(() -> parsed.loadFromString(merged));
        assertEquals(2, parsed.getInt("config-version"));
        assertEquals("zh_CN", parsed.getString("language"));
        assertEquals("keep-me", parsed.getString("my-custom-key"));
        assertEquals(1, parsed.getInt("alpha"));
        assertEquals(List.of("one", "two"), parsed.getStringList("auto-features"));
        assertFalse(parsed.getBoolean("market.enabled"));
        assertEquals(0.05, parsed.getDouble("market.fee"), 0.0001);
        assertEquals(List.of("world", "world_nether"), parsed.getStringList("market.worlds"));
        assertEquals(42, parsed.getInt("market.custom-option"));

        // User comments survive; comments of the newly inserted keys are copied from the defaults.
        assertTrue(merged.contains("# 用户自己的头部注释"));
        assertTrue(merged.contains("# 用户自己写的市场设置"));
        assertTrue(merged.contains("# 用户自己的注释：自定义键"));
        assertTrue(merged.contains("# 顶级键"));
        assertTrue(merged.contains("# 手续费"));
        assertTrue(merged.contains("# 列表键"));
        assertEquals(1, countOccurrences(merged, "config-version:"));

        // The old file is backed up next to the config, named after the old version.
        assertNotNull(result.backupFile());
        assertTrue(result.backupFile().getName().matches("config-backup-1-\\d{8}-\\d{6}\\.yml"));
        assertTrue(result.backupFile().isFile());
        String backup = Files.readString(result.backupFile().toPath(), StandardCharsets.UTF_8);
        assertEquals(ConfigUpdater.normalize(LEGACY), ConfigUpdater.normalize(backup));
    }

    @Test
    void doesNothingWhenVersionsAreEqual(@TempDir Path dataFolder) throws IOException {
        String current = "# 已是最新\nconfig-version: 2\n\nlanguage: 'zh_CN'\n";
        Path config = dataFolder.resolve("config.yml");
        Files.writeString(config, current, StandardCharsets.UTF_8);
        long stamp = 1_600_000_000_000L;
        assertTrue(config.toFile().setLastModified(stamp));

        ConfigUpdater.UpgradeResult result =
                ConfigUpdater.upgradeConfig(dataFolder.toFile(), stream(DEFAULTS), LOGGER);

        assertEquals(ConfigUpdater.Status.UP_TO_DATE, result.status());
        assertFalse(result.changed());
        assertEquals(stamp, config.toFile().lastModified());
        assertEquals(current, Files.readString(config, StandardCharsets.UTF_8));
        assertFalse(hasBackup(dataFolder));
    }

    @Test
    void onlyWarnsWhenDiskIsNewer(@TempDir Path dataFolder) throws IOException {
        String future = "# 未来版本\nconfig-version: 5\n\nlanguage: 'zh_CN'\n";
        Path config = dataFolder.resolve("config.yml");
        Files.writeString(config, future, StandardCharsets.UTF_8);

        ConfigUpdater.UpgradeResult result =
                ConfigUpdater.upgradeConfig(dataFolder.toFile(), stream(DEFAULTS), LOGGER);

        assertEquals(ConfigUpdater.Status.DISK_NEWER, result.status());
        assertEquals(5, result.oldVersion());
        assertFalse(result.changed());
        assertNull(result.backupFile());
        assertEquals(future, Files.readString(config, StandardCharsets.UTF_8));
        assertFalse(hasBackup(dataFolder));
    }

    @Test
    void doesNotCreateConfigOnFirstRun(@TempDir Path dataFolder) throws IOException {
        ConfigUpdater.UpgradeResult result =
                ConfigUpdater.upgradeConfig(dataFolder.toFile(), stream(DEFAULTS), LOGGER);

        assertEquals(ConfigUpdater.Status.FIRST_RUN, result.status());
        assertFalse(Files.exists(dataFolder.resolve("config.yml")));
        assertFalse(hasBackup(dataFolder));
    }

    @Test
    void mergesMissingKeysIntoConfigurationSections() throws InvalidConfigurationException {
        YamlConfiguration defaults = new YamlConfiguration();
        defaults.loadFromString(DEFAULTS);
        YamlConfiguration disk = new YamlConfiguration();
        disk.loadFromString(LEGACY);

        List<String> added = ConfigUpdater.mergeMissingKeys(defaults, disk);

        assertTrue(added.containsAll(List.of("alpha", "auto-features", "market.fee", "market.worlds")));
        assertEquals("zh_CN", disk.getString("language"));
        assertEquals("keep-me", disk.getString("my-custom-key"));
        assertFalse(disk.getBoolean("market.enabled"));
        assertEquals(42, disk.getInt("market.custom-option"));
        assertEquals(0.05, disk.getDouble("market.fee"), 0.0001);
        assertEquals(List.of("one", "two"), disk.getStringList("auto-features"));
        assertFalse(added.contains(ConfigUpdater.VERSION_KEY));
    }

    @Test
    void keepsUserScalarThatReplacesADefaultSection() throws InvalidConfigurationException {
        YamlConfiguration defaults = new YamlConfiguration();
        defaults.loadFromString(DEFAULTS);
        YamlConfiguration disk = new YamlConfiguration();
        disk.loadFromString("config-version: 1\nmarket: disabled-by-user\n");

        List<String> added = ConfigUpdater.mergeMissingKeys(defaults, disk);

        assertEquals("disabled-by-user", disk.getString("market"));
        assertTrue(added.stream().noneMatch(key -> key.startsWith("market.")));
    }

    @Test
    void textMergeIsIdempotentWhenNothingIsMissing() {
        String text = ConfigUpdater.normalize(DEFAULTS);

        ConfigUpdater.TextMergeResult result = ConfigUpdater.mergeMissingKeysIntoText(text, DEFAULTS);

        assertTrue(result.addedKeys().isEmpty());
        assertEquals(text, result.text());
    }

    @Test
    void textMergeInsertsMissingTopLevelKeyWithItsComment() {
        String disk = "config-version: 1\n\nlanguage: 'zh_CN'\n";

        ConfigUpdater.TextMergeResult result = ConfigUpdater.mergeMissingKeysIntoText(disk, DEFAULTS);

        assertTrue(result.text().contains("# 顶级键"));
        assertTrue(result.text().contains("alpha: 1"));
        assertIndexOrder(result.text(), "language: 'zh_CN'", "alpha: 1");
    }

    @Test
    void setConfigVersionKeepsInlineComment() {
        String text = "config-version: 1 # 配置文件结构版本\nlanguage: 'zh_CN'\n";

        String updated = ConfigUpdater.setConfigVersionInText(text, 2, DEFAULTS);

        assertTrue(updated.startsWith("config-version: 2 # 配置文件结构版本"));
        assertEquals(1, countOccurrences(updated, "config-version:"));
    }

    @Test
    void setConfigVersionInsertsLineAndDefaultCommentWhenAbsent() {
        String text = "# 用户头部注释\nlanguage: 'zh_CN'\n";

        String updated = ConfigUpdater.setConfigVersionInText(text, 2, DEFAULTS);

        assertTrue(updated.startsWith("# 用户头部注释"));
        assertTrue(updated.contains("# 默认配置说明"));
        assertTrue(updated.contains("config-version: 2"));
        assertIndexOrder(updated, "# 用户头部注释", "config-version: 2");
    }

    @Test
    void readsLegacyVersionWhenKeyIsMissingOrNotAnInteger() throws InvalidConfigurationException {
        YamlConfiguration empty = new YamlConfiguration();
        empty.loadFromString("language: 'zh_CN'\n");
        assertEquals(ConfigUpdater.LEGACY_VERSION, ConfigUpdater.getConfigVersion(empty));

        YamlConfiguration textVersion = new YamlConfiguration();
        textVersion.loadFromString("config-version: '2'\n");
        assertEquals(ConfigUpdater.LEGACY_VERSION, ConfigUpdater.getConfigVersion(textVersion));

        YamlConfiguration current = new YamlConfiguration();
        current.loadFromString("config-version: 3\n");
        assertEquals(3, ConfigUpdater.getConfigVersion(current));
    }

    private static boolean hasBackup(Path dataFolder) throws IOException {
        try (var files = Files.list(dataFolder)) {
            return files.anyMatch(path -> path.getFileName().toString().startsWith("config-backup-"));
        }
    }

    private static int countOccurrences(String text, String needle) {
        int count = 0;
        int index = text.indexOf(needle);
        while (index >= 0) {
            count++;
            index = text.indexOf(needle, index + 1);
        }
        return count;
    }

    private static void assertIndexOrder(String text, String first, String second) {
        int firstIndex = text.indexOf(first);
        int secondIndex = text.indexOf(second);
        assertTrue(firstIndex >= 0, "'" + first + "' missing from merged text:\n" + text);
        assertTrue(secondIndex >= 0, "'" + second + "' missing from merged text:\n" + text);
        assertTrue(firstIndex < secondIndex,
                "expected '" + first + "' before '" + second + "' in:\n" + text);
    }
}
