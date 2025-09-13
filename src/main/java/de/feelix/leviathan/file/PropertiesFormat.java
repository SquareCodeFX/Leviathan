package de.feelix.leviathan.file;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

class PropertiesFormat implements ConfigFormat {
    public Map<String, Object> load(File file) throws IOException {
        Map<String, Object> out = new LinkedHashMap<>();
        if (!file.exists() || file.length() == 0) return out;
        Properties props = new Properties();
        try (InputStream in = new FileInputStream(file)) {
            props.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        for (String name : props.stringPropertyNames()) {
            out.put(name, props.getProperty(name));
        }
        return out;
    }

    public void save(File file,
                     Map<String, Object> data,
                     Map<String, List<String>> comments,
                     List<String> header,
                     List<String> footer) throws IOException {
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
            Object v = data.get(key);
            lines.add(escapeKey(key) + "=" + escapeValue(v));
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

    private static String escapeKey(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case ' ': sb.append("\\ "); break;
                case '\\': sb.append("\\\\"); break;
                case '=': case ':': case '#': case '!': sb.append('\\').append(c); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default: sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String escapeValue(Object v) {
        String s = v == null ? "" : String.valueOf(v);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case ' ': sb.append("\\ "); break;
                case '\\': sb.append("\\\\"); break;
                case '=': case ':': case '#': case '!': sb.append('\\').append(c); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default: sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String safe(String s) { return s == null ? "" : s.replace("\r", "").replace("\n", " "); }

    public String getName() { return "properties"; }
}
