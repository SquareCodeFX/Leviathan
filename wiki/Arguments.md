### Arguments

Leviathan provides a rich, type‑safe argument system. You declare positional arguments in order using `SlashCommandBuilder` methods. Parsing, validation, and tab completion are integrated.

#### Built‑in argument helpers

- Numeric: `argInt`, `argLong`, `argDouble`, `argFloat`
- Text: `argString`
- Boolean: `argBoolean`
- UUID: `argUUID`
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
