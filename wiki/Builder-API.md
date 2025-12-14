### Builder API

The `SlashCommandBuilder` is the fluent entry point for configuring every aspect of a command before producing an immutable `SlashCommand`.

Create a builder:

```java
SlashCommandBuilder b = SlashCommand.create("name");
// or
SlashCommandBuilder b2 = SlashCommand.builder("name");
```

Build and register:

```java
SlashCommand cmd = SlashCommand.create("greet")
    .description("Greets a player")
    .permission("leviathan.greet")
    .aliases("hello", "hi")
    .playersOnly()
    .argPlayer("target")
    .executes(ctx -> {
        Player p = ctx.get("target", Player.class);
        ctx.get("sender", CommandSender.class).sendMessage("Hello, " + p.getName());
    })
    .build();

cmd.register(plugin);
```

#### Metadata

- `description(String)` / `withDescription(String)` — Human‑readable description.
- `permission(String)` / `withPermission(String)` — Required permission node. Omit for public.
- `aliases(String...)` / `alias(String)` / `withAliases(String...)` — Alternative names.
- `playersOnly()` — Shortcut for `playerOnly(true)`: restricts execution to players.
- `playerOnly(boolean)` — Restrict to type; use guards for more complex checks.
- `sendErrors(boolean)` — If true, errors are sent to the sender using message provider.
- `messages(MessageProvider)` / `withMessages(MessageProvider)` — Plug custom i18n/messages.

#### Help and Usage

- `enableHelp(boolean)` — If enabled, the command auto‑generates help messages and usage.
- `helpPageSize(int)` — Page size for help pagination.
- `validateOnTab(boolean)` — Validate inputs during tab completion for smarter suggestions.
- `sanitizeInputs(boolean)` — Normalize/trim user inputs; see Overview.
- `fuzzySubcommandMatching(boolean)` — Suggest closest subcommand if unknown.
- `fuzzyMatchThreshold(double)` — Set similarity threshold (0.0-1.0) for fuzzy matching. Default is 0.6.
- `awaitConfirmation(boolean)` — Require the user to execute the command twice within a timeout period to confirm execution. Useful for destructive or irreversible commands.
- `debugMode(boolean)` — Enable diagnostic logging (e.g., fuzzy match results). Disabled by default for production.

#### Arguments

Positional arguments are declared in order. Supported convenience builders:

- `argInt(String)`, `argLong(String)`, `argDouble(String)`, `argFloat(String)`, `argBoolean(String)`
- `argString(String)` — By default consumes a single token; configure `ArgContext` for greedy etc.
- `argUUID(String)`
- `argDuration(String)` — Parses time strings like `30s`, `5m`, `2h`, `1d`, `1w`, `1mo`, `1y`, and combinations like `2h30m` to milliseconds.
- Bukkit types: `argPlayer(String)`, `argOfflinePlayer(String)`, `argWorld(String)`, `argMaterial(String)`
- Enums: `argEnum(String, Class<E>)`
- Ranges/length: `argIntRange(String, min, max)`, `argLongRange(...)`, `argDoubleRange(...)`, `argFloatRange(...)`, `argStringLength(String, min, max)`
- Choices: `argChoices(String name, Map<String,T> choices, String displayType)` to map user‑facing keys to arbitrary values.
- Command choices: `argCommandChoices(String name, Map<String, SlashCommand> choices)` to pick one subcommand by name.
- Page helpers: `argPage()`, `argPage(String)`, `argPage(String, int defaultPage)` integrate with pagination.
- Generic/custom: `arg(String name, ArgumentParser<T> parser)` and variants with `ArgContext`.
- Conditional: `argIf(String name, ArgumentParser<T> parser, Predicate<CommandContext> condition)` to include an arg depending on previously parsed values or sender environment.

Each argument has an optional `ArgContext` to influence nullability, optionality, greedy behavior, default values, completion, display, etc.

#### Flags

- `flag(Flag flag)` / `withFlag(Flag)` — Add a pre‑built flag.
- `flag(String name, char shortForm, String longForm)` — Convenience to declare a flag with `-s` and `--long` forms.
- `flagShort(String name, char shortForm)` — `-x` only.
- `flagLong(String name, String longForm)` — `--example` only.

Flags are boolean toggles. Read them at runtime via `CommandContext.getFlag(name)`.

#### Key‑Value Options

- `keyValue(KeyValue<?>)` / `withKeyValue(KeyValue<?>)` — Add a typed `key=value` option.
- Convenience factories:
  - `keyValueString(String name)` / `keyValueString(String name, String default)`
  - `keyValueInt(String name)` / `keyValueInt(String name, int default)`
  - `keyValueBoolean(String name)` / `keyValueBoolean(String name, boolean default)`
  - `keyValueEnum(String name, Class<E>)` / `keyValueEnum(String name, Class<E>, E default)`

Retrieve at runtime via `CommandContext.getKeyValue*(...)` or `getKeyValue(name, Class)`.

#### Subcommands

- `sub(SlashCommand... subs)` / `withSubcommands(...)` — Attach subcommands. Subcommands can have their own args/flags/etc. They inherit plugin registration under the parent.
- `subIf(BooleanSupplier condition, SlashCommand... subs)` — Conditionally register subcommands based on a predicate. Useful for feature-flag or config-dependent registration.
- `subIf(JavaPlugin plugin, Predicate<JavaPlugin> condition, SlashCommand... subs)` — Conditionally register subcommands with plugin access in the condition.
- `parent(SlashCommandBuilder parentBuilder)` — Register this command as a subcommand to the specified parent builder. This is the reverse operation of `sub()`, allowing a subcommand to register itself to its parent instead of the parent registering its children.

#### Execution

- `executes(CommandAction action)` — Synchronous execution block. Receives `CommandContext`.
- `async(boolean)` — Mark command as async; typically combine with `executesAsync(...)`.
- `executesAsync(CommandAction action)` — Convenience: run the same `CommandAction` on an async dispatcher.
- `executesAsync(AsyncCommandAction action)` — Advanced async API receiving progress/cancellation.
- `executesAsync(AsyncCommandAction action, long timeoutMillis)` — Async with timeout.

#### Cooldowns

- `perUserCooldown(long cooldownMillis)` — Per‑sender throttling (by UUID or name depending on sender type).
- `perServerCooldown(long cooldownMillis)` — Global throttling across all senders.

#### Metrics

- `enableMetrics(boolean)` — Enable execution metrics collection. Default: false.

When enabled, access metrics via `SlashCommand.metrics()`:

```java
SlashCommand cmd = SlashCommand.create("example")
    .enableMetrics(true)
    .executes(ctx -> { /* ... */ })
    .build();

// Later, retrieve metrics
CommandMetrics metrics = cmd.metrics();

// Get individual statistics
long total = metrics.getTotalExecutions();
long successful = metrics.getSuccessfulExecutions();
long failed = metrics.getFailedExecutions();
double successRate = metrics.getSuccessRate();  // 0-100%
double avgTime = metrics.getAverageExecutionTimeMs();
long minTime = metrics.getMinExecutionTimeMs();
long maxTime = metrics.getMaxExecutionTimeMs();
long firstExec = metrics.getFirstExecutionTime();  // Timestamp
long lastExec = metrics.getLastExecutionTime();    // Timestamp

// Get error count by type
long permErrors = metrics.getErrorCount(ErrorType.NO_PERMISSION);
long validationErrors = metrics.getErrorCount(ErrorType.VALIDATION_FAILED);

// Get all metrics as a map (useful for serialization)
Map<String, Object> snapshot = metrics.getSnapshot();

// Reset metrics
metrics.reset();

// toString() for quick overview
System.out.println(metrics);
// Output: CommandMetrics{total=150, success=142, failed=8, successRate=94.7%, avgTime=12.3ms}
```

Tracked metrics:
- Total/successful/failed executions
- Success rate percentage
- Execution time (average, min, max)
- First and last execution timestamps
- Errors by type (permission, validation, parsing, etc.)

#### Guards and Validation

- `require(Class<? extends CommandSender> type)` — Built‑in guard ensuring sender is instance of `type`. Provides a default error message.
- `require(Guard... guards)` — Provide custom guard(s) executed before parsing or execution.
- `requirePermission(String permission)` — Shortcut to add a permission-based guard.
- `requireAnyPermission(String... permissions)` — Require that the sender has at least one of the specified permissions.
- `requireAllPermissions(String... permissions)` — Require that the sender has all of the specified permissions.
- `addCrossArgumentValidator(CrossArgumentValidator validator)` / `crossValidate(CrossArgumentValidator)` — Validate relationships between arguments after parsing (e.g., `min <= max`).
- `crossValidateChain(CrossArgumentValidator... validators)` — Add multiple validators at once that all must pass.

#### Exceptions and Diagnostics

- `exceptionHandler(ExceptionHandler)` — Override error handling.
- `detailedExceptionHandler(JavaPlugin)` — Install `DetailedExceptionHandler` with plugin context for thread/heap diagnostics.
- `detailedExceptionHandler(DetailedExceptionHandler)` — Provide your own instance.

#### Produce & Register

- `build()` — Produce an immutable `SlashCommand`.
- `register(JavaPlugin plugin)` — Shortcut to build and register in one chain in some flows; typically call on the built `SlashCommand`.
