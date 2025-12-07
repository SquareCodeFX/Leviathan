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

#### Multi-value collections

If your parser or command model introduces list‑style inputs (e.g., `tags=red,green,blue`) you can expose them through `getMultiValue(name)` and `getMultiValue(name, Class<T>)`. The `CommandContext` also offers `hasMultiValue` and `allMultiValues`.
