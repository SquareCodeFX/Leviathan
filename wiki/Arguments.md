### Arguments

Leviathan provides a rich, type‑safe argument system. You declare positional arguments in order using `SlashCommandBuilder` methods. Parsing, validation, and tab completion are integrated.

#### Built‑in argument helpers

- Numeric: `argInt`, `argLong`, `argDouble`, `argFloat`
- Text: `argString`
- Boolean: `argBoolean`
- UUID: `argUUID`
- Duration: `argDuration` — Parses time strings to milliseconds
- Bukkit types: `argPlayer`, `argOfflinePlayer`, `argWorld`, `argMaterial`
- Enums: `argEnum(name, Class<E>)`

Example:

```java
SlashCommand tp = SlashCommand.create("tp")
    .argPlayer("target")
    .argWorld("world")
    .executes(ctx -> {
        Player target = ctx.get("target", Player.class);
        World world = ctx.get("world", World.class);
        // ... teleport logic
    })
    .build();
```

#### Ranges and length constraints

- `argIntRange(name, min, max)`
- `argLongRange(name, min, max)`
- `argDoubleRange(name, min, max)`
- `argFloatRange(name, min, max)`
- `argStringLength(name, minLen, maxLen)`

These fail fast with a descriptive error if violated.

#### Choices and command choices

- `argChoices(name, Map<String,T> choices, String displayType)` — The user selects from keys; you receive the mapped value.
- `argCommandChoices(name, Map<String, SlashCommand> choices)` — Provide a list of subcommands as a choice.

Example:

```java
Map<String, Integer> levels = new LinkedHashMap<>();
levels.put("easy", 1);
levels.put("normal", 2);
levels.put("hard", 3);

SlashCommand set = SlashCommand.create("setdifficulty")
    .argChoices("mode", levels, "difficulty")
    .executes(ctx -> {
        int level = ctx.get("mode", Integer.class);
        // apply difficulty level
    })
    .build();
```

#### Conditional arguments

Use `argIf(name, parser, Predicate<CommandContext> condition)` to include an argument only if the condition matches.

```java
SlashCommand give = SlashCommand.create("give")
    .argPlayer("target")
    .argMaterial("item")
    .argIf("amount", ArgParsers.INT, ctx -> ctx.getFlag("bulk"))
    .flagLong("bulk", "bulk")
    .executes(ctx -> {
        int amount = ctx.getOrDefault("amount", Integer.class, 1);
        // ...
    })
    .build();
```

#### Duration arguments

`argDuration(name)` parses human-readable time strings into milliseconds. Supported formats:

- `30s` — 30 seconds
- `5m` — 5 minutes
- `2h` — 2 hours
- `1d` — 1 day
- `1w` — 1 week
- `1mo` — 1 month (30 days)
- `1y` — 1 year (365 days)
- Combinations: `2h30m`, `1d12h`, `1w2d`

Example:

```java
SlashCommand tempban = SlashCommand.create("tempban")
    .argPlayer("target")
    .argDuration("duration")
    .argString("reason", ArgContext.builder().optional(true).greedy(true).build())
    .executes((sender, ctx) -> {
        Player target = ctx.get("target", Player.class);
        long durationMs = ctx.get("duration", Long.class);
        String reason = ctx.getStringOrDefault("reason", "No reason");

        // Ban player for the specified duration
        banService.ban(target, durationMs, reason);
        sender.sendMessage("Banned " + target.getName() + " for " + formatDuration(durationMs));
    })
    .build();
```

With `ArgContext` for validation:

```java
SlashCommand mute = SlashCommand.create("mute")
    .argPlayer("target")
    .argDuration("duration", ArgContext.builder()
        .description("How long to mute the player (e.g., 30m, 2h, 1d)")
        .build())
    .executes((sender, ctx) -> { /* ... */ })
    .build();
```

#### Page arguments

`argPage()`, `argPage(name)`, and `argPage(name, defaultPage)` integrate with the pagination utilities. The parsed value is an `int` page index respecting your configured defaults.

#### Custom parsers

Use `arg(name, ArgumentParser<T> parser)` to plug your own parser. Combine with `ArgContext` for fine‑grained control.

```java
SlashCommand custom = SlashCommand.create("custom")
    .arg("color", ArgParsers.enumParser(ChatColor.class))
    .executes(ctx -> {
        ChatColor c = ctx.get("color", ChatColor.class);
    })
    .build();
```

#### ArgContext

Most `arg*` methods have an overload with `ArgContext` allowing you to specify properties such as optionality, default value, greedy behavior, display name, custom completion list, and more.

##### Basic Properties

```java
ArgContext ctx = ArgContext.builder()
    .optional(true)                          // Make argument optional
    .defaultValue(10)                        // Default value if not provided
    .greedy(true)                            // Consume all remaining tokens (string only, must be last)
    .permission("admin.use")                 // Per-argument permission
    .didYouMean(true)                        // Enable "did you mean?" suggestions
    .description("The target player name")   // Description shown in help
    .build();
```

##### Descriptions for Help

Add descriptions to arguments for better help output:

```java
SlashCommand ban = SlashCommand.create("ban")
    .description("Ban a player from the server")
    .arg("target", ArgParsers.PLAYER, ArgContext.builder()
        .description("The player to ban")
        .build())
    .arg("reason", ArgParsers.STRING, ArgContext.builder()
        .optional(true)
        .description("Reason for the ban")
        .build())
    .arg("duration", ArgParsers.STRING, ArgContext.builder()
        .optional(true)
        .description("Ban duration (e.g., 1d, 1w, permanent)")
        .build())
    .executes((sender, ctx) -> { /* ... */ })
    .build();
```

Or using the fluent Arg API:

```java
SlashCommand ban = SlashCommand.create("ban")
    .argPlayer("target").withDescription("The player to ban")
    .argString("reason").optional(true).withDescription("Reason for the ban")
    .executes((sender, ctx) -> { /* ... */ })
    .build();
```

When `/ban help` is used, the help system will display:

```
Usage: /ban <target> [reason] [duration]
Ban a player from the server

Arguments:
  <target> - Player - The player to ban
  [reason] - String - Reason for the ban
  [duration] - String - Ban duration (e.g., 1d, 1w, permanent)
```

##### Validation Constraints

ArgContext supports built-in validation for numeric and string types:

**Numeric ranges:**

```java
// Integer range
ArgContext intCtx = ArgContext.builder()
    .intRange(1, 100)
    .build();

// Or individual bounds
ArgContext intCtx2 = ArgContext.builder()
    .intMin(1)
    .intMax(100)
    .build();

// Long range
ArgContext longCtx = ArgContext.builder()
    .longRange(0L, 1_000_000L)
    .build();

// Double range
ArgContext doubleCtx = ArgContext.builder()
    .doubleRange(0.0, 1.0)
    .build();

// Float range
ArgContext floatCtx = ArgContext.builder()
    .floatRange(0.0f, 100.0f)
    .build();
```

**String constraints:**

```java
// Length constraints
ArgContext lenCtx = ArgContext.builder()
    .stringLengthRange(3, 16)  // 3-16 characters
    .build();

// Regex pattern
ArgContext patternCtx = ArgContext.builder()
    .stringPattern("[a-zA-Z0-9_]+")  // Alphanumeric and underscores only
    .build();

// Using compiled Pattern
ArgContext patternCtx2 = ArgContext.builder()
    .stringPattern(Pattern.compile("^[A-Z]{2,4}$"))
    .build();
```

#### Custom Validators

For complex validation logic beyond simple ranges and patterns, use custom validators.

##### Validator Interface

```java
@FunctionalInterface
public interface Validator<T> {
    /**
     * @param value the value to validate
     * @return null if valid, or an error message if invalid
     */
    @Nullable String validate(@Nullable T value);
}
```

##### Basic Usage

```java
// Validate username format
Validator<String> usernameValidator = value -> {
    if (value == null || value.isEmpty()) {
        return "Username cannot be empty";
    }
    if (value.length() < 3) {
        return "Username must be at least 3 characters";
    }
    if (value.length() > 16) {
        return "Username cannot exceed 16 characters";
    }
    if (!value.matches("^[a-zA-Z0-9_]+$")) {
        return "Username can only contain letters, numbers, and underscores";
    }
    if (bannedNames.contains(value.toLowerCase())) {
        return "This username is not allowed";
    }
    return null;  // Valid
};

SlashCommand register = SlashCommand.create("register")
    .argString("username", ArgContext.builder()
        .addValidator(usernameValidator)
        .build())
    .executes(ctx -> { /* ... */ })
    .build();
```

##### Multiple Validators

You can chain multiple validators — they run in order, and the first error stops validation:

```java
Validator<Integer> positiveValidator = value ->
    value == null || value <= 0 ? "Value must be positive" : null;

Validator<Integer> evenValidator = value ->
    value != null && value % 2 != 0 ? "Value must be even" : null;

Validator<Integer> maxValidator = value ->
    value != null && value > 1000 ? "Value cannot exceed 1000" : null;

ArgContext ctx = ArgContext.builder()
    .addValidator(positiveValidator)
    .addValidator(evenValidator)
    .addValidator(maxValidator)
    .build();

SlashCommand cmd = SlashCommand.create("setvalue")
    .argInt("value", ctx)
    .executes(ctx -> { /* ... */ })
    .build();
```

##### Practical Examples

**Email validator:**

```java
Validator<String> emailValidator = value -> {
    if (value == null) return "Email is required";
    if (!value.contains("@") || !value.contains(".")) {
        return "Invalid email format";
    }
    return null;
};
```

**Price validator (positive, max 2 decimals):**

```java
Validator<Double> priceValidator = value -> {
    if (value == null || value < 0) {
        return "Price must be non-negative";
    }
    String str = String.valueOf(value);
    int decimalPos = str.indexOf('.');
    if (decimalPos >= 0 && str.length() - decimalPos - 1 > 2) {
        return "Price can have at most 2 decimal places";
    }
    return null;
};
```

**Unique name validator (check database):**

```java
Validator<String> uniqueNameValidator = value -> {
    if (value == null) return "Name is required";
    if (database.nameExists(value)) {
        return "This name is already taken";
    }
    return null;
};
```

**Date range validator:**

```java
Validator<String> futureDateValidator = value -> {
    if (value == null) return "Date is required";
    try {
        LocalDate date = LocalDate.parse(value);
        if (date.isBefore(LocalDate.now())) {
            return "Date must be in the future";
        }
        return null;
    } catch (DateTimeParseException e) {
        return "Invalid date format (use YYYY-MM-DD)";
    }
};
```

##### Combining with Built-in Validation

Custom validators work alongside built-in constraints:

```java
ArgContext ctx = ArgContext.builder()
    // Built-in range check (runs first)
    .intRange(1, 100)
    // Custom validation (runs after range check passes)
    .addValidator(value -> {
        if (value != null && isPrime(value)) {
            return "Value cannot be a prime number";
        }
        return null;
    })
    .build();
```

#### String Validation Shortcuts

ArgContext provides convenient shortcut methods for common string validation patterns:

```java
// Require valid email format
ArgContext emailCtx = ArgContext.builder()
    .requireEmail()
    .build();

// Require alphanumeric only (letters and digits)
ArgContext alphaCtx = ArgContext.builder()
    .requireAlphanumeric()
    .build();

// Require valid identifier (letters, digits, underscores, starts with letter/underscore)
ArgContext identCtx = ArgContext.builder()
    .requireIdentifier()
    .build();

// Require valid Minecraft username (3-16 chars, letters/digits/underscores)
ArgContext mcCtx = ArgContext.builder()
    .requireMinecraftUsername()
    .build();

// Require valid URL
ArgContext urlCtx = ArgContext.builder()
    .requireUrl()
    .build();

// Require no whitespace
ArgContext noSpaceCtx = ArgContext.builder()
    .requireNoWhitespace()
    .build();
```

These shortcuts are equivalent to calling `stringPattern()` with the appropriate regex.

#### String Transformers

Transform string input before validation:

```java
// Convert to lowercase
ArgContext lowerCtx = ArgContext.builder()
    .transformLowercase()
    .build();

// Convert to uppercase
ArgContext upperCtx = ArgContext.builder()
    .transformUppercase()
    .build();

// Trim whitespace
ArgContext trimCtx = ArgContext.builder()
    .transformTrim()
    .build();

// Normalize whitespace (trim + collapse multiple spaces)
ArgContext normalizeCtx = ArgContext.builder()
    .transformNormalizeWhitespace()
    .build();
```

#### Completion Helpers

Convenience methods for adding completions:

```java
// Add a single completion
ArgContext ctx1 = ArgContext.builder()
    .addCompletion("option1")
    .addCompletion("option2")
    .build();

// Add multiple completions at once
ArgContext ctx2 = ArgContext.builder()
    .addCompletions("easy", "normal", "hard")
    .build();

// Generate completions from an enum
ArgContext ctx3 = ArgContext.builder()
    .completionsFromEnum(GameMode.class)
    .build();

// Add range hint for numeric arguments
ArgContext ctx4 = ArgContext.builder()
    .rangeHint(1, 100)  // Shows "[1-100]" as hint
    .intRange(1, 100)   // Actual validation
    .build();
```

#### Fluent Aliases

ArgContext.Builder provides fluent aliases for better readability:

```java
ArgContext ctx = ArgContext.builder()
    .withPermission("admin.use")       // Same as permission()
    .withCompletions(List.of("a", "b")) // Same as completionsPredefined()
    .withDescription("The target")     // Same as description()
    .withDynamicCompletions(provider)  // Same as completionsDynamic()
    .withAsyncDynamicCompletions(prov) // Same as completionsDynamicAsync()
    .withAsyncCompletions(supplier)    // Same as completionsPredefinedAsync()
    .build();
```

For advanced tab-completion features including async completions and permission-filtered suggestions, see [Advanced Completions](Advanced-Completions.md).

### Custom ArgumentParser Interface

For full control over argument parsing, implement the `ArgumentParser<T>` interface:

```java
public interface ArgumentParser<T> {
    /** Short type name for error messages (e.g., "int", "uuid") */
    @NotNull String getTypeName();

    /** Parse input to target type. Never returns null. */
    @NotNull ParseResult<T> parse(@NotNull String input, @NotNull CommandSender sender);

    /** Provide tab-completion suggestions. Never returns null. */
    @NotNull List<String> complete(@NotNull String input, @NotNull CommandSender sender);
}
```

**Contract requirements:**
- Implementations must be **stateless and thread-safe**
- `parse()` must never return null — always return a `ParseResult`
- `complete()` must return a non-null list (possibly empty)

#### Creating a Custom Parser

```java
public class UUIDParser implements ArgumentParser<UUID> {

    @Override
    public @NotNull String getTypeName() {
        return "uuid";
    }

    @Override
    public @NotNull ParseResult<UUID> parse(@NotNull String input, @NotNull CommandSender sender) {
        try {
            UUID uuid = UUID.fromString(input);
            return ParseResult.success(uuid);
        } catch (IllegalArgumentException e) {
            return ParseResult.failure("Invalid UUID format");
        }
    }

    @Override
    public @NotNull List<String> complete(@NotNull String input, @NotNull CommandSender sender) {
        return List.of();  // UUIDs are too complex to suggest
    }
}
```

#### Using Custom Parsers

```java
private static final UUIDParser UUID_PARSER = new UUIDParser();

SlashCommand lookup = SlashCommand.create("lookup")
    .arg("id", UUID_PARSER, ArgContext.builder()
        .description("The unique identifier")
        .build())
    .executes((sender, ctx) -> {
        UUID id = ctx.require("id", UUID.class);
        // Use the parsed UUID...
    })
    .build();
```

#### ParseResult

```java
// Successful parse
ParseResult.success(value);

// Failed parse with error message
ParseResult.failure("Error message");

// Check result
if (result.isSuccess()) {
    T value = result.value();
} else {
    String error = result.errorMessage();
}
```

#### Context-Aware Parser Example

```java
public class HomeParser implements ArgumentParser<Location> {

    @Override
    public @NotNull ParseResult<Location> parse(@NotNull String input, @NotNull CommandSender sender) {
        if (!(sender instanceof Player player)) {
            return ParseResult.failure("This argument requires a player");
        }
        Location home = homeService.getHome(player.getUniqueId(), input);
        if (home == null) {
            return ParseResult.failure("Home '" + input + "' not found");
        }
        return ParseResult.success(home);
    }

    @Override
    public @NotNull List<String> complete(@NotNull String input, @NotNull CommandSender sender) {
        if (!(sender instanceof Player player)) return List.of();
        return homeService.getHomeNames(player.getUniqueId());
    }
}
```
