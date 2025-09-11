# Leviathan API (Spigot/Paper)

Leviathan is a lightweight API providing quality-of-life tweaks and helpful tools for latest Spigot/Paper. The first published module is the command API built around a fluent, type-safe command specification called `FluentCommand`.

Artifact Browser: https://repo.squarecode.de/#/releases/de/feelix/leviathan/Leviathan


## Installation

Repository (add to your build tool):
- id: repo-releases
- name: Reposilite Repository
- url: https://repo.squarecode.de/releases

Maven
```
<repository>
  <id>repo-releases</id>
  <name>Reposilite Repository</name>
  <url>https://repo.squarecode.de/releases</url>
</repository>

<dependency>
  <groupId>de.feelix.leviathan</groupId>
  <artifactId>Leviathan</artifactId>
  <version>1.0.1</version>
</dependency>
```

Gradle (Groovy DSL)
```
repositories {
  maven { url 'https://repo.squarecode.de/releases' }
}

dependencies {
  implementation 'de.feelix.leviathan:Leviathan:1.0.1'
}
```

Gradle (Kotlin DSL)
```
repositories {
  maven("https://repo.squarecode.de/releases")
}

dependencies {
  implementation("de.feelix.leviathan:Leviathan:1.0.1")
}
```


## Quick start

1) Declare your command(s) in `plugin.yml`.

```yml
commands:
  example:
    description: Example root command
    usage: /example
```

2) Build and register your command in `onEnable()` using `FluentCommand`.

```java
import de.feelix.leviathan.command.FluentCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class MyPlugin extends JavaPlugin {
  @Override
  public void onEnable() {
    FluentCommand.builder("example")
      .description("Sends a hello message")
      .executes((sender, ctx) -> sender.sendMessage("Hello from Leviathan!"))
      .register(this); // registers as executor and tab-completer
  }
}
```


## FluentCommand overview

FluentCommand lets you declare commands with strongly-typed arguments, per-argument permissions, optional and greedy trailing arguments, built-in tab completion, sub-commands, and asynchronous execution.

Builder entry point
- `FluentCommand.builder(String name)`

Command-wide options
- `description(String)` sets a human readable description.
- `permission(String)` gates the command by a Bukkit permission.
- `playerOnly(boolean)` restricts execution to players (not console).
- `sendErrors(boolean)` controls whether user-facing error messages are sent (parsing/permission/usage).
- `async(boolean)` runs your action off the main thread using `CompletableFuture`.
- `executesAsync(CommandAction)` shorthand to set the action and enable async in one call.
- `validateOnTab(boolean)` validates previously typed args while tab-completing and optionally informs the user.

Argument definitions (required by default)
- `argInt(String name)` → parses 32-bit integers.
- `argLong(String name)` → parses 64-bit integers.
- `argString(String name)` → parses a single token string.
- `argUUID(String name)` → parses a canonical UUID.
- `argGreedyString(String name)` → trailing string that captures the rest of the line (must be last).
- `argChoices(String name, Map<String,T> choices, String displayType)` → fixed set of aliases mapping to values; aliases are case-insensitive.
- `argCommandChoices(String name, Map<String,FluentCommand> choices)` → like `argChoices` but returns a `FluentCommand` value for manual dispatch.
- `argOneOf(String name, String displayType, ArgumentParser<? extends T>... parsers)` → accepts the first parser that succeeds; completions are merged.

Argument modifiers
- `optional()` marks the most recently added argument optional (all required args must come first).
- `argPermission(String permission)` sets a permission required to use/see the most recent argument. Hidden in tab-complete if missing.
- `arg(Arg<?>)` add a fully constructed argument (advanced).

Sub-commands
- `sub(FluentCommand... subs)` adds subcommands using each sub’s `getName()` as the alias.
- `sub(String alias, FluentCommand sub)` adds a subcommand under a custom alias. Subcommands are routed automatically: the first token selects the sub.

Action and registration
- `executes(CommandAction)` sets the action to run after successful parsing/validation.
- `build()` validates and returns the immutable command (rarely needed directly).
- `register(JavaPlugin)` (on the builder or on the command) registers as executor + tab-completer for the command declared in `plugin.yml`.

Usage string and errors
- Usage is auto-built as `<required> [optional] ...` and shown when args are too few/many.
- When `sendErrors(true)` (default), permission and parsing errors are sent to the user with type-aware messages, e.g. “Invalid value for 'amount' (expected int)”.

Tab-completion
- Respects command-level and per-argument permissions.
- Sub-commands are suggested at the first token.
- When `validateOnTab(true)`, previously typed tokens are parsed and errors are surfaced early; suggestions are hidden on invalid input.
- Greedy final arguments use the entire remaining string as the completion prefix.


## CommandContext API

Actions receive a `CommandContext` with typed accessors to your parsed values.
- `raw()` returns the original `String[]` argument tokens.
- `getOptional(name, type)` returns an `Optional<T>` if present and of the right type.
- `get(name, type)` returns `T` or `null`.
- `getOrThrow(name, type)` / `require(name, type)` throws if missing or of wrong type.
- `has(name)` checks presence.

### Functional argument retrieval (new)

In addition to the classic getters above, you can retrieve and map arguments using a functional style:
- `arg(String name, Function<OptionMapping,T> mapper)` — builds an `OptionMapping` for the named argument and applies your mapper to return a typed value.
- `OptionMapping` exposes: `name()`, `raw()`, `optionType()`, generic `getAs(Class<T>)`, and convenient typed getters `getAsString()`, `getAsInt()`, `getAsLong()`, `getAsUuid()`.
- `ArgumentMapper` provides static helpers so you can use method references like `ctx.arg("amount", ArgumentMapper::getAsInt)`.
- `OptionType` is a broad hint for the kind of value (INT, LONG, STRING, UUID, CHOICE, UNKNOWN). You can inspect it via `mapping.optionType()` if needed.

Example A — Basic typed retrieval with method references
```java
import de.feelix.leviathan.command.*;

FluentCommand.builder("pay")
  .description("Send currency to another player by UUID")
  .argUUID("to")
  .argInt("amount")
  .executes((sender, ctx) -> {
    java.util.UUID to = ctx.arg("to", ArgumentMapper::getAsUuid);
    int amount = ctx.arg("amount", ArgumentMapper::getAsInt);
    sender.sendMessage("Sending " + amount + " to " + to + "...");
  })
  .register(this);
```

Example B — Working with choices and custom types
```java
import de.feelix.leviathan.command.*;
import org.bukkit.GameMode;

java.util.Map<String, GameMode> gm = new java.util.HashMap<>();
// case-insensitive aliases
gm.put("0", GameMode.SURVIVAL); gm.put("survival", GameMode.SURVIVAL);
gm.put("1", GameMode.CREATIVE); gm.put("creative", GameMode.CREATIVE);

de.feelix.leviathan.command.FluentCommand.builder("gm")
  .argChoices("mode", gm, "gamemode") // stores a GameMode in the context
  .executes((sender, ctx) -> {
    // Use a lambda to specify the desired type explicitly
    GameMode mode = ctx.arg("mode", m -> m.getAs(GameMode.class));

    // Optionally, inspect the broad kind of the option
    OptionType kind = ctx.arg("mode", OptionMapping::optionType); // CHOICE for choices

    sender.sendMessage("Selected mode: " + mode + " (" + kind + ")");
  })
  .register(this);
```


## Exceptions (for reference)
- `CommandConfigurationException` → invalid builder/configuration (e.g., required after optional, duplicate argument names, greedy not last).
- `ParsingException` → invalid parser setup or parser contract violated.
- `ApiMisuseException` → misuse of the API at runtime (e.g., registering a subcommand directly).
- `CommandExecutionException` → your action threw an exception; optionally also sent to user if `sendErrors(true)`.


## plugin.yml sample
```yml
name: MyPlugin
main: com.example.MyPlugin
version: 1.0.0
api-version: '1.20'
commands:
  admin:
    description: Admin root command
    usage: /admin <subcommand>
```


## Advanced command examples (covering all features across examples)

Example 1 — Subcommands with per-argument permissions, optional and greedy args, validateOnTab
```java
import de.feelix.leviathan.command.*;
import de.feelix.leviathan.parser.ArgParsers;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class MyPlugin extends JavaPlugin {
  @Override public void onEnable() {
    FluentCommand ban = FluentCommand.builder("ban")
      .description("Ban a player with optional duration and reason")
      .argUUID("target")
      .argLong("durationSeconds").optional().argPermission("leviathan.ban.temp")
      .argGreedyString("reason").optional()
      .validateOnTab(true)
      .executes((sender, ctx) -> {
        // implement ban logic
      })
      .build();

    FluentCommand mute = FluentCommand.builder("mute")
      .argUUID("target")
      .argLong("durationSeconds").optional().argPermission("leviathan.mute.temp")
      .argGreedyString("reason").optional()
      .executes((sender, ctx) -> { /* implement mute */ })
      .build();

    FluentCommand admin = FluentCommand.builder("admin")
      .description("Admin tools")
      .permission("leviathan.admin")
      .sendErrors(true)
      .sub(ban, mute) // automatic routing on first token
      .executes((sender, ctx) -> sender.sendMessage("Use /admin <ban|mute> ..."))
      .build();

    admin.register(this);
  }
}
```

Example 2 — Choices and optional target (gamemode)
```java
import de.feelix.leviathan.command.*;
import org.bukkit.GameMode;

Map<String, GameMode> gm = new HashMap<>();
// case-insensitive aliases
gm.put("0", GameMode.SURVIVAL); gm.put("survival", GameMode.SURVIVAL);
gm.put("1", GameMode.CREATIVE); gm.put("creative", GameMode.CREATIVE);
gm.put("2", GameMode.ADVENTURE); gm.put("adventure", GameMode.ADVENTURE);
gm.put("3", GameMode.SPECTATOR); gm.put("spectator", GameMode.SPECTATOR);

FluentCommand.builder("gm")
  .permission("leviathan.gm")
  .argChoices("mode", gm, "gamemode")
  .argUUID("target").optional()
  .executes((sender, ctx) -> { /* set gm for self or target */ })
  .register(this);
```

Example 3 — Async command with greedy trailing message, player-only
```java
FluentCommand.builder("mail")
  .description("Send offline mail")
  .playerOnly(false)
  .argUUID("to")
  .argGreedyString("message") // captures the rest of the line
  .executesAsync((sender, ctx) -> {
    // heavy I/O (database) off the main thread
  })
  .register(this);
```

Example 4 — oneOf: accept UUID or long id; merged completions
```java
FluentCommand.builder("lookup")
  .description("Lookup by uuid or numeric id")
  .argOneOf("key", "uuid-or-id", ArgParsers.uuidParser(), ArgParsers.longParser())
  .executes((sender, ctx) -> {
    Object key = ctx.require("key", Object.class);
    if (key instanceof java.util.UUID uuid) {
      // handle uuid
    } else if (key instanceof Long id) {
      // handle id
    }
  })
  .register(this);
```

Example 5 — Command-choices dispatch (manual subcommand routing via parsed value)
```java
FluentCommand title = FluentCommand.builder("title")
  .argGreedyString("text").optional()
  .executes((sender, ctx) -> { /* send title */ })
  .build();

FluentCommand chat = FluentCommand.builder("chat")
  .argGreedyString("text").optional()
  .executes((sender, ctx) -> { /* broadcast chat */ })
  .build();

Map<String, FluentCommand> actions = new LinkedHashMap<>();
actions.put("title", title);
actions.put("chat", chat);

FluentCommand.builder("announce")
  .argCommandChoices("action", actions)
  .argGreedyString("payload").optional()
  .executes((sender, ctx) -> {
    FluentCommand sub = ctx.require("action", FluentCommand.class);
    // Dispatch remaining tokens (everything after the first token)
    String[] raw = ctx.raw();
    String[] remaining = java.util.Arrays.copyOfRange(raw, 1, raw.length);
    sub.execute(sender, sub.getName(), remaining);
  })
  .register(this);
```

Notes on features used across examples
- Subcommands via `sub(...)` with automatic routing.
- Choices with case-insensitive aliases.
- `oneOf` to accept multiple types.
- Greedy trailing argument capturing the rest of the input.
- Optional arguments and ordering rules (required first, then optional).
- Per-argument permissions via `argPermission(...)` (hidden in tab-complete when missing).
- Command-level permission and `playerOnly`.
- Async execution via `executesAsync`.
- Validation-aware tab completion via `validateOnTab(true)`.


## FastBoard Scoreboard API
We bundle and implement the excellent FastBoard API for scoreboards. For full usage and advanced documentation, please refer to the upstream project:
- FastBoard: https://github.com/MrMicky-FR/FastBoard

If you need more examples or edge cases, consult FastBoard’s README and examples there.


## EventBus — Application events

Leviathan ships a lightweight, thread-safe EventBus you can use to decouple components of your plugin or application.
It supports synchronous and asynchronous delivery, listener priorities, cancellation, and delivery to base/super interfaces.

Key features
- Simple object-oriented API with annotations
- Sync and async event processing
- Event priorities (HIGHEST, HIGH, NORMAL, LOW, LOWEST)
- Cancellation with opt-in delivery for already cancelled events
- Thread-safe registration, unregistration, and dispatch
- Dispatch to listeners of superclasses and interfaces of the event

Key types
- de.feelix.leviathan.event.Event — marker interface for all events
- de.feelix.leviathan.event.Cancellable — adds isCancelled()/setCancelled(boolean)
- de.feelix.leviathan.event.Listener — marker for listener instances
- de.feelix.leviathan.event.Subscribe — annotation for listener methods; supports priority, async, ignoreCancelled
- de.feelix.leviathan.event.EventPriority — ordering of handlers
- de.feelix.leviathan.event.EventBus — the bus itself (register/unregister/post/postAsync)
- de.feelix.leviathan.exceptions.EventBusException — wraps listener invocation errors

Quick start
```java
import de.feelix.leviathan.event.*;

// 1) Define an event
public final class UserLoginEvent implements Event { // or implements Cancellable if you need cancellation
  private final java.util.UUID userId;
  public UserLoginEvent(java.util.UUID userId) { this.userId = userId; }
  public java.util.UUID getUserId() { return userId; }
}

// 2) Create a listener
public final class AuthListener implements Listener {
  @Subscribe(priority = EventPriority.HIGHEST)
  public void onLogin(UserLoginEvent e) {
    // high-priority validation can cancel subsequent handlers if you use a Cancellable event
  }
}

// 3) Wire it up (e.g., in your plugin enable)
EventBus bus = new EventBus();
bus.register(new AuthListener());

// 4) Post an event (sync)
bus.post(new UserLoginEvent(java.util.UUID.randomUUID()));

// Or entirely async (ordered on the bus's executor)
bus.postAsync(new UserLoginEvent(java.util.UUID.randomUUID()))
   .thenAccept(evt -> {/* completed */});
```

Defining cancellable events
```java
public final class ChatMessageEvent implements Cancellable {
  private final String message;
  private boolean cancelled;
  public ChatMessageEvent(String message) { this.message = message; }
  public String getMessage() { return message; }
  @Override public boolean isCancelled() { return cancelled; }
  @Override public void setCancelled(boolean c) { this.cancelled = c; }
}

public final class ChatListener implements Listener {
  // Early moderation — can cancel to prevent later non-opt-in handlers
  @Subscribe(priority = EventPriority.HIGHEST)
  public void filter(ChatMessageEvent e) {
    if (e.getMessage().contains("forbidden")) e.setCancelled(true);
  }

  // This handler runs even if the event was cancelled earlier
  @Subscribe(ignoreCancelled = true)
  public void log(ChatMessageEvent e) {
    // logging still runs for cancelled events
  }
}
```

Registration and unregistration
```java
EventBus bus = new EventBus();
ChatListener listener = new ChatListener();
bus.register(listener);
// ... later
bus.unregister(listener);
```

Posting events
- post(E event): synchronous on the caller thread. Handlers marked async=true are executed on the bus executor without blocking the caller.
- postAsync(E event): schedules the entire listener chain on the bus executor and returns a CompletableFuture<E>. Handlers are invoked in priority order on that executor.

Priorities and ordering
- Priorities order is HIGHEST → HIGH → NORMAL → LOW → LOWEST.
- post(E) delivers handlers in priority order on the caller thread except those with async=true which are offloaded to the executor.
- postAsync(E) delivers the entire chain on the executor in priority order.

Cancellation semantics
- If an event implements Cancellable and isCancelled()==true, only handlers with @Subscribe(ignoreCancelled = true) will be invoked.
- With post(E): a handler that cancels the event will prevent subsequent synchronous handlers at lower or equal priorities from running. Already scheduled async=true handlers may still run because they were scheduled before cancellation took effect.
- With postAsync(E): cancellation deterministically affects later handlers because the whole chain is executed in a single ordered task on the executor.

Thread safety
- EventBus uses ConcurrentHashMap and CopyOnWriteArrayList; you can register/unregister and post from multiple threads safely.
- Listener methods themselves must be thread-safe if you use async delivery.

Event hierarchy delivery
- Listeners for a base event type will receive subclass events. Interfaces implemented by the event are also considered.

Custom executor and lifecycle
```java
java.util.concurrent.ExecutorService exec = java.util.concurrent.Executors.newFixedThreadPool(2);
EventBus bus = new EventBus(exec);
// ...
bus.shutdown(); // only call if you manage the executor lifecycle here
```

Validation and errors
- @Subscribe methods must be non-static and accept exactly one parameter that implements Event; violations throw ApiMisuseException.
- Listener invocation failures are wrapped in EventBusException with the original cause.
- Public/protected/private methods are supported; reflection is made accessible when possible.

Best practices
- Keep listener methods small and non-blocking; use async=true or postAsync for heavy work.
- Prefer postAsync when you need deterministic cancellation ordering with asynchronous execution.
- Document whether your events are cancellable and at which priorities consumers should act.
- Always unregister listeners you no longer need.

FAQ
- Q: Can one listener method handle multiple event types? A: No; one parameter means one event type per method. You can add multiple methods.
- Q: Do listeners for interfaces get called? A: Yes, interfaces implemented by the event are considered during dispatch.
- Q: How do I listen to all events? A: Create a base interface/class extending Event and have all events implement/extend it; register a listener for that type.

## Links
- Artifact Browser: https://repo.squarecode.de/#/releases/de/feelix/leviathan/Leviathan
- Repository (releases): https://repo.squarecode.de/releases
