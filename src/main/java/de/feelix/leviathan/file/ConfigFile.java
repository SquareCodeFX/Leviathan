package de.feelix.leviathan.file;

import de.feelix.leviathan.exceptions.ApiMisuseException;

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
    private final Path path;
    private final ConfigFormat format;
    private final Map<Path, FileAPI.CachedConfig> cache;

    ConfigFile(Path path, ConfigFormat format, Map<Path, FileAPI.CachedConfig> cache) {
        this.path = path;
        this.format = format;
        this.cache = cache;
    }

    private File file() { return path.toFile(); }

    private FileAPI.CachedConfig ensureLoaded() {
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
                    throw new RuntimeException("Failed to load config (" + format.getName() + ") from " + f.getAbsolutePath(), e);
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

    private void save(FileAPI.CachedConfig c) {
        try {
            Files.createDirectories(path.getParent());
            format.save(file(), c.data != null ? c.data : Collections.emptyMap(),
                    c.comments != null ? c.comments : Collections.emptyMap(),
                    c.header != null ? c.header : Collections.emptyList(),
                    c.footer != null ? c.footer : Collections.emptyList());
            c.lastModified = file().lastModified();
            c.dirty = false;
        } catch (IOException e) {
            throw new RuntimeException("Failed to save config (" + format.getName() + ") to " + file().getAbsolutePath(), e);
        }
    }

    public String getString(String key) { return asString(getRaw(key)); }
    public String getStringOrDefault(String key, String def) { Object v = getRaw(key); return v == null ? def : asString(v); }
    public String getStringOrSet(String key, String alt) { return getOrSet(key, alt, this::setString, this::getString); }

    public Integer getInt(String key) { return asInt(getRaw(key)); }
    public int getIntOrDefault(String key, int def) { Integer v = asInt(getRaw(key)); return v == null ? def : v; }
    public int getIntOrSet(String key, int alt) { return getOrSet(key, alt, this::setInt, this::getInt); }

    public Long getLong(String key) { return asLong(getRaw(key)); }
    public long getLongOrDefault(String key, long def) { Long v = asLong(getRaw(key)); return v == null ? def : v; }
    public long getLongOrSet(String key, long alt) { return getOrSet(key, alt, this::setLong, this::getLong); }

    public Float getFloat(String key) { return asFloat(getRaw(key)); }
    public float getFloatOrDefault(String key, float def) { Float v = asFloat(getRaw(key)); return v == null ? def : v; }
    public float getFloatOrSet(String key, float alt) { return getOrSet(key, alt, this::setFloat, this::getFloat); }

    public Double getDouble(String key) { return asDouble(getRaw(key)); }
    public double getDoubleOrDefault(String key, double def) { Double v = asDouble(getRaw(key)); return v == null ? def : v; }
    public double getDoubleOrSet(String key, double alt) { return getOrSet(key, alt, this::setDouble, this::getDouble); }

    public Boolean getBoolean(String key) { return asBoolean(getRaw(key)); }
    public boolean getBooleanOrDefault(String key, boolean def) { Boolean v = asBoolean(getRaw(key)); return v == null ? def : v; }
    public boolean getBooleanOrSet(String key, boolean alt) { return getOrSet(key, alt, this::setBoolean, this::getBoolean); }

    public Byte getByte(String key) { return asByte(getRaw(key)); }
    public byte getByteOrDefault(String key, byte def) { Byte v = asByte(getRaw(key)); return v == null ? def : v; }
    public byte getByteOrSet(String key, byte alt) { return getOrSet(key, alt, this::setByte, this::getByte); }

    public List<Object> getList(String key) { return asList(getRaw(key)); }
    public List<Object> getListOrDefault(String key, List<Object> def) { List<Object> v = asList(getRaw(key)); return v == null ? def : v; }
    public List<Object> getListOrSet(String key, List<Object> alt) { return getOrSet(key, alt, this::setList, this::getList); }

    public List<String> getStringList(String key) {
        List<Object> raw = asList(getRaw(key));
        if (raw == null) return null;
        List<String> out = new ArrayList<>(raw.size());
        for (Object o : raw) out.add(asString(o));
        return out;
    }
    public List<String> getStringListOrDefault(String key, List<String> def) {
        List<String> v = getStringList(key); return v == null ? def : v;
    }
    public List<String> getStringListOrSet(String key, List<String> alt) {
        return getOrSet(key, alt, this::setStringList, this::getStringList);
    }

    public void setString(String key, String value) { set(key, value); }
    public void setInt(String key, int value) { set(key, value); }
    public void setLong(String key, long value) { set(key, value); }
    public void setFloat(String key, float value) { set(key, value); }
    public void setDouble(String key, double value) { set(key, value); }
    public void setBoolean(String key, boolean value) { set(key, value); }
    public void setByte(String key, byte value) { set(key, value); }
    public void setList(String key, List<Object> value) { set(key, value); }
    public void setStringList(String key, List<String> value) { set(key, new ArrayList<>(value)); }

    public boolean contains(String key) { return ensureLoaded().data.containsKey(key); }

    public void remove(String key) {
        FileAPI.CachedConfig c = ensureLoaded();
        if (c.data.remove(key) != null) {
            if (c.comments != null) c.comments.remove(key);
            save(c);
        }
    }

    public void setComment(String key, List<String> commentLines) {
        FileAPI.CachedConfig c = ensureLoaded();
        if (c.comments == null) c.comments = new ConcurrentHashMap<>();
        c.comments.put(key, commentLines == null ? Collections.emptyList() : new ArrayList<>(commentLines));
        save(c);
    }

    public void setHeader(List<String> headerLines) {
        FileAPI.CachedConfig c = ensureLoaded();
        c.header = headerLines == null ? Collections.emptyList() : new ArrayList<>(headerLines);
        save(c);
    }

    public void setFooter(List<String> footerLines) {
        FileAPI.CachedConfig c = ensureLoaded();
        c.footer = footerLines == null ? Collections.emptyList() : new ArrayList<>(footerLines);
        save(c);
    }

    public void reload() {
        // Ensure a cache entry exists and force a reload from disk
        cache.computeIfAbsent(path, p -> new FileAPI.CachedConfig(format));
        FileAPI.CachedConfig c = cache.get(path);
        c.data = null; // mark as not loaded so ensureLoaded() will load from disk
        ensureLoaded();
    }

    public void save() { save(ensureLoaded()); }

    // Internal helpers
    private Object getRaw(String key) { return ensureLoaded().data.get(key); }

    private <T> T getOrSet(String key, T alt, java.util.function.BiConsumer<String, T> setter, java.util.function.Function<String, T> getter) {
        T existing = getter.apply(key);
        if (existing != null) return existing;
        setter.accept(key, alt);
        return alt;
    }

    private void set(String key, Object value) {
        FileAPI.CachedConfig c = ensureLoaded();
        c.data.put(key, value);
        save(c);
    }

    // Type conversions
    private static String asString(Object v) { return v == null ? null : String.valueOf(v); }
    private static Integer asInt(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.intValue();
        try { return Integer.parseInt(v.toString().trim()); } catch (Exception ignored) { return null; }
    }
    private static Long asLong(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        try { return Long.parseLong(v.toString().trim()); } catch (Exception ignored) { return null; }
    }
    private static Float asFloat(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.floatValue();
        try { return Float.parseFloat(v.toString().trim()); } catch (Exception ignored) { return null; }
    }
    private static Double asDouble(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(v.toString().trim()); } catch (Exception ignored) { return null; }
    }
    private static Boolean asBoolean(Object v) {
        if (v == null) return null;
        if (v instanceof Boolean b) return b;
        String s = v.toString().trim().toLowerCase(Locale.ROOT);
        if ("true".equals(s) || "yes".equals(s) || "on".equals(s)) return true;
        if ("false".equals(s) || "no".equals(s) || "off".equals(s)) return false;
        return null;
    }
    private static Byte asByte(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.byteValue();
        try { return Byte.parseByte(v.toString().trim()); } catch (Exception ignored) { return null; }
    }

    @SuppressWarnings("unchecked")
    private static List<Object> asList(Object v) {
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
