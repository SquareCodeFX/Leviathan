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
