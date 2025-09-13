package de.feelix.leviathan.itemstack;

import de.feelix.leviathan.annotations.NotNull;
import de.feelix.leviathan.exceptions.ApiMisuseException;
import de.feelix.leviathan.util.Preconditions;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Fluent builder for constructing and configuring Bukkit ItemStack instances.
 * <p>
 * This class provides a chainable API for setting common properties such as name,
 * lore, enchantments, color (for leather armor), and book metadata. It enforces
 * basic preconditions for parameters and uses custom exceptions to signal API misuse.
 * </p>
 */
public class ItemStackBuilder {

    private final @NotNull ItemStack item;
    private final @NotNull ItemMeta meta;

    /**
     * Create a new builder with a fresh ItemStack of the given material.
     *
     * @param material the material of the item (must not be null)
     */
    private ItemStackBuilder(@NotNull Material material) {
        this(new ItemStack(Preconditions.checkNotNull(material, "material")));
    }

    /**
     * Create a new builder with a fresh ItemStack of the given material and data value.
     *
     * @param material the material of the item (must not be null)
     * @param data     the legacy data/durability byte
     */
    private ItemStackBuilder(@NotNull Material material, byte data) {
        this(new ItemStack(Preconditions.checkNotNull(material, "material"), 1, data));
    }

    /**
     * Create a new builder wrapping an existing ItemStack.
     *
     * @param item an existing item to configure (must not be null)
     */
    private ItemStackBuilder(@NotNull ItemStack item) {
        this.item = Preconditions.checkNotNull(item, "item");
        ItemMeta m = Preconditions.checkNotNull(item.getItemMeta(), "itemMeta");
        this.meta = m;
    }

    /**
     * Factory method that creates a builder for a new ItemStack with the given material.
     *
     * @param material the material (must not be null)
     * @return a new ItemStackBuilder
     */
    public static @NotNull ItemStackBuilder create(@NotNull Material material) {
        return new ItemStackBuilder(material);
    }

    /**
     * Factory method that creates a builder for a new ItemStack with the given material and legacy data value.
     *
     * @param material the material (must not be null)
     * @param data     the legacy data value
     * @return a new ItemStackBuilder
     */
    public static @NotNull ItemStackBuilder create(@NotNull Material material, byte data) {
        return new ItemStackBuilder(material, data);
    }

    /**
     * Factory method that wraps an existing ItemStack.
     *
     * @param item the item to wrap (must not be null)
     * @return a new ItemStackBuilder wrapping the provided item
     */
    public static @NotNull ItemStackBuilder create(@NotNull ItemStack item) {
        return new ItemStackBuilder(item);
    }

    /**
     * Set the stack amount. Validates against the item's maximum stack size.
     *
     * @param amount the desired amount (1..item.getMaxStackSize())
     * @return this builder for chaining
     * @throws ApiMisuseException if the amount is out of range
     */
    public @NotNull ItemStackBuilder setAmount(int amount) {
        int max = item.getMaxStackSize();
        Preconditions.checkArgument(amount >= 1 && amount <= max,
            "amount must be between 1 and " + max + ": " + amount);
        item.setAmount(amount);
        return this;
    }

    /**
     * Set the display name of the item.
     *
     * @param name the non-null, non-blank display name
     * @return this builder
     */
    public @NotNull ItemStackBuilder setName(@NotNull String name) {
        meta.setDisplayName(Preconditions.checkNotBlank(name, "name"));
        return this;
    }

    /**
     * Append multiple lines to the item's lore.
     *
     * @param lines the lines to add (must not be null)
     * @return this builder
     */
    public @NotNull ItemStackBuilder lore(@NotNull ArrayList<String> lines) {
        Preconditions.checkNotNull(lines, "lines");
        List<String> lore = meta.getLore() == null ? new ArrayList<>() : new ArrayList<>(meta.getLore());
        lore.addAll(lines);
        meta.setLore(lore);
        return this;
    }

    /**
     * Append multiple lines to the item's lore.
     *
     * @param lines the lines to add (must not be null)
     * @return this builder
     */
    public @NotNull ItemStackBuilder lore(@NotNull String... lines) {
        Preconditions.checkNotNull(lines, "lines");
        List<String> lore = meta.getLore() == null ? new ArrayList<>() : new ArrayList<>(meta.getLore());
        lore.addAll(Arrays.asList(lines));
        meta.setLore(lore);
        return this;
    }

    /**
     * Set the item durability (damage) value.
     *
     * @param durability non-negative durability value
     * @return this builder
     */
    public @NotNull ItemStackBuilder durability(@NotNull Integer durability) {
        Preconditions.checkNotNull(durability, "durability");
        Preconditions.checkArgument(durability >= 0, "durability must be >= 0");
        item.setDurability((short) durability.intValue());
        return this;
    }

    /**
     * Add an enchantment at a given level.
     *
     * @param enchantment the enchantment to add (must not be null)
     * @param level       the level (>= 1)
     * @return this builder
     */
    public @NotNull ItemStackBuilder enchantment(@NotNull Enchantment enchantment, int level) {
        Preconditions.checkNotNull(enchantment, "enchantment");
        Preconditions.checkArgument(level >= 1, "enchantment level must be >= 1");
        meta.addEnchant(enchantment, level, true);
        return this;
    }

    /**
     * Add an enchantment with a default level of 1.
     *
     * @param enchantment the enchantment to add (must not be null)
     * @return this builder
     */
    public @NotNull ItemStackBuilder enchantment(@NotNull Enchantment enchantment) {
        Preconditions.checkNotNull(enchantment, "enchantment");
        meta.addEnchant(enchantment, 1, false);
        return this;
    }

    /**
     * Remove the given enchantment, if present.
     *
     * @param enchantment the enchantment to remove (must not be null)
     * @return this builder
     */
    public @NotNull ItemStackBuilder removeEnchantment(@NotNull Enchantment enchantment) {
        Preconditions.checkNotNull(enchantment, "enchantment");
        meta.removeEnchant(enchantment);
        return this;
    }

    /**
     * Remove all enchantments from the item.
     *
     * @return this builder
     */
    public @NotNull ItemStackBuilder clearEnchantments() {
        item.getEnchantments().keySet().forEach(item::removeEnchantment);
        return this;
    }

    /**
     * Change the material type of the underlying item.
     *
     * @param material the new material (must not be null)
     * @return this builder
     */
    public @NotNull ItemStackBuilder type(@NotNull Material material) {
        item.setType(Preconditions.checkNotNull(material, "material"));
        return this;
    }

    /**
     * Clear all lore lines, resulting in an empty lore list.
     *
     * @return this builder
     */
    public @NotNull ItemStackBuilder clearLore() {
        meta.setLore(new ArrayList<>());
        return this;
    }

    /**
     * Set the leather armor color. Only valid for leather armor items.
     *
     * @param color the color to apply (must not be null)
     * @return this builder
     * @throws ApiMisuseException if the item is not a leather armor piece
     */
    public @NotNull ItemStackBuilder color(@NotNull Color color) {
        Preconditions.checkNotNull(color, "color");
        if (item.getType() == Material.LEATHER_BOOTS
            || item.getType() == Material.LEATHER_CHESTPLATE
            || item.getType() == Material.LEATHER_HELMET
            || item.getType() == Material.LEATHER_LEGGINGS) {
            ((LeatherArmorMeta) meta).setColor(color);
            return flag(ItemFlag.HIDE_ATTRIBUTES);
        } else {
            throw new ApiMisuseException("color() only applicable for leather armor");
        }
    }

    /**
     * Set the author of a written book. Only valid for WRITTEN_BOOK items.
     *
     * @param name the author name (must not be blank)
     * @return this builder
     * @throws ApiMisuseException if the item is not a written book
     */
    public @NotNull ItemStackBuilder author(@NotNull String name) {
        if (item.getType() == Material.WRITTEN_BOOK) {
            ((BookMeta) meta).setAuthor(Preconditions.checkNotBlank(name, "name"));
            return this;
        } else {
            throw new ApiMisuseException("author() only applicable for written books");
        }
    }

    /**
     * Replace the content of a specific page in a written book.
     *
     * @param content the page content (must not be null)
     * @param index   1-based page index
     * @return this builder
     * @throws ApiMisuseException if the item is not a written book
     */
    public @NotNull ItemStackBuilder page(@NotNull String content, int index) {
        Preconditions.checkNotNull(content, "content");
        Preconditions.checkArgument(index >= 1, "page index must be >= 1");
        if (item.getType() == Material.WRITTEN_BOOK) {
            ((BookMeta) meta).setPage(index, content);
            return this;
        } else {
            throw new ApiMisuseException("page(index) only applicable for written books");
        }
    }

    /**
     * Append a new page to a written book.
     *
     * @param content the page content (must not be null)
     * @return this builder
     * @throws ApiMisuseException if the item is not a written book
     */
    public @NotNull ItemStackBuilder page(@NotNull String content) {
        Preconditions.checkNotNull(content, "content");
        if (item.getType() == Material.WRITTEN_BOOK) {
            ((BookMeta) meta).addPage(content);
            return this;
        } else {
            throw new ApiMisuseException("page(content) only applicable for written books");
        }
    }

    /**
     * Add one or more item flags.
     *
     * @param flag the flags to add (must not be null)
     * @return this builder
     */
    public @NotNull ItemStackBuilder flag(@NotNull ItemFlag... flag) {
        Preconditions.checkNotNull(flag, "flag");
        meta.addItemFlags(flag);
        return this;
    }

    /**
     * Remove one or more item flags.
     *
     * @param flag the flags to remove (must not be null)
     * @return this builder
     */
    public @NotNull ItemStackBuilder removeFlag(@NotNull ItemFlag... flag) {
        Preconditions.checkNotNull(flag, "flag");
        meta.removeItemFlags(flag);
        return this;
    }

    /**
     * Apply a subtle glow by adding a hidden, harmless enchantment.
     *
     * @return this builder
     */
    public @NotNull ItemStackBuilder glow() {
        this.enchantment(Enchantment.UNBREAKING);
        this.flag(ItemFlag.HIDE_ENCHANTS);
        return this;
    }

    /**
     * Remove the glow effect by removing the hidden enchantment.
     *
     * @return this builder
     */
    public @NotNull ItemStackBuilder unglow() {
        this.removeEnchantment(Enchantment.UNBREAKING);
        return this;
    }

    /**
     * Mark the item as unbreakable.
     *
     * @return this builder
     */
    public @NotNull ItemStackBuilder unbreakable() {
        meta.setUnbreakable(true);
        return this;
    }

    /**
     * Apply the accumulated meta changes back to the underlying ItemStack.
     *
     * @return this builder
     */
    public @NotNull ItemStackBuilder setMeta() {
        this.item.setItemMeta(this.meta);
        return this;
    }

    /**
     * Return the underlying ItemStack reference.
     *
     * @return the item being built (never null)
     */
    public @NotNull ItemStack getItem() {
        return item;
    }

    /**
     * Finalize the build by applying the meta to the item and returning it.
     *
     * @return the built item (never null)
     */
    public @NotNull ItemStack build() {
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Build the item and register a post-build action in the provided map.
     * The action receives the built ItemStack when invoked later.
     *
     * @param consumer the action to associate with the built item (must not be null)
     * @param map      the target map to register the association (must not be null)
     * @return the built item
     */
    public @NotNull ItemStack buildWithAction(
        @NotNull Consumer<ItemStack> consumer,
        @NotNull Map<ItemStack, Consumer<ItemStack>> map
    ) {
        Preconditions.checkNotNull(consumer, "consumer");
        Preconditions.checkNotNull(map, "map");
        ItemStack itemStack = this.build();
        map.put(itemStack, consumer);
        return itemStack;
    }

    /**
     * Build the item and register a click action in the provided map.
     * The action receives the click type and the built ItemStack when invoked later.
     *
     * @param consumer the action to associate with the built item (must not be null)
     * @param map      the target map to register the association (must not be null)
     * @return the built item
     */
    public @NotNull ItemStack buildWithAction(
        @NotNull BiConsumer<ClickType, ItemStack> consumer,
        @NotNull Map<ItemStack, BiConsumer<ClickType, ItemStack>> map
    ) {
        Preconditions.checkNotNull(consumer, "consumer");
        Preconditions.checkNotNull(map, "map");
        ItemStack itemStack = this.build();
        map.put(itemStack, consumer);
        return itemStack;
    }
}
