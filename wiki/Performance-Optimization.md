# Performance Optimization

This page documents the performance optimization utilities available in Leviathan's command framework. These tools help reduce memory allocations, cache expensive operations, and improve command execution throughput.

## Overview

The `de.feelix.leviathan.command.performance` package provides six core optimization features:

| Feature | Purpose | Use Case |
|---------|---------|----------|
| **ObjectPool** | Reduce GC pressure | Frequently created short-lived objects |
| **ArgumentCache** | Cache argument values | Player names, world names, materials |
| **LazyArgument** | Defer parsing | Optional/conditionally-used arguments |
| **ParallelParser** | Concurrent parsing | Multiple expensive arguments |
| **CommandPrecompiler** | Pre-compile structures | Complex validation patterns |
| **ResultCache** | Cache results | Expensive command outputs |

---

## ObjectPool

Thread-safe object pooling to reduce garbage collection pressure from frequently created short-lived objects.

### Creating a Pool

```java
// Basic pool with factory and reset function
ObjectPool<StringBuilder> sbPool = ObjectPool.create(
    StringBuilder::new,       // Factory: creates new instances
    sb -> sb.setLength(0),    // Reset: cleans objects for reuse
    50                        // Max pool size
);

// Pool without reset (for immutable-like objects)
ObjectPool<MyObject> pool = ObjectPool.create(MyObject::new, 100);
```

### Using Pooled Objects

```java
// Manual borrow/release pattern
StringBuilder sb = sbPool.borrow();
try {
    sb.append("Hello ").append(playerName);
    player.sendMessage(sb.toString());
} finally {
    sbPool.release(sb);
}

// Automatic with lambda (recommended)
String result = sbPool.withPooled(sb -> {
    sb.append("Player: ").append(name);
    return sb.toString();
});

// Void operations
sbPool.usePooled(sb -> {
    sb.append("Log entry: ").append(message);
    logger.info(sb.toString());
});
```

### Pool Statistics

```java
ObjectPool.PoolStats stats = sbPool.getStats();

long borrowed = stats.totalBorrowed();   // Total borrow count
long released = stats.totalReleased();   // Total release count
long created = stats.objectsCreated();   // New objects created
long reused = stats.objectsReused();     // Objects reused from pool
int current = stats.currentPoolSize();   // Current available objects
double rate = stats.reuseRate();         // Reuse percentage (0.0-1.0)

// Reset statistics
sbPool.resetStats();
```

### Pre-warming

```java
// Pre-create objects before heavy load
sbPool.prewarm(20);  // Create 20 objects upfront
```

---

## ArgumentCache

Caching layer for frequently-used argument values with automatic TTL-based expiration.

### Built-in Caches

```java
// Get cached player names (updates automatically)
List<String> players = ArgumentCache.getPlayerNames();

// Get cached world names
List<String> worlds = ArgumentCache.getWorldNames();

// Get cached material names
List<String> materials = ArgumentCache.getMaterialNames();

// Filter by prefix (for tab completion)
List<String> filtered = ArgumentCache.getPlayerNames("No");  // Notch, NoobMaster, etc.
```

### Custom Caching

```java
// Cache any value with TTL
String result = ArgumentCache.getOrCompute(
    "expensive-key",
    () -> expensiveComputation(),
    5, TimeUnit.MINUTES
);

// Check if cached
boolean cached = ArgumentCache.isCached("expensive-key");

// Invalidate specific entry
ArgumentCache.invalidate("expensive-key");

// Clear all custom caches
ArgumentCache.clearCustomCaches();
```

### Typed Caches

```java
// Create a typed cache for specific data
ArgumentCache.TypedCache<List<String>> homeCache = ArgumentCache.typed(
    "homes",
    5, TimeUnit.MINUTES
);

// Store and retrieve
homeCache.put("player-uuid", List.of("home1", "home2"));
List<String> homes = homeCache.get("player-uuid");

// Get or compute
List<String> homes = homeCache.getOrCompute("player-uuid", () -> loadHomes(uuid));
```

### Cache Statistics

```java
ArgumentCache.CacheStats stats = ArgumentCache.getStats();

long hits = stats.hits();
long misses = stats.misses();
double hitRate = stats.hitRate();
int size = stats.size();
long evictions = stats.evictions();
```

---

## LazyArgument

Defers argument parsing until the value is actually needed. Useful when arguments may not be used in all code paths.

### Creating Lazy Arguments

```java
// From raw value and parser (most common)
LazyArgument<Player> lazyPlayer = LazyArgument.of(
    "Notch",              // Raw input
    playerParser,         // ArgumentParser
    sender                // CommandSender for context
);

// From pre-computed value
LazyArgument<Integer> lazyAmount = LazyArgument.ofValue(100);

// From supplier (computed on first access)
LazyArgument<Config> lazyConfig = LazyArgument.fromSupplier(
    () -> loadConfigFromDisk()
);
```

### Using Lazy Arguments

```java
// Get the value (parses on first call, cached after)
Player player = lazyPlayer.get();

// Check state without triggering parse
boolean computed = lazyPlayer.isComputed();
boolean hasError = lazyPlayer.hasError();
String error = lazyPlayer.getError();

// Get raw value (if available)
String raw = lazyPlayer.getRaw();
```

### Functional API

```java
// Map to different type
LazyArgument<String> lazyName = lazyPlayer.map(Player::getName);

// FlatMap for chaining
LazyArgument<Location> lazyLoc = lazyPlayer.flatMap(p ->
    LazyArgument.ofValue(p.getLocation())
);

// Filter with predicate
LazyArgument<Player> onlineOnly = lazyPlayer.filter(Player::isOnline);

// Get as Optional
Optional<Player> optPlayer = lazyPlayer.asOptional();

// OrElse variants
Player player = lazyPlayer.orElse(defaultPlayer);
Player player = lazyPlayer.orElseGet(() -> Bukkit.getPlayer("default"));
Player player = lazyPlayer.orElseThrow(() -> new NoSuchPlayerException());

// Handle errors
lazyPlayer.ifError(error -> logger.warn("Parse failed: " + error));

// Conditional execution
lazyPlayer.ifPresent(player -> player.sendMessage("Hello!"));
```

### Use Cases

```java
// Conditional argument usage
public void execute(CommandContext ctx) {
    LazyArgument<Player> target = LazyArgument.of(ctx.getRaw("target"), playerParser, sender);

    if (ctx.getFlag("self")) {
        // Target argument never parsed - saves CPU
        doAction((Player) sender);
    } else {
        // Only parsed when actually needed
        doAction(target.get());
    }
}
```

---

## ParallelParser

Parses independent arguments concurrently using a thread pool. Best for commands with multiple expensive arguments.

### Creating a Parser

```java
// Default configuration (4 threads)
ParallelParser parser = ParallelParser.create();

// Custom thread count
ParallelParser parser = ParallelParser.create(8);

// Full configuration
ParallelParser parser = ParallelParser.builder()
    .threads(8)
    .parallelThreshold(3)      // Only parallelize if 3+ args
    .timeout(5, TimeUnit.SECONDS)
    .build();
```

### Parsing Arguments

```java
// Single argument (uses threshold to decide parallel vs sequential)
Object result = parser.parse("Notch", playerParser, sender);

// Batch parsing (recommended for multiple args)
Map<String, Object> results = parser.batch()
    .add("player", "Notch", playerParser)
    .add("world", "world_nether", worldParser)
    .add("amount", "64", intParser)
    .add("material", "DIAMOND", materialParser)
    .execute(sender);

// Access results
Player player = (Player) results.get("player");
World world = (World) results.get("world");
int amount = (int) results.get("amount");
```

### Error Handling

```java
Map<String, Object> results = parser.batch()
    .add("player", "InvalidPlayer", playerParser)
    .add("amount", "not-a-number", intParser)
    .execute(sender);

// Check for errors
if (parser.hasErrors()) {
    Map<String, String> errors = parser.getErrors();
    errors.forEach((arg, msg) -> sender.sendMessage("Error in " + arg + ": " + msg));
}
```

### Statistics

```java
ParallelParser.Stats stats = parser.getStats();

long totalParsed = stats.totalArgumentsParsed();
long parallelRuns = stats.parallelExecutions();
long sequentialRuns = stats.sequentialExecutions();
double avgTime = stats.averageParseTimeNanos();
```

---

## CommandPrecompiler

Pre-compiles command structures at registration time for faster runtime access.

### Compiling Commands

```java
// Compile a command's argument structure
CompiledCommand compiled = CommandPrecompiler.compile("give", args);

// With additional options
CompiledCommand compiled = CommandPrecompiler.builder("give")
    .withArgs(args)
    .precompilePatterns(true)
    .precomputeCompletions(true)
    .build();
```

### Using Compiled Data

```java
// Fast argument index lookup (O(1) instead of O(n))
int playerIndex = compiled.getArgumentIndex("player");
int amountIndex = compiled.getArgumentIndex("amount");

// Pre-compiled validation patterns
Pattern emailPattern = compiled.getValidationPattern("email");
boolean valid = emailPattern.matcher(input).matches();

// Static completions (pre-computed at compile time)
List<String> gamemodes = compiled.getStaticCompletions("gamemode");

// Pre-built usage string
String usage = compiled.getUsageString();  // "/give <player> <item> [amount]"
```

### Registration Integration

```java
// Compile at registration time
SlashCommand cmd = SlashCommand.builder("give")
    .arg("player", playerParser)
    .arg("item", materialParser)
    .arg("amount", intParser, ArgContext.builder().optional(true).build())
    .build();

// Store compiled data for runtime use
CompiledCommand compiled = CommandPrecompiler.compile(cmd);
compiledCommands.put("give", compiled);
```

---

## ResultCache

Caches expensive command execution results with TTL-based expiration.

### Creating a Cache

```java
// Basic cache with TTL
ResultCache cache = ResultCache.create(5, TimeUnit.MINUTES);

// With max size
ResultCache cache = ResultCache.create(5, TimeUnit.MINUTES, 1000);

// Full configuration
ResultCache cache = ResultCache.builder()
    .ttl(5, TimeUnit.MINUTES)
    .maxSize(1000)
    .evictionPolicy(EvictionPolicy.LRU)
    .build();
```

### Caching Results

```java
// Get or compute with string key
Object stats = cache.getOrCompute("weekly-stats", () -> computeWeeklyStats());

// With structured key builder
CacheKey key = CacheKeyBuilder.forCommand("stats")
    .withSender(sender)
    .withArg("type", "weekly")
    .withArg("player", playerName)
    .build();

Object result = cache.getOrCompute(key, () -> computePlayerStats(playerName));
```

### Sender-Specific Caching

```java
// Per-sender results
Object result = cache.getOrComputeForSender(
    sender,
    "personal-stats",
    () -> computePersonalStats(sender)
);

// Invalidate for specific sender
cache.invalidateForSender(sender);
```

### Cache Management

```java
// Manual invalidation
cache.invalidate("weekly-stats");
cache.invalidateByPrefix("stats:");

// Clear all
cache.clear();

// Evict expired entries
int removed = cache.evictExpired();

// Statistics
ResultCache.Stats stats = cache.getStats();
long hits = stats.hits();
long misses = stats.misses();
double hitRate = stats.hitRate();
int size = stats.size();
```

---

## PerformanceManager

Central singleton for managing all performance features with pre-configured defaults.

### Getting the Manager

```java
PerformanceManager perf = PerformanceManager.getInstance();
```

### Accessing Components

```java
// Pre-configured pools
ObjectPool<StringBuilder> sbPool = perf.getStringBuilderPool();
ObjectPool<ArrayList<?>> listPool = perf.getArrayListPool();

// Caches
ArgumentCache argCache = perf.getArgumentCache();
ResultCache resultCache = perf.getResultCache();

// Parsers
ParallelParser parallelParser = perf.getParallelParser();
```

### Combined Statistics

```java
PerformanceManager.PerformanceStats stats = perf.getStats();

// Pool stats
long poolBorrowed = stats.totalPoolBorrowed();
long poolReused = stats.totalPoolReused();
double poolReuseRate = stats.poolReuseRate();

// Cache stats
long cacheHits = stats.totalCacheHits();
long cacheMisses = stats.totalCacheMisses();
double cacheHitRate = stats.cacheHitRate();

// Parse stats
long argsParsed = stats.totalArgumentsParsed();
double avgParseTime = stats.averageParseTimeNanos();
```

### Configuration

```java
// Configure at startup
PerformanceManager.configure(config -> config
    .stringBuilderPoolSize(100)
    .arrayListPoolSize(50)
    .resultCacheTTL(10, TimeUnit.MINUTES)
    .resultCacheMaxSize(500)
    .parallelParserThreads(4)
    .parallelThreshold(3)
);
```

---

## PerformanceOptimizer

High-level static utility methods for easy integration without managing instances.

### Quick Usage

```java
// Use pooled StringBuilder
String msg = PerformanceOptimizer.withStringBuilder(sb -> {
    sb.append("Player ").append(name).append(" joined");
    return sb.toString();
});

// Cache a result
Object stats = PerformanceOptimizer.cached("stats:" + playerId, 5, TimeUnit.MINUTES,
    () -> computeExpensiveStats(playerId));

// Lazy argument
LazyArgument<Player> lazy = PerformanceOptimizer.lazy("Notch", playerParser, sender);

// Parallel parse
Map<String, Object> results = PerformanceOptimizer.parseParallel(sender,
    Map.of(
        "player", Pair.of("Notch", playerParser),
        "amount", Pair.of("64", intParser)
    )
);
```

---

## Best Practices

### When to Use Each Feature

| Feature | Use When |
|---------|----------|
| **ObjectPool** | Creating 100+ short-lived objects per tick |
| **ArgumentCache** | Tab completions, repeated lookups |
| **LazyArgument** | Optional args, conditional code paths |
| **ParallelParser** | 3+ expensive args (DB/API calls) |
| **CommandPrecompiler** | Complex patterns, many args |
| **ResultCache** | Expensive ops with stable results |

### Performance Tips

1. **Don't over-optimize** - Only use these tools where profiling shows bottlenecks
2. **Pool sizing** - Start small, increase based on stats
3. **Cache TTL** - Balance freshness vs performance
4. **Parallel threshold** - Parallelism has overhead, only use for expensive operations
5. **Monitor stats** - Use the statistics APIs to validate optimizations

### Thread Safety

All classes in this package are thread-safe:

- ObjectPool uses ConcurrentLinkedQueue
- ArgumentCache uses ConcurrentHashMap
- LazyArgument uses double-checked locking
- ParallelParser uses ExecutorService
- ResultCache uses ConcurrentHashMap with atomic operations

---

## Complete API Reference

### ObjectPool

| Method | Description |
|--------|-------------|
| `create(factory, reset, maxSize)` | Create a pool |
| `borrow()` | Get an object from pool |
| `release(obj)` | Return object to pool |
| `withPooled(function)` | Use object with auto-release |
| `usePooled(consumer)` | Use object (void) with auto-release |
| `prewarm(count)` | Pre-create objects |
| `getStats()` | Get pool statistics |
| `resetStats()` | Reset statistics |

### ArgumentCache

| Method | Description |
|--------|-------------|
| `getPlayerNames()` | Get cached player names |
| `getWorldNames()` | Get cached world names |
| `getMaterialNames()` | Get cached material names |
| `getOrCompute(key, supplier, ttl, unit)` | Cache custom value |
| `invalidate(key)` | Remove cached entry |
| `typed(prefix, ttl, unit)` | Create typed cache |
| `getStats()` | Get cache statistics |

### LazyArgument

| Method | Description |
|--------|-------------|
| `of(raw, parser, sender)` | Create from parser |
| `ofValue(value)` | Create from constant |
| `fromSupplier(supplier)` | Create from supplier |
| `get()` | Get value (triggers parse) |
| `isComputed()` | Check if computed |
| `hasError()` | Check for error |
| `getError()` | Get error message |
| `map(function)` | Transform value |
| `flatMap(function)` | Chain lazy arguments |
| `filter(predicate)` | Filter value |
| `orElse(default)` | Get or default |
| `asOptional()` | Convert to Optional |

### ParallelParser

| Method | Description |
|--------|-------------|
| `create()` | Create with defaults |
| `create(threads)` | Create with thread count |
| `builder()` | Get configuration builder |
| `parse(raw, parser, sender)` | Parse single argument |
| `batch()` | Start batch builder |
| `getStats()` | Get parser statistics |
| `shutdown()` | Shutdown thread pool |

### ResultCache

| Method | Description |
|--------|-------------|
| `create(ttl, unit)` | Create with TTL |
| `create(ttl, unit, maxSize)` | Create with TTL and max size |
| `getOrCompute(key, supplier)` | Get or compute value |
| `getOrComputeForSender(sender, key, supplier)` | Per-sender caching |
| `invalidate(key)` | Remove entry |
| `invalidateForSender(sender)` | Remove sender entries |
| `clear()` | Clear all entries |
| `evictExpired()` | Remove expired entries |
| `getStats()` | Get cache statistics |

### PerformanceManager

| Method | Description |
|--------|-------------|
| `getInstance()` | Get singleton |
| `getStringBuilderPool()` | Get StringBuilder pool |
| `getArrayListPool()` | Get ArrayList pool |
| `getArgumentCache()` | Get argument cache |
| `getResultCache()` | Get result cache |
| `getParallelParser()` | Get parallel parser |
| `getStats()` | Get combined statistics |
| `configure(config)` | Configure defaults |
