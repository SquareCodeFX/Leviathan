package de.feelix.leviathan.file;

import de.feelix.leviathan.annotations.NotNull;
import de.feelix.leviathan.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Format abstraction to load/save simple top-level key/value maps with optional per-key comments,
 * header and footer comment lines. Nested structures are not supported by this abstraction.
 */
interface ConfigFormat {
    /**
     * Loads a flat, top-level key/value map from the provided file path.
     * Implementations should preserve insertion order if possible.
     *
     * @param file the file to read from (must not be null)
     * @return a non-null, mutable Map of top-level keys to values
     * @throws IOException if reading or parsing fails
     */
    @NotNull Map<String, Object> load(@NotNull File file) throws IOException;

    /**
     * Saves the provided flat map to the target file, optionally including comments.
     * Implementations should overwrite the file contents entirely.
     *
     * @param file     the file to write to (must not be null)
     * @param data     non-null top-level key/value pairs to persist (map may be empty)
     * @param comments optional per-key comment lines (ignored by formats without comment support)
     * @param header   optional header comment lines to place before data
     * @param footer   optional footer comment lines to place after data
     * @throws IOException if writing fails
     */
    void save(@NotNull File file,
              @NotNull Map<String, Object> data,
              @Nullable Map<String, List<String>> comments,
              @Nullable List<String> header,
              @Nullable List<String> footer) throws IOException;

    /**
     * Gets a short, lowercase name of the format (e.g., "yaml", "json").
     *
     * @return the non-null format name for diagnostics and logging
     */
    @NotNull String getName();
}
