### Interactive Prompting

Interactive prompting allows commands to gather missing arguments from users through a conversational flow, rather than failing immediately when required arguments are missing.

#### Overview

Instead of showing an error like "Missing argument: target", interactive mode prompts the user:

```
[Interactive] Please enter a value for target (The player to ban):
Options: Steve, Alex, Player123
Type 'cancel' to abort.
```

This creates a more user-friendly experience, especially for complex commands with multiple arguments.

#### Enabling Interactive Mode

Enable interactive prompting on specific arguments using `ArgContext`:

```java
SlashCommand ban = SlashCommand.create("ban")
    .argPlayer("target", ArgContext.builder()
        .interactive(true)
        .description("The player to ban")
        .build())
    .argString("reason", ArgContext.builder()
        .interactive(true)
        .optional(true)
        .description("Reason for the ban")
        .build())
    .argDuration("duration", ArgContext.builder()
        .interactive(true)
        .optional(true)
        .description("Ban duration")
        .build())
    .executes((sender, ctx) -> {
        // Arguments gathered interactively if not provided
        Player target = ctx.require("target", Player.class);
        String reason = ctx.getStringOrDefault("reason", "No reason specified");
        // ...
    })
    .build();
```

Or using the fluent Arg API:

```java
SlashCommand cmd = SlashCommand.create("give")
    .arg(Arg.of("player", ArgParsers.PLAYER)
        .interactive(true)
        .withDescription("The recipient"))
    .arg(Arg.of("item", ArgParsers.MATERIAL)
        .interactive(true)
        .withDescription("The item to give"))
    .executes((sender, ctx) -> { /* ... */ })
    .build();
```

#### How It Works

1. **Command Invocation**: User runs `/ban` without arguments
2. **Session Start**: System detects missing interactive arguments and starts a prompt session
3. **Prompting**: User is prompted for each missing argument one by one
4. **Input Handling**: User types responses in chat; the system parses and validates each
5. **Completion**: Once all arguments are collected, the command executes normally

#### Session Commands

During an interactive session, users can:

- Type the requested value to continue
- Type `cancel` to abort the session
- Type `skip` to skip optional arguments (only works for arguments marked as optional)

#### Session Timeout

Sessions automatically timeout after 60 seconds of inactivity. The user will see:

```
[Interactive] Session timed out.
```

#### InteractivePrompt API

The `InteractivePrompt` class provides static methods for managing sessions:

```java
// Check if a player has an active session
boolean active = InteractivePrompt.hasActiveSession(player);

// Get the current session
PromptSession session = InteractivePrompt.getSession(player);

// Cancel a session programmatically
InteractivePrompt.cancelSession(player);

// Clean up when player disconnects
InteractivePrompt.cleanupPlayer(player);
```

#### PromptSession Details

Access information about an active session:

```java
PromptSession session = InteractivePrompt.getSession(player);
if (session != null) {
    // Check session state
    boolean active = session.isActive();
    boolean timedOut = session.isTimedOut();

    // Get progress (0.0 to 1.0)
    double progress = session.progress();

    // Get current argument being prompted
    Arg<?> currentArg = session.currentArg();

    // Get already collected values
    Map<String, Object> collected = session.collectedValues();
}
```

#### Configuration

Customize interactive prompting behavior using `InteractivePrompt.Config`:

```java
InteractivePrompt.Config config = InteractivePrompt.Config.builder()
    .enabled(true)                    // Enable/disable interactive mode
    .timeout(120)                     // Timeout in seconds (default: 60)
    .promptPrefix("§e[Input] §f")     // Customize prompt prefix
    .cancelWord("abort")              // Word to cancel (default: "cancel")
    .skipWord("next")                 // Word to skip optional args (default: "skip")
    .build();
```

#### Handling Chat Events

To integrate interactive prompting with your plugin, handle chat events:

```java
@EventHandler(priority = EventPriority.LOWEST)
public void onChat(AsyncPlayerChatEvent event) {
    Player player = event.getPlayer();

    // Check if input was handled by an interactive session
    if (InteractivePrompt.handleInput(player, event.getMessage())) {
        event.setCancelled(true);  // Don't broadcast as chat
    }
}
```

#### Player Disconnect Cleanup

Clean up sessions when players disconnect:

```java
@EventHandler
public void onQuit(PlayerQuitEvent event) {
    InteractivePrompt.cleanupPlayer(event.getPlayer());
}
```

#### Best Practices

1. **Use sparingly**: Only enable interactive mode for arguments that are complex or commonly forgotten
2. **Provide descriptions**: Always set descriptions for interactive arguments to guide users
3. **Limit options**: If using predefined completions, keep the list reasonable (≤10 options shown)
4. **Consider permissions**: Interactive mode only works for players, not console
5. **Handle timeouts gracefully**: Users may walk away; ensure your plugin handles cancelled sessions

#### Example: Complete Setup

```java
public class MyPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        // Register command
        SlashCommand wizard = SlashCommand.create("createshop")
            .playersOnly()
            .argString("name", ArgContext.builder()
                .interactive(true)
                .description("Shop name (3-16 characters)")
                .stringLengthRange(3, 16)
                .build())
            .argEnum("type", ShopType.class, ArgContext.builder()
                .interactive(true)
                .description("Type of shop")
                .build())
            .argDouble("tax", ArgContext.builder()
                .interactive(true)
                .optional(true)
                .description("Tax rate (0-100%)")
                .doubleRange(0.0, 100.0)
                .build())
            .executes((sender, ctx) -> {
                String name = ctx.require("name", String.class);
                ShopType type = ctx.require("type", ShopType.class);
                double tax = ctx.getDoubleOrDefault("tax", 5.0);

                createShop(name, type, tax);
                sender.sendMessage("§aShop '" + name + "' created!");
            })
            .build();

        // Register the command
        LeviathanRegistry.register(this, wizard);

        // Register event handlers
        getServer().getPluginManager().registerEvents(new Listener() {
            @EventHandler(priority = EventPriority.LOWEST)
            public void onChat(AsyncPlayerChatEvent event) {
                if (InteractivePrompt.handleInput(event.getPlayer(), event.getMessage())) {
                    event.setCancelled(true);
                }
            }

            @EventHandler
            public void onQuit(PlayerQuitEvent event) {
                InteractivePrompt.cleanupPlayer(event.getPlayer());
            }
        }, this);
    }
}
```
