package de.feelix.leviathan.file;

import com.moandjiezana.toml.Toml;
import de.feelix.leviathan.annotations.NotNull;
import de.feelix.leviathan.annotations.Nullable;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * TOML format implementation for ConfigFormat.
 * <p>
 * Reads and writes flat (top-level) key/value pairs using toml4j. Supports per-key comments,
 * header and footer when saving by emitting comment lines starting with '#'.
 */
class TomlFormat implements ConfigFormat {
    /**
     * Loads a top-level map from a TOML file using toml4j. Only top-level keys are included in the
     * result map. Returns an empty LinkedHashMap for empty files.
     */
    @SuppressWarnings("unchecked")
    public @NotNull Map<String, Object> load(@NotNull File file) throws IOException {
        if (!file.exists() || file.length() == 0) return new LinkedHashMap<>();
        Toml toml = new Toml().read(file);
        Map<String, Object> raw = toml.toMap();
        // Keep only top-level entries
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : raw.entrySet()) {
            out.put(e.getKey(), e.getValue());
        }
        return out;
    }

    /**
     * Saves a flat map to TOML. Includes optional header/footer and per-key comments by emitting
     * comment lines beginning with '#'. Strings are quoted; lists are written as TOML arrays.
     */
    public void save(@NotNull File file,
                     @NotNull Map<String, Object> data,
                     @Nullable Map<String, List<String>> comments,
                     @Nullable List<String> header,
                     @Nullable List<String> footer) throws IOException {
        List<String> lines = new ArrayList<>();
        if (header != null && !header.isEmpty()) {
            for (String h : header) lines.add("# " + safe(h));
            lines.add("");
        }
        List<String> keys = new ArrayList<>(data.keySet());
        Collections.sort(keys);
        for (String key : keys) {
            List<String> kc = comments != null ? comments.get(key) : null;
            if (kc != null && !kc.isEmpty()) for (String c : kc) lines.add("# " + safe(c));
            Object value = data.get(key);
            lines.add(key + " = " + tomlScalar(value));
        }
        if (footer != null && !footer.isEmpty()) {
            lines.add("");
            for (String f : footer) lines.add("# " + safe(f));
        }
        try (Writer w = new OutputStreamWriter(new FileOutputStream(file, false), StandardCharsets.UTF_8)) {
            for (int i = 0; i < lines.size(); i++) {
                w.write(lines.get(i));
                if (i < lines.size() - 1) w.write("\n");
            }
        }
    }

    private static @NotNull String tomlScalar(@Nullable Object v) {
        if (v == null) return ""; // empty value
        if (v instanceof Boolean || v instanceof Number) return String.valueOf(v);
        if (v instanceof List<?> list) {
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(tomlScalar(list.get(i)));
            }
            sb.append("]");
            return sb.toString();
        }
        // String or other -> quote
        String s = String.valueOf(v);
        return '"' + s.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
    }

    private static @NotNull String safe(@Nullable String s) { return s == null ? "" : s.replace("\r", "").replace("\n", " "); }

    /**
     * {@inheritDoc}
     */
    public @NotNull String getName() { return "toml"; }
}
