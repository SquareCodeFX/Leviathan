package de.feelix.leviathan.command.pagination;

import de.feelix.leviathan.annotations.NotNull;
import de.feelix.leviathan.annotations.Nullable;
import de.feelix.leviathan.command.message.MessageProvider;
import de.feelix.leviathan.command.pagination.config.PaginationConfig;
import de.feelix.leviathan.command.pagination.datasource.ListDataSource;
import de.feelix.leviathan.command.pagination.domain.NavigationWindow;
import de.feelix.leviathan.command.pagination.domain.PaginatedResult;
import de.feelix.leviathan.command.pagination.domain.PageInfo;
import de.feelix.leviathan.command.pagination.service.PaginationService;
import de.feelix.leviathan.command.pagination.util.PaginationUtils;
import de.feelix.leviathan.util.Preconditions;
import org.bukkit.command.CommandSender;

import java.util.Collection;
import java.util.List;
import java.util.function.Function;

/**
 * Helper class that bridges the SlashCommand system with the pagination system.
 * Provides convenient, high-level methods for creating and displaying paginated command output.
 * <p>
 * Design notes:
 * <ul>
 *   <li>Stateless utility — all methods are static and thread-safe.</li>
 *   <li>Friendly defaults — page size defaults to 10 and page numbers are 1-based.</li>
 *   <li>Separation of concerns — pagination (data slicing) is handled by services, while this class
 *       focuses on formatting and delivery to a {@code CommandSender}.</li>
 *   <li>Empty handling — when no items are present, the builder emits an optional header and a configurable
 *       empty message (default: "§7No items found.").</li>
 * </ul>
 * <p>
 * <b>Example usage:</b>
 * <pre>{@code
 * SlashCommand.builder("list")
 *     .argInt("page", ArgContext.builder().optional(true).defaultValue(1).build())
 *     .executes((sender, ctx) -> {
 *         int page = ctx.getIntOrDefault("page", 1);
 *         List<String> items = getItems();
 *         
 *         PaginationHelper.paginate(items)
 *             .page(page)
 *             .pageSize(10)
 *             .header("§6=== Item List ===")
 *             .formatter(item -> "§7- " + item)
 *             .send(sender);
 *     })
 *     .register(plugin);
 * }</pre>
 *
 * @since 1.2.0
 */
public final class PaginationHelper {

    private PaginationHelper() {
        // Utility class - prevent instantiation
    }

    /**
     * Start building a paginated output from a collection of items.
     *
     * @param items the items to paginate
     * @param <T>   the type of items
     * @return a new PaginatedOutputBuilder
     */
    public static <T> @NotNull PaginatedOutputBuilder<T> paginate(@NotNull Collection<T> items) {
        Preconditions.checkNotNull(items, "items");
        return new PaginatedOutputBuilder<>(items);
    }

    /**
     * Start building a paginated output from a list of items.
     *
     * @param items the items to paginate
     * @param <T>   the type of items
     * @return a new PaginatedOutputBuilder
     */
    public static <T> @NotNull PaginatedOutputBuilder<T> paginate(@NotNull List<T> items) {
        Preconditions.checkNotNull(items, "items");
        return new PaginatedOutputBuilder<>(items);
    }

    /**
     * Quick pagination with explicit page size — returns a {@link PaginatedResult} for manual handling.
     * <p>
     * The {@code pageNumber} is 1-based. If it is out of range, underlying services clamp the value to the
     * nearest valid page.
     *
     * @param items      the items to paginate (must not be null)
     * @param pageNumber the page number (1-based)
     * @param pageSize   items per page (minimum 1)
     * @param <T>        the type of items
     * @return the paginated result
     */
    public static <T> @NotNull PaginatedResult<T> getPage(@NotNull Collection<T> items, int pageNumber, int pageSize) {
        Preconditions.checkNotNull(items, "items");
        PaginationService<T> service = PaginationService.<T>builder()
                .dataSource(ListDataSource.of(items))
                .config(PaginationConfig.builder().pageSize(pageSize).build())
                .build();
        return service.getPage(pageNumber);
    }

    /**
     * Quick pagination with default page size of 10.
     * Use when you want a simple paginated slice without configuring a builder.
     *
     * @param items      the items to paginate
     * @param pageNumber the page number (1-based)
     * @param <T>        the type of items
     * @return the paginated result
     */
    public static <T> @NotNull PaginatedResult<T> getPage(@NotNull Collection<T> items, int pageNumber) {
        return getPage(items, pageNumber, 10);
    }

    /**
     * Format a paginated result as a list of strings ready for sending.
     * The resulting list includes item lines and a trailing footer with page info.
     *
     * @param result    the paginated result
     * @param formatter function to format each item as a string
     * @param <T>       the type of items
     * @return list of formatted lines including footer
     */
    public static <T> @NotNull List<String> format(@NotNull PaginatedResult<T> result,
                                                    @NotNull Function<T, String> formatter) {
        return format(result, formatter, null, null);
    }

    /**
     * Format a paginated result with custom header and footer.
     * Page info is always appended at the end; if a non-empty {@code footer} is supplied, it will precede page info
     * on the same line.
     *
     * @param result    the paginated result
     * @param formatter function to format each item as a string
     * @param header    optional header line
     * @param footer    optional footer line (page info will be appended)
     * @param <T>       the type of items
     * @return list of formatted lines
     */
    public static <T> @NotNull List<String> format(@NotNull PaginatedResult<T> result,
                                                    @NotNull Function<T, String> formatter,
                                                    @Nullable String header,
                                                    @Nullable String footer) {
        Preconditions.checkNotNull(result, "result");
        Preconditions.checkNotNull(formatter, "formatter");

        java.util.ArrayList<String> lines = new java.util.ArrayList<>();

        // Add header if present
        if (header != null && !header.isEmpty()) {
            lines.add(header);
        }

        // Format each item
        for (T item : result.getItems()) {
            lines.add(formatter.apply(item));
        }

        // Add page info footer
        String pageInfo = formatPageInfo(result.getPageInfo());
        if (footer != null && !footer.isEmpty()) {
            lines.add(footer + " " + pageInfo);
        } else {
            lines.add(pageInfo);
        }

        return lines;
    }

    /**
     * Format page info as a string using Minecraft legacy color codes.
     * Example output: {@code §7Page §f1§7/§f5 §7(§f10 §7items)}
     *
     * @param pageInfo the page info
     * @return formatted page info string
     */
    public static @NotNull String formatPageInfo(@NotNull PageInfo pageInfo) {
        Preconditions.checkNotNull(pageInfo, "pageInfo");
        return String.format("§7Page §f%d§7/§f%d §7(§f%d §7items)",
                pageInfo.getCurrentPage(),
                pageInfo.getTotalPages(),
                pageInfo.getTotalElements());
    }

    /**
     * Format page info with navigation hints (e.g., clickable commands in chat plugins).
     * The hints include previous/next commands when applicable.
     *
     * @param pageInfo    the page info
     * @param commandBase the base command for navigation hints (e.g., "/list")
     * @return formatted string with navigation hints
     */
    public static @NotNull String formatPageInfoWithNavigation(@NotNull PageInfo pageInfo, @NotNull String commandBase) {
        Preconditions.checkNotNull(pageInfo, "pageInfo");
        Preconditions.checkNotNull(commandBase, "commandBase");

        StringBuilder sb = new StringBuilder();
        sb.append("§7Page §f").append(pageInfo.getCurrentPage())
                .append("§7/§f").append(pageInfo.getTotalPages());

        if (pageInfo.hasPreviousPage() || pageInfo.hasNextPage()) {
            sb.append(" §8[");
            if (pageInfo.hasPreviousPage()) {
                sb.append("§e").append(commandBase).append(" ").append(pageInfo.getCurrentPage() - 1).append("§8 <- ");
            }
            if (pageInfo.hasNextPage()) {
                sb.append("§8-> §e").append(commandBase).append(" ").append(pageInfo.getCurrentPage() + 1);
            }
            sb.append("§8]");
        }

        return sb.toString();
    }

    /**
     * Create a simple navigation bar string.
     *
     * @param currentPage the current page number
     * @param totalPages  the total number of pages
     * @return navigation bar like {@literal "<< [1] 2 3 ... 10 >>"}
     */
    public static @NotNull String createNavigationBar(int currentPage, int totalPages) {
        return createNavigationBar(currentPage, totalPages, PaginationConfig.defaults());
    }

    /**
     * Create a navigation bar string with custom config.
     *
     * @param currentPage the current page number
     * @param totalPages  the total number of pages
     * @param config      pagination config for styling
     * @return navigation bar string
     */
    public static @NotNull String createNavigationBar(int currentPage, int totalPages, @NotNull PaginationConfig config) {
        Preconditions.checkNotNull(config, "config");

        StringBuilder sb = new StringBuilder("§7");

        // Previous arrow
        if (currentPage > 1) {
            sb.append("§e").append(config.getPreviousSymbol()).append(" §7");
        } else {
            sb.append("§8").append(config.getPreviousSymbol()).append(" §7");
        }

        // Page numbers
        List<Integer> visiblePages = PaginationUtils.generatePageNumbers(currentPage, totalPages, config.getVisiblePages());

        // Show start ellipsis if needed
        if (!visiblePages.isEmpty() && visiblePages.get(0) > 1) {
            sb.append("1 §8").append(config.getEllipsis()).append("§7 ");
        }

        for (int i = 0; i < visiblePages.size(); i++) {
            int page = visiblePages.get(i);
            if (i > 0) {
                sb.append(" ");
            }
            if (page == currentPage) {
                sb.append("§e§l[").append(page).append("]§r§7");
            } else {
                sb.append(page);
            }
        }

        // Show end ellipsis if needed
        if (!visiblePages.isEmpty() && visiblePages.get(visiblePages.size() - 1) < totalPages) {
            sb.append(" §8").append(config.getEllipsis()).append("§7 ").append(totalPages);
        }

        // Next arrow
        if (currentPage < totalPages) {
            sb.append(" §e").append(config.getNextSymbol());
        } else {
            sb.append(" §8").append(config.getNextSymbol());
        }

        return sb.toString();
    }

    /**
     * Render a compact page window overview using NavigationWindow.
     * <p>
     * This produces output like: {@code (1 ... 4 | _5_ | 6 ... 10)} where the current page
     * is highlighted with the configured prefix/suffix, and ellipses indicate more pages.
     *
     * @param window the navigation window containing visible pages
     * @param config pagination config for styling
     * @return formatted page window string
     */
    public static @NotNull String renderPageWindow(@NotNull NavigationWindow window, @NotNull PaginationConfig config) {
        Preconditions.checkNotNull(window, "window");
        Preconditions.checkNotNull(config, "config");

        StringBuilder sb = new StringBuilder();
        sb.append("(");

        List<Integer> visiblePages = window.getVisiblePages();
        boolean first = true;

        if (window.showStartEllipsis()) {
            sb.append("1 ").append(config.getEllipsis()).append(" ");
        }

        for (int page : visiblePages) {
            if (!first) {
                sb.append(config.getPageSeparator());
            }
            first = false;

            if (page == window.getCurrentPage()) {
                sb.append(config.getCurrentPagePrefix())
                    .append(page)
                    .append(config.getCurrentPageSuffix());
            } else {
                sb.append(page);
            }
        }

        if (window.showEndEllipsis()) {
            sb.append(" ").append(config.getEllipsis()).append(" ").append(window.getTotalPages());
        }

        sb.append(")");
        return sb.toString();
    }

    /**
     * Render a compact page window overview from a PaginatedResult.
     * <p>
     * Convenience method that extracts the NavigationWindow from the result.
     *
     * @param result the paginated result
     * @param config pagination config for styling
     * @return formatted page window string
     */
    public static @NotNull String renderPageWindow(@NotNull PaginatedResult<?> result, @NotNull PaginationConfig config) {
        Preconditions.checkNotNull(result, "result");
        return renderPageWindow(result.getNavigationWindow(), config);
    }

    /**
     * Render a compact page window overview with default config.
     *
     * @param window the navigation window containing visible pages
     * @return formatted page window string
     */
    public static @NotNull String renderPageWindow(@NotNull NavigationWindow window) {
        return renderPageWindow(window, PaginationConfig.defaults());
    }

    /**
     * Render a compact page window overview using a MessageProvider for customizable formatting.
     * <p>
     * This allows full customization of the page window output through the MessageProvider interface,
     * enabling localization and custom styling of the pagination display.
     *
     * @param window the navigation window containing visible pages
     * @param config pagination config for styling parameters
     * @param messages the message provider for rendering
     * @return formatted page window string
     */
    public static @NotNull String renderPageWindow(@NotNull NavigationWindow window, 
                                                    @NotNull PaginationConfig config,
                                                    @NotNull MessageProvider messages) {
        Preconditions.checkNotNull(window, "window");
        Preconditions.checkNotNull(config, "config");
        Preconditions.checkNotNull(messages, "messages");

        return messages.paginationPageWindow(
            window.getVisiblePages(),
            window.getCurrentPage(),
            window.getTotalPages(),
            window.showStartEllipsis(),
            window.showEndEllipsis(),
            config.getEllipsis(),
            config.getPageSeparator(),
            config.getCurrentPagePrefix(),
            config.getCurrentPageSuffix()
        );
    }

    /**
     * Render a compact page window overview from a PaginatedResult using a MessageProvider.
     * <p>
     * Convenience method that extracts the NavigationWindow from the result.
     *
     * @param result the paginated result
     * @param config pagination config for styling
     * @param messages the message provider for rendering
     * @return formatted page window string
     */
    public static @NotNull String renderPageWindow(@NotNull PaginatedResult<?> result, 
                                                    @NotNull PaginationConfig config,
                                                    @NotNull MessageProvider messages) {
        Preconditions.checkNotNull(result, "result");
        return renderPageWindow(result.getNavigationWindow(), config, messages);
    }

    /**
     * Send a paginated result to a command sender.
     *
     * @param sender    the command sender
     * @param result    the paginated result
     * @param formatter function to format each item
     * @param <T>       the type of items
     */
    public static <T> void send(@NotNull CommandSender sender, @NotNull PaginatedResult<T> result,
                                @NotNull Function<T, String> formatter) {
        send(sender, result, formatter, null, null);
    }

    /**
     * Send a paginated result to a command sender with header and footer.
     *
     * @param sender    the command sender
     * @param result    the paginated result
     * @param formatter function to format each item
     * @param header    optional header line
     * @param footer    optional footer line
     * @param <T>       the type of items
     */
    public static <T> void send(@NotNull CommandSender sender, @NotNull PaginatedResult<T> result,
                                @NotNull Function<T, String> formatter,
                                @Nullable String header, @Nullable String footer) {
        Preconditions.checkNotNull(sender, "sender");
        List<String> lines = format(result, formatter, header, footer);
        for (String line : lines) {
            sender.sendMessage(line);
        }
    }

    /**
     * Builder for creating paginated command output with fluent API.
     *
     * @param <T> the type of items
     */
    public static final class PaginatedOutputBuilder<T> {
        private final Collection<T> items;
        private int pageNumber = 1;
        private int pageSize = 10;
        private @Nullable String header = null;
        private @Nullable String footer = null;
        private @Nullable String emptyMessage = "§7No items found.";
        private @NotNull Function<T, String> formatter = Object::toString;
        private boolean showNavigation = true;
        private boolean showPageOverview = false;
        private @Nullable String commandBase = null;
        private @Nullable PaginationConfig config = null;

        PaginatedOutputBuilder(@NotNull Collection<T> items) {
            this.items = items;
        }

        /**
         * Set the page number to display (1-based).
         * Values less than 1 will be clamped to 1.
         *
         * @param pageNumber the page number to display (1-based index)
         * @return this builder for method chaining
         */
        public @NotNull PaginatedOutputBuilder<T> page(int pageNumber) {
            this.pageNumber = Math.max(1, pageNumber);
            return this;
        }

        /**
         * Set the number of items per page.
         * Values less than 1 will be clamped to 1.
         *
         * @param pageSize the maximum number of items to display per page
         * @return this builder for method chaining
         */
        public @NotNull PaginatedOutputBuilder<T> pageSize(int pageSize) {
            this.pageSize = Math.max(1, pageSize);
            return this;
        }

        /**
         * Set the header line displayed before items.
         * Supports Minecraft color codes (e.g., §6 for gold).
         *
         * @param header the header text, or null for no header
         * @return this builder for method chaining
         */
        public @NotNull PaginatedOutputBuilder<T> header(@Nullable String header) {
            this.header = header;
            return this;
        }

        /**
         * Set the footer line displayed after items (before page info).
         * Supports Minecraft color codes (e.g., §7 for gray).
         *
         * @param footer the footer text, or null for no footer
         * @return this builder for method chaining
         */
        public @NotNull PaginatedOutputBuilder<T> footer(@Nullable String footer) {
            this.footer = footer;
            return this;
        }

        /**
         * Set the message displayed when the item collection is empty.
         * Defaults to "§7No items found." if not specified.
         *
         * @param emptyMessage the message to show when there are no items, or null to show nothing
         * @return this builder for method chaining
         */
        public @NotNull PaginatedOutputBuilder<T> emptyMessage(@Nullable String emptyMessage) {
            this.emptyMessage = emptyMessage;
            return this;
        }

        /**
         * Set the formatter function that converts each item to a display string.
         * This function is applied to each item on the current page.
         *
         * @param formatter function that converts an item of type T to a String for display
         * @return this builder for method chaining
         */
        public @NotNull PaginatedOutputBuilder<T> formatter(@NotNull Function<T, String> formatter) {
            Preconditions.checkNotNull(formatter, "formatter");
            this.formatter = formatter;
            return this;
        }

        /**
         * Enable or disable the navigation bar in footer.
         * When enabled, displays navigation arrows and page numbers.
         *
         * @param showNavigation true to show navigation controls, false to hide them
         * @return this builder for method chaining
         */
        public @NotNull PaginatedOutputBuilder<T> showNavigation(boolean showNavigation) {
            this.showNavigation = showNavigation;
            return this;
        }

        /**
         * Enable or disable the compact page overview display.
         * <p>
         * When enabled, displays a compact page window like: {@code (1 ... 4 | _5_ | 6 ... 10)}
         * where the current page is highlighted and ellipses indicate more pages exist.
         *
         * @param showPageOverview true to show the page overview
         * @return this builder
         */
        public @NotNull PaginatedOutputBuilder<T> showPageOverview(boolean showPageOverview) {
            this.showPageOverview = showPageOverview;
            return this;
        }

        /**
         * Set the base command for navigation hints.
         * When set, navigation hints will show commands like "/list 2" for next page.
         *
         * @param commandBase the base command string (e.g., "/list"), or null to disable command hints
         * @return this builder for method chaining
         */
        public @NotNull PaginatedOutputBuilder<T> commandBase(@Nullable String commandBase) {
            this.commandBase = commandBase;
            return this;
        }

        /**
         * Set custom pagination configuration for styling and behavior.
         * If not set, default configuration will be used.
         *
         * @param config the pagination configuration, or null to use defaults
         * @return this builder for method chaining
         * @see PaginationConfig#defaults()
         */
        public @NotNull PaginatedOutputBuilder<T> config(@Nullable PaginationConfig config) {
            this.config = config;
            return this;
        }

        /**
         * Build and return the paginated result containing items for the current page.
         * The page number is automatically clamped to valid range.
         *
         * @return the paginated result with items, page info, and navigation window
         */
        public @NotNull PaginatedResult<T> build() {
            PaginationConfig paginationConfig = config != null ? config :
                    PaginationConfig.builder().pageSize(pageSize).build();

            PaginationService<T> service = PaginationService.<T>builder()
                    .dataSource(ListDataSource.of(items))
                    .config(paginationConfig)
                    .build();

            // Clamp page number to valid range
            int totalPages = service.getTotalPages();
            int validPage = PaginationUtils.clampPageNumber(pageNumber, totalPages);

            return service.getPage(validPage);
        }

        /**
         * Build and format the paginated output as a list of formatted strings.
         * Includes header, formatted items, footer, page info, and optional navigation.
         * If the item collection is empty, returns the header (if set) and empty message.
         *
         * @return list of formatted lines ready to be sent to a player
         */
        public @NotNull List<String> buildLines() {
            if (items.isEmpty()) {
                java.util.ArrayList<String> lines = new java.util.ArrayList<>();
                if (header != null) lines.add(header);
                if (emptyMessage != null) lines.add(emptyMessage);
                return lines;
            }

            PaginatedResult<T> result = build();
            java.util.ArrayList<String> lines = new java.util.ArrayList<>();

            // Header
            if (header != null && !header.isEmpty()) {
                lines.add(header);
            }

            // Items
            for (T item : result.getItems()) {
                lines.add(formatter.apply(item));
            }

            // Footer and navigation
            PaginationConfig navConfig = config != null ? config : PaginationConfig.defaults();
            
            if (footer != null && !footer.isEmpty()) {
                lines.add(footer);
            }

            // Build the footer line with page info and optional navigation/overview
            StringBuilder footerLine = new StringBuilder();
            footerLine.append(formatPageInfo(result.getPageInfo()));

            if (showPageOverview) {
                // Add compact page window overview: (1 ... 4 | _5_ | 6 ... 10)
                String pageOverview = renderPageWindow(result, navConfig);
                footerLine.append(" ").append(pageOverview);
            }

            if (showNavigation) {
                if (commandBase != null) {
                    // Use command-based navigation hints
                    lines.add(formatPageInfoWithNavigation(result.getPageInfo(), commandBase));
                    if (showPageOverview) {
                        // Add page overview on separate line if using command navigation
                        lines.add("§7" + renderPageWindow(result, navConfig));
                    }
                } else {
                    // Add navigation bar
                    String navBar = createNavigationBar(result.getCurrentPage(), result.getTotalPages(), navConfig);
                    footerLine.append(" ").append(navBar);
                    lines.add(footerLine.toString());
                }
            } else {
                lines.add(footerLine.toString());
            }

            return lines;
        }

        /**
         * Build and send the paginated output directly to a command sender.
         * This is a convenience method that combines {@link #buildLines()} and message sending.
         *
         * @param sender the command sender (player or console) to receive the paginated output
         */
        public void send(@NotNull CommandSender sender) {
            Preconditions.checkNotNull(sender, "sender");
            for (String line : buildLines()) {
                sender.sendMessage(line);
            }
        }

        /**
         * Build and return the paginated output as a single string with lines joined by newlines.
         * Useful for logging or storing the paginated output as text.
         *
         * @return the complete paginated output as a single newline-separated string
         */
        public @NotNull String buildString() {
            return String.join("\n", buildLines());
        }
    }
}
