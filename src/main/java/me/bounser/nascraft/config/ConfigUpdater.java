package me.bounser.nascraft.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Keeps a server's {@code config.yml} in sync with the config.yml bundled in the jar.
 *
 * <p>The resource file carries a top-level {@value #VERSION_KEY} integer. When a plugin
 * update ships a higher version than the file on disk, the disk file is first backed up
 * and then every key that exists in the bundled default but not on disk is merged in.
 * User values, user-added keys and (most importantly) the Chinese comments in the file
 * are preserved, because the merge is done on the raw text instead of re-serialising the
 * parsed {@link YamlConfiguration} (which drops every comment). Only if the text merge
 * cannot be applied does the updater fall back to a {@code YamlConfiguration} merge and
 * say so in the log.
 *
 * <p>Version rules:
 * <ul>
 *   <li>disk &lt; bundled: back up, merge missing keys, write the new version</li>
 *   <li>disk == bundled: do absolutely nothing (no rewrite, no IO)</li>
 *   <li>disk &gt; bundled: warn only, never touch the file</li>
 *   <li>no file on disk: nothing to upgrade (first generation is handled by
 *       {@code JavaPlugin#saveDefaultConfig()})</li>
 * </ul>
 *
 * <p>Files without {@code config-version} (configs written before this updater existed)
 * are treated as version {@value #LEGACY_VERSION}.
 */
public final class ConfigUpdater {

    /** Top-level key holding the config schema version. */
    public static final String VERSION_KEY = "config-version";

    /** Version assumed for config.yml files that do not have a config-version key yet. */
    public static final int LEGACY_VERSION = 1;

    /** Name prefix of the backup file created before an upgrade. */
    public static final String BACKUP_PREFIX = "config-backup-";

    private static final DateTimeFormatter BACKUP_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    /** Indentation used when a missing key has to be inserted below an existing section. */
    private static final int INDENT_STEP = 2;

    /** Simple block-mapping key line: {@code key:} or {@code key: value}. */
    private static final Pattern KEY_LINE = Pattern.compile("^([A-Za-z0-9_.-]+):(.*)$");

    /** Matches the existing version line so it can be rewritten without losing an inline comment. */
    private static final Pattern VERSION_LINE =
            Pattern.compile("^(\\s*" + VERSION_KEY + "\\s*:\\s*)([^#]*?)(\\s*#.*)?$");

    /** YAML block scalar indicator, e.g. {@code |} or {@code >-}. */
    private static final Pattern BLOCK_SCALAR = Pattern.compile("^[|>][+-]?\\d*$");

    /** Outcome of an upgrade attempt. */
    public enum Status {
        /** There is no config.yml on disk yet; nothing was touched. */
        FIRST_RUN,
        /** Disk and bundled schema versions are equal; the file was not touched. */
        UP_TO_DATE,
        /** The disk file was backed up and merged up to the bundled version. */
        UPGRADED,
        /** The disk file is newer than this plugin; it was only warned about. */
        DISK_NEWER,
        /** The bundled default or the disk file could not be read; nothing was touched. */
        FAILED
    }

    /**
     * Result of {@link #upgradeConfig(JavaPlugin)}.
     *
     * @param status            what the updater decided
     * @param oldVersion        schema version found on disk
     * @param newVersion        schema version of the bundled default config
     * @param addedKeys         dotted paths of the keys that were merged in
     * @param conflicts         dotted paths skipped because the existing value has a different type
     * @param backupFile        backup created before the upgrade, or {@code null}
     * @param commentsPreserved {@code false} only when the text merge failed and the
     *                          YamlConfiguration fallback rewrote the file
     * @param message           short human-readable explanation (also logged)
     */
    public record UpgradeResult(Status status,
                                int oldVersion,
                                int newVersion,
                                List<String> addedKeys,
                                List<String> conflicts,
                                File backupFile,
                                boolean commentsPreserved,
                                String message) {

        public boolean changed() {
            return status == Status.UPGRADED;
        }
    }

    /**
     * Result of the comment-preserving text merge.
     *
     * @param text       merged YAML document (comments from the original file kept)
     * @param addedKeys  dotted paths that were inserted
     * @param conflicts  dotted paths skipped because the disk value has a different type
     */
    public record TextMergeResult(String text, List<String> addedKeys, List<String> conflicts) {

        public boolean changed() {
            return !addedKeys.isEmpty();
        }
    }

    /** A key line found while scanning a YAML document, with the information needed to re-indent it. */
    private record KeyLine(int index, int indent, String key, String line) {
    }

    private ConfigUpdater() {
    }

    // ------------------------------------------------------------------
    // Entry point used by Config
    // ------------------------------------------------------------------

    /**
     * Compares {@code plugins/Nascraft/config.yml} with the config.yml bundled in the jar and
     * upgrades the former when it is older. Safe to call on every startup/reload: a file that
     * is already up to date is never rewritten.
     */
    public static UpgradeResult upgradeConfig(JavaPlugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        try (InputStream defaults = plugin.getResource("config.yml")) {
            if (defaults == null) {
                String message = "Bundled config.yml is missing; config upgrade skipped.";
                plugin.getLogger().warning(message);
                return new UpgradeResult(Status.FAILED, -1, -1, List.of(), List.of(), null,
                        true, message);
            }
            return upgradeConfig(plugin.getDataFolder(), defaults, plugin.getLogger());
        } catch (IOException e) {
            String message = "Could not read config files; config upgrade skipped: " + e.getMessage();
            plugin.getLogger().warning(message);
            return new UpgradeResult(Status.FAILED, -1, -1, List.of(), List.of(), null,
                    true, message);
        }
    }

    /**
     * Testable overload. {@code defaultConfigStream} must supply the bundled default config.yml
     * as UTF-8 text. Package-private on purpose: the plugin always goes through
     * {@link #upgradeConfig(JavaPlugin)}, tests use this directly with a temp folder.
     */
    static UpgradeResult upgradeConfig(File dataFolder, InputStream defaultConfigStream, Logger logger)
            throws IOException {
        Objects.requireNonNull(dataFolder, "dataFolder");
        Objects.requireNonNull(defaultConfigStream, "defaultConfigStream");
        Objects.requireNonNull(logger, "logger");

        File diskFile = new File(dataFolder, "config.yml");

        if (!diskFile.isFile()) {
            return new UpgradeResult(Status.FIRST_RUN, -1, -1, List.of(), List.of(), null,
                    true, "config.yml does not exist yet; first generation is left to saveDefaultConfig()");
        }

        String defaultText = normalize(new String(defaultConfigStream.readAllBytes(), StandardCharsets.UTF_8));
        String diskText = normalize(Files.readString(diskFile.toPath(), StandardCharsets.UTF_8));

        YamlConfiguration defaults;
        YamlConfiguration disk;
        try {
            defaults = loadYaml(defaultText);
            disk = loadYaml(diskText);
        } catch (IllegalArgumentException e) {
            String message = "config.yml could not be parsed; upgrade skipped: " + e.getMessage();
            logger.warning(message);
            return new UpgradeResult(Status.FAILED, -1, -1, List.of(), List.of(), null,
                    true, message);
        }

        int defaultVersion = getConfigVersion(defaults);
        int diskVersion = getConfigVersion(disk);

        if (diskVersion == defaultVersion) {
            return new UpgradeResult(Status.UP_TO_DATE, diskVersion, defaultVersion,
                    List.of(), List.of(), null, true,
                    "config.yml is already at schema version " + diskVersion + "; nothing to do");
        }

        if (diskVersion > defaultVersion) {
            String message = "config.yml has schema version " + diskVersion
                    + ", which is newer than this plugin's bundled schema version " + defaultVersion
                    + ". The file was left untouched; unknown values may be ignored.";
            logger.warning(message);
            return new UpgradeResult(Status.DISK_NEWER, diskVersion, defaultVersion,
                    List.of(), List.of(), null, true, message);
        }

        // diskVersion < defaultVersion -> back up first, then merge.
        File backup = nextBackupFile(dataFolder, diskVersion);
        Files.copy(diskFile.toPath(), backup.toPath());
        logger.info("Backed up old config.yml to " + backup.getName());

        List<String> added;
        List<String> conflicts;
        boolean commentsPreserved = true;

        try {
            TextMergeResult merged = mergeMissingKeysIntoText(diskText, defaultText);
            added = merged.addedKeys();
            conflicts = merged.conflicts();
            String upgradedText = setConfigVersionInText(merged.text(), defaultVersion, defaultText);
            Files.writeString(diskFile.toPath(), ensureTrailingNewline(upgradedText),
                    StandardCharsets.UTF_8);
        } catch (RuntimeException e) {
            // The text merge depends on the default file being written in plain block style.
            // If that ever changes, still upgrade the values (comments will be lost) instead
            // of leaving the user on an old schema.
            commentsPreserved = false;
            logger.warning("Could not preserve comments while upgrading config.yml ("
                    + e.getMessage() + "); falling back to a YamlConfiguration merge. "
                    + "Comments in config.yml will be lost for this upgrade.");

            YamlConfiguration fallback = loadYaml(diskText);
            added = mergeMissingKeys(defaults, fallback);
            conflicts = List.of();
            fallback.set(VERSION_KEY, defaultVersion);
            fallback.save(diskFile);
        }

        if (added.isEmpty()) {
            logger.info("Config schema upgraded from v" + diskVersion + " to v" + defaultVersion
                    + " (no new keys were needed).");
        } else {
            logger.info("Config schema upgraded from v" + diskVersion + " to v" + defaultVersion
                    + "; added " + added.size() + " missing key(s): " + String.join(", ", added));
        }
        if (!conflicts.isEmpty()) {
            logger.warning("These config keys were left untouched because the existing value has "
                    + "a different type than the bundled default: " + String.join(", ", conflicts));
        }

        String message = "config.yml upgraded from schema v" + diskVersion + " to v" + defaultVersion
                + " (backup: " + backup.getName() + ", added keys: " + added.size() + ")";
        return new UpgradeResult(Status.UPGRADED, diskVersion, defaultVersion,
                List.copyOf(added), List.copyOf(conflicts), backup, commentsPreserved, message);
    }

    // ------------------------------------------------------------------
    // Version helpers
    // ------------------------------------------------------------------

    /**
     * Reads the schema version of a parsed config. Files without the key (or with a
     * non-integer value) are treated as {@link #LEGACY_VERSION}.
     */
    public static int getConfigVersion(ConfigurationSection section) {
        if (section == null || !section.contains(VERSION_KEY) || !section.isInt(VERSION_KEY)) {
            return LEGACY_VERSION;
        }
        return section.getInt(VERSION_KEY);
    }

    // ------------------------------------------------------------------
    // Merge logic, shared by the text merge and the YamlConfiguration fallback
    // ------------------------------------------------------------------

    /**
     * Merges every key present in {@code defaults} but missing from {@code target} into
     * {@code target}, recursing through nested sections. Existing values (including lists
     * and user-added keys) are never overwritten or removed.
     *
     * <p>When the default is a section but the user value at that path is a scalar, the
     * path is reported as a conflict and skipped instead of being replaced. The
     * {@value #VERSION_KEY} key is intentionally not merged here: the updater writes it
     * itself after merging.
     *
     * @return dotted paths of the keys that were added, in default-file order
     */
    public static List<String> mergeMissingKeys(ConfigurationSection defaults, ConfigurationSection target) {
        Objects.requireNonNull(defaults, "defaults");
        Objects.requireNonNull(target, "target");
        List<String> added = new ArrayList<>();
        mergeSection(defaults, target, "", added, new ArrayList<>());
        return added;
    }

    private static void mergeSection(ConfigurationSection defaults,
                                     ConfigurationSection target,
                                     String prefix,
                                     List<String> added,
                                     List<String> conflicts) {
        for (String key : defaults.getKeys(false)) {
            String path = childPath(prefix, key);
            if (isVersionPath(path)) continue;

            ConfigurationSection defaultChild = defaults.getConfigurationSection(key);
            Object targetRaw = target.get(key);
            ConfigurationSection targetChild = target.getConfigurationSection(key);

            if (defaultChild != null) {
                if (targetChild == null) {
                    if (targetRaw != null) {
                        // User replaced a section with a plain value: keep their value.
                        conflicts.add(path);
                        continue;
                    }
                    targetChild = target.createSection(key);
                }
                mergeSection(defaultChild, targetChild, path, added, conflicts);
            } else if (!target.contains(key)) {
                target.set(key, copyValue(defaults.get(key)));
                added.add(path);
            }
        }
    }

    /**
     * Collects the highest-level default paths that are absent from {@code target}. A missing
     * section is reported once with its whole subtree instead of one entry per leaf, so the
     * text merge can copy the section and its comments as a block.
     */
    private static void collectMissingRoots(ConfigurationSection defaults,
                                            ConfigurationSection target,
                                            String prefix,
                                            List<String> missing,
                                            List<String> conflicts) {
        for (String key : defaults.getKeys(false)) {
            String path = childPath(prefix, key);
            if (isVersionPath(path)) continue;

            ConfigurationSection defaultChild = defaults.getConfigurationSection(key);
            Object targetRaw = target.get(key);
            ConfigurationSection targetChild = target.getConfigurationSection(key);

            if (defaultChild != null) {
                if (targetChild == null) {
                    if (targetRaw != null) {
                        conflicts.add(path);
                    } else {
                        missing.add(path);
                    }
                } else {
                    collectMissingRoots(defaultChild, targetChild, path, missing, conflicts);
                }
            } else if (!target.contains(key)) {
                missing.add(path);
            }
        }
    }

    // ------------------------------------------------------------------
    // Comment-preserving text merge
    // ------------------------------------------------------------------

    /**
     * Text-level counterpart of {@link #mergeMissingKeys(ConfigurationSection, ConfigurationSection)}:
     * inserts the missing keys (with the comments that precede them in {@code defaultText}) into
     * {@code diskText} without rewriting or reformatting the rest of the file. Comments and
     * formatting of existing lines are therefore preserved.
     *
     * <p>New keys are inserted at the end of the closest existing parent section, or appended at
     * the end of the document for top-level keys. The merged document is returned; nothing is read
     * from or written to disk here.
     *
     * @throws IllegalStateException if the default file is not written in the block style this
     *                               merge understands (caller falls back to YamlConfiguration)
     */
    public static TextMergeResult mergeMissingKeysIntoText(String diskText, String defaultText) {
        String original = normalize(diskText);
        String bundled = normalize(defaultText);

        YamlConfiguration defaults = loadYaml(bundled);
        YamlConfiguration disk = loadYaml(original);

        List<String> missing = new ArrayList<>();
        List<String> conflicts = new ArrayList<>();
        collectMissingRoots(defaults, disk, "", missing, conflicts);

        List<String> lines = new ArrayList<>(Arrays.asList(original.split("\n", -1)));
        Map<String, KeyLine> defaultIndex = indexKeyLines(bundled);
        List<String> added = new ArrayList<>();

        for (String path : missing) {
            KeyLine source = defaultIndex.get(path);
            if (source == null) {
                throw new IllegalStateException("Bundled config does not contain key '" + path + "'");
            }
            List<String> block = defaultBlock(bundled, defaultIndex, path);

            // Re-index after every insertion: line numbers of the following insertions shift.
            Map<String, KeyLine> currentIndex = indexKeyLines(String.join("\n", lines));
            String parent = parentPath(path);

            int insertAt;
            int targetIndent;
            if (parent.isEmpty()) {
                insertAt = topLevelAppendIndex(lines);
                targetIndent = 0;
            } else {
                KeyLine parentLine = currentIndex.get(parent);
                if (parentLine == null) {
                    throw new IllegalStateException("Could not locate section '" + parent
                            + "' in config.yml");
                }
                if (!isSectionLine(parentLine.line)) {
                    throw new IllegalStateException("Section '" + parent
                            + "' is not written in block style");
                }
                insertAt = sectionBlockEnd(lines, parentLine);
                targetIndent = parentLine.indent + INDENT_STEP;
            }

            List<String> shifted = shiftBlock(block, targetIndent - source.indent);
            List<String> toInsert = new ArrayList<>(shifted.size() + 1);
            if (insertAt > 0 && !lines.get(insertAt - 1).isBlank()) {
                toInsert.add("");
            }
            toInsert.addAll(shifted);
            lines.addAll(insertAt, toInsert);
            added.add(path);
        }

        return new TextMergeResult(String.join("\n", lines), List.copyOf(added), List.copyOf(conflicts));
    }

    /**
     * Replaces the value of the top-level {@value #VERSION_KEY} line, or inserts the line near
     * the top of the document when it is missing. Existing comments and inline comments are kept.
     * When a comment block for the key exists in {@code defaultText}, it is copied along with the
     * inserted line.
     */
    public static String setConfigVersionInText(String text, int version, String defaultText) {
        List<String> lines = new ArrayList<>(Arrays.asList(normalize(text).split("\n", -1)));
        Map<String, KeyLine> index = indexKeyLines(String.join("\n", lines));
        KeyLine existing = index.get(VERSION_KEY);

        if (existing != null) {
            String line = lines.get(existing.index);
            Matcher matcher = VERSION_LINE.matcher(line);
            if (!matcher.matches()) {
                throw new IllegalStateException("Unsupported config-version line: '" + line + "'");
            }
            String inlineComment = matcher.group(3) == null ? "" : matcher.group(3);
            lines.set(existing.index, matcher.group(1) + version + inlineComment);
            return String.join("\n", lines);
        }

        int insertAt = 0;
        while (insertAt < lines.size() && isComment(lines.get(insertAt))) {
            insertAt++;
        }
        while (insertAt < lines.size() && lines.get(insertAt).isBlank()) {
            insertAt++;
        }

        List<String> toInsert = new ArrayList<>();
        toInsert.addAll(versionCommentBlock(defaultText));
        toInsert.add(VERSION_KEY + ": " + version);
        toInsert.add("");
        lines.addAll(insertAt, toInsert);
        return String.join("\n", lines);
    }

    private static List<String> versionCommentBlock(String defaultText) {
        List<String> comments = new ArrayList<>();
        if (defaultText == null) return comments;

        String bundled = normalize(defaultText);
        KeyLine source = indexKeyLines(bundled).get(VERSION_KEY);
        if (source == null) return comments;

        List<String> lines = Arrays.asList(bundled.split("\n", -1));
        for (int i = source.index - 1; i >= 0; i--) {
            String line = lines.get(i);
            if (!isComment(line) || indentOf(line) != source.indent) break;
            comments.add(0, line);
        }
        return comments;
    }

    /** Extracts the comment block and value/subtree of a key from the bundled config text. */
    private static List<String> defaultBlock(String text, Map<String, KeyLine> index, String path) {
        List<String> lines = Arrays.asList(normalize(text).split("\n", -1));
        KeyLine source = index.get(path);

        int start = source.index;
        while (start > 0) {
            String previous = lines.get(start - 1);
            if (isComment(previous) && indentOf(previous) == source.indent) {
                start--;
            } else {
                break;
            }
        }
        return new ArrayList<>(lines.subList(start, sectionBlockEnd(lines, source)));
    }

    /** Returns the index just after the last line belonging to a key's block. */
    private static int sectionBlockEnd(List<String> lines, KeyLine keyLine) {
        int end = keyLine.index + 1;
        int i = keyLine.index + 1;
        while (i < lines.size()) {
            String line = lines.get(i);
            if (line.isBlank()) {
                i++;
                continue;
            }
            if (indentOf(line) <= keyLine.indent) {
                break;
            }
            end = i + 1;
            i++;
        }
        return end;
    }

    /** Position where a missing top-level key can be appended, keeping the trailing newline. */
    private static int topLevelAppendIndex(List<String> lines) {
        int end = lines.size();
        if (end > 0 && lines.get(end - 1).isEmpty()) {
            end--;
        }
        while (end > 0 && lines.get(end - 1).isBlank()) {
            end--;
        }
        return end;
    }

    /** Shifts a block of source lines to a new base indentation. */
    private static List<String> shiftBlock(List<String> block, int delta) {
        List<String> shifted = new ArrayList<>(block.size());
        for (String line : block) {
            if (line.isBlank()) {
                shifted.add("");
            } else {
                int indent = indentOf(line);
                shifted.add(" ".repeat(Math.max(0, indent + delta)) + line.substring(indent));
            }
        }
        return shifted;
    }

    // ------------------------------------------------------------------
    // Tiny YAML line scanner (block style only; never re-serialises anything)
    // ------------------------------------------------------------------

    /** Maps every dotted key path of a block-style YAML document to its line. */
    private static Map<String, KeyLine> indexKeyLines(String text) {
        List<String> lines = Arrays.asList(normalize(text).split("\n", -1));
        Map<String, KeyLine> result = new LinkedHashMap<>();
        Deque<KeyLine> stack = new ArrayDeque<>();
        int blockScalarIndent = -1;

        for (int i = 0; i < lines.size(); i++) {
            String raw = lines.get(i);
            if (raw.isBlank()) continue;

            int indent = indentOf(raw);

            if (blockScalarIndent >= 0) {
                if (indent > blockScalarIndent) continue;
                blockScalarIndent = -1;
            }

            String trimmed = raw.trim();
            if (trimmed.startsWith("#") || trimmed.equals("---") || trimmed.equals("...")
                    || trimmed.equals("-") || trimmed.startsWith("- ")) {
                continue;
            }

            while (!stack.isEmpty() && indent <= stack.peek().indent()) {
                stack.pop();
            }

            Matcher matcher = KEY_LINE.matcher(trimmed);
            if (!matcher.matches()) continue;

            KeyLine keyLine = new KeyLine(i, indent, matcher.group(1), raw);
            result.put(buildPath(stack, keyLine.key), keyLine);

            String rest = matcher.group(2).trim();
            if (rest.isEmpty() || rest.startsWith("#")) {
                stack.push(keyLine);
            } else if (BLOCK_SCALAR.matcher(rest).matches()) {
                blockScalarIndent = indent;
            }
        }
        return result;
    }

    private static String buildPath(Deque<KeyLine> stack, String key) {
        StringBuilder builder = new StringBuilder();
        for (KeyLine parent : stack) {
            builder.insert(0, parent.key + ".");
        }
        return builder.append(key).toString();
    }

    private static boolean isSectionLine(String raw) {
        Matcher matcher = KEY_LINE.matcher(raw.trim());
        if (!matcher.matches()) return false;
        String rest = matcher.group(2).trim();
        return rest.isEmpty() || rest.startsWith("#");
    }

    private static boolean isComment(String line) {
        return line.trim().startsWith("#");
    }

    private static int indentOf(String line) {
        int spaces = 0;
        while (spaces < line.length() && line.charAt(spaces) == ' ') {
            spaces++;
        }
        return spaces;
    }

    // ------------------------------------------------------------------
    // Small utilities
    // ------------------------------------------------------------------

    /** Normalises line endings and strips a leading BOM; the files are always UTF-8. */
    public static String normalize(String text) {
        if (text == null) return "";
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        if (!normalized.isEmpty() && normalized.charAt(0) == '\uFEFF') {
            normalized = normalized.substring(1);
        }
        return normalized;
    }

    private static String ensureTrailingNewline(String text) {
        if (text.isEmpty() || text.endsWith("\n")) return text;
        return text + "\n";
    }

    private static YamlConfiguration loadYaml(String text) {
        YamlConfiguration configuration = new YamlConfiguration();
        try {
            configuration.loadFromString(normalize(text));
        } catch (InvalidConfigurationException e) {
            throw new IllegalArgumentException("invalid YAML: " + e.getMessage(), e);
        }
        return configuration;
    }

    private static Object copyValue(Object value) {
        if (value instanceof List<?> list) return new ArrayList<>(list);
        if (value instanceof Map<?, ?> map) return new LinkedHashMap<>(map);
        return value;
    }

    private static String childPath(String prefix, String key) {
        return prefix.isEmpty() ? key : prefix + "." + key;
    }

    private static String parentPath(String path) {
        int dot = path.lastIndexOf('.');
        return dot < 0 ? "" : path.substring(0, dot);
    }

    private static boolean isVersionPath(String path) {
        return path.equals(VERSION_KEY) || path.startsWith(VERSION_KEY + ".");
    }

    private static File nextBackupFile(File dataFolder, int oldVersion) {
        String base = BACKUP_PREFIX + oldVersion + "-"
                + LocalDateTime.now().format(BACKUP_TIMESTAMP);
        File candidate = new File(dataFolder, base + ".yml");
        int suffix = 2;
        while (candidate.exists()) {
            candidate = new File(dataFolder, base + "-" + suffix++ + ".yml");
        }
        return candidate;
    }
}
