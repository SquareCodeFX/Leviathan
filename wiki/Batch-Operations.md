### Batch Operations

Leviathan supports batch operations that allow commands to execute against multiple targets simultaneously. This is useful for administrative commands that need to affect many entities at once.

#### Overview

Batch operations provide:

- **Multiple Targets**: Process lists of players, entities, or any objects
- **Parallel Execution**: Optional concurrent processing for performance
- **Progress Tracking**: Real-time feedback on batch progress
- **Error Handling**: Configurable behavior on failures (continue or stop)
- **Result Aggregation**: Detailed success/failure statistics

#### Basic Usage

To create a batch command, use a variadic argument for targets and configure batch execution:

```java
SlashCommand cmd = SlashCommand.create("heal")
    .argVariadic(VariadicArg.of("players", ArgParsers.playerParser()))
    .batch("players")  // Use default batch config
    .batchAction((Player player, BatchContext<Player> ctx) -> {
        player.setHealth(player.getMaxHealth());
        ctx.recordSuccess();
    })
    .register(plugin);
```

Usage: `/heal player1 player2 player3` or `/heal @a` (with selector support)

#### Batch Configuration

Configure batch behavior using the builder pattern:

```java
SlashCommand cmd = SlashCommand.create("kick")
    .argVariadic(VariadicArg.of("players", ArgParsers.playerParser()))
    .argString("reason", ArgContext.builder().optional(true).defaultValue("No reason").build())
    .batch("players", config -> config
        .maxBatchSize(50)           // Maximum targets allowed (default: 100)
        .parallel(true)             // Enable parallel execution (default: false)
        .continueOnFailure(true)    // Continue if some targets fail (default: true)
        .showProgress(true)         // Show progress messages (default: false)
        .timeout(30_000)            // Timeout in milliseconds (default: 60000)
    )
    .batchAction((Player player, BatchContext<Player> ctx) -> {
        String reason = ctx.argString("reason", "No reason");
        player.kickPlayer(reason);
        ctx.recordSuccess();
    })
    .register(plugin);
```

#### BatchConfig Options

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `maxBatchSize` | int | 100 | Maximum number of targets allowed |
| `parallel` | boolean | false | Execute actions concurrently |
| `continueOnFailure` | boolean | true | Continue processing after failures |
| `showProgress` | boolean | false | Send progress messages to sender |
| `timeout` | long | 60000 | Maximum execution time in milliseconds |

#### BatchContext API

The `BatchContext<T>` provides information and utilities during batch execution:

```java
.batchAction((Player player, BatchContext<Player> ctx) -> {
    // Access the command sender
    CommandSender sender = ctx.sender();

    // Get values from the original command
    String reason = ctx.argString("reason", "default");
    int duration = ctx.argInt("duration", 60);
    boolean silent = ctx.flag("silent");

    // Access all targets
    List<Player> allTargets = ctx.targets();
    int totalCount = ctx.size();

    // Track progress
    int currentIndex = ctx.currentIndex();
    double progress = ctx.progress();  // 0.0 to 1.0

    // Report results
    ctx.recordSuccess();  // Mark current target as successful
    ctx.recordFailure();  // Mark current target as failed

    // Shared state between batch entries
    ctx.put("bannedCount", ctx.get("bannedCount", Integer.class) + 1);

    // Check for cancellation
    if (ctx.isCancelled()) {
        return;  // Stop processing
    }

    // Timing
    long elapsed = ctx.elapsedTimeNanos();
})
```

#### Error Handling

Handle errors gracefully within batch actions:

```java
.batchAction((Player player, BatchContext<Player> ctx) -> {
    try {
        performRiskyOperation(player);
        ctx.recordSuccess();
    } catch (Exception e) {
        // Failure is automatically recorded
        // If continueOnFailure is true, next target will be processed
        throw e;  // Or handle silently
    }
})
```

#### Batch Results

After batch execution, results are provided to the sender:

- **All Success**: `"Batch complete: 10 successful."`
- **Partial Success**: `"Batch partially complete: 8 successful, 2 failed."`
- **All Failed**: `"Batch operation failed: 10 errors."`

#### Parallel Execution

When `parallel(true)` is configured, batch operations execute concurrently:

```java
.batch("players", config -> config
    .parallel(true)
    .maxParallelism(4)  // Limit concurrent threads
)
```

**Note**: Ensure your batch action is thread-safe when using parallel execution.

#### Example: Mass Ban Command

```java
SlashCommand cmd = SlashCommand.create("massban")
    .description("Ban multiple players at once")
    .permission("admin.massban")
    .argVariadic(VariadicArg.of("players", ArgParsers.offlinePlayerParser()))
    .argString("reason", ArgContext.builder().optional(true).defaultValue("Banned by admin").build())
    .flag("permanent", 'p', "permanent")
    .batch("players", config -> config
        .maxBatchSize(20)
        .parallel(false)
        .continueOnFailure(true)
        .showProgress(true)
    )
    .batchAction((OfflinePlayer player, BatchContext<OfflinePlayer> ctx) -> {
        String reason = ctx.argString("reason", "Banned by admin");
        boolean permanent = ctx.flag("permanent");

        // Perform ban
        if (permanent) {
            plugin.getBanManager().banPermanent(player.getUniqueId(), reason);
        } else {
            plugin.getBanManager().banTemporary(player.getUniqueId(), reason, Duration.ofDays(7));
        }

        ctx.recordSuccess();
    })
    .register(plugin);
```

#### Core Classes

| Class | Purpose |
|-------|---------|
| `BatchConfig` | Configuration for batch execution |
| `BatchAction<T>` | Functional interface for the action to execute per target |
| `BatchContext<T>` | Runtime context with targets, state, and utilities |
| `BatchExecutor<T>` | Executes batch operations (parallel/sequential) |
| `BatchResult<T>` | Aggregated results after batch completion |
| `BatchEntry<T>` | Result of a single target operation |

#### Custom Messages

Override batch messages in your `MessageProvider`:

```java
public class CustomMessages implements MessageProvider {
    @Override
    public String batchSizeExceeded(int size, int maxSize) {
        return "Too many targets! Max: " + maxSize;
    }

    @Override
    public String batchStarted(int targetCount) {
        return "Processing " + targetCount + " targets...";
    }

    @Override
    public String batchComplete(int success, int failed) {
        return success + " completed, " + failed + " failed.";
    }
}
```
