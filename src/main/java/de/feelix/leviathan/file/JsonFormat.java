package de.feelix.leviathan.file;

import com.google.gson.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

class JsonFormat implements ConfigFormat {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public Map<String, Object> load(File file) throws IOException {
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

    public void save(File file,
                     Map<String, Object> data,
                     Map<String, List<String>> comments,
                     List<String> header,
                     List<String> footer) throws IOException {
        // JSON doesn't support comments; we ignore header/footer/per-key comments.
        JsonObject obj = toJsonObject(data);
        try (Writer w = new OutputStreamWriter(new FileOutputStream(file, false), StandardCharsets.UTF_8)) {
            GSON.toJson(obj, w);
        }
    }

    private static Map<String, Object> toJavaMap(JsonObject o) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> e : o.entrySet()) {
            map.put(e.getKey(), toJava(e.getValue()));
        }
        return map;
    }

    private static Object toJava(JsonElement e) {
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

    private static JsonObject toJsonObject(Map<String, Object> data) {
        JsonObject obj = new JsonObject();
        for (Map.Entry<String, Object> e : data.entrySet()) {
            obj.add(e.getKey(), toJson(e.getValue()));
        }
        return obj;
    }

    @SuppressWarnings("unchecked")
    private static JsonElement toJson(Object v) {
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

    public String getName() { return "json"; }
}
