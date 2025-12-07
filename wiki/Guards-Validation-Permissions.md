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
