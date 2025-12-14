### Help, Usage, and Tab Completion

This page covers the built‑in help/usage features and the tab completion system.

#### Usage string

- `SlashCommand#usage()` returns a computed usage string based on the configured arguments, flags, and key‑values.
- Internally, `computeUsageString()` formats argument placeholders in declaration order and may include optional markers depending on `ArgContext`.

Quick view:

```java
SlashCommand cmd = SlashCommand.create("example")
    .argString("name")
    .argInt("age")
    .flagLong("verbose", "verbose")
    .keyValueInt("limit", 25)
    .executes(ctx -> { /* ... */ })
    .build();

String usage = cmd.usage();
```

#### Help generation

- Enable help via `enableHelp(true)`. The command will respond with a paginated help message when invoked with help requests (e.g., no args or `help` subcommand depending on your tree).
- Control page size with `helpPageSize(int)`.
- Help content includes subcommands (if any), sorted sensibly via `categorizeByArguments` and their descriptions.

The underlying method `generateHelpMessage(label, pageNumber, sender)` formats and sends the help page.

#### Input sanitation

- `sanitizeInputs(boolean)` toggles input normalization. When enabled, the command sanitizes raw user tokens (trimming whitespace, normalizing unicode where safe) before parsing. This improves robustness for user input pasted from chat or copied text.

#### Fuzzy subcommand matching

- `fuzzySubcommandMatching(boolean)` enables suggestions when a user mistypes a subcommand. The system uses string similarity to propose the closest match.

#### Tab completion

- `SlashCommand` implements Bukkit’s `TabCompleter` and collaborates with `TabCompletionHandler` and `DynamicCompletionContext` to provide intelligent suggestions.
- `validateOnTab(boolean)` controls whether partial input is syntactically/semantically validated for more accurate suggestions. When `true`, invalid tokens may produce constrained completions rather than generic ones.

Completion sources include:

- Argument types (players, worlds, materials, enums, choices)
- Subcommand names
- Known flags (`-x`, `--example`) and key names for key‑values

Example:

```java
SlashCommand find = SlashCommand.create("find")
    .validateOnTab(true)
    .argStringWithCompletions("type", "user", "world", "item")
    .keyValueInt("limit", 25)
    .flagLong("exact", "exact")
    .executes(ctx -> { /* ... */ })
    .build();
```

Now, when the user types `/find <TAB>`, the completion list will include `user`, `world`, `item`, flags like `--exact`, and key names like `limit=` when appropriate.

#### HelpFormatter

Customize help message appearance by implementing `HelpFormatter`:

```java
public class MyHelpFormatter implements HelpFormatter {
    @Override
    public @NotNull String formatHeader(@NotNull String commandPath, @Nullable String description) {
        return "§6=== " + commandPath + " ===\n§7" + (description != null ? description : "");
    }

    @Override
    public @NotNull String formatUsage(@NotNull String commandPath, @NotNull String usagePattern) {
        return "§eUsage: §f/" + commandPath + " " + usagePattern + "\n";
    }

    @Override
    public @NotNull String formatArgument(@NotNull Arg<?> arg, int index, boolean isLast) {
        String name = arg.optional() ? "[" + arg.name() + "]" : "<" + arg.name() + ">";
        return "  §b" + name + "§7 - " + arg.parser().getTypeName() + "\n";
    }

    @Override
    public @NotNull String formatFlag(@NotNull Flag flag, int index) {
        StringBuilder sb = new StringBuilder("  §a");
        if (flag.shortForm() != null) sb.append("-").append(flag.shortForm());
        if (flag.longForm() != null) {
            if (flag.shortForm() != null) sb.append(", ");
            sb.append("--").append(flag.longForm());
        }
        return sb.append("§7 - ").append(flag.description()).append("\n").toString();
    }

    @Override
    public @NotNull String formatKeyValue(@NotNull KeyValue<?> kv, int index) {
        return "  §d--" + kv.key() + "=<value>§7 - " + kv.description() + "\n";
    }

    @Override
    public @NotNull String formatSubcommand(@NotNull String name, @Nullable String description, @NotNull List<String> aliases) {
        return "  §e" + name + "§7 - " + (description != null ? description : "") + "\n";
    }

    @Override
    public @NotNull String formatSectionHeader(@NotNull String sectionName) {
        return "\n§6" + sectionName + ":\n";
    }

    @Override
    public @NotNull String formatFooter(@NotNull String commandPath) {
        return "§8Use /" + commandPath + " help <subcommand> for more info";
    }

    @Override
    public @NotNull String assembleHelp(@NotNull String header, @NotNull String usage,
            @NotNull List<String> arguments, @NotNull List<String> flags,
            @NotNull List<String> keyValues, @NotNull List<String> subcommands, @NotNull String footer) {
        StringBuilder sb = new StringBuilder();
        sb.append(header).append(usage);
        if (!arguments.isEmpty()) {
            sb.append(formatSectionHeader("Arguments"));
            arguments.forEach(sb::append);
        }
        if (!flags.isEmpty()) {
            sb.append(formatSectionHeader("Flags"));
            flags.forEach(sb::append);
        }
        if (!keyValues.isEmpty()) {
            sb.append(formatSectionHeader("Options"));
            keyValues.forEach(sb::append);
        }
        if (!subcommands.isEmpty()) {
            sb.append(formatSectionHeader("Subcommands"));
            subcommands.forEach(sb::append);
        }
        if (!footer.isEmpty()) sb.append("\n").append(footer);
        return sb.toString();
    }
}

// Apply custom formatter
SlashCommand cmd = SlashCommand.create("mycommand")
    .helpFormatter(new MyHelpFormatter())
    .build();
```

##### Built-in Formatters

```java
// Default formatter with Minecraft colors
HelpFormatter defaultFormatter = HelpFormatter.defaultFormatter();

// Plain text without colors
HelpFormatter plainFormatter = HelpFormatter.plainFormatter();

SlashCommand cmd = SlashCommand.create("example")
    .helpFormatter(HelpFormatter.plainFormatter())
    .build();
```

#### MessageProvider

Customize all user-facing messages by implementing `MessageProvider`:

```java
public class SpanishMessageProvider implements MessageProvider {
    @Override
    public @NotNull String noPermission() {
        return "§cNo tienes permiso para usar este comando.";
    }

    @Override
    public @NotNull String playerOnly() {
        return "§cEste comando solo puede ser usado por jugadores.";
    }

    @Override
    public @NotNull String userCooldown(@NotNull String formattedTime) {
        return "§cDebes esperar " + formattedTime + " antes de usar este comando.";
    }

    // ... implement all other methods
}

// Apply custom messages
SlashCommand cmd = SlashCommand.create("example")
    .messageProvider(new SpanishMessageProvider())
    .build();
```

`MessageProvider` covers:
- Permission messages (`noPermission`, `playerOnly`, `guardFailed`)
- Cooldown messages (`userCooldown`, `serverCooldown`)
- Parsing errors (`invalidArgumentValue`, `insufficientArguments`, `tooManyArguments`)
- Validation messages (`validationFailed`, `crossValidationFailed`, numeric/string constraints)
- Help formatting (`helpUsage`, `helpSubCommandsHeader`)
- Guard messages (`guardPermission`, `guardInWorld`, `guardGameMode`, etc.)
- Pagination messages (`paginationPageInfo`, `paginationHeader`, `paginationFooter`)
- Error messages (`executionError`, `internalError`, `commandTimeout`)

Use `DefaultMessageProvider` as a starting point or reference.
