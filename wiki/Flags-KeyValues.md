### Flags and Key-Value Options

Leviathan supports boolean flags and typed `key=value` options alongside positional arguments. Both are declared on the builder and retrieved via `CommandContext`.

#### Flags

- Represent feature toggles like `-v`/`--verbose`.
- Multiple syntaxes are supported in user input: short `-v -a`, combined short `-va` (if implemented by parser), and long `--verbose`.

Declare flags:

```java
SlashCommand cmd = SlashCommand.create("backup")
    .flag("verbose", 'v', "verbose")
    .flagShort("dryrun", 'n')
    .flagLong("force", "force")
    .executes(ctx -> {
        boolean verbose = ctx.getFlag("verbose");
        boolean dry = ctx.getFlag("dryrun", false);
        boolean force = ctx.getFlag("force");
        // ...
    })
    .build();
```

You can also build a `Flag` manually and add it via `flag(Flag)` or `withFlag(Flag)`.

##### Flag Factory Methods

Create flags quickly using factory methods:

```java
// Short form only (-s)
Flag silent = Flag.ofShort("silent", 's');

// Long form only (--verbose)
Flag verbose = Flag.ofLong("verbose", "verbose");

// Both short and long forms (-d, --debug)
Flag debug = Flag.of("debug", 'd', "debug");
```

##### Flag.Builder

For full control, use the builder:

```java
Flag confirm = Flag.builder("confirm")
    .shortForm('c')
    .longForm("confirm")
    .description("Require confirmation before action")
    .defaultValue(false)          // Default when flag is not present
    .supportsNegation(true)       // Allow --no-confirm syntax
    .permission("admin.confirm")  // Per-flag permission
    .build();
```

Builder options:

- `shortForm(char)` — Single character for `-x` syntax
- `longForm(String)` — Word for `--xxx` syntax
- `description(String)` — Human-readable description for help
- `defaultValue(boolean)` — Value when flag is not provided (default: false)
- `supportsNegation(boolean)` — Allow `--no-xxx` to explicitly set false (default: true)
- `permission(String)` — Permission required to use this flag

##### Negation Syntax

When `supportsNegation(true)` is set (the default), users can explicitly set a flag to false using `--no-xxx`:

```java
// Command definition
SlashCommand cmd = SlashCommand.create("backup")
    .flag("confirm", 'c', "confirm")  // Supports --no-confirm
    .executes(ctx -> {
        boolean confirm = ctx.getFlag("confirm");
        // confirm will be:
        // - false if neither --confirm nor --no-confirm provided
        // - true if --confirm provided
        // - false if --no-confirm provided
    })
    .build();
```

This is useful when a flag has `defaultValue(true)` and users need to explicitly disable it.

Runtime API (from `CommandContext`):

- `boolean getFlag(String name)` — defaults to `false` if unknown.
- `boolean getFlag(String name, boolean defaultValue)` — explicit default.
- `boolean hasFlag(String name)` — whether the user provided it.
- `Map<String, Boolean> allFlags()` — snapshot of all flags.

#### Key-Value Options

Key‑values are typed pairs like `limit=25`, `mode=SAFE`, or `color=red` that can appear anywhere after the command label.

Declare key‑values:

```java
SlashCommand cmd = SlashCommand.create("search")
    .keyValueString("query")
    .keyValueInt("limit", 25)
    .keyValueBoolean("caseSensitive", false)
    .keyValueEnum("mode", Mode.class, Mode.SMART)
    .executes(ctx -> {
        String q = ctx.getKeyValueString("query");
        int limit = ctx.getKeyValueInt("limit", 25);
        boolean cs = ctx.getKeyValueBoolean("caseSensitive", false);
        Mode mode = ctx.getKeyValue("mode", Mode.class, Mode.SMART);
        // ...
    })
    .build();
```

Runtime API (from `CommandContext`):

- `T getKeyValue(String name, Class<T> type)` / `Optional<T> optionalKeyValue(...)`
- `T getKeyValue(String name, Class<T> type, T defaultValue)`
- Shorthands: `getKeyValueString`, `getKeyValueInt`, `getKeyValueBoolean`
- `boolean hasKeyValue(String name)`
- `Map<String, Object> allKeyValues()`

##### KeyValue Factory Methods

Create key-values quickly using factory methods:

```java
// Required string (must be provided)
KeyValue<String> name = KeyValue.ofString("name");

// Optional string with default
KeyValue<String> reason = KeyValue.ofString("reason", "No reason");

// Required integer
KeyValue<Integer> count = KeyValue.ofInt("count");

// Optional integer with default
KeyValue<Integer> limit = KeyValue.ofInt("limit", 10);

// Required long
KeyValue<Long> id = KeyValue.ofLong("id");

// Optional long with default
KeyValue<Long> timestamp = KeyValue.ofLong("timestamp", 0L);

// Required double
KeyValue<Double> price = KeyValue.ofDouble("price");

// Optional double with default
KeyValue<Double> rate = KeyValue.ofDouble("rate", 1.0);

// Required boolean
KeyValue<Boolean> enabled = KeyValue.ofBoolean("enabled");

// Optional boolean with default
KeyValue<Boolean> verbose = KeyValue.ofBoolean("verbose", false);

// Required enum
KeyValue<GameMode> mode = KeyValue.ofEnum("mode", GameMode.class);

// Optional enum with default
KeyValue<GameMode> gameMode = KeyValue.ofEnum("gamemode", GameMode.class, GameMode.SURVIVAL);
```

##### KeyValue.Builder

For full control, use the builder:

```java
KeyValue<Integer> timeout = KeyValue.builder("timeout", ArgParsers.intParser())
    .longForm("connection-timeout")  // Use --connection-timeout=30 in input
    .description("Connection timeout in seconds")
    .defaultValue(30)                // Default when not provided
    .required(false)                 // Make optional (implied by defaultValue)
    .permission("admin.timeout")     // Per-key-value permission
    .build();

// For multi-value support
KeyValue<String> tags = KeyValue.builder("tags", ArgParsers.stringParser())
    .description("Tags for the item")
    .multipleValues(true)            // Allow tags=pvp,survival,hardcore
    .valueSeparator(",")             // Separator (default is comma)
    .optional()                      // Shortcut for required(false)
    .build();
```

Builder options:

- `longForm(String)` — Key name used in input (default: same as name)
- `description(String)` — Human-readable description for help
- `defaultValue(T)` — Default value when not provided (implies optional)
- `required(boolean)` — Whether the key-value must be provided
- `optional()` — Shortcut for `required(false)`
- `multipleValues(boolean)` — Allow comma-separated values
- `valueSeparator(String)` — Separator for multiple values (default: ",")
- `permission(String)` — Permission required to use this key-value

##### Input Formats

Key-values support multiple input formats:

```
/command key=value
/command key:value
/command --key value
/command --key=value
```

For multi-value key-values:

```
/command tags=pvp,survival,hardcore
```

For quoted values with spaces:

```
/command reason="This is a long reason"
```

#### Multi-value collections

If your parser or command model introduces list‑style inputs (e.g., `tags=red,green,blue`) you can expose them through `getMultiValue(name)` and `getMultiValue(name, Class<T>)`. The `CommandContext` also offers `hasMultiValue` and `allMultiValues`.

Example with multi-value key-value:

```java
SlashCommand tag = SlashCommand.create("tag")
    .argPlayer("target")
    .keyValue(KeyValue.builder("tags", ArgParsers.stringParser())
        .multipleValues(true)
        .build())
    .executes((sender, ctx) -> {
        Player target = ctx.require("target", Player.class);
        List<String> tags = ctx.getMultiValue("tags");
        for (String t : tags) {
            tagService.addTag(target, t);
        }
        sender.sendMessage("Added " + tags.size() + " tags");
    })
    .build();
```
