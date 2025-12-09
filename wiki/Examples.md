### Cookbook: Common Recipes

This page provides end‑to‑end snippets showing how to combine features of the SlashCommand system.

#### 1) Simple greet command

```java
SlashCommand greet = SlashCommand.create("greet")
    .description("Greets the specified player")
    .playersOnly()
    .argPlayer("target")
    .executes(ctx -> {
        Player me = ctx.get("sender", Player.class);
        Player target = ctx.get("target", Player.class);
        target.sendMessage("" + me.getName() + " says hi!");
    })
    .build();
```

#### 2) Admin command with permission and guard

```java
Guard maintenanceEnabled = new Guard() {
    @Override public boolean test(CommandSender sender) { return config.isMaintenance(); }
    @Override public String errorMessage() { return "Maintenance mode is disabled."; }
};

SlashCommand kickAll = SlashCommand.create("kickall")
    .permission("leviathan.admin.kickall")
    .require(maintenanceEnabled)
    .executes(ctx -> {
        // kick logic
    })
    .build();
```

#### 3) Flags and key-values

```java
SlashCommand backup = SlashCommand.create("backup")
    .flag("verbose", 'v', "verbose")
    .keyValueString("target")
    .keyValueBoolean("compress", true)
    .executes(ctx -> {
        boolean verbose = ctx.getFlag("verbose");
        String target = ctx.getKeyValueString("target");
        boolean compress = ctx.getKeyValueBoolean("compress", true);
        // run backup
    })
    .build();
```

#### 4) Async with timeout and progress

```java
SlashCommand map = SlashCommand.create("map")
    .executesAsync((ctx, progress, token) -> {
        for (int i = 0; i <= 100; i++) {
            if (token.isCancelled()) return; // stop early
            progress.set(i);
            // do work chunk
        }
    }, 20_000L)
    .build();
```

#### 5) Subcommands with help

```java
SlashCommand add = SlashCommand.create("add")
    .argInt("a").argInt("b")
    .executes(ctx -> ctx.get("sender", CommandSender.class).sendMessage(
        String.valueOf(ctx.get("a", Integer.class) + ctx.get("b", Integer.class))))
    .build();

SlashCommand math = SlashCommand.create("math")
    .enableHelp(true)
    .helpPageSize(5)
    .sub(add)
    .build();
```

#### 6) Pagination integration

```java
SlashCommand list = SlashCommand.create("listitems")
    .argPage("page", 1)
    .executes(ctx -> {
        int page = ctx.get("page", Integer.class);
        List<String> items = itemService.list();
        PaginationDataSource<String> ds = new ListDataSource<>(items);
        PaginatedResult<String> result = ds.page(page, 10);
        for (String s : result.items()) ctx.get("sender", CommandSender.class).sendMessage(s);
        ctx.get("sender", CommandSender.class).sendMessage(
            "Page " + result.pageInfo().current() + "/" + result.pageInfo().total());
    })
    .build();
```

#### 7) Conditional argument

```java
SlashCommand give = SlashCommand.create("give")
    .flagLong("bulk", "bulk")
    .argPlayer("target")
    .argMaterial("item")
    .argIf("amount", ArgParsers.INT, ctx -> ctx.getFlag("bulk"))
    .executes(ctx -> {
        int amount = ctx.getOrDefault("amount", Integer.class, 1);
        // give item
    })
    .build();
```

#### 8) Subcommand registration using parent()

The `parent()` method allows a subcommand to register itself to a parent builder, providing an alternative approach to building command hierarchies:

```java
// Traditional approach: parent registers children
SlashCommand add = SlashCommand.create("add")
    .argInt("a").argInt("b")
    .executes(ctx -> {
        int sum = ctx.get("a", Integer.class) + ctx.get("b", Integer.class);
        ctx.get("sender", CommandSender.class).sendMessage("Sum: " + sum);
    })
    .build();

SlashCommand math = SlashCommand.create("math")
    .enableHelp(true)
    .sub(add)
    .build();

// Alternative approach: child registers to parent
SlashCommandBuilder parentBuilder = SlashCommand.builder("venias")
    .description("Manage infractions")
    .enableHelp(true);

SlashCommand history = SlashCommand.builder("history")
    .description("View infraction history")
    .argOfflinePlayer("player")
    .parent(parentBuilder)  // Register as child of parent
    .executes((sender, ctx) -> {
        // Show history...
    })
    .build();

SlashCommand clear = SlashCommand.builder("clear")
    .description("Clear infractions")
    .argOfflinePlayer("player")
    .parent(parentBuilder)  // Register as child of parent
    .executes((sender, ctx) -> {
        // Clear infractions...
    })
    .build();

// Now build and register parent (children are already included)
parentBuilder.build().register(plugin);
```

**Why use parent()?**
- Organize subcommands in separate files or classes
- Allow subcommands to register themselves instead of the parent managing them
- Simplify complex command hierarchies with many subcommands
- Enable modular command design where subcommands can be added independently

#### 9) Confirmation for destructive commands

```java
SlashCommand reset = SlashCommand.create("reseteconomy")
    .permission("leviathan.admin.economy.reset")
    .awaitConfirmation(true)
    .executes((sender, ctx) -> {
        // Reset entire economy database
        economyService.reset();
        sender.sendMessage("Economy has been reset.");
    })
    .build();
```

When a player first runs `/reseteconomy`, they'll receive a confirmation message. They must execute the command again within 10 seconds to confirm. This prevents accidental execution of dangerous commands.
