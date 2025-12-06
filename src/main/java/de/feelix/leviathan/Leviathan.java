package de.feelix.leviathan;

import de.feelix.leviathan.annotations.NotNull;
import de.feelix.leviathan.command.argument.ArgContext;
import de.feelix.leviathan.command.core.SlashCommand;
import de.feelix.leviathan.command.core.SlashCommandBuilder;
import de.feelix.leviathan.command.pagination.PaginationHelper;
import de.feelix.leviathan.command.pagination.config.PaginationConfig;
import de.feelix.leviathan.command.pagination.domain.PaginatedResult;

import java.util.Collection;
import java.util.List;

/**
 * Main API facade for the Leviathan command framework.
 * <p>
 * This class provides convenient static entry points for creating commands and builders,
 * offering a cleaner and more discoverable API surface.
 * <p>
 * <b>Example usage:</b>
 * <pre>{@code
 * Leviathan.command("example")
 *     .description("An example command")
 *     .argString("name")
 *     .executes((sender, ctx) -> {
 *         String name = ctx.get("name", String.class);
 *         sender.sendMessage("Hello, " + name + "!");
 *     })
 *     .register(plugin);
 * }</pre>
 *
 * <b>Paginated command example:</b>
 * <pre>{@code
 * Leviathan.command("list")
 *     .argPage()
 *     .executes((sender, ctx) -> {
 *         int page = ctx.getIntOrDefault("page", 1);
 *         Leviathan.paginate(items)
 *             .page(page)
 *             .pageSize(10)
 *             .header("§6=== Items ===")
 *             .formatter(item -> "§7- " + item)
 *             .send(sender);
 *     })
 *     .register(plugin);
 * }</pre>
 *
 * @since 1.2.0
 */
public final class Leviathan {

    private Leviathan() {
        // Utility class - prevent instantiation
    }

    /**
     * Create a new command builder with the given name.
     * <p>
     * This is the primary entry point for building commands in the Leviathan framework.
     * Equivalent to {@link SlashCommand#builder(String)}.
     *
     * @param name the command name as declared in plugin.yml
     * @return a new SlashCommandBuilder instance
     */
    public static @NotNull SlashCommandBuilder command(@NotNull String name) {
        return SlashCommand.builder(name);
    }

    /**
     * Create a new argument context builder for configuring argument behavior.
     * <p>
     * Use this to define validation rules, completions, permissions, and other
     * argument-specific options.
     * <p>
     * Equivalent to {@link ArgContext#builder()}.
     *
     * @return a new ArgContext.Builder instance
     */
    public static @NotNull ArgContext.Builder argContext() {
        return ArgContext.builder();
    }

    /**
     * Get the default argument context (no special configuration).
     * <p>
     * Equivalent to {@link ArgContext#defaultContext()}.
     *
     * @return the default ArgContext instance
     */
    public static @NotNull ArgContext defaultArgContext() {
        return ArgContext.defaultContext();
    }

    /**
     * Start building a paginated output from a collection of items.
     * <p>
     * This is the primary entry point for creating paginated command output.
     * Use this in conjunction with {@link SlashCommandBuilder#argPage()} for
     * seamless pagination support.
     * <p>
     * <b>Example:</b>
     * <pre>{@code
     * Leviathan.paginate(playerList)
     *     .page(pageNumber)
     *     .pageSize(10)
     *     .header("§6Online Players")
     *     .formatter(player -> "§7- " + player.getName())
     *     .send(sender);
     * }</pre>
     *
     * @param items the items to paginate
     * @param <T>   the type of items
     * @return a new PaginatedOutputBuilder
     * @see PaginationHelper#paginate(Collection)
     */
    public static <T> @NotNull PaginationHelper.PaginatedOutputBuilder<T> paginate(@NotNull Collection<T> items) {
        return PaginationHelper.paginate(items);
    }

    /**
     * Start building a paginated output from a list of items.
     *
     * @param items the items to paginate
     * @param <T>   the type of items
     * @return a new PaginatedOutputBuilder
     * @see PaginationHelper#paginate(List)
     */
    public static <T> @NotNull PaginationHelper.PaginatedOutputBuilder<T> paginate(@NotNull List<T> items) {
        return PaginationHelper.paginate(items);
    }

    /**
     * Quick pagination with default settings - returns paginated items for manual handling.
     *
     * @param items      the items to paginate
     * @param pageNumber the page number (1-based)
     * @param pageSize   items per page
     * @param <T>        the type of items
     * @return the paginated result
     * @see PaginationHelper#getPage(Collection, int, int)
     */
    public static <T> @NotNull PaginatedResult<T> getPage(@NotNull Collection<T> items, int pageNumber, int pageSize) {
        return PaginationHelper.getPage(items, pageNumber, pageSize);
    }

    /**
     * Quick pagination with default page size of 10.
     *
     * @param items      the items to paginate
     * @param pageNumber the page number (1-based)
     * @param <T>        the type of items
     * @return the paginated result
     * @see PaginationHelper#getPage(Collection, int)
     */
    public static <T> @NotNull PaginatedResult<T> getPage(@NotNull Collection<T> items, int pageNumber) {
        return PaginationHelper.getPage(items, pageNumber);
    }

    /**
     * Create a new pagination configuration builder.
     * <p>
     * Use this to customize pagination behavior like page size, visible pages,
     * caching, and display symbols.
     *
     * @return a new PaginationConfig.Builder instance
     */
    public static @NotNull PaginationConfig.Builder paginationConfig() {
        return PaginationConfig.builder();
    }

    /**
     * Get the default pagination configuration.
     *
     * @return the default PaginationConfig instance
     */
    public static @NotNull PaginationConfig defaultPaginationConfig() {
        return PaginationConfig.defaults();
    }
}
