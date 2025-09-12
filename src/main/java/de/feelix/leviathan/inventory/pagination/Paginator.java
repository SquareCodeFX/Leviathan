package de.feelix.leviathan.inventory.pagination;

import de.feelix.leviathan.annotations.NotNull;
import de.feelix.leviathan.inventory.FluentInventory;
import de.feelix.leviathan.inventory.ItemButton;
import de.feelix.leviathan.util.Preconditions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Lightweight paginator that lays out ItemButtons into a FluentInventory at specified slots.
 */
public final class Paginator {

    private final List<ItemButton> items;
    private final int[] slots;

    /**
     * Create a paginator over the supplied items rendered into the provided slot positions.
     *
     * @param items the item buttons to paginate (must not be null)
     * @param slots the slot indices that form the page area; order is used for layout
     */
    public Paginator(@NotNull List<ItemButton> items, int... slots) {
        this.items = new ArrayList<>(Preconditions.checkNotNull(items, "items"));
        Preconditions.checkArgument(slots != null && slots.length > 0, "slots must not be empty");
        this.slots = Arrays.copyOf(slots, slots.length);
    }

    /**
     * Total number of pages based on current items and page capacity.
     * Never returns less than 1.
     */
    public int pageCount() {
        int perPage = slots.length;
        if (perPage == 0) return 1;
        return Math.max(1, (int) Math.ceil(items.size() / (double) perPage));
    }

    /**
     * Render the specified page into the given inventory. The page index is clamped to valid bounds.
     * Existing buttons in the target slots are cleared first.
     *
     * @param inv       the inventory to render into (must not be null)
     * @param pageIndex the 0-based page index to render; values outside range are clamped
     */
    public void render(@NotNull FluentInventory inv, int pageIndex) {
        Preconditions.checkNotNull(inv, "inv");
        int pages = pageCount();
        if (pages == 0) pages = 1;
        int page = Math.max(0, Math.min(pageIndex, pages - 1));
        int start = page * slots.length;
        int end = Math.min(start + slots.length, items.size());

        // Clear the area first
        for (int slot : slots) inv.set(slot, (ItemButton) null);

        // Fill current page
        int s = 0;
        for (int i = start; i < end; i++) {
            inv.set(slots[s++], items.get(i));
        }
    }

    /**
     * The page area slot indices (defensive copy).
     */
    public int[] slots() { return Arrays.copyOf(slots, slots.length); }
}
