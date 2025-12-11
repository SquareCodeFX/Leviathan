### Progress Tracking

This page covers progress visualization for long-running async commands using `ProgressBar` and `ProgressReporter`.

#### Overview

When commands perform time-consuming operations (database migrations, file processing, bulk operations), users benefit from visual feedback. Leviathan provides:

- `Progress` — Basic interface for reporting progress messages
- `ProgressBar` — Customizable visual progress bar builder
- `ProgressReporter` — Wrapper that combines ProgressBar with Progress

#### Basic Progress Reporting

The simplest way to report progress in async commands:

```java
SlashCommand scan = SlashCommand.create("scan")
    .executesAsync((ctx, progress, cancellation) -> {
        progress.report("Starting scan...");

        for (int i = 0; i < 100; i++) {
            if (cancellation.isCancelled()) {
                progress.report("Scan cancelled.");
                return;
            }

            doWork(i);
            progress.report("Progress: " + i + "%");
        }

        progress.report("Scan complete!");
    })
    .build();
```

#### ProgressBar

`ProgressBar` creates customizable visual progress indicators with a fluent builder API.

##### Basic Usage

```java
ProgressBar bar = ProgressBar.builder()
    .total(100)
    .build();

String output = bar.render(50);
// Output: §a███████████████§7░░░░░░░░░░░░░░░
```

##### Builder Options

| Method | Default | Description |
|--------|---------|-------------|
| `total(int)` | 100 | Maximum value for the progress |
| `width(int)` | 30 | Width in characters |
| `filledChar(char)` | `'█'` | Character for completed portion |
| `emptyChar(char)` | `'░'` | Character for remaining portion |
| `characters(char, char)` | — | Set both characters at once |
| `prefix(String)` | `""` | Text before the bar |
| `suffix(String)` | `""` | Text after the bar |
| `showPercentage(boolean)` | false | Show percentage (e.g., "50.0%") |
| `showRatio(boolean)` | false | Show ratio (e.g., "(50/100)") |
| `colorFilled(String)` | `"§a"` | Color code for filled portion |
| `colorEmpty(String)` | `"§7"` | Color code for empty portion |
| `colorText(String)` | `"§f"` | Color code for text |
| `colors(String, String, String)` | — | Set all colors at once |

##### Customization Examples

**With percentage and prefix:**

```java
ProgressBar bar = ProgressBar.builder()
    .total(100)
    .width(20)
    .prefix("Loading: ")
    .showPercentage(true)
    .build();

bar.render(75);
// Output: §fLoading: §a███████████████§7░░░░░ §f75.0%
```

**With ratio display:**

```java
ProgressBar bar = ProgressBar.builder()
    .total(500)
    .showRatio(true)
    .showPercentage(true)
    .build();

bar.render(250);
// Output: §a███████████████§7░░░░░░░░░░░░░░░ §f50.0% §f(250/500)
```

**Custom characters and colors:**

```java
ProgressBar bar = ProgressBar.builder()
    .total(100)
    .width(25)
    .characters('=', '-')
    .colors("§6", "§8", "§e")  // Orange filled, dark gray empty, yellow text
    .prefix("[")
    .suffix("]")
    .showPercentage(true)
    .build();

bar.render(60);
// Output: §e[§6===============§8----------§e] §e60.0%
```

**Compact style:**

```java
ProgressBar bar = ProgressBar.builder()
    .total(100)
    .width(10)
    .characters('#', '.')
    .prefix("(")
    .suffix(")")
    .build();

bar.render(30);
// Output: §f(§a###§7.......§f)
```

##### Rendering Methods

```java
ProgressBar bar = ProgressBar.builder().total(100).build();

// Render by absolute value
String s1 = bar.render(50);

// Render with additional message
String s2 = bar.render(50, "Processing files...");
// Output: §a███████████████§7░░░░░░░░░░░░░░░ §f- Processing files...

// Render by percentage (0.0 to 1.0)
String s3 = bar.renderPercentage(0.75);

// Render percentage with message
String s4 = bar.renderPercentage(0.75, "Almost done!");
```

#### ProgressReporter

`ProgressReporter` wraps a `ProgressBar` and `Progress` together for convenient reporting in async commands.

##### Basic Usage

```java
SlashCommand process = SlashCommand.create("process")
    .executesAsync((ctx, progress, cancellation) -> {
        ProgressBar bar = ProgressBar.builder()
            .total(100)
            .showPercentage(true)
            .prefix("Processing: ")
            .build();

        ProgressReporter reporter = bar.reporter(progress);

        for (int i = 0; i <= 100; i++) {
            if (cancellation.isCancelled()) return;

            doWork(i);
            reporter.report(i);  // Automatically renders the progress bar
        }

        reporter.reportRaw("§aProcessing complete!");
    })
    .build();
```

##### Reporter Methods

```java
ProgressReporter reporter = bar.reporter(progress);

// Report by absolute value
reporter.report(50);

// Report with message
reporter.report(50, "Scanning files...");

// Report by percentage (0.0 to 1.0)
reporter.reportPercentage(0.5);

// Report percentage with message
reporter.reportPercentage(0.5, "Halfway there!");

// Report raw message (no progress bar)
reporter.reportRaw("Additional status message");
```

#### Complete Example

A comprehensive example showing all features:

```java
SlashCommand backup = SlashCommand.create("backup")
    .permission("admin.backup")
    .argWorld("world")
    .executesAsync((ctx, progress, cancellation) -> {
        World world = ctx.get("world", World.class);
        List<Chunk> chunks = getLoadedChunks(world);

        // Create a customized progress bar
        ProgressBar bar = ProgressBar.builder()
            .total(chunks.size())
            .width(25)
            .prefix("§6Backup: §f")
            .showPercentage(true)
            .showRatio(true)
            .colorFilled("§a")
            .colorEmpty("§8")
            .build();

        ProgressReporter reporter = bar.reporter(progress);

        reporter.reportRaw("§eStarting backup of world: " + world.getName());

        for (int i = 0; i < chunks.size(); i++) {
            // Check for cancellation
            if (cancellation.isCancelled()) {
                reporter.reportRaw("§cBackup cancelled by user.");
                return;
            }

            Chunk chunk = chunks.get(i);
            backupChunk(chunk);

            // Update progress every 10 chunks to reduce spam
            if (i % 10 == 0 || i == chunks.size() - 1) {
                reporter.report(i + 1, "Chunk " + chunk.getX() + "," + chunk.getZ());
            }
        }

        reporter.reportRaw("§aBackup complete! Saved " + chunks.size() + " chunks.");
    }, 300_000L)  // 5 minute timeout
    .build();
```

#### Integration with Timeout

Progress reporting works seamlessly with async timeouts:

```java
SlashCommand longTask = SlashCommand.create("longtask")
    .executesAsync((ctx, progress, cancellation) -> {
        ProgressBar bar = ProgressBar.builder()
            .total(100)
            .showPercentage(true)
            .build();

        ProgressReporter reporter = bar.reporter(progress);

        for (int i = 0; i <= 100; i++) {
            if (cancellation.isCancelled()) {
                // This is triggered when timeout is reached
                reporter.reportRaw("§cTask timed out or was cancelled.");
                return;
            }

            Thread.sleep(100);  // Simulate work
            reporter.report(i);
        }

        reporter.reportRaw("§aTask completed successfully!");
    }, 5_000L)  // 5 second timeout - will trigger cancellation
    .build();
```

#### Best Practices

1. **Update frequency** — Don't spam updates; report every N items or use time-based throttling
2. **Meaningful messages** — Include context in messages (current item, estimated time)
3. **Check cancellation** — Always check `cancellation.isCancelled()` in loops
4. **Final status** — Always send a final message indicating success/failure/cancellation
5. **Appropriate width** — Use 20-30 character width for chat; adjust for actionbar

```java
// Throttled progress updates
int updateInterval = Math.max(1, total / 20);  // ~20 updates total
for (int i = 0; i < total; i++) {
    doWork(i);
    if (i % updateInterval == 0 || i == total - 1) {
        reporter.report(i + 1);
    }
}
```

#### Styling Presets

Common progress bar styles:

```java
// Minecraft-style
ProgressBar minecraft = ProgressBar.builder()
    .characters('|', '|')
    .colors("§a", "§c", "§f")
    .build();

// Bracket-style
ProgressBar bracket = ProgressBar.builder()
    .characters('=', ' ')
    .prefix("[")
    .suffix("]")
    .build();

// Dots-style
ProgressBar dots = ProgressBar.builder()
    .width(10)
    .characters('*', '.')
    .showPercentage(true)
    .build();

// Gradient-style (requires multiple renders)
ProgressBar gradient = ProgressBar.builder()
    .characters('>', '-')
    .colors("§e", "§7", "§6")
    .prefix("§6[")
    .suffix("§6]")
    .build();
```
