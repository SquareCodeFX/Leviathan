### Async Execution and Cooldowns

This page explains how to run commands asynchronously and how to throttle them with per‑user or global cooldowns.

#### Async execution models

There are three ways to execute work asynchronously:

1. `async(true)` — Marks the command as asynchronous. Use with `executes(...)` to run that action off the main thread.
2. `executesAsync(CommandAction action)` — Convenience that runs the same `CommandAction` asynchronously.
3. `executesAsync(AsyncCommandAction action)` — Advanced API supporting progress and cancellation tokens, with optional timeout.

With timeout:

```java
SlashCommand heavy = SlashCommand.create("heavy")
    .async(true)
    .executesAsync((ctx, progress, cancellation) -> {
        // periodically check cancellation.isCancelled()
        // report progress.set(percent) if desired
        doHeavyWork();
    }, 15_000L) // 15 seconds timeout
    .build();
```

Notes:

- Use async when your action performs blocking I/O or heavy CPU. Avoid blocking the main server thread.
- Respect cancellation to avoid wasting resources if the command is abandoned.

#### Cooldowns

Leviathan can throttle command executions to prevent spam.

- `perUserCooldown(long cooldownMillis)` — Enforces a per‑sender delay between successful executions.
- `perServerCooldown(long cooldownMillis)` — Global delay affecting all senders.

Example:

```java
SlashCommand locate = SlashCommand.create("locate")
    .perUserCooldown(5_000)  // 5s per user
    .perServerCooldown(1_000) // 1s globally
    .executes(ctx -> { /* ... */ })
    .build();
```

Behavior:

- When a cooldown is active, the command does not execute and the user receives an error via the message provider/exception handler.
- Cooldowns are tracked independently for per‑user and per‑server layers.

#### CooldownManager API

For programmatic cooldown control, use `CooldownManager`:

```java
// Check if a user is on cooldown
CooldownResult result = CooldownManager.checkUserCooldown("locate", player.getName(), 5000);
if (result.isOnCooldown()) {
    long remaining = result.remainingMillis();
    player.sendMessage("Please wait " + remaining + "ms");
}

// Check server-wide cooldown
CooldownResult serverResult = CooldownManager.checkServerCooldown("broadcast", 60_000);

// Manually update cooldowns
CooldownManager.updateUserCooldown("command", player.getName());
CooldownManager.updateServerCooldown("command");

// Clear cooldowns
CooldownManager.clearUserCooldown("command", player.getName());
CooldownManager.clearServerCooldown("command");
CooldownManager.clearCooldowns("command");  // Clear all for a command
CooldownManager.clearAllCooldowns();        // Clear everything

// Get remaining time
long remaining = CooldownManager.getRemainingUserCooldown("cmd", player.getName(), 5000);

// Format cooldown message
String msg = CooldownManager.formatCooldownMessage("Please wait %s", remaining);
// Returns: "Please wait 4.5 seconds" or "Please wait 2.3 minutes"
```

##### CooldownResult

```java
CooldownResult result = CooldownManager.checkUserCooldown(...);

if (result.isOnCooldown()) {
    long remaining = result.remainingMillis();
    // Handle cooldown...
} else {
    // Not on cooldown
}
```

##### Cleanup and Statistics

CooldownManager includes automatic cleanup of expired entries:

```java
// Manual cleanup (automatic cleanup also runs periodically)
int cleaned = CooldownManager.cleanupExpired();

// Statistics
int userCount = CooldownManager.getUserCooldownCount();
int serverCount = CooldownManager.getServerCooldownCount();
int totalCount = CooldownManager.getTotalCooldownCount();
int trackedCommands = CooldownManager.getTrackedCommandCount();
long totalCleaned = CooldownManager.getTotalCleanedEntries();
long lastCleanup = CooldownManager.getLastCleanupTime();

// Reset statistics
CooldownManager.resetStatistics();
```

#### ProgressBar

For visual progress indicators in async commands, use `ProgressBar`:

```java
SlashCommand processCmd = SlashCommand.create("process")
    .async(true)
    .executesAsync((ctx, progress, cancellation) -> {
        ProgressBar bar = ProgressBar.builder()
            .total(100)
            .width(30)
            .filledChar('█')
            .emptyChar('░')
            .prefix("Processing: ")
            .showPercentage(true)
            .showRatio(true)
            .colorFilled("§a")  // Green
            .colorEmpty("§7")   // Gray
            .build();

        for (int i = 0; i <= 100; i++) {
            if (cancellation.isCancelled()) break;

            // Report progress with rendered bar
            progress.report(bar.render(i));

            doWork();
        }
    })
    .build();
```

##### ProgressBar Builder Options

```java
ProgressBar bar = ProgressBar.builder()
    .total(100)                    // Total steps (default: 100)
    .width(30)                     // Bar width in characters (default: 30)
    .filledChar('█')               // Character for filled portion
    .emptyChar('░')                // Character for empty portion
    .characters('▓', '░')          // Set both at once
    .prefix("Loading: ")           // Text before the bar
    .suffix(" done")               // Text after the bar
    .showPercentage(true)          // Show "50.0%"
    .showRatio(true)               // Show "(50/100)"
    .colorFilled("§a")             // Color for filled portion
    .colorEmpty("§7")              // Color for empty portion
    .colorText("§f")               // Color for text
    .colors("§a", "§7", "§f")      // Set all colors at once
    .build();
```

##### Rendering Progress

```java
// Render with current value
String output = bar.render(50);  // 50 out of 100

// Render with message
String output = bar.render(50, "Processing items...");

// Render with percentage (0.0 to 1.0)
String output = bar.renderPercentage(0.5);
String output = bar.renderPercentage(0.5, "Halfway there!");
```

##### ProgressReporter Integration

```java
// Create a ProgressReporter for easier integration
ProgressReporter reporter = bar.reporter(progress);

// Report progress easily
reporter.report(50);                  // Update to 50
reporter.report(75, "Almost done");   // Update with message
reporter.reportPercentage(0.9);       // Update by percentage
```
