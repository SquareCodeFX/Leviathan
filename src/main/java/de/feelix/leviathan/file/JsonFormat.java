package de.feelix.leviathan.file;

import com.google.gson.*;
import de.feelix.leviathan.annotations.NotNull;
import de.feelix.leviathan.annotations.Nullable;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * JSON format implementation for ConfigFormat.
 * <p>
 * Reads and writes flat (top-level) key/value pairs using Gson. Comments, headers and footers are
 * not supported by JSON and are ignored when saving.
 */
class JsonFormat implements ConfigFormat {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    /**
     * Loads a top-level map from a JSON file using Gson. When the file is empty or the root is not
     * a JSON object, returns an empty LinkedHashMap.
     */
    public @NotNull Map<String, Object> load(@NotNull File file) throws IOException {
        if (!file.exists() || file.length() == 0) return new LinkedHashMap<>();
        try (Reader r = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            JsonElement el = JsonParser.parseReader(r);
            if (el == null || el.isJsonNull()) return new LinkedHashMap<>();
            if (el.isJsonObject()) {
                return toJavaMap(el.getAsJsonObject());
            }
            // If it's not a JSON object, ignore
            return new LinkedHashMap<>();
        } catch (JsonSyntaxException e) {
            throw new IOException("Invalid JSON: " + e.getMessage(), e);
        }
    }

    /**
     * Saves the provided map as a pretty-printed JSON object using UTF-8. Comments, header and
     * footer are ignored because JSON has no native comment support.
     */
    public void save(@NotNull File file,
                     @NotNull Map<String, Object> data,
                     @Nullable Map<String, List<String>> comments,
                     @Nullable List<String> header,
                     @Nullable List<String> footer) throws IOException {
        // JSON doesn't support comments; we ignore header/footer/per-key comments.
        JsonObject obj = toJsonObject(data);
        try (Writer w = new OutputStreamWriter(new FileOutputStream(file, false), StandardCharsets.UTF_8)) {
            GSON.toJson(obj, w);
        }
    }

    private static @NotNull Map<String, Object> toJavaMap(@NotNull JsonObject o) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> e : o.entrySet()) {
            map.put(e.getKey(), toJava(e.getValue()));
        }
        return map;
    }

    private static @Nullable Object toJava(@Nullable JsonElement e) {
        if (e == null || e.isJsonNull()) return null;
        if (e.isJsonPrimitive()) {
            JsonPrimitive p = e.getAsJsonPrimitive();
            if (p.isBoolean()) return p.getAsBoolean();
            if (p.isNumber()) return p.getAsNumber();
            return p.getAsString();
        }
        if (e.isJsonArray()) {
            List<Object> list = new ArrayList<>();
            for (JsonElement el : e.getAsJsonArray()) list.add(toJava(el));
            return list;
        }
        if (e.isJsonObject()) {
            return toJavaMap(e.getAsJsonObject());
        }
        return null;
    }

    private static @NotNull JsonObject toJsonObject(@NotNull Map<String, Object> data) {
        JsonObject obj = new JsonObject();
        for (Map.Entry<String, Object> e : data.entrySet()) {
            obj.add(e.getKey(), toJson(e.getValue()));
        }
        return obj;
    }

    @SuppressWarnings("unchecked")
    private static @NotNull JsonElement toJson(@Nullable Object v) {
        if (v == null) return JsonNull.INSTANCE;
        if (v instanceof Boolean b) return new JsonPrimitive(b);
        if (v instanceof Number n) return new JsonPrimitive(n);
        if (v instanceof String s) return new JsonPrimitive(s);
        if (v instanceof Map<?, ?> m) {
            JsonObject o = new JsonObject();
            for (Map.Entry<?, ?> me : m.entrySet()) {
                if (me.getKey() != null) o.add(String.valueOf(me.getKey()), toJson(me.getValue()));
            }
            return o;
        }
        if (v instanceof Iterable<?> it) {
            JsonArray arr = new JsonArray();
            for (Object o : it) arr.add(toJson(o));
            return arr;
        }
        return new JsonPrimitive(String.valueOf(v));
    }

    /**
     * {@inheritDoc}
     */
    public @NotNull String getName() { return "json"; }
}
