### Execution Hooks

This page covers the execution hook system for intercepting command execution at various stages.

#### Overview

Execution hooks allow you to run code before and after command execution. Use cases include:

- Logging and auditing
- Additional validation
- Metrics collection
- Resource management
- Transaction-like behavior

#### Before Hooks

Before hooks execute before the command action runs. They can abort execution if needed.

##### Basic Usage

```java
SlashCommand cmd = SlashCommand.create("admin")
    .beforeExecution((sender, ctx) -> {
        logger.info("Admin command started by " + sender.getName());
        return ExecutionHook.BeforeResult.proceed();
    })
    .executes((sender, ctx) -> {
        // Command logic
    })
    .build();
```

##### Aborting Execution

```java
SlashCommand cmd = SlashCommand.create("dangerous")
    .beforeExecution((sender, ctx) -> {
        if (maintenanceMode) {
            return ExecutionHook.BeforeResult.abort("Server is in maintenance mode");
        }
        if (!hasSecondaryAuth(sender)) {
            return ExecutionHook.BeforeResult.abort("Two-factor authentication required");
        }
        return ExecutionHook.BeforeResult.proceed();
    })
    .executes((sender, ctx) -> {
        // Only runs if all before hooks pass
    })
    .build();
```

##### Simple Action Hook

For hooks that don't need to abort:

```java
SlashCommand cmd = SlashCommand.create("example")
    .beforeExecution((sender, ctx) -> {
        // Simple logging - always proceeds
        metrics.recordCommandStart(cmd.name());
    })
    .executes((sender, ctx) -> { /* ... */ })
    .build();
```

##### Validation Hook

```java
SlashCommand cmd = SlashCommand.create("trade")
    .beforeExecution(ExecutionHook.Before.validating(
        (sender, ctx) -> {
            Player p = (Player) sender;
            return p.getInventory().firstEmpty() != -1;
        },
        "Your inventory is full!"
    ))
    .executes((sender, ctx) -> {
        // Trade logic
    })
    .build();
```

#### After Hooks

After hooks execute after the command completes, regardless of success or failure.

##### Basic Usage

```java
SlashCommand cmd = SlashCommand.create("example")
    .afterExecution((sender, ctx, result) -> {
        if (result.isSuccess()) {
            logger.info("Command completed in " + result.executionTimeMillis() + "ms");
        } else {
            logger.warning("Command failed: " + result.exception());
        }
    })
    .executes((sender, ctx) -> { /* ... */ })
    .build();
```

##### Success Hook

Only runs when the command succeeds:

```java
SlashCommand cmd = SlashCommand.create("purchase")
    .onSuccess((sender, ctx) -> {
        Player p = (Player) sender;
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1, 1);
    })
    .executes((sender, ctx) -> {
        // Purchase logic
    })
    .build();
```

##### Failure Hook

Only runs when the command fails:

```java
SlashCommand cmd = SlashCommand.create("risky")
    .onFailure((sender, ctx, error) -> {
        logger.severe("Command failed for " + sender.getName() + ": " + error.getMessage());
        errorTracker.report(error);
    })
    .executes((sender, ctx) -> {
        // Risky logic that might throw
    })
    .build();
```

#### AfterContext

The `AfterContext` provides information about execution:

```java
.afterExecution((sender, ctx, result) -> {
    // Check success/failure
    if (result.isSuccess()) {
        // ...
    }
    if (result.isFailure()) {
        // ...
    }

    // Get exception if failed
    Throwable error = result.exception();  // null if successful

    // Get execution time
    long timeMs = result.executionTimeMillis();
})
```

#### Multiple Hooks

Multiple hooks can be chained and execute in order:

```java
SlashCommand cmd = SlashCommand.create("complex")
    // Before hooks - all must pass
    .beforeExecution((sender, ctx) -> {
        logger.info("Hook 1: Checking permissions...");
        return ExecutionHook.BeforeResult.proceed();
    })
    .beforeExecution((sender, ctx) -> {
        logger.info("Hook 2: Validating input...");
        return ExecutionHook.BeforeResult.proceed();
    })
    .beforeExecution((sender, ctx) -> {
        logger.info("Hook 3: Acquiring resources...");
        return ExecutionHook.BeforeResult.proceed();
    })

    // Command action
    .executes((sender, ctx) -> {
        // Main logic
    })

    // After hooks - all execute regardless of success
    .afterExecution((sender, ctx, result) -> {
        logger.info("Hook A: Releasing resources...");
    })
    .afterExecution((sender, ctx, result) -> {
        logger.info("Hook B: Recording metrics...");
    })
    .build();
```

#### Practical Examples

##### Audit Logging

```java
SlashCommand admin = SlashCommand.create("admin")
    .beforeExecution((sender, ctx) -> {
        auditLog.record(
            sender.getName(),
            "admin",
            ctx.getAll(),
            System.currentTimeMillis()
        );
        return ExecutionHook.BeforeResult.proceed();
    })
    .afterExecution((sender, ctx, result) -> {
        auditLog.recordResult(
            sender.getName(),
            "admin",
            result.isSuccess(),
            result.executionTimeMillis()
        );
    })
    .executes((sender, ctx) -> { /* ... */ })
    .build();
```

##### Rate Limiting (Custom)

```java
Map<UUID, Long> lastExecution = new ConcurrentHashMap<>();

SlashCommand limited = SlashCommand.create("limited")
    .beforeExecution((sender, ctx) -> {
        if (!(sender instanceof Player)) {
            return ExecutionHook.BeforeResult.proceed();
        }
        UUID uuid = ((Player) sender).getUniqueId();
        Long last = lastExecution.get(uuid);
        if (last != null && System.currentTimeMillis() - last < 60000) {
            return ExecutionHook.BeforeResult.abort("Please wait before using this again");
        }
        return ExecutionHook.BeforeResult.proceed();
    })
    .onSuccess((sender, ctx) -> {
        if (sender instanceof Player) {
            lastExecution.put(((Player) sender).getUniqueId(), System.currentTimeMillis());
        }
    })
    .executes((sender, ctx) -> { /* ... */ })
    .build();
```

##### Transaction-Like Behavior

```java
SlashCommand transfer = SlashCommand.create("transfer")
    .beforeExecution((sender, ctx) -> {
        // Acquire lock
        Player from = (Player) sender;
        Player to = ctx.require("target", Player.class);
        if (!economyLock.tryAcquire(from, to)) {
            return ExecutionHook.BeforeResult.abort("Transaction in progress, please wait");
        }
        return ExecutionHook.BeforeResult.proceed();
    })
    .afterExecution((sender, ctx, result) -> {
        // Always release lock
        Player from = (Player) sender;
        ctx.optional("target", Player.class).ifPresent(to -> {
            economyLock.release(from, to);
        });
    })
    .executes((sender, ctx) -> {
        // Transfer money - lock is held
    })
    .build();
```

##### Metrics Collection

```java
SlashCommand metered = SlashCommand.create("metered")
    .beforeExecution((sender, ctx) -> {
        metrics.incrementCommandCount("metered");
    })
    .afterExecution((sender, ctx, result) -> {
        metrics.recordLatency("metered", result.executionTimeMillis());
        if (result.isFailure()) {
            metrics.incrementErrorCount("metered");
        }
    })
    .executes((sender, ctx) -> { /* ... */ })
    .build();
```

#### Best Practices

1. **Keep hooks lightweight** - Don't do expensive operations in hooks
2. **Handle exceptions** - Wrap hook code in try-catch to avoid breaking execution
3. **Order matters** - Before hooks run in registration order
4. **After hooks always run** - Use for cleanup that must happen
5. **Use typed hooks** - Use `onSuccess`/`onFailure` for cleaner code
6. **Log failures** - Before hooks that abort should log why
