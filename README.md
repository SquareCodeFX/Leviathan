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
  <version>1.0.5</version>
</dependency>
```

Gradle (Groovy DSL)
```
repositories {
  maven { url 'https://repo.squarecode.de/releases' }
}

dependencies {
  implementation 'de.feelix.leviathan:Leviathan:1.0.5'
}
```

Gradle (Kotlin DSL)
```
repositories {
  maven("https://repo.squarecode.de/releases")
}

dependencies {
  implementation("de.feelix.leviathan:Leviathan:1.0.5")
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

## Per-argument tab completions (new)

You can configure tab suggestions per argument. There are three sources of suggestions, used in this order of precedence:
- Dynamic provider (if set for the current argument)
- Static list registered for the argument
- The argument’s parser’s built-in completion

Builder helpers (for the most recently added argument)
- `completions(String...)` / `completions(Collection<String>)` — set/replace static suggestions
- `completionProvider(CompletionProvider)` — supply a dynamic provider lambda

Target a specific argument index (0-based)
- `completionsForArg(int index, Collection<String>)`
- `completionProviderForArg(int index, CompletionProvider)`

Runtime (after build) mutability on FluentCommand
- `setCompletions(int index, Collection<String>)`
- `addCompletion(int index, String)` / `addCompletions(int index, Collection<String>)`
- `removeCompletion(int index, String)` / `clearCompletions(int index)` / `getCompletions(int index)`
- `setCompletionProvider(int index, CompletionProvider)` / `clearCompletionProvider(int index)`

Notes
- When `validateOnTab(true)`, previously typed args must parse successfully; otherwise suggestions are hidden and an error may be shown (if `sendErrors(true)`).
- If the current or prior arguments require permissions the sender lacks, suggestions are hidden.
- For a greedy final argument, the completion prefix is the entire remaining substring (including spaces), not just the last token.

Example A — Static suggestions for the latest argument
```java
FluentCommand.builder("kit")
  .argString("name").completions("starter", "pvp", "archer")
  .executes((sender, ctx) -> sender.sendMessage("Selected kit: " + ctx.arg("name", ArgumentMapper::getAsString)))
  .register(this);
```

Example B — Dynamic provider (e.g., online player names)
```java
FluentCommand.builder("msg")
  .argString("target")
  .completionProvider((sender, alias, args, prefix) ->
    org.bukkit.Bukkit.getOnlinePlayers().stream()
      .map(p -> p.getName())
      .filter(n -> n.toLowerCase(java.util.Locale.ROOT).startsWith(prefix.toLowerCase(java.util.Locale.ROOT)))
      .sorted()
      .collect(java.util.stream.Collectors.toList())
  )
  .argGreedyString("message").optional()
  .executes((sender, ctx) -> { /* send private message */ })
  .register(this);
```

Example C — Target a specific argument index
```java
FluentCommand.builder("warp")
  .argString("subcommand")
  .argString("name")
  // Index 1 refers to the second argument ("name")
  .completionsForArg(1, java.util.List.of("spawn", "market", "arena"))
  .executes((sender, ctx) -> { /* handle warp */ })
  .register(this);
```

Example D — Change completions at runtime (e.g., on /reload)
```java
FluentCommand cmd = FluentCommand.builder("kit")
  .argString("name")
  .build();
cmd.register(this);

// Later, when your config changes:
java.util.List<String> kits = loadKitsFromConfig();
cmd.setCompletions(0, kits); // 0 = first argument ("name")

// Swap to a dynamic provider at runtime
cmd.setCompletionProvider(0, (sender, alias, args, prefix) -> kits.stream()
  .filter(n -> n.toLowerCase(java.util.Locale.ROOT).startsWith(prefix.toLowerCase(java.util.Locale.ROOT)))
  .sorted()
  .toList());
```


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



## Inventory UI API — Examples and docs

This section documents the new fluent Inventory API and shows examples for every function. It targets Spigot/Paper 1.20+ and focuses on safe defaults, clear contracts, and easy composition.

Packages
- de.feelix.leviathan.inventory — core types (FluentInventory, InventoryManager, ItemButton, Fillers, Slots)
- de.feelix.leviathan.inventory.click — click handling (ClickAction, ClickContext)
- de.feelix.leviathan.inventory.pagination — pagination (Paginator)
- de.feelix.leviathan.inventory.prompt — prompts (AnvilPrompt, SignPrompt)

Prerequisites
- Initialize the InventoryManager in onEnable so events can be routed to your UIs.

```java
import de.feelix.leviathan.inventory.InventoryManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class MyPlugin extends JavaPlugin {
  @Override public void onEnable() {
    InventoryManager.init(this); // must be called once
  }
}
```

### InventoryManager

Functions covered
- InventoryManager.init(Plugin)
- InventoryManager.get()
- InventoryManager.plugin()

Example — Access the plugin back from the manager when needed
```java
import de.feelix.leviathan.inventory.InventoryManager;

var manager = InventoryManager.get();
org.bukkit.plugin.Plugin plugin = manager.plugin();
// You can use 'plugin' to schedule tasks, register listeners, etc.
```

### FluentInventory — build UIs fluently

Factory methods
- FluentInventory.ofRows(int rows, String title)
- FluentInventory.ofSize(int size, String title)

Configuration and accessors
- title(String)
- cancelUnhandledClicks(boolean)
- getInventory()
- getSize()
- getTitle()
- onClose(Consumer<Player>)

Buttons and contents
- set(int slot, ItemButton button)
- set(int slot, ItemStack item, ClickAction action)
- getButton(int slot)
- clear()
- fill(ItemStack)
- border(ItemStack)

Open
- open(Player)

Coordinate helpers
- slot(int row1Based, int col1Based)
- row(int slot)
- col(int slot)

Example — Basic menu with border, a button, and a close callback
```java
import de.feelix.leviathan.inventory.*;
import de.feelix.leviathan.inventory.click.*;
import de.feelix.leviathan.itemstack.ItemStackBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public void openMenu(Player player) {
  var gray = ItemStackBuilder.create(Material.GRAY_STAINED_GLASS_PANE)
      .setName(" ")
      .setMeta()
      .build();

  var diamond = ItemStackBuilder.create(Material.DIAMOND)
      .setName("&bClick me!")
      .setMeta()
      .build();

  FluentInventory inv = FluentInventory.ofRows(3, "&aDemo Menu")
      .border(gray) // Draws a border using Fillers.border
      .set(FluentInventory.slot(2,5), diamond, ctx -> {
        ctx.player().sendMessage("You clicked a diamond in slot " + ctx.slot());
      })
      .onClose(p -> p.sendMessage("Menu closed."));

  inv.open(player);
}
```

Example — Using ofSize, title change, cancelUnhandledClicks, clear and getButton
```java
FluentInventory inv = FluentInventory.ofSize(27, "&eTitle A")
    .title("&eTitle B")                // prepares a new title (apply by reopen if needed)
    .cancelUnhandledClicks(true);      // default is true; protects UI from item movement

// Place a button using the ItemButton API
var emerald = de.feelix.leviathan.itemstack.ItemStackBuilder.create(Material.EMERALD)
    .setName("&aBuy")
    .setMeta().build();
ItemButton buy = ItemButton.of(emerald, ctx -> ctx.player().sendMessage("Buying..."));
inv.set(13, buy);

// Inspect or modify a placed button
ItemButton b = inv.getButton(13); // may be null if none
if (b != null) {
  b.setItem(de.feelix.leviathan.itemstack.ItemStackBuilder.create(Material.EMERALD)
      .setName("&aBuy (x2)").setMeta().build());
}

// Clear all buttons and visuals
inv.clear();
```

Example — fill() and border() helpers
```java
var filler = de.feelix.leviathan.itemstack.ItemStackBuilder.create(Material.BLACK_STAINED_GLASS_PANE)
    .setName(" ").setMeta().build();
FluentInventory inv = FluentInventory.ofRows(6, "&8Grid").fill(filler); // Fillers.fill under the hood

var border = de.feelix.leviathan.itemstack.ItemStackBuilder.create(Material.RED_STAINED_GLASS_PANE)
    .setName(" ").setMeta().build();
inv.border(border); // Draws only the outer frame
```

Example — Allow taking/placing items on unhandled clicks
```java
FluentInventory inv = FluentInventory.ofRows(3, "&7Sandbox")
    .cancelUnhandledClicks(false); // let players move items when clicking non-button areas
```

Example — Coordinate helpers and mapping
```java
int center = FluentInventory.slot(3,5); // 1-based row/col -> 0-based slot
int row = FluentInventory.row(center);  // -> 3
int col = FluentInventory.col(center);  // -> 5
```

### ItemButton — interactive slots

Functions covered
- ItemButton.of(ItemStack, ClickAction)
- visibleWhen(Predicate<ClickContext>)
- cancelClick(boolean)
- getItem()/setItem(ItemStack)
- getAction()/setAction(ClickAction)

Example — Conditional visibility and allow shift-click
```java
import de.feelix.leviathan.inventory.ItemButton;
import de.feelix.leviathan.inventory.click.ClickContext;
import org.bukkit.Material;

var adminStar = de.feelix.leviathan.itemstack.ItemStackBuilder.create(Material.NETHER_STAR)
    .setName("&cAdmin").setMeta().build();

ItemButton adminButton = ItemButton.of(adminStar, ctx -> ctx.player().performCommand("admin"))
    .visibleWhen(ctx -> ctx.player().hasPermission("myplugin.admin"))
    .cancelClick(false); // let shift-click or pick up if you want to allow movement
```

### ClickAction and ClickContext

Functions covered
- ClickAction.handle(ClickContext)
- ClickContext.player()/event()/slot()/clickedItem()/clickType()

Example — Use click type and event for advanced logic
```java
import de.feelix.leviathan.inventory.click.*;
import org.bukkit.event.inventory.ClickType;

ClickAction action = ctx -> {
  if (ctx.clickType() == ClickType.RIGHT) {
    ctx.player().sendMessage("Right-clicked on " + (ctx.clickedItem() == null ? "empty" : ctx.clickedItem().getType()));
  }
  // Access the underlying InventoryClickEvent if needed
  var event = ctx.event();
  // Example: prevent number-key hotbar swaps specifically
  if (event.getClick() == ClickType.NUMBER_KEY) event.setCancelled(true);
};
```

### Fillers — programmatic fill/border on any Inventory

Functions covered
- Fillers.fill(Inventory, ItemStack)
- Fillers.border(Inventory, ItemStack)

Example
```java
import de.feelix.leviathan.inventory.Fillers;
import org.bukkit.inventory.Inventory;

Inventory any = org.bukkit.Bukkit.createInventory(null, 54);
var glass = de.feelix.leviathan.itemstack.ItemStackBuilder.create(org.bukkit.Material.LIGHT_BLUE_STAINED_GLASS_PANE)
    .setName(" ").setMeta().build();
Fillers.fill(any, glass);

var frame = de.feelix.leviathan.itemstack.ItemStackBuilder.create(org.bukkit.Material.BLUE_STAINED_GLASS_PANE)
    .setName(" ").setMeta().build();
Fillers.border(any, frame);
```

### Slots — compute common slot arrays

Functions covered
- Slots.all(int rows)
- Slots.range(int startInclusive, int endInclusive)
- Slots.rect(int rowStart1, int colStart1, int rowEnd1, int colEnd1)
- Slots.inside(int rows)

Example
```java
import de.feelix.leviathan.inventory.Slots;

int[] every = Slots.all(6);                 // 0..53
int[] topRow = Slots.range(0, 8);           // first 9 slots
int[] middleRect = Slots.rect(3, 3, 4, 7);  // 1-based rows/cols, inclusive
int[] inner = Slots.inside(6);              // area excluding the border
```

### Paginator — laying out pages of buttons

Functions covered
- new Paginator(List<ItemButton> items, int... slots)
- pageCount()
- render(FluentInventory, int pageIndex)
- slots()

Example — Paged list with next/prev controls
```java
import de.feelix.leviathan.inventory.*;
import de.feelix.leviathan.inventory.pagination.Paginator;
import de.feelix.leviathan.itemstack.ItemStackBuilder;
import org.bukkit.Material;

public final class PagedMenu {
  private int page = 0;

  public void open(org.bukkit.entity.Player player, java.util.List<org.bukkit.inventory.ItemStack> entries) {
    FluentInventory inv = FluentInventory.ofRows(6, "&aItems");

    // Build item buttons for each entry
    java.util.List<ItemButton> buttons = new java.util.ArrayList<>();
    for (org.bukkit.inventory.ItemStack it : entries) {
      buttons.add(ItemButton.of(it, ctx -> ctx.player().sendMessage("You clicked " + it.getType())));
    }

    int[] area = Slots.inside(6); // 4 rows x 7 cols
    Paginator paginator = new Paginator(buttons, area);

    // Navigation buttons (left/right of the bottom center)
    var prev = ItemStackBuilder.create(Material.ARROW).setName("&7Prev").setMeta().build();
    var next = ItemStackBuilder.create(Material.ARROW).setName("&7Next").setMeta().build();

    inv.set(FluentInventory.slot(6, 4), prev, ctx -> {
      page = Math.max(0, page - 1);
      paginator.render(inv, page);
    });
    inv.set(FluentInventory.slot(6, 6), next, ctx -> {
      page = Math.min(paginator.pageCount() - 1, page + 1);
      paginator.render(inv, page);
    });

    // Initial render
    paginator.render(inv, page);

    inv.open(player);
  }
}
```

You can also inspect paginator.slots() to see the page area indices if needed.

### Prompts — collect text from players

AnvilPrompt.open(Player, String title, String initialText, Consumer<String> onComplete, Runnable onCancel)
- Opens an anvil rename UI; when the player takes the result, onComplete is called with the entered text.
- If anvil UIs are not supported on your server, it falls back to SignPrompt (chat).

SignPrompt.open(Player, String title, Consumer<String> onComplete, Runnable onCancel, long timeoutTicks)
- Asks the player to type in chat; times out after timeoutTicks.

Example
```java
import de.feelix.leviathan.inventory.prompt.*;
import org.bukkit.entity.Player;

public void askName(Player p) {
  AnvilPrompt.open(p, "&eEnter your nickname", "Steve", text -> {
    p.sendMessage("You entered: " + text);
  }, () -> p.sendMessage("Prompt cancelled"));
}

public void askChat(Player p) {
  SignPrompt.open(p, "&eSay something", msg -> {
    p.sendMessage("You said: " + msg);
  }, () -> p.sendMessage("No response."), 20L * 30); // 30s timeout
}
```

### Putting it together — full menu sample covering most functions
```java
import de.feelix.leviathan.inventory.*;
import de.feelix.leviathan.inventory.click.*;
import de.feelix.leviathan.inventory.pagination.Paginator;
import de.feelix.leviathan.inventory.prompt.*;
import de.feelix.leviathan.itemstack.ItemStackBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public final class ProfileMenu {
  private int page;

  public void open(Player player) {
    InventoryManager.init(org.bukkit.Bukkit.getPluginManager().getPlugin("MyPlugin")); // safe no-op if already initialized

    FluentInventory inv = FluentInventory.ofRows(6, "&bProfile of &f" + player.getName())
        .cancelUnhandledClicks(true)
        .onClose(p -> p.sendMessage("Closed profile."));

    // Frame and sections
    var frame = ItemStackBuilder.create(Material.GRAY_STAINED_GLASS_PANE).setName(" ").setMeta().build();
    inv.border(frame);

    // Buttons
    var rename = ItemStackBuilder.create(Material.NAME_TAG).setName("&eRename").setMeta().build();
    inv.set(FluentInventory.slot(2, 2), rename, ctx ->
        AnvilPrompt.open(ctx.player(), "&eNew name", player.getName(), text -> ctx.player().sendMessage("Set name to: " + text),
            () -> ctx.player().sendMessage("Rename cancelled"))
    );

    var toggle = ItemStackBuilder.create(Material.LEVER).setName("&aToggle setting").setMeta().build();
    ItemButton toggleBtn = ItemButton.of(toggle, ctx -> ctx.player().sendMessage("Toggled!"))
        .visibleWhen(c -> c.player().hasPermission("profile.toggle"));
    inv.set(FluentInventory.slot(2, 8), toggleBtn);

    // Paged achievements in the inner area
    java.util.List<ItemButton> achievements = new java.util.ArrayList<>();
    for (int i = 1; i <= 50; i++) {
      var book = ItemStackBuilder.create(Material.BOOK).setName("&7Achievement #" + i).setMeta().build();
      achievements.add(ItemButton.of(book, ctx -> ctx.player().sendMessage("Viewing #" + i)));
    }
    Paginator paginator = new Paginator(achievements, Slots.inside(6));

    var prev = ItemStackBuilder.create(Material.ARROW).setName("&7Prev").setMeta().build();
    var next = ItemStackBuilder.create(Material.ARROW).setName("&7Next").setMeta().build();
    inv.set(FluentInventory.slot(6, 4), prev, ctx -> { page = Math.max(0, page - 1); paginator.render(inv, page); });
    inv.set(FluentInventory.slot(6, 6), next, ctx -> { page = Math.min(paginator.pageCount() - 1, page + 1); paginator.render(inv, page); });

    paginator.render(inv, page);
    inv.open(player);
  }
}
```

Notes and contracts
- All public methods annotated with @NotNull/@Nullable follow those contracts; passing null to a @NotNull parameter throws ApiMisuseException via Preconditions.
- Sizes must be multiples of 9 between 9 and 54; rows are 1..6.
- Dragging inside API-managed inventories is cancelled by default to protect layout.
- ItemButton.cancelClick(true) prevents item movement on that slot; set false to permit normal behavior.
- cancelUnhandledClicks(false) allows clicks in non-button areas to move items (bottom inventory always allowed unless you cancel explicitly in your handlers).
- InventoryException is a generic runtime exception used by the inventory subsystem for error signaling.



---

## FileAPI (Config Files)

Unified reading and writing of YAML, JSON, TOML and .properties with simple caching and typed accessors. This section documents all functions and provides five end-to-end examples.

Key features
- Supported formats: .yml/.yaml, .json, .toml, .properties (chosen by file extension)
- Typed getters and setters: String, int, long, float, double, boolean, byte, List<Object>, List<String>
- getOrDefault and getOrSet variants for all types
- Per-key comments, file header and footer comments (YAML/TOML/PROPERTIES; JSON ignores comments)
- Caching to avoid redundant file reads; auto-reloads on timestamp change; manual invalidate/clear
- Immediate persistence: all set* and comment/header/footer calls save the file right away
- Flat (top-level) keys only

Packages and classes
- de.feelix.leviathan.file.FileAPI: entrypoint with cache management and format detection
- de.feelix.leviathan.file.ConfigFile: typed access, comments, header/footer, reload/save

Limitations
- Only top-level keys are modeled. Nested structures are not supported by this abstraction.
- JSON does not preserve comments; comment methods are no-ops for JSON output.

### Quick reference: APIs and all functions

FileAPI
- open(File file): ConfigFile — detects format by extension
- invalidate(File file): void — remove one file from cache
- clearCache(): void — clear all cached files

ConfigFile (typed getters)
- getString(String key): String
- getInt(String key): Integer
- getLong(String key): Long
- getFloat(String key): Float
- getDouble(String key): Double
- getBoolean(String key): Boolean
- getByte(String key): Byte
- getList(String key): List<Object>
- getStringList(String key): List<String>

ConfigFile (getOrDefault)
- getStringOrDefault(String key, String def): String
- getIntOrDefault(String key, int def): int
- getLongOrDefault(String key, long def): long
- getFloatOrDefault(String key, float def): float
- getDoubleOrDefault(String key, double def): double
- getBooleanOrDefault(String key, boolean def): boolean
- getByteOrDefault(String key, byte def): byte
- getListOrDefault(String key, List<Object> def): List<Object>
- getStringListOrDefault(String key, List<String> def): List<String>

ConfigFile (getOrSet — sets alt if the key is missing and returns it)
- getStringOrSet(String key, String alt): String
- getIntOrSet(String key, int alt): int
- getLongOrSet(String key, long alt): long
- getFloatOrSet(String key, float alt): float
- getDoubleOrSet(String key, double alt): double
- getBooleanOrSet(String key, boolean alt): boolean
- getByteOrSet(String key, byte alt): byte
- getListOrSet(String key, List<Object> alt): List<Object>
- getStringListOrSet(String key, List<String> alt): List<String>

ConfigFile (setters; each call saves immediately)
- setString(String key, String value): void
- setInt(String key, int value): void
- setLong(String key, long value): void
- setFloat(String key, float value): void
- setDouble(String key, double value): void
- setBoolean(String key, boolean value): void
- setByte(String key, byte value): void
- setList(String key, List<Object> value): void
- setStringList(String key, List<String> value): void

ConfigFile (comments and metadata)
- setComment(String key, List<String> lines): void — per-key comments (YAML/TOML/PROPERTIES)
- setHeader(List<String> lines): void — file header (YAML/TOML/PROPERTIES)
- setFooter(List<String> lines): void — file footer (YAML/TOML/PROPERTIES)

ConfigFile (management)
- contains(String key): boolean
- remove(String key): void
- reload(): void — force reload from disk (keeps cache entry)
- save(): void — persist current in-memory view (usually not needed because setters auto-save)

Notes on lists
- getList returns List<Object>. getStringList converts elements to strings.
- Properties files cannot represent typed lists natively; strings like "[a, b]" or comma-separated values are parsed into a list of strings.


### Example 1: YAML quick start (create, set, read)

```java
import de.feelix.leviathan.file.ConfigFile;
import de.feelix.leviathan.file.FileAPI;

import java.io.File;
import java.util.List;

public class ExampleYamlQuickStart {
  public void run(File dataFolder) {
    ConfigFile cfg = FileAPI.open(new File(dataFolder, "config.yml"));

    // Write some values (auto-saves)
    cfg.setString("name", "Leviathan");
    cfg.setInt("retries", 3);
    cfg.setBoolean("enabled", true);
    cfg.setDouble("ratio", 0.75);
    cfg.setStringList("servers", List.of("eu-1", "us-1"));

    // Optional: comments and header/footer (supported by YAML)
    cfg.setHeader(List.of("Example 1", "Generated by Leviathan"));
    cfg.setComment("retries", List.of("How many attempts before giving up"));
    cfg.setFooter(List.of("End of file"));

    // Read them back
    String name = cfg.getString("name");
    int retries = cfg.getIntOrDefault("retries", 1);
    boolean enabled = cfg.getBoolean("enabled");
    List<String> servers = cfg.getStringList("servers");
  }
}
```


### Example 2: JSON with full typed access and defaults/alternatives

```java
import de.feelix.leviathan.file.ConfigFile;
import de.feelix.leviathan.file.FileAPI;

import java.io.File;
import java.util.List;

public class ExampleJsonTypedAccess {
  public void run(File dataFolder) {
    ConfigFile cfg = FileAPI.open(new File(dataFolder, "settings.json"));

    // getOrSet: write the alternative value if the key is missing
    String host = cfg.getStringOrSet("host", "localhost");
    int port = cfg.getIntOrSet("port", 25565);
    long timeoutMs = cfg.getLongOrSet("timeoutMs", 5_000L);
    float f = cfg.getFloatOrSet("f", 1.25f);
    double threshold = cfg.getDoubleOrSet("threshold", 0.95);
    boolean secure = cfg.getBooleanOrSet("secure", false);
    byte mode = cfg.getByteOrSet("mode", (byte) 1);
    List<String> tags = cfg.getStringListOrSet("tags", List.of("prod", "primary"));

    // getOrDefault: do not modify the file when missing
    int maxConn = cfg.getIntOrDefault("maxConn", 100);

    // Comments are ignored in JSON (output will not contain them)
  }
}
```


### Example 3: TOML with lists and per-key comments

```java
import de.feelix.leviathan.file.ConfigFile;
import de.feelix.leviathan.file.FileAPI;

import java.io.File;
import java.util.List;

public class ExampleTomlListsAndComments {
  public void run(File dataFolder) {
    ConfigFile cfg = FileAPI.open(new File(dataFolder, "app.toml"));

    cfg.setHeader(List.of("Example 3: TOML", "Lists + comments"));

    // Lists are written as TOML arrays
    cfg.setList("levels", List.of(1, 2, 3, 5, 8));
    cfg.setStringList("names", List.of("Alice", "Bob"));

    // Per-key comments
    cfg.setComment("levels", List.of("Fibonacci-ish"));

    // Reads
    var levels = cfg.getList("levels"); // List<Object>
    var names = cfg.getStringList("names"); // List<String>
  }
}
```


### Example 4: .properties usage, contains/remove, string lists

```java
import de.feelix.leviathan.file.ConfigFile;
import de.feelix.leviathan.file.FileAPI;

import java.io.File;
import java.util.List;

public class ExamplePropertiesBasics {
  public void run(File dataFolder) {
    ConfigFile cfg = FileAPI.open(new File(dataFolder, "plugin.properties"));

    // Properties values are strings; lists are stored as "[a, b]" or comma-separated strings
    cfg.setString("welcome", "Hello world");
    cfg.setStringList("roles", List.of("admin", "mod"));

    // Presence and removal
    boolean hasWelcome = cfg.contains("welcome");
    if (hasWelcome) cfg.remove("welcome");

    // Read list back (string parsing supported)
    var roles = cfg.getStringList("roles");

    // Header/footer and per-key comments are supported in .properties
    cfg.setHeader(List.of("Example 4: .properties"));
    cfg.setComment("roles", List.of("Comma-separated or [a, b] notation"));
  }
}
```


### Example 5: Cache control, reload and external changes

```java
import de.feelix.leviathan.file.ConfigFile;
import de.feelix.leviathan.file.FileAPI;

import java.io.File;

public class ExampleCacheReload {
  public void run(File dataFolder) {
    File file = new File(dataFolder, "config.yml");
    ConfigFile cfg = FileAPI.open(file);

    // Normal reads use a cached in-memory view; setters save immediately.
    String before = cfg.getStringOrDefault("value", "none");

    // If an external process edits the file on disk, you can force a reload:
    cfg.reload(); // refreshes from disk and keeps the cache entry
    String after = cfg.getString("value");

    // You can also drop cache entries entirely:
    FileAPI.invalidate(file); // next open/reload re-reads from disk

    // Or clear all caches across files:
    FileAPI.clearCache();
  }
}
```


FAQ and tips
- Format detection is based on the file extension. Default is YAML if the extension is unknown.
- Setters and comment/header/footer calls persist immediately. Use save() only if you changed the underlying file contents manually and then modified the in-memory view.
- get* methods returning boxed types (Integer, Long, etc.) yield null when the value is missing or not parseable; the getOrDefault/getOrSet variants provide primitives or non-null results.
- Values are stored as-is for YAML/JSON/TOML; .properties stores strings. Lists in .properties are represented as bracketed or comma-separated strings and parsed back.
