### SlashCommand Overview

This page introduces the Leviathan SlashCommand system: the architecture, lifecycle, and the main classes you will use to build rich, type‑safe commands.

#### Key Types

- `SlashCommand` — Immutable, executable command with metadata (name, aliases, description, permission), arguments, flags, key‑values, guards, cooldowns, async execution, help/usage, and tab completion.
- `SlashCommandBuilder` — Fluent builder to construct and register a `SlashCommand`.
- `CommandContext` — Read‑only view of parsed inputs (positional arguments, flags, key‑values, multi‑values), and helpers to coerce types.
- `Arg`, `ArgumentParser`, `ArgContext` — Argument model and constraints/options for parsers.
- `Flag`, `KeyValue` — Declarative toggles and `key=value` pairs.
- `Guard` — Predicate‑based preconditions on the sender or environment.
- `ExceptionHandler`, `DetailedExceptionHandler` — Error handling and diagnostics.

#### Command Lifecycle

1. Build the command using `SlashCommandBuilder`.
2. Optionally attach subcommands, guards, cooldowns, flags/key‑values, help, and tab completion behaviors.
3. Provide an execution action via `executes(...)`, `executesAsync(...)`, or `async(true)` with handlers.
4. Register the command against your `JavaPlugin` using `command.register(plugin)`.
5. At runtime, user input is sanitized (optional), tokenized, parsed via registered parsers, validated, and then dispatched.
6. If parsing/guards/cooldowns fail, a structured error is returned via the `ExceptionHandler` and/or messages provider.

#### Minimal Example

```java
SlashCommand ping = SlashCommand.create("ping")
    .description("Simple latency check")
    .executes(ctx -> {
        CommandSender sender = ctx.get("sender", CommandSender.class);
        sender.sendMessage("Pong!");
    })
    .build();

ping.register(plugin);
```

#### Subcommands

Attach hierarchical subcommands to create powerful command trees:

```java
SlashCommand mathAdd = SlashCommand.create("add")
    .argInt("a").argInt("b")
    .executes(ctx -> {
        int a = ctx.get("a", Integer.class);
        int b = ctx.get("b", Integer.class);
        ctx.get("sender", CommandSender.class).sendMessage("= " + (a + b));
    })
    .build();

SlashCommand math = SlashCommand.create("math")
    .sub(mathAdd)
    .enableHelp(true)
    .build();

math.register(plugin);
```

See the rest of the wiki for the complete feature set.
