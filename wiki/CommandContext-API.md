### CommandContext API

This page covers the `CommandContext` API for accessing parsed arguments, flags, and key-values within command execution.

#### Overview

`CommandContext` is the container for all parsed values from a command invocation. It provides:

- Type-safe argument accessors
- Flag and key-value accessors
- Bulk operations and streaming
- Type conversion utilities

#### Basic Accessors

##### Get with Type

```java
// Get a typed value (returns null if missing or wrong type)
String name = ctx.get("name", String.class);
Integer count = ctx.get("count", Integer.class);
Player target = ctx.get("target", Player.class);

// Get with default value
String mode = ctx.getOrDefault("mode", String.class, "default");
int page = ctx.getIntOrDefault("page", 1);

// Get as Optional
Optional<String> reason = ctx.optional("reason", String.class);

// Get or throw if missing
Player player = ctx.orThrow("player", Player.class);
// or using alias:
Player player2 = ctx.require("player", Player.class);
```

##### Convenience Methods

```java
// Type-specific convenience methods
String name = ctx.getStringOrDefault("name", "Unknown");
int count = ctx.getIntOrDefault("count", 0);
long id = ctx.getLongOrDefault("id", 0L);
double price = ctx.getDoubleOrDefault("price", 0.0);
float factor = ctx.getFloatOrDefault("factor", 1.0f);
boolean enabled = ctx.getBooleanOrDefault("enabled", false);
UUID uuid = ctx.getUuidOrDefault("uuid", UUID.randomUUID());
Player player = ctx.getPlayerOrDefault("target", defaultPlayer);
```

#### Type Conversion

Convert values to different types automatically:

```java
// Convert any value to String
String str = ctx.getAsString("value");  // Uses String.valueOf()

// Convert to numeric types (handles String parsing)
Integer num = ctx.getAsInt("count");    // Parses "123" to 123
Long bigNum = ctx.getAsLong("id");      // Parses "123456789" to long
Double dec = ctx.getAsDouble("price");  // Parses "19.99" to double

// Convert to Boolean (handles various formats)
Boolean flag = ctx.getAsBoolean("enabled");
// Recognizes: true/false, yes/no, on/off, 1/0
```

#### Fallback Names

Try multiple argument names in order:

```java
// Try "player", then "target", then "p" - return first found
Player p = ctx.getWithFallback(Player.class, defaultPlayer, "player", "target", "p");

// As Optional (no default)
Optional<String> name = ctx.optionalWithFallback(String.class, "name", "n", "player_name");
```

#### Presence Checks

```java
// Check if a single argument exists
if (ctx.has("reason")) {
    // ...
}

// Check if all arguments exist
if (ctx.hasAll("target", "amount", "reason")) {
    // All three are present
}

// Check if any argument exists
if (ctx.hasAny("silent", "quiet", "no-broadcast")) {
    // At least one is present
}
```

#### Bulk Operations

```java
// Get all arguments as a Map
Map<String, Object> all = ctx.getAll();

// Get all argument names
Set<String> names = ctx.argumentNames();

// Check if empty
if (ctx.isEmpty()) {
    sender.sendMessage("No arguments provided");
}

// Get counts
int argCount = ctx.size();           // Only arguments
int totalCount = ctx.totalSize();    // Args + flags + key-values
```

#### Stream API

```java
// Stream all values
ctx.valueStream()
    .filter(v -> v instanceof Player)
    .forEach(v -> ((Player) v).sendMessage("Hello!"));

// Stream entries (name-value pairs)
ctx.entryStream()
    .filter(e -> e.getValue() instanceof Integer)
    .forEach(e -> logger.info(e.getKey() + " = " + e.getValue()));

// Get first value of a type
Optional<Player> firstPlayer = ctx.getFirstByType(Player.class);

// Get all values of a type
List<Player> allPlayers = ctx.getAllByType(Player.class);
```

#### Functional Operations

```java
// Transform a value if present
Optional<String> upperName = ctx.map("name", String.class, String::toUpperCase);

// Execute action if value present
ctx.ifPresent("target", Player.class, player -> {
    player.sendMessage("You were selected!");
});

// Using functional retrieval
String name = ctx.arg("name", OptionMapping::asString);
Integer count = ctx.arg("count", OptionMapping::asInt);
```

#### Flag Accessors

```java
// Get flag value (defaults to false)
boolean silent = ctx.getFlag("silent");
boolean verbose = ctx.getFlag("verbose", true);  // Custom default

// Check if flag is defined
if (ctx.hasFlag("force")) {
    // Flag was explicitly provided
}

// Get all flags
Map<String, Boolean> flags = ctx.allFlags();
```

#### Key-Value Accessors

```java
// Get typed key-value
String reason = ctx.getKeyValue("reason", String.class);
Integer limit = ctx.getKeyValue("limit", Integer.class);

// With defaults
String reason = ctx.getKeyValue("reason", String.class, "No reason");
int limit = ctx.getKeyValueInt("limit", 10);

// Convenience methods
String str = ctx.getKeyValueString("message");
Integer num = ctx.getKeyValueInt("count");
Boolean flag = ctx.getKeyValueBoolean("enabled");

// As Optional
Optional<String> maybeReason = ctx.optionalKeyValue("reason", String.class);

// Check existence
if (ctx.hasKeyValue("timeout")) {
    // ...
}

// Get all key-values
Map<String, Object> kvs = ctx.allKeyValues();
```

#### Multi-Value Accessors

For key-values with multiple comma-separated values (e.g., `tags=pvp,survival`):

```java
// Get all values for a key
List<String> tags = ctx.getMultiValue("tags");

// With type checking
List<String> tags = ctx.getMultiValue("tags", String.class);

// Check for multi-values
if (ctx.hasMultiValue("filters")) {
    List<String> filters = ctx.getMultiValue("filters");
}

// Get all multi-value pairs
Map<String, List<Object>> allMulti = ctx.allMultiValues();
```

#### Raw Arguments

```java
// Get the original argument array
String[] raw = ctx.raw();
```

#### Complete Example

```java
SlashCommand ban = SlashCommand.create("ban")
    .argPlayer("target")
    .argString("reason", ArgContext.builder().optional(true).build())
    .flag("silent", 's', "silent")
    .keyValueString("duration", "permanent")
    .executes((sender, ctx) -> {
        // Required argument
        Player target = ctx.require("target", Player.class);

        // Optional with default
        String reason = ctx.getStringOrDefault("reason", "No reason provided");

        // Flag
        boolean silent = ctx.getFlag("silent");

        // Key-value with default
        String duration = ctx.getKeyValueString("duration", "permanent");

        // Type conversion
        Integer durationMinutes = ctx.getAsInt("duration");  // If numeric

        // Presence check
        if (ctx.has("reason")) {
            broadcast("Ban reason: " + reason);
        }

        // Stream operations
        ctx.getAllByType(Player.class).forEach(p -> {
            p.sendMessage("A player was banned!");
        });

        // Ban logic...
    })
    .build();
```

#### Dependency Validation

Validate argument dependencies at runtime:

##### Require All

```java
.executes((sender, ctx) -> {
    // Throws ApiMisuseException if any argument is missing
    ctx.requireAll("from", "to", "amount");

    // Now safe to access all three
    Player from = ctx.require("from", Player.class);
    Player to = ctx.require("to", Player.class);
    int amount = ctx.require("amount", Integer.class);
})
```

##### Require Any

```java
.executes((sender, ctx) -> {
    // At least one must be present
    ctx.requireAny("player", "all", "world");

    // Now we know at least one exists
    if (ctx.has("all")) {
        // Handle all players
    } else if (ctx.has("player")) {
        Player p = ctx.require("player", Player.class);
        // Handle specific player
    }
})
```

##### Conditional Requirements

```java
.executes((sender, ctx) -> {
    // If "output" is present, "format" must also be present
    ctx.requireIfPresent("output", "format");

    // Safe to use output and format together
    if (ctx.has("output")) {
        String output = ctx.require("output", String.class);
        String format = ctx.require("format", String.class);
    }
})
```

##### Mutual Exclusivity

```java
.executes((sender, ctx) -> {
    // Only one of these can be present
    ctx.requireMutuallyExclusive("player", "all", "radius");

    // Process the one that exists
    if (ctx.has("player")) {
        // ...
    } else if (ctx.has("all")) {
        // ...
    } else if (ctx.has("radius")) {
        // ...
    }
})
```

##### Gather Present Arguments

```java
.executes((sender, ctx) -> {
    // Collect all present numeric arguments
    Map<String, Integer> limits = ctx.gatherPresent(Integer.class,
        "max-players", "max-items", "max-blocks");

    for (var entry : limits.entrySet()) {
        logger.info(entry.getKey() + " = " + entry.getValue());
    }
})
```

##### Conditional Execution

```java
.executes((sender, ctx) -> {
    // Execute only if all present
    ctx.ifAllPresent(c -> {
        Player from = c.require("from", Player.class);
        Player to = c.require("to", Player.class);
        // Transfer logic
    }, "from", "to");

    // Execute if any present
    ctx.ifAnyPresent(c -> {
        sender.sendMessage("You provided optional filters");
    }, "filter", "tag", "category");
})
```

---

#### Argument Aliases

All CommandContext methods support argument aliases. When an argument has aliases defined, you can access it using any of its names.

##### Defining Aliases

```java
SlashCommand.create("teleport")
    .arg("player", playerParser(), ArgContext.builder()
        .aliases("p", "target", "t")
        .build())
    .arg("location", locationParser(), ArgContext.builder()
        .aliases("loc", "pos", "dest")
        .build())
    .build();

// Or using fluent API on Arg
Arg<Player> playerArg = Arg.of("player", playerParser())
    .withAliases("p", "target", "t");
```

##### Using Aliases in CommandContext

```java
.executes((sender, ctx) -> {
    // All of these return the same value:
    Player target = ctx.get("player", Player.class);
    Player target = ctx.get("p", Player.class);
    Player target = ctx.get("target", Player.class);
    Player target = ctx.get("t", Player.class);

    // Works with all accessor methods:
    ctx.getOrDefault("p", Player.class, defaultPlayer);
    ctx.optional("target", Player.class);
    ctx.require("t", Player.class);
    ctx.has("p");
    ctx.getAsString("target");
    ctx.getAsInt("n");  // If "amount" has alias "n"

    // Works with bulk operations:
    ctx.hasAll("p", "loc");           // Check aliases
    ctx.hasAny("p", "target", "all"); // Mix of aliases and names
    ctx.requireAll("p", "dest");      // Use aliases in validation
    ctx.requireIfPresent("p", "loc"); // Conditional with aliases
    ctx.gatherPresent(Player.class, "p", "target2", "victim");
})
```

##### Alias Introspection

```java
.executes((sender, ctx) -> {
    // Get the alias map (alias -> primary name)
    Map<String, String> aliases = ctx.aliasMap();
    // Example: {"p" -> "player", "target" -> "player", "t" -> "player"}

    // Check if a name is an alias
    boolean isAlias = ctx.isAlias("p");      // true
    boolean isAlias2 = ctx.isAlias("player"); // false (primary name)

    // Get the primary name for an alias
    String primary = ctx.getPrimaryName("p");     // "player"
    String primary2 = ctx.getPrimaryName("player"); // "player" (unchanged)

    // Get value without type checking
    Object value = ctx.argument("p");
})
```

##### Alias-Aware Methods Summary

All these methods support aliases:

| Category | Methods |
|----------|---------|
| **Basic Getters** | `get()`, `getOrDefault()`, `optional()`, `orThrow()`, `require()` |
| **Type-Specific** | `getStringOrDefault()`, `getIntOrDefault()`, `getDoubleOrDefault()`, etc. |
| **Type Conversion** | `getAsString()`, `getAsInt()`, `getAsLong()`, `getAsDouble()`, `getAsBoolean()` |
| **Presence Checks** | `has()`, `hasAll()`, `hasAny()` |
| **Validation** | `requireAll()`, `requireAny()`, `requireIfPresent()`, `requireMutuallyExclusive()` |
| **Bulk Operations** | `gatherPresent()`, `ifAllPresent()`, `ifAnyPresent()` |
| **Functional** | `map()`, `ifPresent()`, `arg()` |
