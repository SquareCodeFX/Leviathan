### Guards, Permissions, and Cross-Argument Validation

This page covers sender permissions, custom guards, and post-parse cross-argument validation.

#### Permissions

- Configure a required node via `permission(String)` on the builder.
- If the sender lacks permission, execution is aborted and an error is sent (when `sendErrors(true)` and/or message provider is present).

Example:

```java
SlashCommand ban = SlashCommand.create("ban")
    .permission("leviathan.admin.ban")
    .argPlayer("target")
    .argString("reason")
    .executes(ctx -> { /* ... */ })
    .build();
```

#### Guards

`Guard` is a functional interface used to enforce preconditions beyond permissions, e.g., sender type, world state, config toggles.

Builder helpers:

- `playersOnly()` — Shortcut ensuring the sender is a `Player`.
- `require(Class<? extends CommandSender> type)` — Ensure the sender is an instance of `type`. Provides a default message.
- `require(Guard... guards)` — Add custom guards.

Example custom guard:

```java
Guard onlyAtNight = new Guard() {
    @Override public boolean test(CommandSender sender) { return isNight(); }
    @Override public String errorMessage() { return "This command can be used only at night."; }
};

SlashCommand howl = SlashCommand.create("howl")
    .require(onlyAtNight)
    .executes(ctx -> { /* ... */ })
    .build();
```

#### Confirmation Requirement

For destructive or irreversible commands (e.g., delete, reset, ban), you can require the user to execute the command twice within a short timeout period to confirm their intention:

```java
SlashCommand deleteWorld = SlashCommand.create("deleteworld")
    .permission("leviathan.admin.deleteworld")
    .argWorld("world")
    .awaitConfirmation(true)
    .executes((sender, ctx) -> {
        World world = ctx.get("world", World.class);
        // Delete world...
        sender.sendMessage("World deleted: " + world.getName());
    })
    .build();
```

**How it works:**

1. When a user first executes the command, they receive a confirmation message
2. The command will not execute; instead, the system tracks a pending confirmation
3. If the user executes the exact same command again within 10 seconds, the command executes normally
4. If the timeout expires, the user must start over

**Use cases:**
- Destructive operations (delete, reset, purge)
- Irreversible actions (ban, unban, promotion/demotion)
- High-impact commands (server restart, economy reset)

The confirmation message can be customized via the `MessageProvider.awaitConfirmation()` method.

#### Cross-Argument Validation

Use `addCrossArgumentValidator(CrossArgumentValidator)` to validate relationships between parsed arguments after parsing succeeds but before execution.

Typical use cases:

- `min <= max`
- `start.before(end)`
- Mutually exclusive options

Example:

```java
SlashCommand range = SlashCommand.create("range")
    .argInt("min")
    .argInt("max")
    .addCrossArgumentValidator(ctx -> {
        int min = ctx.get("min", Integer.class);
        int max = ctx.get("max", Integer.class);
        if (min > max) throw new IllegalArgumentException("min must be <= max");
    })
    .executes(ctx -> { /* ... */ })
    .build();
```

If a validator throws, the `ExceptionHandler` transforms it into a user-facing error.
