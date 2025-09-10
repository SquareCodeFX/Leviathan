# LeviathanCommand

A tiny, fluent, and type‑safe command framework for Bukkit/Spigot/Paper. Build robust commands with strong typing, optional arguments, sub‑argument permissions, greedy strings, optional tab‑validation, and optional async execution — all with a clean builder API.

> Target runtime: Spigot/Paper 1.20+ • Java 17+

- Strongly typed arguments (int, long, string, uuid, oneOf, choices)
- Optional arguments, per‑argument permissions, and player‑only commands
- Greedy trailing string support (captures the remainder of the line) — can be optional
- Optional validation during tab completion to guide users
- Subcommands as arguments by mapping to other FluentCommand instances
- Optional asynchronous execution using CompletableFuture
- Developer‑friendly exceptions and comprehensive Javadoc

## Table of Contents
- Why LeviathanCommand?
- Installation
  - Option A: Shade/Embed
  - Option B: JitPack (after GitHub release)
  - Build from source
- Quick Start
- Examples
  - Choices + optional target
  - Kick with optional reason
  - Subcommands with command choices
  - Async execution
  - Greedy trailing string (optional)
  - Per‑argument permissions
  - Validation on tab complete
- More Examples
  - Player-only command
  - Disable built-in error messages
  - Subcommand tree via Builder.sub(...)
  - Accept multiple numeric types with oneOf
  - CommandContext patterns
  - End-to-end onEnable example
- API Overview
  - FluentCommand & Builder
  - Arguments & parsers
  - CommandContext helpers
  - Exceptions
- Bukkit plugin.yml
- Troubleshooting
- Versioning & Compatibility
- Contributing
- License

---

## Why LeviathanCommand?
Minecraft command handling can quickly get messy: parsing, validation, tab completion, and permission checks all in one place. LeviathanCommand provides a small, documented, fluent API that keeps your command logic focused and safe, while surfacing configuration mistakes early with clear exceptions.

## Installation

This library is designed to be embedded (shaded) into your plugin, or consumed via JitPack once you publish a GitHub release.

### Option A: Shade/Embed (recommended)
1) Clone or download this repository, then build it.
2) Copy the resulting JAR into your plugin’s build and shade it, or copy the `de.feelix.leviathan` sources into a module of your project.

Gradle example (shading with the jar you built into `libs/`):
```gradle
repositories { mavenCentral() }

dependencies {
    implementation files('libs/LeviathanCommand-1.0-SNAPSHOT.jar')
    compileOnly 'org.spigotmc:spigot-api:1.20.4-R0.1-SNAPSHOT'
}
```

### Option B: JitPack (after you publish a GitHub release)
Once this repository is public with a tagged release, you can consume via JitPack:
```gradle
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.<your_github_username>:LeviathanCommand:<tag>'
    compileOnly 'org.spigotmc:spigot-api:1.20.4-R0.1-SNAPSHOT'
}
```
Replace `<your_github_username>` and `<tag>` (e.g., `1.0.0`).

### Build from source
This repository uses Gradle and Java 17.
```powershell
# Windows
./gradlew.bat build

# macOS/Linux
./gradlew build
```
Artifacts are produced under `build/libs/`.

## Quick Start
Declare your command in `plugin.yml`, then register a `FluentCommand` in your plugin’s `onEnable` method.

plugin.yml:
```yaml
commands:
  hello:
    description: Say hello
    usage: /hello <name> [times]
```

Java:
```java
public final class MyPlugin extends JavaPlugin {
  @Override public void onEnable() {
    FluentCommand.builder("hello")
      .description("Say hello")
      .argString("name")
      .argInt("times").optional()
      .executes((sender, ctx) -> {
        String name = ctx.require("name", String.class);
        int times = Optional.ofNullable(ctx.get("times", Integer.class)).orElse(1);
        for (int i = 0; i < times; i++) sender.sendMessage("Hello, " + name + "!");
      })
      .register(this);
  }
}
```

## Examples

### Choices + optional target
```java
Map<String, GameMode> gm = Map.of(
  "0", SURVIVAL, "survival", SURVIVAL,
  "1", CREATIVE, "creative", CREATIVE,
  "2", ADVENTURE, "adventure", ADVENTURE,
  "3", SPECTATOR, "spectator", SPECTATOR
);

FluentCommand.builder("gamemode")
  .permission("leviathan.gamemode")
  .description("Change your or another player's game mode")
  .argChoices("mode", gm, "gamemode")
  .argUUID("target").optional()
  .executes((sender, ctx) -> {
    GameMode mode = ctx.require("mode", GameMode.class);
    Player target = (sender instanceof Player p) ? p : null;
    if (ctx.has("target")) {
      UUID id = ctx.require("target", UUID.class);
      target = Bukkit.getPlayer(id);
    }
    if (target == null) { sender.sendMessage("§cTarget is not online."); return; }
    target.setGameMode(mode);
    sender.sendMessage("§aSet " + target.getName() + " to " + mode.name().toLowerCase());
  })
  .register(this);
```

### Kick with optional reason
```java
FluentCommand.builder("kick")
  .permission("leviathan.kick")
  .description("Kick a player with an optional reason")
  .argUUID("target")
  .argString("reason").optional()
  .executes((sender, ctx) -> {
    UUID id = ctx.require("target", UUID.class);
    Player target = Bukkit.getPlayer(id);
    if (target == null) { sender.sendMessage("§cTarget is not online."); return; }
    String reason = ctx.getOptional("reason", String.class).orElse("Kicked by an operator.");
    target.kickPlayer(reason);
  })
  .register(this);
```

### Subcommands with command choices
You can parse a token directly into a `FluentCommand` and dispatch to it:
```java
FluentCommand info = FluentCommand.builder("info").executes((s,c)-> s.sendMessage("Info!" )).build();
FluentCommand help = FluentCommand.builder("help").executes((s,c)-> s.sendMessage("Help!" )).build();
Map<String, FluentCommand> subs = Map.of("info", info, "help", help);

FluentCommand.builder("users")
  .argCommandChoices("sub", subs)
  .executes((sender, ctx) -> {
    FluentCommand sub = ctx.require("sub", FluentCommand.class);
    // Dispatch using the subcommand's name and remaining raw args
    String[] remaining = Arrays.copyOfRange(ctx.raw(), 1, ctx.raw().length);
    sub.execute(sender, sub.getName(), remaining);
  })
  .register(this);
```

### Async execution
Run heavy logic off the main thread using CompletableFuture. Be careful with Bukkit API calls — most must run on the main thread.
```java
FluentCommand.builder("heavy")
  .async(true)
  .executes((sender, ctx) -> {
    // runs asynchronously
    performExpensiveTask();
  })
  .register(this);
```

### Greedy trailing string (optional)
Capture the remainder of the line as one string. This argument must be last and use the string parser; it can be optional.
```java
FluentCommand.builder("say")
  .argGreedyString("message").optional()
  .executes((sender, ctx) -> {
    String msg = ctx.getOptional("message", String.class).orElse("Hello!");
    sender.getServer().broadcastMessage(msg);
  })
  .register(this);
```

### Per‑argument permissions
Hide or deny specific arguments for users without a given permission.
```java
FluentCommand.builder("tp")
  .argUUID("target").argPermission("leviathan.tp.target")
  .executes((s, ctx) -> { /* ... */ })
  .register(this);
```

### Validation on tab complete
Enable type checking of previously typed tokens during tab completion. If a prior arg is invalid, suggestions are hidden and (optionally) an error is shown.
```java
FluentCommand.builder("warp")
  .validateOnTab(true)
  .argString("name")
  .executes((s, c) -> { /* ... */ })
  .register(this);
```

## API Overview

### FluentCommand & Builder
Key builder methods:
- `description(String)`
- `permission(String)` and `playerOnly(boolean)`
- `sendErrors(boolean)` to control user‑facing error feedback
- Argument helpers: `argInt`, `argLong`, `argString`, `argUUID`, `argGreedyString`
- Advanced: `argChoices(name, map, displayType)`, `argOneOf(name, displayType, parsers...)`, `argCommandChoices(name, Map<String, FluentCommand>)`
- `optional()` to mark the most recently added argument optional
- `argPermission(String)` to require a permission for the last added argument
- `validateOnTab(boolean)` to type‑check prior args while tabbing
- `async(boolean)` or `executesAsync(...)` to execute off the main thread
- `executes(CommandAction)` to handle execution
- `register(JavaPlugin)` to bind to the command declared in plugin.yml

Runtime helpers:
- `execute(CommandSender, String label, String[] args)` to programmatically dispatch (useful for subcommands)
- `getName()` and `getPermission()` for integration purposes

### Arguments & parsers
- `ArgumentParser<T>` contract: `getTypeName()`, `parse(...)`, `complete(...)`
- Built‑in parsers in `ArgParsers`: `intParser`, `longParser`, `stringParser`, `uuidParser`, `choices`, `oneOf`
- Greedy trailing arguments must be last and use the string parser. They can be optional.

### CommandContext helpers
- `get(name, type)` returns value or null
- `getOptional(name, type)` returns Optional
- `require(name, type)`/`getOrThrow(...)` enforce presence and type; throw `ApiMisuseException` on mismatch
- `raw()` returns a copy of the original argument array

### Exceptions
- `CommandConfigurationException`: Misconfigured command (e.g., required‑after‑optional, duplicate arg names, missing plugin.yml entry, invalid greedy placement)
- `ParsingException`: Parser contract/configuration violations (e.g., null suggestions, invalid alias maps)
- `ApiMisuseException`: Developer misuse at runtime (e.g., wrong type requested from context)

Clear developer errors, preserved user‑facing behavior:
- If `sendErrors(true)` (default), invalid input and permission denials show concise messages.
- Tab validation is opt‑in via `validateOnTab(true)`.

### Nullability annotations
Public API methods and parameters are annotated with simple `@NotNull` / `@Nullable` markers from `de.feelix.leviathan.annotations`. These are compile‑time hints for IDEs and static analysis only; they do not add any runtime dependency or behavior.

## Bukkit plugin.yml
Declare your base commands in `plugin.yml`. At runtime, call `register(plugin)` for each `FluentCommand` using the same name.
```yaml
name: MyPlugin
version: 1.0.0
main: com.example.myplugin.MyPlugin
api-version: '1.20'
commands:
  hello:
    description: Say hello
    usage: /hello <name> [times]
```

## Versioning & Compatibility
- Java 17+
- Spigot API 1.20.4+ (works on Paper)
- Semantic‑ish versioning for releases (MAJOR may break APIs; MINOR adds features; PATCH is safe)

## Contributing
Issues and pull requests are welcome. Please include:
- A concise description of the problem/feature
- Repro steps or a small code sample
- Version info (MC/Spigot/Paper, Java)

## License
No explicit license is included in this repository at the moment. If you are the maintainer, consider adding a LICENSE file (e.g., MIT or Apache‑2.0) before publishing a public release.


---

## More Examples

### Player-only command
```java
FluentCommand.builder("spawn")
  .description("Teleport yourself to the world spawn")
  .playerOnly(true)
  .executes((sender, ctx) -> {
    Player p = (Player) sender; // safe: playerOnly(true)
    p.teleport(p.getWorld().getSpawnLocation());
    p.sendMessage("§aTeleported to spawn.");
  })
  .register(this);
```

### Disable built-in error messages (sendErrors)
You can take full control over messaging by disabling automatic errors and handling feedback yourself.
```java
FluentCommand.builder("rename")
  .description("Rename something with basic validation")
  .sendErrors(false)
  .argString("name")
  .executes((sender, ctx) -> {
    String name = ctx.get("name", String.class);
    if (name == null || name.length() < 3) {
      sender.sendMessage("§cUsage: /rename <name> (min 3 chars)");
      return;
    }
    // do the renaming...
    sender.sendMessage("§aRenamed to " + name);
  })
  .register(this);
```

### Subcommand tree via Builder.sub(...)
You can define real subcommands and let LeviathanCommand route both execution and tab-completion automatically.

Note: Do not call .register(...) on subcommands. Only register the root command declared in plugin.yml. Subcommands are dispatched by the root command automatically. If you build a subcommand as a FluentCommand and then call subCommand.register(plugin), an ApiMisuseException will be thrown.
```java
FluentCommand set = FluentCommand.builder("set")
  .description("Set a key to a value")
  .argString("key")
  .argGreedyString("value").optional()
  .executes((s, c) -> s.sendMessage("set " + c.require("key", String.class)))
  .build();

FluentCommand del = FluentCommand.builder("delete")
  .description("Delete a key")
  .argString("key")
  .executes((s, c) -> s.sendMessage("deleted " + c.require("key", String.class)))
  .build();

FluentCommand.builder("config")
  .description("Manage config")
  .sub(set, del) // automatic routing to /config set|delete ...
  .executes((s, c) -> s.sendMessage("§7Usage: /config <set|delete>"))
  .register(this);
```

### Accept multiple numeric types with oneOf
`oneOf` lets you combine multiple parsers that share a common supertype. Here we accept either int or long and retrieve it as a `Number`.
```java
FluentCommand.builder("pay")
  .description("Pay an amount")
  .argOneOf("amount", "number", ArgParsers.intParser(), ArgParsers.longParser())
  .executes((s, c) -> {
    Number amount = c.require("amount", Number.class);
    long asLong = amount.longValue();
    s.sendMessage("§aPaying " + asLong + " coins...");
  })
  .register(this);
```

### CommandContext patterns
```java
.executes((sender, ctx) -> {
  // Required value (throws ApiMisuseException if missing or wrong type)
  UUID id = ctx.require("target", UUID.class);

  // Optional value two ways
  String reason1 = ctx.get("reason", String.class); // null when missing
  String reason2 = ctx.getOptional("reason", String.class).orElse("default");

  // Presence check
  if (ctx.has("silent")) { /* ... */ }

  // Original raw tokens as typed by the user
  String[] raw = ctx.raw();
})
```

### End-to-end `onEnable` example
```java
@Override public void onEnable() {
  // /hello <name> [times]
  FluentCommand.builder("hello")
    .description("Say hello")
    .argString("name")
    .argInt("times").optional()
    .executes((sender, ctx) -> {
      String name = ctx.require("name", String.class);
      int times = Optional.ofNullable(ctx.get("times", Integer.class)).orElse(1);
      for (int i = 0; i < times; i++) sender.sendMessage("Hello, " + name + "!");
    })
    .register(this);

  // /config set|delete ... (subcommand routing)
  FluentCommand set = FluentCommand.builder("set")
    .argString("key").argGreedyString("value").optional()
    .executes((s, c) -> s.sendMessage("set " + c.require("key", String.class)))
    .build();
  FluentCommand del = FluentCommand.builder("delete")
    .argString("key")
    .executes((s, c) -> s.sendMessage("deleted " + c.require("key", String.class)))
    .build();
  FluentCommand.builder("config").sub(set, del).register(this);
}
```

## Troubleshooting
- Command does nothing: Ensure the command name is declared in plugin.yml and you called `register(plugin)`.
- "Greedy argument must be the last": Greedy args can only be last and must use the string parser.
- "Required argument cannot appear after an optional": Place all required args before optional ones.
- "Duplicate subcommand alias" or choices alias errors: Aliases are case-insensitive; avoid duplicates like `Home` vs `home`.
- Null suggestions / parse results: If you implement custom parsers, never return null from `parse` or `complete`.
- No tab suggestions appear: You may have `validateOnTab(true)` with an invalid earlier token or missing permissions.
