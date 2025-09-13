package de.feelix.leviathan.file;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Format abstraction to load/save simple top-level key/value maps with optional per-key comments,
 * header and footer comment lines. Nested structures are not supported by this abstraction.
 */
interface ConfigFormat {
    Map<String, Object> load(File file) throws IOException;

    void save(File file,
              Map<String, Object> data,
              Map<String, List<String>> comments,
              List<String> header,
              List<String> footer) throws IOException;

    String getName();
}
