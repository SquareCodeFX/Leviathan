### Exceptions and Error Handling

Leviathan centralizes failures (parsing, guards, cooldowns, permission, execution) and routes them through an `ExceptionHandler`. You can customize messages and diagnostics.

#### Error flow

During `SlashCommand#execute(...)` the following can fail:

- Permission/guard checks
- Argument parsing and validation
- Cross-argument validators
- Cooldown checks
- Action execution (sync or async)

On failure, `sendErrorMessage(sender, ErrorType, message, exception)` is invoked. If `sendErrors(true)` is enabled and a `MessageProvider` is set, user-friendly messages are sent to the sender. Otherwise, minimal feedback is provided.

#### ExceptionHandler

- Interface: `ExceptionHandler`
- You can install a custom instance via `builder.exceptionHandler(handler)`.
- Typical responsibilities: mapping exceptions to `ErrorType`, formatting messages, adding hints.

#### DetailedExceptionHandler

`DetailedExceptionHandler` enriches reports with JVM/thread diagnostics to aid debugging production issues.

Ways to enable:

- `builder.detailedExceptionHandler(JavaPlugin plugin)` — convenience that wires up collectors.
- Or `builder.detailedExceptionHandler(DetailedExceptionHandler instance)`.

Related classes:

- `JvmInfoCollector` — Captures JVM properties, memory usage, GC info.
- `ThreadDumpCollector` — Captures stack traces of running threads.

#### ErrorType

Categorization enum used throughout error handling (permission, invalid usage, cooldown, internal error, etc.). This drives how messages are built and which suggestions to show.

#### Suggestion System ("Did you mean...?")

Leviathan includes a powerful suggestion system that helps users when they make typos or mistakes. The `SuggestionEngine` uses Levenshtein distance to find similar options and present "Did you mean...?" suggestions.

##### Automatic Suggestions

Enable automatic suggestions on arguments using `ArgContext`:

```java
SlashCommand cmd = SlashCommand.create("gamemode")
    .argEnum("mode", GameMode.class, ArgContext.builder()
        .didYouMean(true)  // Enable suggestions
        .build())
    .executes((sender, ctx) -> { /* ... */ })
    .build();
```

When a user types `/gamemode survial`, they'll see:

```
Invalid game mode: survial
Did you mean: survival?
```

##### SuggestionEngine API

Use `SuggestionEngine` directly for custom suggestion scenarios:

```java
// Basic suggestion
List<String> options = List.of("survival", "creative", "adventure", "spectator");
SuggestionEngine.Suggestion suggestion = SuggestionEngine.suggest("survial", options);

if (suggestion.hasMatch()) {
    sender.sendMessage("Did you mean: " + suggestion.best() + "?");
    // Or show multiple suggestions
    sender.sendMessage("Did you mean one of: " + String.join(", ", suggestion.suggestions()) + "?");
}
```

##### Configuration Options

```java
// Customize suggestion parameters
SuggestionEngine.Suggestion suggestion = SuggestionEngine.suggest(
    "input",           // User input
    options,           // Available options
    3,                 // Max suggestions to return
    0.6                // Minimum similarity threshold (0.0-1.0)
);
```

##### Subcommand Suggestions

Get suggestions for mistyped subcommands:

```java
SuggestionEngine.Suggestion suggestion = SuggestionEngine.suggestSubcommand(
    "tpa",             // User input
    parentCommand      // Parent SlashCommand with subcommands
);

if (suggestion.hasMatch()) {
    sender.sendMessage("Unknown subcommand. Did you mean: " + suggestion.best() + "?");
}
```

##### Argument Suggestions

Get suggestions for invalid argument values:

```java
SuggestionEngine.Suggestion suggestion = SuggestionEngine.suggestArgument(
    "dimand",          // User input
    arg                // Arg<?> with completions defined
);
```

##### Suggestion Record

The `Suggestion` record provides convenient access methods:

```java
SuggestionEngine.Suggestion suggestion = SuggestionEngine.suggest(input, options);

// Check if any suggestions found
boolean hasMatch = suggestion.hasMatch();

// Get best match (null if none)
String best = suggestion.best();

// Get all suggestions as list
List<String> all = suggestion.suggestions();

// Get as set (no duplicates)
Set<String> unique = suggestion.suggestionsAsSet();

// Get formatted message
String message = suggestion.formatMessage("Unknown value: " + input);
// Output: "Unknown value: survial. Did you mean: survival?"
```

##### Integration with Error Messages

The suggestion system integrates with error handling:

```java
SlashCommand cmd = SlashCommand.create("warp")
    .argString("location", ArgContext.builder()
        .didYouMean(true)
        .completionsPredefined(List.of("spawn", "shop", "arena", "hub"))
        .build())
    .executes((sender, ctx) -> {
        String location = ctx.require("location", String.class);
        // Validate location exists
        if (!warpExists(location)) {
            // Get suggestion for invalid location
            var suggestion = SuggestionEngine.suggest(location, getWarpNames());
            String msg = "Unknown warp: " + location;
            if (suggestion.hasMatch()) {
                msg += ". Did you mean: " + suggestion.best() + "?";
            }
            sender.sendMessage(msg);
            return;
        }
        // Teleport...
    })
    .build();
```

#### ExceptionSuggestionRegistry

Register hints for common errors to improve the UX:

- Suggest missing flags or key‑values when similar names exist.
- Recommend the closest subcommand when `fuzzySubcommandMatching(true)`.

#### MessageProvider

- `MessageProvider` allows full control over user‑facing text.
- Use `DefaultMessageProvider` or provide your own implementation for localization and customization.

Example customizing error messages:

```java
builder.messages(new MessageProvider() {
    @Override public String noPermission(String perm) { return "You lack: " + perm; }
    @Override public String invalidArgument(String name, String reason) { return "Arg '" + name + "': " + reason; }
    // implement other methods as needed
});
```
