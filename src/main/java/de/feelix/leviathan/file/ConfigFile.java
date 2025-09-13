package de.feelix.leviathan.file;

import de.feelix.leviathan.annotations.NotNull;
import de.feelix.leviathan.annotations.Nullable;
import de.feelix.leviathan.exceptions.ApiMisuseException;
import de.feelix.leviathan.exceptions.ConfigException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents one configuration file accessed via FileAPI.
 * Provides typed getters/setters, default/alternative setters, and comment/header/footer management.
 */
public final class ConfigFile {
    /** Absolute, normalized path to the backing configuration file. */
    private final Path path;
    /** The format implementation bound to this file (derived from the file extension). */
    private final ConfigFormat format;
    /** Shared cache map from FileAPI: used to load/reload and persist data across instances. */
    private final Map<Path, FileAPI.CachedConfig> cache;

    /**
     * Creates a new ConfigFile facade bound to the given path, format and shared cache.
     * Intended for use by FileAPI; users should call FileAPI.open(File).
     *
     * @param path   absolute, normalized file path
     * @param format format implementation resolved from file extension
     * @param cache  shared cache map managed by FileAPI
     */
    ConfigFile(Path path, ConfigFormat format, Map<Path, FileAPI.CachedConfig> cache) {
        this.path = path;
        this.format = format;
        this.cache = cache;
    }

    /**
     * Resolve the underlying java.io.File from the stored Path.
     *
     * @return non-null File pointing to the config file location
     */
    private @NotNull File file() { return path.toFile(); }

    /**
     * Ensure that a cache entry exists and the in-memory data is loaded from disk
     * when necessary. If the on-disk timestamp has changed, reloads the file
     * using the bound {@link ConfigFormat} implementation.
     *
     * @return non-null cache entry for this file
     * @throws ConfigException if loading/parsing fails
     */
    private @NotNull FileAPI.CachedConfig ensureLoaded() {
        FileAPI.CachedConfig c = cache.get(path);
        if (c == null) throw new ApiMisuseException("Cache entry missing for " + path);
        File f = file();
        long lm = f.exists() ? f.lastModified() : -1L;
        if (c.data == null || c.lastModified != lm) {
            // Load from disk
            Map<String, Object> loaded = new HashMap<>();
            Map<String, List<String>> comments = new HashMap<>();
            List<String> header = new ArrayList<>();
            List<String> footer = new ArrayList<>();
            if (f.exists()) {
                try {
                    loaded = format.load(f);
                } catch (IOException e) {
                    throw new ConfigException("Failed to load config (" + format.getName() + ") from " + f.getAbsolutePath(), e);
                }
            }
            c.data = loaded;
            c.comments = comments;
            c.header = header;
            c.footer = footer;
            c.lastModified = lm;
            c.dirty = false;
        }
        return c;
    }

    /**
     * Persist the provided cache entry to disk using the bound format.
     * Creates parent directories as needed.
     *
     * @param c non-null cache entry
     * @throws ConfigException if saving fails
     */
    private void save(@NotNull FileAPI.CachedConfig c) {
        try {
            Files.createDirectories(path.getParent());
            format.save(file(), c.data != null ? c.data : Collections.emptyMap(),
                    c.comments != null ? c.comments : Collections.emptyMap(),
                    c.header != null ? c.header : Collections.emptyList(),
                    c.footer != null ? c.footer : Collections.emptyList());
            c.lastModified = file().lastModified();
            c.dirty = false;
        } catch (IOException e) {
            throw new ConfigException("Failed to save config (" + format.getName() + ") to " + file().getAbsolutePath(), e);
        }
    }

    /**
     * Gets the value for the given key as a String.
     *
     * @param key top-level key
     * @return the string value or null if missing or not convertible
     */
    public String getString(String key) { return asString(getRaw(key)); }
    /**
     * Gets the value as String, or returns the provided default without modifying the file.
     *
     * @param key top-level key
     * @param def default value to return when the key is missing
     * @return string value or def when absent
     */
    public String getStringOrDefault(String key, String def) { Object v = getRaw(key); return v == null ? def : asString(v); }
    /**
     * Gets the value as String, or writes and returns the provided alternative if the key is missing.
     * This method persists the alternative to disk when the key is absent.
     *
     * @param key top-level key
     * @param alt alternative value to set and return if missing
     * @return existing string value or the alternative when absent
     */
    public String getStringOrSet(String key, String alt) { return getOrSet(key, alt, this::setString, this::getString); }

    /**
     * Gets the value for the given key as an Integer.
     *
     * @param key top-level key
     * @return the integer value or null if missing or not parseable
     */
    public Integer getInt(String key) { return asInt(getRaw(key)); }
    /**
     * Gets the value as int, or returns the provided default without modifying the file.
     *
     * @param key top-level key
     * @param def default primitive value to return when missing
     * @return existing value or def
     */
    public int getIntOrDefault(String key, int def) { Integer v = asInt(getRaw(key)); return v == null ? def : v; }
    /**
     * Gets the value as int, or writes and returns the provided alternative if the key is missing.
     * This method persists the alternative to disk when the key is absent.
     *
     * @param key top-level key
     * @param alt alternative value to set and return if missing
     * @return existing value or the alternative when absent
     */
    public int getIntOrSet(String key, int alt) { return getOrSet(key, alt, this::setInt, this::getInt); }

    /**
     * Gets the value for the given key as a Long.
     *
     * @param key top-level key
     * @return the long value or null if missing or not parseable
     */
    public Long getLong(String key) { return asLong(getRaw(key)); }
    /**
     * Gets the value as long, or returns the provided default without modifying the file.
     *
     * @param key top-level key
     * @param def default primitive value to return when missing
     * @return existing value or def
     */
    public long getLongOrDefault(String key, long def) { Long v = asLong(getRaw(key)); return v == null ? def : v; }
    /**
     * Gets the value as long, or writes and returns the provided alternative if the key is missing.
     * This method persists the alternative to disk when the key is absent.
     *
     * @param key top-level key
     * @param alt alternative value to set and return if missing
     * @return existing value or the alternative when absent
     */
    public long getLongOrSet(String key, long alt) { return getOrSet(key, alt, this::setLong, this::getLong); }

    /**
     * Gets the value for the given key as a Float.
     *
     * @param key top-level key
     * @return the float value or null if missing or not parseable
     */
    public Float getFloat(String key) { return asFloat(getRaw(key)); }
    /**
     * Gets the value as float, or returns the provided default without modifying the file.
     *
     * @param key top-level key
     * @param def default primitive value to return when missing
     * @return existing value or def
     */
    public float getFloatOrDefault(String key, float def) { Float v = asFloat(getRaw(key)); return v == null ? def : v; }
    /**
     * Gets the value as float, or writes and returns the provided alternative if the key is missing.
     * This method persists the alternative to disk when the key is absent.
     *
     * @param key top-level key
     * @param alt alternative value to set and return if missing
     * @return existing value or the alternative when absent
     */
    public float getFloatOrSet(String key, float alt) { return getOrSet(key, alt, this::setFloat, this::getFloat); }

    /**
     * Gets the value for the given key as a Double.
     *
     * @param key top-level key
     * @return the double value or null if missing or not parseable
     */
    public Double getDouble(String key) { return asDouble(getRaw(key)); }
    /**
     * Gets the value as double, or returns the provided default without modifying the file.
     *
     * @param key top-level key
     * @param def default primitive value to return when missing
     * @return existing value or def
     */
    public double getDoubleOrDefault(String key, double def) { Double v = asDouble(getRaw(key)); return v == null ? def : v; }
    /**
     * Gets the value as double, or writes and returns the provided alternative if the key is missing.
     * This method persists the alternative to disk when the key is absent.
     *
     * @param key top-level key
     * @param alt alternative value to set and return if missing
     * @return existing value or the alternative when absent
     */
    public double getDoubleOrSet(String key, double alt) { return getOrSet(key, alt, this::setDouble, this::getDouble); }

    /**
     * Gets the value for the given key as a Boolean.
     *
     * @param key top-level key
     * @return the boolean value or null if missing or not parseable
     */
    public Boolean getBoolean(String key) { return asBoolean(getRaw(key)); }
    /**
     * Gets the value as boolean, or returns the provided default without modifying the file.
     *
     * @param key top-level key
     * @param def default primitive value to return when missing
     * @return existing value or def
     */
    public boolean getBooleanOrDefault(String key, boolean def) { Boolean v = asBoolean(getRaw(key)); return v == null ? def : v; }
    /**
     * Gets the value as boolean, or writes and returns the provided alternative if the key is missing.
     * This method persists the alternative to disk when the key is absent.
     *
     * @param key top-level key
     * @param alt alternative value to set and return if missing
     * @return existing value or the alternative when absent
     */
    public boolean getBooleanOrSet(String key, boolean alt) { return getOrSet(key, alt, this::setBoolean, this::getBoolean); }

    /**
     * Gets the value for the given key as a Byte.
     *
     * @param key top-level key
     * @return the byte value or null if missing or not parseable
     */
    public Byte getByte(String key) { return asByte(getRaw(key)); }
    /**
     * Gets the value as byte, or returns the provided default without modifying the file.
     *
     * @param key top-level key
     * @param def default primitive value to return when missing
     * @return existing value or def
     */
    public byte getByteOrDefault(String key, byte def) { Byte v = asByte(getRaw(key)); return v == null ? def : v; }
    /**
     * Gets the value as byte, or writes and returns the provided alternative if the key is missing.
     * This method persists the alternative to disk when the key is absent.
     *
     * @param key top-level key
     * @param alt alternative value to set and return if missing
     * @return existing value or the alternative when absent
     */
    public byte getByteOrSet(String key, byte alt) { return getOrSet(key, alt, this::setByte, this::getByte); }

    /**
     * Gets the value as a generic list ({@code List<Object>}), if present and convertible.
     *
     * @param key top-level key
     * @return a List of objects, or null if missing or not a list
     */
    public List<Object> getList(String key) { return asList(getRaw(key)); }
    /**
     * Gets the value as a list, or returns the provided default without modifying the file.
     *
     * @param key top-level key
     * @param def default list to return when missing
     * @return existing list or def
     */
    public List<Object> getListOrDefault(String key, List<Object> def) { List<Object> v = asList(getRaw(key)); return v == null ? def : v; }
    /**
     * Gets the value as a list, or writes and returns the provided alternative if the key is missing.
     * This method persists the alternative to disk when the key is absent.
     *
     * @param key top-level key
     * @param alt alternative list to set and return if missing
     * @return existing list or the alternative when absent
     */
    public List<Object> getListOrSet(String key, List<Object> alt) { return getOrSet(key, alt, this::setList, this::getList); }

    /**
     * Gets the value as a list of strings, converting elements with String.valueOf.
     *
     * @param key top-level key
     * @return a {@code List<String>} or null if the underlying value is missing or not a list
     */
    public List<String> getStringList(String key) {
        List<Object> raw = asList(getRaw(key));
        if (raw == null) return null;
        List<String> out = new ArrayList<>(raw.size());
        for (Object o : raw) out.add(asString(o));
        return out;
    }
    /**
     * Gets the value as a list of strings, or returns the provided default without modifying the file.
     *
     * @param key top-level key
     * @param def default list to return when missing
     * @return existing list or def
     */
    public List<String> getStringListOrDefault(String key, List<String> def) {
        List<String> v = getStringList(key); return v == null ? def : v;
    }
    /**
     * Gets the value as a list of strings, or writes and returns the provided alternative if the key is missing.
     * This method persists the alternative to disk when the key is absent.
     *
     * @param key top-level key
     * @param alt alternative list to set and return if missing
     * @return existing list or the alternative when absent
     */
    public List<String> getStringListOrSet(String key, List<String> alt) {
        return getOrSet(key, alt, this::setStringList, this::getStringList);
    }

    /**
     * Sets a String value for the given key and saves immediately.
     */
    public void setString(String key, String value) { set(key, value); }
    /**
     * Sets an int value for the given key and saves immediately.
     */
    public void setInt(String key, int value) { set(key, value); }
    /**
     * Sets a long value for the given key and saves immediately.
     */
    public void setLong(String key, long value) { set(key, value); }
    /**
     * Sets a float value for the given key and saves immediately.
     */
    public void setFloat(String key, float value) { set(key, value); }
    /**
     * Sets a double value for the given key and saves immediately.
     */
    public void setDouble(String key, double value) { set(key, value); }
    /**
     * Sets a boolean value for the given key and saves immediately.
     */
    public void setBoolean(String key, boolean value) { set(key, value); }
    /**
     * Sets a byte value for the given key and saves immediately.
     */
    public void setByte(String key, byte value) { set(key, value); }
    /**
     * Sets a list value ({@code List<Object>}) for the given key and saves immediately.
     */
    public void setList(String key, List<Object> value) { set(key, value); }
    /**
     * Sets a list of strings for the given key and saves immediately.
     */
    public void setStringList(String key, List<String> value) { set(key, new ArrayList<>(value)); }

    /**
     * Checks whether the configuration contains the given top-level key.
     *
     * @param key top-level key
     * @return true if present in the in-memory view (auto-loaded from disk if needed)
     */
    public boolean contains(String key) { return ensureLoaded().data.containsKey(key); }

    /**
     * Removes the given key (and its associated comment, if any) and saves immediately.
     * A no-op when the key is absent.
     *
     * @param key top-level key to remove
     */
    public void remove(String key) {
        FileAPI.CachedConfig c = ensureLoaded();
        if (c.data.remove(key) != null) {
            if (c.comments != null) c.comments.remove(key);
            save(c);
        }
    }

    /**
     * Sets per-key comment lines for the given key and saves immediately.
     * Supported by YAML/TOML/PROPERTIES; ignored by JSON on write.
     *
     * @param key           top-level key the comment belongs to
     * @param commentLines  comment lines without the leading '#' (null clears comments)
     */
    public void setComment(String key, List<String> commentLines) {
        FileAPI.CachedConfig c = ensureLoaded();
        if (c.comments == null) c.comments = new ConcurrentHashMap<>();
        c.comments.put(key, commentLines == null ? Collections.emptyList() : new ArrayList<>(commentLines));
        save(c);
    }

    /**
     * Sets header comment lines and saves immediately.
     * Supported by YAML/TOML/PROPERTIES; ignored by JSON on write.
     *
     * @param headerLines header lines without the leading '#'
     */
    public void setHeader(List<String> headerLines) {
        FileAPI.CachedConfig c = ensureLoaded();
        c.header = headerLines == null ? Collections.emptyList() : new ArrayList<>(headerLines);
        save(c);
    }

    /**
     * Sets footer comment lines and saves immediately.
     * Supported by YAML/TOML/PROPERTIES; ignored by JSON on write.
     *
     * @param footerLines footer lines without the leading '#'
     */
    public void setFooter(List<String> footerLines) {
        FileAPI.CachedConfig c = ensureLoaded();
        c.footer = footerLines == null ? Collections.emptyList() : new ArrayList<>(footerLines);
        save(c);
    }

    /**
     * Forces a reload from disk by invalidating the in-memory data for this file.
     * Keeps the shared cache entry intact.
     */
    public void reload() {
        // Ensure a cache entry exists and force a reload from disk
        cache.computeIfAbsent(path, p -> new FileAPI.CachedConfig(format));
        FileAPI.CachedConfig c = cache.get(path);
        c.data = null; // mark as not loaded so ensureLoaded() will load from disk
        ensureLoaded();
    }

    /**
     * Persists the current in-memory view to disk.
     * Usually unnecessary because all setter and comment/header/footer methods auto-save.
     */
    public void save() { save(ensureLoaded()); }

    // Internal helpers
    /**
     * Internal: get the raw stored value for a key without conversion.
     *
     * @param key non-null top-level key
     * @return the raw value or null if missing
     */
    private @Nullable Object getRaw(@NotNull String key) { return ensureLoaded().data.get(key); }

    /**
     * Internal: common pattern to return an existing value or set-and-return an alternative.
     *
     * @param key     non-null key
     * @param alt     alternative value to set when missing
     * @param setter  setter method reference
     * @param getter  getter method reference
     * @return the existing value if present, otherwise {@code alt}
     */
    private <T> @NotNull T getOrSet(@NotNull String key, @NotNull T alt, @NotNull java.util.function.BiConsumer<String, T> setter, @NotNull java.util.function.Function<String, T> getter) {
        T existing = getter.apply(key);
        if (existing != null) return existing;
        setter.accept(key, alt);
        return alt;
    }

    /**
     * Internal: put a value into the in-memory map and persist immediately.
     *
     * @param key   non-null key
     * @param value value to store (may be null)
     */
    private void set(@NotNull String key, @Nullable Object value) {
        FileAPI.CachedConfig c = ensureLoaded();
        c.data.put(key, value);
        save(c);
    }

    // Type conversions
    /** Convert an arbitrary object to a String, or null if the object is null. */
    private static @Nullable String asString(@Nullable Object v) { return v == null ? null : String.valueOf(v); }
    /** Convert an arbitrary object to an Integer, or null when not convertible. */
    private static @Nullable Integer asInt(@Nullable Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.intValue();
        try { return Integer.parseInt(v.toString().trim()); } catch (Exception ignored) { return null; }
    }
    /** Convert an arbitrary object to a Long, or null when not convertible. */
    private static @Nullable Long asLong(@Nullable Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        try { return Long.parseLong(v.toString().trim()); } catch (Exception ignored) { return null; }
    }
    /** Convert an arbitrary object to a Float, or null when not convertible. */
    private static @Nullable Float asFloat(@Nullable Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.floatValue();
        try { return Float.parseFloat(v.toString().trim()); } catch (Exception ignored) { return null; }
    }
    /** Convert an arbitrary object to a Double, or null when not convertible. */
    private static @Nullable Double asDouble(@Nullable Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(v.toString().trim()); } catch (Exception ignored) { return null; }
    }
    /** Convert an arbitrary object to a Boolean, or null when not convertible. */
    private static @Nullable Boolean asBoolean(@Nullable Object v) {
        if (v == null) return null;
        if (v instanceof Boolean b) return b;
        String s = v.toString().trim().toLowerCase(Locale.ROOT);
        if ("true".equals(s) || "yes".equals(s) || "on".equals(s)) return true;
        if ("false".equals(s) || "no".equals(s) || "off".equals(s)) return false;
        return null;
    }
    /** Convert an arbitrary object to a Byte, or null when not convertible. */
    private static @Nullable Byte asByte(@Nullable Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.byteValue();
        try { return Byte.parseByte(v.toString().trim()); } catch (Exception ignored) { return null; }
    }

    @SuppressWarnings("unchecked")
    private static @Nullable List<Object> asList(@Nullable Object v) {
        if (v == null) return null;
        if (v instanceof List) return (List<Object>) v;
        if (v instanceof String s) {
            // Handle bracketed list representations like "[a, b]"
            String str = s.trim();
            if (str.startsWith("[") && str.endsWith("]")) {
                str = str.substring(1, str.length() - 1).trim();
            }
            if (str.isEmpty()) return new ArrayList<>();
            // try comma-separated
            String[] parts = str.split(",");
            List<Object> out = new ArrayList<>(parts.length);
            for (String p : parts) out.add(p.trim());
            return out;
        }
        return Collections.singletonList(v);
    }
}
