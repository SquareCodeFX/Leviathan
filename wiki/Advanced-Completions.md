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

The `DynamicCompletionContext` provides full runtime information:

```java
.completionsDynamic(ctx -> {
    // The command sender
    CommandSender sender = ctx.sender();

    // Get a previously parsed argument
    String category = ctx.getParsedArgument("category", String.class);

    // Get raw input tokens
    String[] rawArgs = ctx.rawArgs();

    // The current partial input being typed
    String currentInput = ctx.currentInput();

    // The argument index being completed
    int argIndex = ctx.argumentIndex();

    return generateCompletions(sender, category);
})
```

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
