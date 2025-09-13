package de.feelix.leviathan.file;

import de.feelix.leviathan.annotations.NotNull;
import de.feelix.leviathan.annotations.Nullable;
import de.feelix.leviathan.util.Preconditions;

import java.io.File;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * FileAPI: Unified configuration file access for YAML, JSON, TOML and .properties
 * with simple caching to avoid unnecessary disk reads.
 */
public final class FileAPI {
    /**
     * In-memory cache keyed by absolute, normalized file path. Each entry stores the
     * last known on-disk modification timestamp and the parsed view of the file so that
     * repeated reads avoid disk I/O. Entries are refreshed when timestamps differ.
     */
    private static final Map<Path, CachedConfig> CACHE = new ConcurrentHashMap<>();

    private FileAPI() {}

    /**
     * Opens a configuration file and returns a ConfigFile facade for it.
     * <p>
     * The format is detected from the file name extension:
     * .yml/.yaml → YAML, .json → JSON, .toml → TOML, .properties → Java properties.
     * If the extension is unknown, YAML is used by default.
     * <p>
     * A cache entry is created on first open for the normalized absolute path; subsequent
     * reads reuse the cached, parsed view and only hit disk when the on-disk timestamp changes.
     *
     * @param file the target configuration file (absolute or relative path); must not be null
     * @return a non-null ConfigFile wrapper bound to the detected format and shared cache
     * @throws de.feelix.leviathan.exceptions.ApiMisuseException if {@code file} is null
     */
    public static @NotNull ConfigFile open(@NotNull File file) {
        Preconditions.checkNotNull(file, "file");
        ConfigFormat format = detectFormat(file.getName());
        Path path = file.toPath().toAbsolutePath().normalize();
        CACHE.computeIfAbsent(path, p -> new CachedConfig(format));
        return new ConfigFile(path, format, CACHE);
    }

    /**
     * Removes one file's cached entry. The next open/reload will re-read from disk.
     *
     * @param file the file whose cache entry should be invalidated; no-op when null
     */
    public static void invalidate(@Nullable File file) {
        if (file == null) return;
        Path path = file.toPath().toAbsolutePath().normalize();
        CACHE.remove(path);
    }

    /**
     * Clears all cached configuration entries across all files.
     * Subsequent access to any ConfigFile will reload from disk.
     */
    public static void clearCache() { CACHE.clear(); }

    /**
     * Detects the configuration format to use based on the file name.
     *
     * @param filename the file name (case-insensitive extension is used)
     * @return a non-null ConfigFormat implementation; defaults to YAML when unknown
     */
    private static @NotNull ConfigFormat detectFormat(@NotNull String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".yml") || lower.endsWith(".yaml")) return new YamlFormat();
        if (lower.endsWith(".json")) return new JsonFormat();
        if (lower.endsWith(".toml")) return new TomlFormat();
        if (lower.endsWith(".properties")) return new PropertiesFormat();
        // Default to YAML
        return new YamlFormat();
    }

    /**
     * Package-private cache entry representing the current in-memory view of a config file.
     * Holds parsed data and simple metadata necessary to decide when to reload and how to save.
     */
    static final class CachedConfig {
        /** Last known on-disk modified timestamp (File#lastModified). */
        volatile long lastModified;
        /** The format implementation used to load/save this file. */
        final ConfigFormat format;
        /** Parsed data as a flat, top-level key/value map. Nested structures are not modeled. */
        Map<String, Object> data; // flattened, top-level keys only
        /** Optional per-key comment lines for formats that support comments (YAML/TOML/PROPERTIES). */
        Map<String, java.util.List<String>> comments; // per-key comment lines
        /** Optional file header comment lines. */
        java.util.List<String> header; // header comment lines
        /** Optional file footer comment lines. */
        java.util.List<String> footer; // footer comment lines
        /** True if there are unsaved in-memory changes (primarily for internal use). */
        volatile boolean dirty;

        CachedConfig(@NotNull ConfigFormat format) {
            this.format = format;
        }
    }
}
