package de.feelix.leviathan.file;

import de.feelix.leviathan.annotations.NotNull;
import de.feelix.leviathan.annotations.Nullable;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * YAML format implementation for ConfigFormat.
 * <p>
 * Reads and writes flat (top-level) key/value pairs. Supports per-key comments, header and footer
 * when saving by emitting inline comment lines starting with '#'. Nested structures are not modeled
 * by ConfigFormat and will be flattened only at the top level when loading.
 */
class YamlFormat implements ConfigFormat {
    /**
     * Loads a top-level map from a YAML file using SnakeYAML. If the file is empty or not a mapping,
     * an empty LinkedHashMap is returned. Only top-level keys are kept.
     */
    @SuppressWarnings("unchecked")
    public @NotNull Map<String, Object> load(@NotNull File file) throws IOException {
        if (!file.exists() || file.length() == 0) return new LinkedHashMap<>();
        try (InputStream in = new FileInputStream(file)) {
            Yaml yaml = new Yaml();
            Object obj = yaml.load(in);
            if (obj instanceof Map<?, ?> m) {
                Map<String, Object> out = new LinkedHashMap<>();
                for (Map.Entry<?, ?> e : m.entrySet()) {
                    if (e.getKey() != null)
                        out.put(String.valueOf(e.getKey()), e.getValue());
                }
                return out;
            }
            return new LinkedHashMap<>();
        }
    }

    /**
     * Saves a flat map to YAML. Includes optional header/footer and per-key comments by emitting
     * comment lines that start with '#'. List values are written as sequence items. The file is
     * overwritten using UTF-8 encoding.
     */
    public void save(@NotNull File file,
                     @NotNull Map<String, Object> data,
                     @Nullable Map<String, List<String>> comments,
                     @Nullable List<String> header,
                     @Nullable List<String> footer) throws IOException {
        // Build YAML with comments manually (flat keys only)
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
            writeYamlEntry(lines, key, value);
            lines.add("");
        }
        if (footer != null && !footer.isEmpty()) {
            for (String f : footer) lines.add("# " + safe(f));
        }
        try (Writer w = new OutputStreamWriter(new FileOutputStream(file, false), StandardCharsets.UTF_8)) {
            for (int i = 0; i < lines.size(); i++) {
                w.write(lines.get(i));
                if (i < lines.size() - 1) w.write("\n");
            }
        }
    }

    private static void writeYamlEntry(@NotNull List<String> lines, @NotNull String key, @Nullable Object value) {
        if (value instanceof List<?> list) {
            lines.add(key + ":");
            for (Object o : list) {
                lines.add("  - " + yamlScalar(o));
            }
            return;
        }
        lines.add(key + ": " + yamlScalar(value));
    }

    private static @NotNull String yamlScalar(@Nullable Object v) {
        if (v == null) return "null";
        if (v instanceof Number || v instanceof Boolean) return String.valueOf(v);
        String s = String.valueOf(v);
        // Quote if contains risky characters
        if (s.contains(":") || s.contains("#") || s.startsWith("[") || s.startsWith("{") || s.contains("\n") || s.contains("\r") ) {
            return "'" + s.replace("'", "''") + "'";
        }
        return s;
    }

    private static @NotNull String safe(@Nullable String s) { return s == null ? "" : s.replace("\r", "").replace("\n", " "); }

    /**
     * {@inheritDoc}
     */
    public @NotNull String getName() { return "yaml"; }
}
