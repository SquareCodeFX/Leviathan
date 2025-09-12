package de.feelix.leviathan.inventory;

import de.feelix.leviathan.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Helper to compute common slot arrays.
 */
public final class Slots {
    private Slots() {}

    /**
     * All slots in a chest-like grid for the given number of rows.
     *
     * @param rows number of rows (1..6)
     * @return an array of 0-based slot indices in ascending order
     */
    public static int[] all(int rows) { return range(0, rows * 9 - 1); }

    /**
     * A contiguous range of 0-based slots, inclusive of endpoints.
     *
     * @param startInclusive first slot (0-based)
     * @param endInclusive   last slot (0-based)
     * @return an array containing all indices in the range, or empty if start > end
     */
    public static int[] range(int startInclusive, int endInclusive) {
        int len = Math.max(0, endInclusive - startInclusive + 1);
        int[] arr = new int[len];
        for (int i = 0; i < len; i++) arr[i] = startInclusive + i;
        return arr;
    }

    /**
     * A rectangle of slots defined by 1-based row/column coordinates, inclusive.
     *
     * @param rowStart1 starting row (1-based)
     * @param colStart1 starting col (1-based)
     * @param rowEnd1   ending row (1-based)
     * @param colEnd1   ending col (1-based)
     * @return an array of 0-based slot indices covering the rectangle, row-major
     */
    public static int[] rect(int rowStart1, int colStart1, int rowEnd1, int colEnd1) {
        int rs = Math.min(rowStart1, rowEnd1), re = Math.max(rowStart1, rowEnd1);
        int cs = Math.min(colStart1, colEnd1), ce = Math.max(colStart1, colEnd1);
        List<Integer> list = new ArrayList<>();
        for (int r = rs; r <= re; r++) {
            for (int c = cs; c <= ce; c++) {
                list.add(FluentInventory.slot(r, c));
            }
        }
        return list.stream().mapToInt(Integer::intValue).toArray();
    }

    /**
     * Inner area of a chest UI excluding the outermost border.
     * For rows &lt;= 2 there is no inner area and an empty array is returned.
     *
     * @param rows number of rows (1..6)
     * @return an array of 0-based slot indices representing the inside area
     */
    public static int[] inside(int rows) {
        // inside area excluding the outer border
        if (rows <= 2) return new int[0];
        List<Integer> list = new ArrayList<>();
        for (int r = 2; r <= rows - 1; r++) {
            for (int c = 2; c <= 8; c++) {
                list.add(FluentInventory.slot(r, c));
            }
        }
        return list.stream().mapToInt(Integer::intValue).toArray();
    }
}
