### Advanced Completions

This page covers advanced tab-completion features including dynamic completions, async completions, and permission-filtered completions.

#### Overview

Leviathan supports several types of tab completions:

1. **Predefined completions** — Static list of suggestions
2. **Dynamic completions** — Runtime-generated based on context
3. **Async completions** — Asynchronous fetching (e.g., from database)
4. **Permission-filtered completions** — Filtered by sender permissions

All completion providers are configured via `ArgContext`.

#### Predefined Completions

The simplest form: a static list of suggestions.

```java
SlashCommand cmd = SlashCommand.create("setmode")
    .argStringWithCompletions("mode", "easy", "normal", "hard")
    .executes(ctx -> { /* ... */ })
    .build();

// Or using ArgContext builder:
SlashCommand cmd2 = SlashCommand.create("setmode")
    .argString("mode", ArgContext.builder()
        .withCompletions(List.of("easy", "normal", "hard"))
        .build())
    .executes(ctx -> { /* ... */ })
    .build();
```

You can also derive completions from an enum:

```java
SlashCommand cmd = SlashCommand.create("setgame")
    .argString("game", ArgContext.builder()
        .completionsFromEnum(GameMode.class)
        .build())
    .executes(ctx -> { /* ... */ })
    .build();
```

#### Dynamic Completions

Dynamic completions are computed at runtime based on the current context. Use `DynamicCompletionProvider` when suggestions depend on:

- The command sender
- Previously parsed arguments
- Server state

```java
SlashCommand homes = SlashCommand.create("home")
    .argString("name", ArgContext.builder()
        .completionsDynamic(ctx -> {
            Player player = (Player) ctx.sender();
            return homeService.getHomeNames(player);
        })
        .build())
    .executes(ctx -> { /* teleport to home */ })
    .build();
```

##### Factory Methods

`DynamicCompletionProvider` provides convenient factory methods:

**Permission-filtered completions:**

Only show completions the sender has permission to use:

```java
List<String> allItems = List.of("diamond_sword", "golden_apple", "elytra");

ArgContext ctx = ArgContext.builder()
    .completionsDynamic(DynamicCompletionProvider.permissionFiltered(
        allItems,
        "myplugin.item."  // Checks myplugin.item.diamond_sword, etc.
    ))
    .build();
```

**Context-based completions:**

Generate completions based on previously parsed arguments:

```java
ArgContext ctx = ArgContext.builder()
    .completionsDynamic(DynamicCompletionProvider.contextBased(dynCtx -> {
        // Access previously parsed arguments
        String category = dynCtx.getParsedArgument("category", String.class);
        if ("weapons".equals(category)) {
            return List.of("sword", "bow", "axe");
        } else if ("armor".equals(category)) {
            return List.of("helmet", "chestplate", "leggings", "boots");
        }
        return List.of();
    }))
    .build();
```

**Combined completions:**

Merge multiple completion sources:

```java
DynamicCompletionProvider combined = DynamicCompletionProvider.combined(
    ctx -> getOnlinePlayerNames(),
    ctx -> getOfflinePlayerNames(),
    DynamicCompletionProvider.permissionFiltered(specialPlayers, "admin.see.")
);

ArgContext ctx = ArgContext.builder()
    .completionsDynamic(combined)
    .build();
```

#### Async Completions

For completions that require I/O operations (database queries, HTTP requests), use async providers to avoid blocking the main thread.

##### AsyncDynamicCompletionProvider

Full context-aware async completions:

```java
SlashCommand search = SlashCommand.create("search")
    .argString("query", ArgContext.builder()
        .completionsDynamicAsync(ctx -> CompletableFuture.supplyAsync(() -> {
            // Fetch from database off the main thread
            Player player = (Player) ctx.sender();
            return database.getRecentSearches(player.getUniqueId());
        }))
        .build())
    .executes(ctx -> { /* ... */ })
    .build();
```

**From synchronous provider:**

Wrap a synchronous provider to run it asynchronously:

```java
DynamicCompletionProvider syncProvider = ctx -> expensiveComputation();

ArgContext ctx = ArgContext.builder()
    .completionsDynamicAsync(AsyncDynamicCompletionProvider.fromSync(syncProvider))
    .build();
```

**Combined async providers:**

```java
AsyncDynamicCompletionProvider combined = AsyncDynamicCompletionProvider.combined(
    ctx -> fetchFromDatabase(ctx),
    ctx -> fetchFromApi(ctx)
);
```

##### AsyncPredefinedCompletionSupplier

For async loading of a static list (e.g., config reload, cached data):

```java
SlashCommand regions = SlashCommand.create("region")
    .argString("name", ArgContext.builder()
        .completionsPredefinedAsync(() -> CompletableFuture.supplyAsync(() -> {
            // Load region names from file/database
            return regionManager.getAllRegionNames();
        }))
        .build())
    .executes(ctx -> { /* ... */ })
    .build();
```

**From static list:**

Immediately return a list (useful for testing or fallback):

```java
AsyncPredefinedCompletionSupplier supplier =
    AsyncPredefinedCompletionSupplier.fromList(List.of("option1", "option2"));
```

**From synchronous supplier:**

```java
AsyncPredefinedCompletionSupplier supplier =
    AsyncPredefinedCompletionSupplier.fromSync(() -> loadOptionsFromConfig());
```

#### DynamicCompletionContext

The `DynamicCompletionContext` is a record providing full runtime information for dynamic completions:

```java
.completionsDynamic(ctx -> {
    // The command sender
    CommandSender sender = ctx.sender();

    // The command alias used (e.g., "tp" or "teleport")
    String alias = ctx.alias();

    // Get raw input tokens
    String[] rawArgs = ctx.providedArgs();

    // The current argument index being completed
    int argIndex = ctx.currentArgIndex();

    // The current partial input being typed (prefix)
    String prefix = ctx.prefix();

    // All argument definitions for this command
    List<Arg<?>> allArgs = ctx.allArgs();

    // Previously parsed argument values (Map<String, Object>)
    Map<String, Object> parsedSoFar = ctx.parsedArgsSoFar();

    // Get a specific previously parsed argument
    String category = (String) ctx.parsedArgsSoFar().get("category");

    // The SlashCommand instance
    SlashCommand command = ctx.command();

    return generateCompletions(sender, category);
})
```

**Record fields:**

| Field | Type | Description |
|-------|------|-------------|
| `sender()` | `CommandSender` | The command sender requesting completions |
| `alias()` | `String` | The command alias used |
| `providedArgs()` | `String[]` | All arguments typed so far |
| `currentArgIndex()` | `int` | Index of the argument being completed |
| `prefix()` | `String` | Current partial input being typed |
| `allArgs()` | `List<Arg<?>>` | All argument definitions |
| `parsedArgsSoFar()` | `Map<String, Object>` | Previously parsed argument values |
| `command()` | `SlashCommand` | The SlashCommand instance |

#### Complete Example

Here's a comprehensive example combining multiple completion strategies:

```java
SlashCommand shop = SlashCommand.create("shop")
    .argString("category", ArgContext.builder()
        .withCompletions(List.of("weapons", "armor", "potions", "food"))
        .build())
    .argString("item", ArgContext.builder()
        .completionsDynamic(DynamicCompletionProvider.contextBased(ctx -> {
            String category = ctx.getParsedArgument("category", String.class);
            return shopService.getItemsInCategory(category);
        }))
        .build())
    .argInt("amount", ArgContext.builder()
        .rangeHint(1, 64)
        .intRange(1, 64)
        .build())
    .argString("recipient", ArgContext.builder()
        .optional(true)
        .completionsDynamicAsync(ctx -> CompletableFuture.supplyAsync(() -> {
            // Fetch friends list from database
            Player buyer = (Player) ctx.sender();
            return friendsService.getFriendNames(buyer.getUniqueId());
        }))
        .build())
    .executes((sender, ctx) -> {
        String category = ctx.get("category", String.class);
        String item = ctx.get("item", String.class);
        int amount = ctx.get("amount", Integer.class);
        String recipient = ctx.getOrDefault("recipient", String.class, sender.getName());
        // Process purchase...
    })
    .build();
```

#### Best Practices

1. **Use async for I/O** — Database queries, HTTP requests, and file reads should use async providers
2. **Cache when possible** — If completions don't change often, cache them
3. **Limit results** — Return at most 50-100 suggestions for good UX
4. **Filter by input** — The framework filters by prefix, but you can pre-filter for performance
5. **Handle errors gracefully** — Async providers should handle exceptions and return empty lists on failure

```java
.completionsDynamicAsync(ctx -> CompletableFuture.supplyAsync(() -> {
    try {
        return database.fetchCompletions();
    } catch (Exception e) {
        logger.warning("Failed to fetch completions: " + e.getMessage());
        return List.of(); // Return empty on error
    }
}))
```

#### CompletionCache

`CompletionCache` is a thread-safe caching layer for tab completions that prevents server lag from expensive completion lookups (database queries, API calls, etc.).

##### Creating a Cache

```java
// Create with custom TTL
CompletionCache cache = CompletionCache.withTTL(5, TimeUnit.MINUTES);

// Create with TTL and maximum size
CompletionCache cache = CompletionCache.withTTLAndSize(5, TimeUnit.MINUTES, 100);

// Create with default settings (5 minutes TTL, max 100 entries)
CompletionCache cache = CompletionCache.createDefault();
```

##### Basic Usage

```java
// Cache expensive completions
CompletionCache cache = CompletionCache.withTTL(5, TimeUnit.MINUTES);

SlashCommand homes = SlashCommand.create("home")
    .argString("name", ArgContext.builder()
        .completionsDynamic(ctx -> {
            Player player = (Player) ctx.sender();
            String cacheKey = "homes:" + player.getUniqueId();
            return cache.getOrCompute(cacheKey, () -> {
                // This expensive database call only happens once per 5 minutes
                return database.getHomeNames(player.getUniqueId());
            });
        })
        .build())
    .executes(ctx -> { /* ... */ })
    .build();
```

##### Cache Operations

```java
// Get or compute (most common)
List<String> completions = cache.getOrCompute("key", () -> fetchExpensiveData());

// Get without computing (returns null if not cached)
List<String> cached = cache.get("key");

// Store directly
cache.put("key", List.of("option1", "option2"));

// Check if cached
if (cache.isCached("key")) {
    // ...
}

// Get cache size
int size = cache.size();

// Get cache statistics
Map<String, Object> stats = cache.getStats();
```

##### Invalidation

```java
// Invalidate a specific entry
cache.invalidate("homes:player-uuid");

// Invalidate all entries with a prefix (useful for per-player caches)
cache.invalidateByPrefix("homes:");

// Clear entire cache
cache.clear();

// Manually evict expired entries
int removed = cache.evictExpired();
```

##### Static Helper Methods

Wrap existing providers with caching:

```java
// Wrap a provider with caching (global key)
ArgContext.DynamicCompletionProvider cachedProvider = CompletionCache.cached(
    cache,
    "all-items",
    ctx -> fetchAllItems()
);

// Wrap with per-sender caching (each player gets their own cache entry)
ArgContext.DynamicCompletionProvider perPlayerProvider = CompletionCache.cachedPerSender(
    cache,
    "player-items",
    ctx -> fetchPlayerItems((Player) ctx.sender())
);

// Use in ArgContext
ArgContext ctx = ArgContext.builder()
    .completionsDynamic(cachedProvider)
    .build();
```

##### Complete Caching Example

```java
public class ShopCommand {
    // Shared cache for shop completions
    private static final CompletionCache SHOP_CACHE =
        CompletionCache.withTTLAndSize(2, TimeUnit.MINUTES, 50);

    public SlashCommand create() {
        return SlashCommand.create("shop")
            .argString("category", ArgContext.builder()
                .completionsDynamic(ctx -> SHOP_CACHE.getOrCompute(
                    "categories",
                    () -> shopService.getAllCategories()))
                .build())
            .argString("item", ArgContext.builder()
                .completionsDynamic(ctx -> {
                    String category = (String) ctx.parsedArgsSoFar().get("category");
                    return SHOP_CACHE.getOrCompute(
                        "items:" + category,
                        () -> shopService.getItemsInCategory(category));
                })
                .build())
            .executes((sender, ctx) -> { /* ... */ })
            .build();
    }

    // Call when shop data changes
    public void onShopDataChanged() {
        SHOP_CACHE.clear();
    }

    // Or invalidate specific category
    public void onCategoryChanged(String category) {
        SHOP_CACHE.invalidate("items:" + category);
    }
}
