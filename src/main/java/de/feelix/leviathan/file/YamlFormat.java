package de.feelix.leviathan.file;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

class YamlFormat implements ConfigFormat {
    @SuppressWarnings("unchecked")
    public Map<String, Object> load(File file) throws IOException {
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

    public void save(File file,
                     Map<String, Object> data,
                     Map<String, List<String>> comments,
                     List<String> header,
                     List<String> footer) throws IOException {
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

    private static void writeYamlEntry(List<String> lines, String key, Object value) {
        if (value instanceof List<?> list) {
            lines.add(key + ":");
            for (Object o : list) {
                lines.add("  - " + yamlScalar(o));
            }
            return;
        }
        lines.add(key + ": " + yamlScalar(value));
    }

    private static String yamlScalar(Object v) {
        if (v == null) return "null";
        if (v instanceof Number || v instanceof Boolean) return String.valueOf(v);
        String s = String.valueOf(v);
        // Quote if contains risky characters
        if (s.contains(":") || s.contains("#") || s.startsWith("[") || s.startsWith("{") || s.contains("\n") || s.contains("\r") ) {
            return "'" + s.replace("'", "''") + "'";
        }
        return s;
    }

    private static String safe(String s) { return s == null ? "" : s.replace("\r", "").replace("\n", " "); }

    public String getName() { return "yaml"; }
}
