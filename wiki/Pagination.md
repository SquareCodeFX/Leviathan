### Pagination Utilities

Leviathan includes a small pagination toolkit to produce consistent, interactive paginated outputs for commands. These utilities are optional but integrate nicely with `argPage(...)` and the help system.

#### Key Types

- `PaginationConfig` — Global/default settings: page size, navigation window, symbols, etc.
- `PaginationDataSource<T>` — Abstraction for paged data providers.
  - Implementations: `ListDataSource<T>`, `LazyDataSource<T>`
- `PaginatedResult<T>` — A page slice with metadata such as current page, total pages, etc.
- `PageInfo` — Information about page indices and bounds.
- `InteractivePaginator<T>` — Service to render and navigate pages (e.g., clickable text if your platform supports it) and to cache results.
- `PaginationManager` / `PaginationService` — High‑level API to manage caches and render flows.
- `LruPaginationCache` — Cache for recent pagination results; `CacheStats` for metrics.

#### Basic usage

Build a data source and request a page:

```java
List<String> items = fetchItems();
PaginationDataSource<String> ds = new ListDataSource<>(items);

int page = 1; // or parsed via argPage()
PaginatedResult<String> result = ds.page(page, 10); // page size 10

for (String row : result.items()) {
    sender.sendMessage(row);
}

sender.sendMessage(String.format("Page %d/%d", result.pageInfo().current(), result.pageInfo().total()));
```

#### Using InteractivePaginator

For richer UX and caching:

```java
PaginationConfig config = new PaginationConfig();
config.setPageSize(10);

InteractivePaginator<String> paginator = new InteractivePaginator<>(
    new LruPaginationCache<>(100), // 100 entries cache
    config
);

PaginationDataSource<String> ds = new ListDataSource<>(fetchItems());
PaginatedResult<String> page1 = paginator.paginate(ds, 1);

paginator.render(sender, page1); // Delegates to a rendering strategy inside service
```

Depending on your platform integration, `render` can map to sending formatted text or building components.

#### With Commands

Combine with `argPage()` and `argPage(name, defaultPage)` to parse the desired page from the command input.

```java
SlashCommand listHomes = SlashCommand.create("listhomes")
    .argPage("page", 1)
    .executes(ctx -> {
        int page = ctx.get("page", Integer.class);
        PaginationDataSource<Home> ds = new ListDataSource<>(homeService.list(ctx));
        PaginatedResult<Home> result = ds.page(page, 10);
        // Render homes
    })
    .build();
```

#### Utilities

- `PaginationUtils` — Helpers for clamping pages, computing navigation windows, and formatting labels.
- `PaginationHelper.renderFooter(...)` — Renders a combined footer with page info and page window overview (e.g., `§7Page §f3§7/§f10 (1 ... 2 | _3_ | 4 ... 10)`).

#### Customizing Messages

The `MessageProvider` interface allows full customization of pagination messages. The `paginationFooter(...)` method combines page info and page window navigation into a single formatted output:

```java
// In your custom MessageProvider implementation:
@Override
public String paginationFooter(
    List<Integer> visiblePages,
    int currentPage,
    int totalPages,
    boolean showStartEllipsis,
    boolean showEndEllipsis,
    String ellipsis,
    String pageSeparator,
    String currentPagePrefix,
    String currentPageSuffix
) {
    // Return combined footer like: "Page 3/10 (1 ... 2 | [3] | 4 ... 10)"
    return "Page " + currentPage + "/" + totalPages + " " + buildPageWindow(...);
}
```

This unified method replaces the previous separate `paginationFooter` and `paginationPageWindow` methods.
