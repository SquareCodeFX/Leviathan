package de.feelix.leviathan.inventory;

import de.feelix.leviathan.annotations.NotNull;
import de.feelix.leviathan.annotations.Nullable;
import de.feelix.leviathan.inventory.click.ClickAction;
import de.feelix.leviathan.inventory.click.ClickContext;
import de.feelix.leviathan.util.Preconditions;
import org.bukkit.inventory.ItemStack;

import java.util.function.Predicate;

/**
 * An interactive button within an inventory slot, holding an ItemStack and a click action.
 */
public final class ItemButton {
    private ItemStack item;
    private ClickAction action;
    private @Nullable Predicate<ClickContext> visibility;
    private boolean cancelClick = true;

    private ItemButton(ItemStack item, ClickAction action) {
        this.item = Preconditions.checkNotNull(item, "item");
        this.action = Preconditions.checkNotNull(action, "action");
    }

    /**
     * Create a new button with the given display item and click handler.
     *
     * @param item   the item to show in the slot (must not be null)
     * @param action the action to execute when the slot is clicked (must not be null)
     * @return a new ItemButton instance
     */
    public static @NotNull ItemButton of(@NotNull ItemStack item, @NotNull ClickAction action) {
        return new ItemButton(item, action);
    }

    /**
     * Set a visibility predicate for this button. When present, the button is considered
     * visible only if the predicate returns true for the current click context.
     *
     * @param predicate a predicate to evaluate visibility, or null to always show the button
     * @return this button for chaining
     */
    public @NotNull ItemButton visibleWhen(@Nullable Predicate<ClickContext> predicate) {
        this.visibility = predicate;
        return this;
    }

    /**
     * Control whether this button cancels the click event. When true (default), the
     * click is prevented from moving items. Set to false to allow normal item movement.
     *
     * @param cancel true to cancel the click, false to allow it to pass
     * @return this button for chaining
     */
    public @NotNull ItemButton cancelClick(boolean cancel) {
        this.cancelClick = cancel;
        return this;
    }

    /**
     * Evaluate whether this button should be considered visible for the given context.
     * If no visibility predicate is set, the button is visible by default.
     *
     * @param ctx the click context used to evaluate visibility (must not be null)
     * @return true if visible; false if hidden
     */
    public boolean isVisible(@NotNull ClickContext ctx) {
        return visibility == null || visibility.test(ctx);
    }

    /**
         * Whether clicks on this button are cancelled by default.
         *
         * @return true if the click is cancelled; false otherwise
         */
        public boolean cancelClick() { return cancelClick; }

    /**
         * Get the current ItemStack displayed by this button.
         */
        public @NotNull ItemStack getItem() { return item; }

    /**
         * Replace the displayed ItemStack.
         *
         * @param item the new item to display (must not be null)
         */
        public void setItem(@NotNull ItemStack item) { this.item = Preconditions.checkNotNull(item, "item"); }

    /**
         * Get the click handler associated with this button.
         */
        public @NotNull ClickAction getAction() { return action; }

    /**
         * Replace the click handler for this button.
         *
         * @param action the new action to run on click (must not be null)
         */
        public void setAction(@NotNull ClickAction action) { this.action = Preconditions.checkNotNull(action, "action"); }
}
