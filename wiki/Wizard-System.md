### Wizard System

Leviathan provides an interactive wizard system for complex, multi-step command interactions. Wizards guide users through a series of questions and choices to collect information before executing an action.

#### Overview

The wizard system provides:

- **Decision Trees**: Branching logic based on user choices
- **Multiple Node Types**: Questions, text input, actions, and terminal nodes
- **Variable Collection**: Store and use user input throughout the wizard
- **Conditional Options**: Show/hide options based on previous choices
- **Navigation**: Go back to previous nodes, cancel anytime
- **Timeout Handling**: Automatic session expiration

#### Basic Usage

Create a wizard definition and attach it to a command:

```java
WizardDefinition wizard = WizardDefinition.builder("setup-kit")
    .description("Create a custom kit")
    .question("type", "What type of kit do you want?", node -> node
        .option("combat", "Combat Kit", "weapon-choice")
        .option("utility", "Utility Kit", "tool-choice")
        .option("food", "Food Kit", "food-amount")
    )
    .question("weapon-choice", "Choose your weapon:", node -> node
        .option("sword", "Diamond Sword", "confirm")
        .option("bow", "Bow & Arrows", "confirm")
        .option("axe", "Battle Axe", "confirm")
    )
    .question("tool-choice", "Choose your tools:", node -> node
        .option("mining", "Mining Set", "confirm")
        .option("farming", "Farming Set", "confirm")
    )
    .input("food-amount", "amount", "How many food items?", ArgParsers.intParser(), "confirm")
    .terminal("confirm", ctx -> {
        Player player = ctx.player();
        String type = ctx.getString("type", "unknown");
        player.sendMessage("Kit created: " + type);
        // Give items based on choices...
    })
    .build();

SlashCommand cmd = SlashCommand.create("createkit")
    .playerOnly(true)
    .wizard(wizard)
    .register(plugin);
```

#### Node Types

##### Question Nodes

Present multiple-choice options to the user:

```java
.question("difficulty", "Select difficulty level:", node -> node
    .option("easy", "Easy Mode", "next-step")
    .option("normal", "Normal Mode", "next-step")
    .option("hard", "Hard Mode", "next-step")
    .option("extreme", "Extreme Mode", "next-step")
)
```

##### Input Nodes

Collect typed input from the user:

```java
.input("player-name", "name", "Enter the player's name:", ArgParsers.stringParser(), "next-step")
.input("amount", "count", "How many items?", ArgParsers.intParser(), "next-step")
.input("location", "coords", "Enter location (x y z):", ArgParsers.locationParser(), "next-step")
```

##### Action Nodes

Execute code and continue to another node:

```java
.action("validate", ctx -> {
    // Perform validation or intermediate processing
    int amount = ctx.getInt("amount", 1);
    if (amount > 64) {
        ctx.player().sendMessage("Amount too high, setting to 64.");
        ctx.put("amount", 64);
    }
}, "next-step")
```

##### Terminal Nodes

Execute the final action and complete the wizard:

```java
.terminal("complete", ctx -> {
    Player player = ctx.player();
    String item = ctx.getString("item", "DIAMOND");
    int amount = ctx.getInt("amount", 1);

    // Give items to player
    ItemStack stack = new ItemStack(Material.valueOf(item), amount);
    player.getInventory().addItem(stack);
    player.sendMessage("Received " + amount + " " + item + "!");
})
```

#### WizardOption Builder

Create options with advanced configuration:

```java
.question("action", "What would you like to do?", node -> node
    .option(WizardOption.builder("delete")
        .displayText("Delete Everything")
        .description("Permanently removes all data")
        .nextNode("confirm-delete")
        .requiresConfirmation(true)  // Double-confirm dangerous actions
        .condition(ctx -> ctx.player().hasPermission("admin.delete"))
        .build())
    .option(WizardOption.builder("cancel")
        .displayText("Cancel")
        .action(ctx -> ctx.player().sendMessage("Cancelled."))
        .build())
)
```

##### Option Configuration

| Method | Description |
|--------|-------------|
| `displayText(String)` | Text shown to the user |
| `description(String)` | Additional description |
| `nextNode(String)` | Node to navigate to when selected |
| `action(WizardAction)` | Action to execute when selected |
| `requiresConfirmation(boolean)` | Require double confirmation |
| `condition(Predicate)` | Show only if condition is true |

#### WizardContext API

The `WizardContext` provides access to collected data and navigation:

```java
.terminal("complete", ctx -> {
    // Get the player
    Player player = ctx.player();

    // Get collected variables
    String name = ctx.getString("name", "default");
    int count = ctx.getInt("count", 1);
    boolean enabled = ctx.getBoolean("enabled", false);

    // Check if variable exists
    if (ctx.has("optional-value")) {
        // ...
    }

    // Get the path taken through the wizard
    List<String> path = ctx.navigationHistory();

    // Get all choices made
    Map<String, String> choices = ctx.choices();

    // Access the original command context
    CommandContext cmdCtx = ctx.commandContext();
})
```

#### Navigation

Users can navigate through the wizard:

- **Type option key/number**: Select an option
- **Type "back"**: Go to the previous node
- **Type "cancel"**: Exit the wizard

```java
.question("step2", "Continue?", node -> node
    .option("yes", "Yes, continue", "step3")
    .option("no", "No, go back", "step1")  // Custom back navigation
)
```

#### Conditional Options

Show options based on previous choices or context:

```java
.question("extras", "Select extras:", node -> node
    .option(WizardOption.builder("vip")
        .displayText("VIP Package")
        .nextNode("vip-options")
        .condition(ctx -> ctx.player().hasPermission("kit.vip"))
        .build())
    .option(WizardOption.builder("standard")
        .displayText("Standard Package")
        .nextNode("standard-options")
        .build())
)
```

#### Skip Conditions

Skip nodes based on previous answers:

```java
.question("armor", "Choose armor:", node -> node
    .option("diamond", "Diamond Armor", "enchant")
    .option("iron", "Iron Armor", "enchant")
    .option("none", "No Armor", "weapons")  // Skip enchant step
    .skipIf(ctx -> ctx.getString("type", "").equals("utility"))
)
```

#### Timeout Handling

Configure session timeout:

```java
WizardDefinition wizard = WizardDefinition.builder("setup")
    .timeout(Duration.ofMinutes(5))  // Session expires after 5 minutes
    .timeoutMessage("Session expired. Please start again.")
    // ... nodes
    .build();
```

#### Inline Wizard Definition

Define a wizard directly in the command builder:

```java
SlashCommand cmd = SlashCommand.create("quicksetup")
    .wizard("quick-setup", wizard -> wizard
        .description("Quick setup wizard")
        .question("choice", "Pick one:", node -> node
            .option("a", "Option A", "done")
            .option("b", "Option B", "done")
        )
        .terminal("done", ctx -> {
            ctx.player().sendMessage("You chose: " + ctx.getString("choice", "none"));
        })
    )
    .register(plugin);
```

#### WizardManager

Manage active wizard sessions programmatically:

```java
// Check if player has active wizard
if (WizardManager.hasActiveSession(player)) {
    // ...
}

// Cancel a player's wizard session
WizardManager.cancelSession(player);

// Handle player disconnect
@EventHandler
public void onQuit(PlayerQuitEvent event) {
    WizardManager.cancelSession(event.getPlayer());
}
```

#### Complete Example: Warp Creation Wizard

```java
WizardDefinition warpWizard = WizardDefinition.builder("create-warp")
    .description("Create a new warp point")
    .timeout(Duration.ofMinutes(3))

    // Step 1: Enter warp name
    .input("name", "warpName", "Enter the warp name:", ArgParsers.stringParser(), "visibility")

    // Step 2: Choose visibility
    .question("visibility", "Who can use this warp?", node -> node
        .option("public", "Everyone", "category")
        .option("private", "Only you", "category")
        .option(WizardOption.builder("group")
            .displayText("Specific permission group")
            .nextNode("permission-input")
            .condition(ctx -> ctx.player().hasPermission("warp.admin"))
            .build())
    )

    // Step 2b: Permission input (only if "group" selected)
    .input("permission", "perm", "Enter required permission:", ArgParsers.stringParser(), "category")

    // Step 3: Choose category
    .question("category", "Select category:", node -> node
        .option("spawn", "Spawn Points", "confirm")
        .option("dungeon", "Dungeons", "confirm")
        .option("shop", "Shops", "confirm")
        .option("custom", "Custom", "custom-category")
    )

    // Step 3b: Custom category input
    .input("custom-category", "cat", "Enter custom category name:", ArgParsers.stringParser(), "confirm")

    // Final confirmation
    .question("confirm", "Create warp with these settings?", node -> node
        .option(WizardOption.builder("yes")
            .displayText("Yes, create warp")
            .action(ctx -> {
                Player player = ctx.player();
                String name = ctx.getString("warpName", "unnamed");
                String visibility = ctx.getString("visibility", "public");
                String category = ctx.has("custom-category")
                    ? ctx.getString("custom-category", "misc")
                    : ctx.getString("category", "misc");

                // Create the warp
                Location loc = player.getLocation();
                // warpManager.createWarp(name, loc, visibility, category);

                player.sendMessage("§aWarp '" + name + "' created!");
            })
            .build())
        .option("no", "Cancel", null)  // null nextNode means wizard ends
    )
    .build();

SlashCommand cmd = SlashCommand.create("createwarp")
    .description("Create a new warp point")
    .playerOnly(true)
    .permission("warp.create")
    .wizard(warpWizard)
    .register(plugin);
```

#### Core Classes

| Class | Purpose |
|-------|---------|
| `WizardDefinition` | Complete wizard structure and configuration |
| `WizardNode` | A single node in the decision tree |
| `WizardOption` | A selectable option within a node |
| `WizardContext` | Runtime context with collected variables |
| `WizardSession` | An active wizard session for a player |
| `WizardManager` | Manages all active sessions |
| `WizardAction` | Functional interface for terminal actions |

#### Custom Messages

Override wizard messages in your `MessageProvider`:

```java
public class CustomMessages implements MessageProvider {
    @Override
    public String wizardStarted(String wizardName) {
        return "§d[Wizard] §fStarting " + wizardName + "...";
    }

    @Override
    public String wizardCompleted() {
        return "§a[Wizard] §fCompleted successfully!";
    }

    @Override
    public String wizardCancelled() {
        return "§c[Wizard] §fCancelled.";
    }

    @Override
    public String wizardTimeout() {
        return "§c[Wizard] §fSession timed out.";
    }
}
```
