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
