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
- `requirePermission(String permission)` — Shortcut to add a permission-based guard.
- `requireAnyPermission(String... permissions)` — Require at least one of the specified permissions.
- `requireAllPermissions(String... permissions)` — Require all of the specified permissions.

##### Permission Shortcut Guards

```java
// Single permission
SlashCommand admin = SlashCommand.create("admin")
    .requirePermission("myplugin.admin")
    .executes(ctx -> { /* ... */ })
    .build();

// Any of multiple permissions (OR logic)
SlashCommand moderate = SlashCommand.create("moderate")
    .requireAnyPermission("myplugin.admin", "myplugin.moderator", "myplugin.helper")
    .executes(ctx -> { /* ... */ })
    .build();

// All permissions required (AND logic)
SlashCommand superadmin = SlashCommand.create("superadmin")
    .requireAllPermissions("myplugin.admin", "myplugin.superadmin")
    .executes(ctx -> { /* ... */ })
    .build();
```

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

#### Built-in Guard Factory Methods

Leviathan provides static factory methods for common guard patterns. All factory methods require a `MessageProvider` for localized error messages.

##### Permission Guard

Check if sender has a specific permission:

```java
MessageProvider messages = new DefaultMessageProvider();

SlashCommand admin = SlashCommand.create("admin")
    .require(Guard.permission("myplugin.admin", messages))
    .executes(ctx -> { /* ... */ })
    .build();
```

##### World Guard

Restrict command to a specific world:

```java
SlashCommand spawn = SlashCommand.create("spawn")
    .require(Guard.inWorld("world_spawn", messages))
    .executes(ctx -> {
        // Only works in world_spawn
    })
    .build();
```

##### GameMode Guard

Require a specific game mode:

```java
SlashCommand creative = SlashCommand.create("fly")
    .require(Guard.inGameMode(GameMode.CREATIVE, messages))
    .executes(ctx -> { /* ... */ })
    .build();
```

##### Operator Guard

Restrict to server operators:

```java
SlashCommand op = SlashCommand.create("opcommand")
    .require(Guard.opOnly(messages))
    .executes(ctx -> { /* ... */ })
    .build();
```

##### Level Guards

Check player experience level:

```java
// Minimum level required
SlashCommand enchant = SlashCommand.create("superenchant")
    .require(Guard.minLevel(30, messages))
    .executes(ctx -> { /* ... */ })
    .build();

// Level must be within range
SlashCommand midgame = SlashCommand.create("midgame")
    .require(Guard.levelRange(10, 50, messages))
    .executes(ctx -> { /* ... */ })
    .build();
```

##### Health Guard

Check player health:

```java
SlashCommand risky = SlashCommand.create("risky")
    .require(Guard.healthAbove(10.0, messages))  // Must have > 10 health
    .executes(ctx -> { /* ... */ })
    .build();
```

##### Food Level Guard

Check player hunger:

```java
SlashCommand run = SlashCommand.create("sprint")
    .require(Guard.foodLevelAbove(6, messages))  // Must have > 6 food
    .executes(ctx -> { /* ... */ })
    .build();
```

##### Flying Guard

Check if player is flying:

```java
SlashCommand aerial = SlashCommand.create("airstrike")
    .require(Guard.isFlying(messages))
    .executes(ctx -> { /* ... */ })
    .build();
```

##### Custom Predicate Guard

For any custom condition:

```java
SlashCommand day = SlashCommand.create("sunpower")
    .require(Guard.custom(
        sender -> {
            if (!(sender instanceof Player p)) return false;
            long time = p.getWorld().getTime();
            return time < 12000;  // Daytime
        },
        "This command only works during daytime!"
    ))
    .executes(ctx -> { /* ... */ })
    .build();
```

#### Combining Multiple Guards

Guards can be combined for complex requirements:

```java
SlashCommand elite = SlashCommand.create("elite")
    .permission("myplugin.elite")
    .require(
        Guard.inWorld("world_arena", messages),
        Guard.inGameMode(GameMode.SURVIVAL, messages),
        Guard.minLevel(50, messages),
        Guard.healthAbove(15.0, messages)
    )
    .executes(ctx -> {
        // Player must:
        // 1. Have myplugin.elite permission
        // 2. Be in world_arena
        // 3. Be in survival mode
        // 4. Have level >= 50
        // 5. Have health > 15
    })
    .build();
```

All guards are evaluated in order. The first failing guard stops execution and sends its error message to the sender.

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

#### Cross-Argument Validator Factory Methods

Leviathan provides static factory methods for common cross-argument validation patterns:

##### Mutually Exclusive Arguments

Ensure at most one of the specified arguments is provided:

```java
SlashCommand transfer = SlashCommand.create("transfer")
    .argInt("amount").optional(true)
    .flag("all", 'a', "all")
    .crossValidate(CrossArgumentValidator.mutuallyExclusive("amount", "all"))
    .executes(ctx -> { /* ... */ })
    .build();

// With custom error message
SlashCommand cmd = SlashCommand.create("cmd")
    .crossValidate(CrossArgumentValidator.mutuallyExclusive(
        "Cannot use both player and all at the same time!",
        "player", "all"))
    .build();
```

##### Require All Together

Ensure all specified arguments are provided together (if any is present, all must be):

```java
SlashCommand dateRange = SlashCommand.create("daterange")
    .argString("startDate").optional(true)
    .argString("endDate").optional(true)
    .crossValidate(CrossArgumentValidator.requiresAll("startDate", "endDate"))
    .executes(ctx -> { /* ... */ })
    .build();
```

##### Require At Least One

Ensure at least one of the specified arguments is provided:

```java
SlashCommand target = SlashCommand.create("target")
    .argPlayer("player").optional(true)
    .argString("coords").optional(true)
    .crossValidate(CrossArgumentValidator.requiresAny("player", "coords"))
    .executes(ctx -> { /* ... */ })
    .build();
```

##### Conditional Requirements

If a trigger argument is present, require other arguments:

```java
SlashCommand export = SlashCommand.create("export")
    .argString("output").optional(true)
    .argString("format").optional(true)
    .crossValidate(CrossArgumentValidator.requiresIfPresent("output", "format"))
    .executes(ctx -> { /* ... */ })
    .build();

// With custom message
SlashCommand cmd = SlashCommand.create("cmd")
    .crossValidate(CrossArgumentValidator.requiresIfPresent(
        "output",
        "When specifying output, you must also provide format",
        "format"))
    .build();
```

##### Range Validation

Ensure min is less than or equal to max:

```java
SlashCommand range = SlashCommand.create("range")
    .argInt("min")
    .argInt("max")
    .crossValidate(CrossArgumentValidator.range("min", "max"))
    .executes(ctx -> { /* ... */ })
    .build();

// With custom message
SlashCommand cmd = SlashCommand.create("cmd")
    .crossValidate(CrossArgumentValidator.range("min", "max",
        "Minimum value must not exceed maximum!"))
    .build();
```

##### Custom Comparison

Compare two arguments with a custom predicate:

```java
SlashCommand dates = SlashCommand.create("dates")
    .argString("start")
    .argString("end")
    .crossValidate(CrossArgumentValidator.comparing(
        "start", "end",
        (start, end) -> start.compareTo(end) <= 0,
        "Start date must be before end date",
        String.class))
    .executes(ctx -> { /* ... */ })
    .build();
```

##### Conditional Validation

Apply validation based on a custom condition:

```java
SlashCommand trade = SlashCommand.create("trade")
    .argInt("amount")
    .crossValidate(CrossArgumentValidator.conditionalRequires(
        ctx -> ctx.getFlag("premium"),
        "Premium members must provide a verification code",
        "verificationCode"))
    .executes(ctx -> { /* ... */ })
    .build();
```

##### Combining Validators

Combine multiple validators into one:

```java
SlashCommand complex = SlashCommand.create("complex")
    .crossValidate(CrossArgumentValidator.all(
        CrossArgumentValidator.mutuallyExclusive("player", "all"),
        CrossArgumentValidator.requiresAny("player", "all"),
        CrossArgumentValidator.range("min", "max")
    ))
    .executes(ctx -> { /* ... */ })
    .build();
```
