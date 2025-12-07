### Async Execution and Cooldowns

This page explains how to run commands asynchronously and how to throttle them with per‑user or global cooldowns.

#### Async execution models

There are three ways to execute work asynchronously:

1. `async(true)` — Marks the command as asynchronous. Use with `executes(...)` to run that action off the main thread.
2. `executesAsync(CommandAction action)` — Convenience that runs the same `CommandAction` asynchronously.
3. `executesAsync(AsyncCommandAction action)` — Advanced API supporting progress and cancellation tokens, with optional timeout.

With timeout:

```java
SlashCommand heavy = SlashCommand.create("heavy")
    .async(true)
    .executesAsync((ctx, progress, cancellation) -> {
        // periodically check cancellation.isCancelled()
        // report progress.set(percent) if desired
        doHeavyWork();
    }, 15_000L) // 15 seconds timeout
    .build();
```

Notes:

- Use async when your action performs blocking I/O or heavy CPU. Avoid blocking the main server thread.
- Respect cancellation to avoid wasting resources if the command is abandoned.

#### Cooldowns

Leviathan can throttle command executions to prevent spam.

- `perUserCooldown(long cooldownMillis)` — Enforces a per‑sender delay between successful executions.
- `perServerCooldown(long cooldownMillis)` — Global delay affecting all senders.

Example:

```java
SlashCommand locate = SlashCommand.create("locate")
    .perUserCooldown(5_000)  // 5s per user
    .perServerCooldown(1_000) // 1s globally
    .executes(ctx -> { /* ... */ })
    .build();
```

Behavior:

- When a cooldown is active, the command does not execute and the user receives an error via the message provider/exception handler.
- Cooldowns are tracked independently for per‑user and per‑server layers.
