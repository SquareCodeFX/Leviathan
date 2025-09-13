package de.feelix.leviathan.file;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * FileAPI: Unified configuration file access for YAML, JSON, TOML and .properties
 * with simple caching to avoid unnecessary disk reads.
 */
public final class FileAPI {
    private static final Map<Path, CachedConfig> CACHE = new ConcurrentHashMap<>();

    private FileAPI() {}

    /** Open (or create cache entry for) a configuration file. */
    public static ConfigFile open(File file) {
        Objects.requireNonNull(file, "file");
        ConfigFormat format = detectFormat(file.getName());
        Path path = file.toPath().toAbsolutePath().normalize();
        CACHE.computeIfAbsent(path, p -> new CachedConfig(format));
        return new ConfigFile(path, format, CACHE);
    }

    /** Removes a file from cache (next access will reload from disk). */
    public static void invalidate(File file) {
        if (file == null) return;
        Path path = file.toPath().toAbsolutePath().normalize();
        CACHE.remove(path);
    }

    /** Clears all cached files. */
    public static void clearCache() { CACHE.clear(); }

    private static ConfigFormat detectFormat(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".yml") || lower.endsWith(".yaml")) return new YamlFormat();
        if (lower.endsWith(".json")) return new JsonFormat();
        if (lower.endsWith(".toml")) return new TomlFormat();
        if (lower.endsWith(".properties")) return new PropertiesFormat();
        // Default to YAML
        return new YamlFormat();
    }

    /** Package-private cache entry. */
    static final class CachedConfig {
        volatile long lastModified;
        final ConfigFormat format;
        Map<String, Object> data; // flattened, top-level keys only
        Map<String, java.util.List<String>> comments; // per-key comment lines
        java.util.List<String> header; // header comment lines
        java.util.List<String> footer; // footer comment lines
        volatile boolean dirty;

        CachedConfig(ConfigFormat format) {
            this.format = format;
        }
    }
}
