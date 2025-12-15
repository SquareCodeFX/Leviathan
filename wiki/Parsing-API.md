# Parsing API - Split Parsing from Execution

The Parsing API allows developers to separate the parsing of command arguments from the execution of the command action. This provides more control over error handling, validation, and execution flow.

## Overview

Traditionally, the `execute()` method handles both parsing and execution:
- Parses arguments
- Sends error messages directly to the user
- Executes the command action

The new Parsing API provides methods that:
- Parse arguments without executing
- Return errors as a list instead of sending them directly
- Allow custom error handling
- Support dry-run/preview functionality

## Core Classes

### CommandParseResult

Represents the result of parsing command arguments. Contains either a successful `CommandContext` or a list of `CommandParseError`s.

```java
CommandParseResult result = command.parse(sender, label, args);

if (result.isSuccess()) {
    CommandContext ctx = result.context();
    // Use the parsed context
} else {
    List<CommandParseError> errors = result.errors();
    // Handle errors
}
```

#### Key Methods

| Method | Description |
|--------|-------------|
| `isSuccess()` | Returns true if parsing succeeded |
| `isFailure()` | Returns true if parsing failed |
| `context()` | Returns the parsed context (null if failed) |
| `contextOrThrow()` | Returns context or throws exception |
| `errors()` | Returns list of parsing errors |
| `firstError()` | Returns the first error (or null) |
| `errorCount()` | Returns the number of errors |

#### Fluent API

```java
command.parse(sender, label, args)
    .ifSuccess(ctx -> {
        // Execute logic with parsed context
        Player target = ctx.get("target", Player.class);
        int amount = ctx.getIntOrDefault("amount", 1);
    })
    .ifFailure(errors -> {
        // Handle errors without automatic messaging
        errors.forEach(error -> {
            logger.warn("Parse error: " + error.message());
        });
    });
```

#### Execution Helper

```java
command.parse(sender, label, args)
    .executeIfSuccess(sender, (s, ctx) -> {
        s.sendMessage("Command executed successfully!");
    });
```

#### Mapping and Transformation

```java
// Map to a different type
Optional<String> playerName = result.map(ctx ->
    ctx.get("target", Player.class).getName()
);

// Map with default
String name = result.mapOrDefault(
    ctx -> ctx.get("target", Player.class).getName(),
    "unknown"
);
```

#### Error Utilities

```java
// Get all error messages
List<String> messages = result.errorMessages();

// Join messages
String joined = result.joinedErrorMessages(", ");

// Send errors to user manually
result.sendErrorsTo(sender);

// Send only first error
result.sendFirstErrorTo(sender);
```

### CommandParseError

Represents a single parsing error with detailed information.

```java
CommandParseError error = CommandParseError.parsing("count", "Invalid integer value")
    .forArgument("count")
    .withInput("abc")
    .withSuggestions("1", "10", "100");
```

#### Factory Methods

| Method | Description |
|--------|-------------|
| `of(type, message)` | Create generic error |
| `permission(message)` | Permission denied error |
| `playerOnly(message)` | Player-only command error |
| `guardFailed(message)` | Guard check failed |
| `cooldown(message)` | Cooldown active error |
| `confirmationRequired(message)` | Confirmation needed |
| `parsing(argName, message)` | Argument parsing error |
| `validation(argName, message)` | Validation error |
| `argumentPermission(argName, message)` | Argument permission error |
| `usage(message)` | Usage/syntax error |
| `crossValidation(message)` | Cross-argument validation error |
| `internal(message)` | Internal error |
| `subcommandNotFound(name, message)` | Unknown subcommand |

#### Builder Methods (Immutable)

```java
// Add argument name
error.forArgument("count");

// Add raw input
error.withInput("abc");

// Add suggestions
error.withSuggestions("1", "10", "100");
error.withSuggestions(List.of("red", "green", "blue"));

// Change message
error.withMessage("New message");
```

#### Accessors

| Method | Description |
|--------|-------------|
| `type()` | Get the error type (ErrorType enum) |
| `message()` | Get the error message |
| `argumentName()` | Get the argument name (or null) |
| `rawInput()` | Get the raw input (or null) |
| `suggestions()` | Get "did you mean" suggestions |
| `hasArgumentName()` | Check if argument name is set |
| `hasRawInput()` | Check if raw input is set |
| `hasSuggestions()` | Check if suggestions available |
| `isAccessError()` | Check if access-related error |
| `isInputError()` | Check if input-related error |

#### Display Methods

```java
// Formatted debug string
String formatted = error.toFormattedString();
// "[PARSING] Argument 'count': Invalid integer (input: 'abc') Did you mean: 1, 10, 100?"

// User-friendly message
String userMsg = error.toUserMessage();
// "Invalid integer Did you mean: 1, 10, 100?"
```

### ParseOptions

Configuration options for customizing the parsing process.

```java
ParseOptions options = ParseOptions.builder()
    .checkCooldowns(true)
    .includeSubcommands(true)
    .collectAllErrors(true)
    .includeSuggestions(true)
    .checkConfirmation(false)
    .skipGuards(false)
    .skipPermissionChecks(false)
    .build();

CommandParseResult result = command.parse(sender, label, args, options);
```

#### Preset Options

| Preset | Description |
|--------|-------------|
| `ParseOptions.DEFAULT` | Basic parsing, fail-fast, with suggestions |
| `ParseOptions.STRICT` | Includes cooldowns, subcommands, suggestions |
| `ParseOptions.LENIENT` | Collect all errors, include suggestions |

#### Options Reference

| Option | Default | Description |
|--------|---------|-------------|
| `checkCooldowns` | false | Check server/user cooldowns |
| `includeSubcommands` | false | Route to subcommands |
| `collectAllErrors` | false | Collect all errors vs fail-fast |
| `includeSuggestions` | true | Include "did you mean" suggestions |
| `checkConfirmation` | false | Check confirmation requirements |
| `skipGuards` | false | Skip guard checks |
| `skipPermissionChecks` | false | Skip permission checks |

## Parsing Methods

### parse(sender, label, args)

Basic parsing without cooldowns or subcommand routing.

```java
CommandParseResult result = command.parse(sender, label, args);
```

**Performs:**
- Permission checks (command and argument level)
- Player-only validation
- Guard evaluation
- Flag and key-value parsing
- Positional argument parsing with validation
- Cross-argument validation

**Does NOT perform:**
- Cooldown checks
- Confirmation handling
- Command execution
- Before/after execution hooks

### parse(sender, label, args, options)

Parsing with configurable options.

```java
ParseOptions options = ParseOptions.builder()
    .checkCooldowns(true)
    .includeSubcommands(true)
    .collectAllErrors(true)
    .build();

CommandParseResult result = command.parse(sender, label, args, options);
```

### parseStrict(sender, label, args)

Parsing with cooldown checks (equivalent to `ParseOptions.STRICT` without subcommands).

```java
CommandParseResult result = command.parseStrict(sender, label, args);
```

### parseAndExecute(sender, label, args, action)

Parse and execute with custom action, returning result for error handling.

```java
command.parseAndExecute(sender, label, args, (s, ctx) -> {
    Player target = ctx.require("target", Player.class);
    target.sendMessage("Hello!");
}).ifFailure(errors -> {
    sender.sendMessage("§cCommand failed: " + errors.get(0).message());
});
```

## Usage Examples

### Basic Usage

```java
SlashCommand giveCommand = SlashCommand.builder("give")
    .arg("player", ArgParsers.playerParser())
    .arg("item", ArgParsers.materialParser())
    .arg("amount", ArgParsers.intParser(), ArgContext.builder()
        .optional(true)
        .defaultValue(1)
        .intRange(1, 64)
        .build())
    .register(plugin);

// Later, in custom handling code:
CommandParseResult result = giveCommand.parse(sender, "give", args);

if (result.isSuccess()) {
    CommandContext ctx = result.context();
    Player target = ctx.require("player", Player.class);
    Material item = ctx.require("item", Material.class);
    int amount = ctx.getIntOrDefault("amount", 1);

    // Custom logic before giving item
    if (economy.getBalance(sender) < calculatePrice(item, amount)) {
        sender.sendMessage("§cYou cannot afford this!");
        return;
    }

    target.getInventory().addItem(new ItemStack(item, amount));
    sender.sendMessage("§aGave " + amount + " " + item + " to " + target.getName());
} else {
    // Custom error display
    result.sendFirstErrorTo(sender);
}
```

### Collecting All Errors

```java
ParseOptions options = ParseOptions.builder()
    .collectAllErrors(true)
    .includeSuggestions(true)
    .build();

CommandParseResult result = command.parse(sender, label, args, options);

if (result.isFailure()) {
    sender.sendMessage("§cThe following errors occurred:");
    for (CommandParseError error : result.errors()) {
        sender.sendMessage("§7- §c" + error.toUserMessage());
    }
}
```

### Dry-Run Validation

```java
// Validate input without executing (skip guards)
ParseOptions dryRun = ParseOptions.builder()
    .skipGuards(true)
    .skipPermissionChecks(true)
    .build();

CommandParseResult result = command.parse(sender, label, args, dryRun);

if (result.isSuccess()) {
    sender.sendMessage("§aInput is valid! Execute with /confirm");
    // Store pending command for later execution
    pendingCommands.put(sender.getName(), args);
}
```

### Custom Error Logging

```java
command.parse(sender, label, args)
    .ifSuccess(ctx -> executeCommand(sender, ctx))
    .ifFailure(errors -> {
        // Log errors for analytics
        for (CommandParseError error : errors) {
            analytics.trackError(
                error.type(),
                error.argumentName(),
                error.rawInput()
            );
        }

        // Show user-friendly message
        sender.sendMessage("§cInvalid command. " +
            (errors.get(0).hasSuggestions()
                ? "Did you mean: " + String.join(", ", errors.get(0).suggestions())
                : "Use /help for syntax."));
    });
```

### Subcommand Routing

```java
// Enable subcommand routing in parse
ParseOptions withSubcommands = ParseOptions.builder()
    .includeSubcommands(true)
    .includeSuggestions(true)
    .build();

CommandParseResult result = parentCommand.parse(sender, label, args, withSubcommands);
// Will automatically route to subcommands and return their parse result
```

### Error Filtering

```java
CommandParseResult result = command.parse(sender, label, args);

if (result.isFailure()) {
    // Get only permission errors
    List<CommandParseError> permissionErrors = result.errorsByType(ErrorType.PERMISSION);

    // Get only input errors
    List<CommandParseError> inputErrors = result.errorsByCategory(ErrorType.ErrorCategory.INPUT);

    // Get errors for specific argument
    List<CommandParseError> countErrors = result.errorsForArgument("count");

    // Check error categories
    if (result.hasAccessErrors()) {
        sender.sendMessage("§cYou don't have permission!");
    } else if (result.hasInputErrors()) {
        sender.sendMessage("§cInvalid input: " + result.firstError().message());
    }
}
```

## Feature Comparison

| Feature | `execute()` | `parse()` | `parse(options)` |
|---------|-------------|-----------|------------------|
| Permission check | ✅ | ✅ | ✅ (configurable) |
| Player-only check | ✅ | ✅ | ✅ |
| Guards | ✅ | ✅ | ✅ (configurable) |
| Cooldowns | ✅ | ❌ | ✅ (configurable) |
| Confirmation | ✅ | ❌ | ✅ (configurable) |
| Subcommands | ✅ | ❌ | ✅ (configurable) |
| Auto-help | ✅ | ❌ | ❌ |
| Flag/KV parsing | ✅ | ✅ | ✅ |
| Argument parsing | ✅ | ✅ | ✅ |
| Validation | ✅ | ✅ | ✅ |
| Cross-validation | ✅ | ✅ | ✅ |
| Did-you-mean | ✅ | ✅ | ✅ (configurable) |
| Collect all errors | ❌ | ❌ | ✅ (configurable) |
| Before hooks | ✅ | ❌ | ❌ |
| After hooks | ✅ | ❌ | ❌ |
| Sends messages | ✅ | ❌ | ❌ |
| Returns result | ❌ | ✅ | ✅ |

## Unit Testing with Parse API

The Parse API is designed to be highly testable. Since it returns a `CommandParseResult` object instead of sending messages directly, you can easily write unit tests to verify parsing behavior.

### Basic Test Setup

```java
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GiveCommandTest {

    private SlashCommand giveCommand;
    private Player mockPlayer;

    @BeforeEach
    void setUp() {
        giveCommand = SlashCommand.builder("give")
            .arg("player", ArgParsers.playerParser())
            .arg("amount", ArgParsers.intParser(), ArgContext.builder()
                .intRange(1, 64)
                .build())
            .build();

        mockPlayer = mock(Player.class);
        when(mockPlayer.hasPermission(anyString())).thenReturn(true);
        when(mockPlayer.getName()).thenReturn("TestPlayer");
    }

    @Test
    void testValidArgs_ShouldSucceed() {
        String[] args = {"Notch", "10"};

        CommandParseResult result = giveCommand.parse(mockPlayer, "give", args);

        assertTrue(result.isSuccess());
        assertNotNull(result.context());
        assertEquals(10, result.context().require("amount", Integer.class));
    }

    @Test
    void testInvalidAmount_ShouldFail() {
        String[] args = {"Notch", "100"}; // Over max of 64

        CommandParseResult result = giveCommand.parse(mockPlayer, "give", args);

        assertTrue(result.isFailure());
        assertEquals(1, result.errorCount());
        assertEquals(ErrorType.VALIDATION, result.firstError().type());
    }

    @Test
    void testMissingArgs_ShouldFail() {
        String[] args = {}; // No arguments

        CommandParseResult result = giveCommand.parse(mockPlayer, "give", args);

        assertTrue(result.isFailure());
        assertEquals(ErrorType.USAGE, result.firstError().type());
    }
}
```

### Testing with ParseOptions

```java
@Test
void testCollectAllErrors() {
    String[] args = {"invalid_player", "invalid_amount"};
    ParseOptions options = ParseOptions.builder()
        .collectAllErrors(true)
        .build();

    CommandParseResult result = command.parse(mockPlayer, "cmd", args, options);

    assertTrue(result.isFailure());
    assertTrue(result.errorCount() > 1); // Multiple errors collected
}

@Test
void testSkipPermissions_ForAdminTool() {
    // Player without permission
    when(mockPlayer.hasPermission("admin.give")).thenReturn(false);

    String[] args = {"Notch", "10"};
    ParseOptions options = ParseOptions.builder()
        .skipPermissionChecks(true)
        .build();

    // Should succeed despite missing permission
    CommandParseResult result = command.parse(mockPlayer, "give", args, options);
    assertTrue(result.isSuccess());
}

@Test
void testSkipGuards_ForDryRun() {
    // Guard that would normally fail
    SlashCommand cmd = SlashCommand.builder("restricted")
        .guard(Guard.of(s -> false, "Access denied"))
        .arg("value", ArgParsers.stringParser())
        .build();

    ParseOptions dryRun = ParseOptions.builder()
        .skipGuards(true)
        .build();

    CommandParseResult result = cmd.parse(mockPlayer, "restricted", new String[]{"test"}, dryRun);
    assertTrue(result.isSuccess()); // Guards were skipped
}
```

### Testing Error Details

```java
@Test
void testErrorDetails() {
    String[] args = {"abc"}; // Invalid integer

    CommandParseResult result = command.parse(mockPlayer, "cmd", args);

    assertTrue(result.isFailure());
    CommandParseError error = result.firstError();

    assertEquals(ErrorType.PARSING, error.type());
    assertEquals("amount", error.argumentName());
    assertEquals("abc", error.rawInput());
    assertTrue(error.isInputError());
}

@Test
void testSuggestions() {
    // Command with predefined choices
    SlashCommand cmd = SlashCommand.builder("color")
        .arg("color", ArgParsers.choiceParser("red", "green", "blue"),
             ArgContext.builder().didYouMean(true).build())
        .build();

    ParseOptions options = ParseOptions.builder()
        .includeSuggestions(true)
        .build();

    CommandParseResult result = cmd.parse(mockPlayer, "color", new String[]{"rad"}, options);

    assertTrue(result.isFailure());
    assertTrue(result.firstError().hasSuggestions());
    assertTrue(result.firstError().suggestions().contains("red"));
}
```

### Testing Subcommands

```java
@Test
void testSubcommandRouting() {
    SlashCommand parent = SlashCommand.builder("admin")
        .subcommand(SlashCommand.builder("kick")
            .arg("player", ArgParsers.playerParser())
            .build())
        .subcommand(SlashCommand.builder("ban")
            .arg("player", ArgParsers.playerParser())
            .arg("reason", ArgParsers.greedyStringParser())
            .build())
        .build();

    ParseOptions options = ParseOptions.builder()
        .includeSubcommands(true)
        .build();

    // Test kick subcommand
    CommandParseResult kickResult = parent.parse(mockPlayer, "admin",
        new String[]{"kick", "Notch"}, options);
    assertTrue(kickResult.isSuccess());

    // Test invalid subcommand
    CommandParseResult invalidResult = parent.parse(mockPlayer, "admin",
        new String[]{"invalid"}, options);
    assertTrue(invalidResult.isFailure());
    assertEquals(ErrorType.USAGE, invalidResult.firstError().type());
}
```

### Testing Cross-Argument Validation

```java
@Test
void testCrossValidation() {
    SlashCommand cmd = SlashCommand.builder("range")
        .arg("min", ArgParsers.intParser())
        .arg("max", ArgParsers.intParser())
        .crossValidate(ctx -> {
            int min = ctx.require("min", Integer.class);
            int max = ctx.require("max", Integer.class);
            return min > max ? "min must be less than max" : null;
        })
        .build();

    // Valid: min < max
    CommandParseResult valid = cmd.parse(mockPlayer, "range", new String[]{"1", "10"});
    assertTrue(valid.isSuccess());

    // Invalid: min > max
    CommandParseResult invalid = cmd.parse(mockPlayer, "range", new String[]{"10", "1"});
    assertTrue(invalid.isFailure());
    assertEquals(ErrorType.CROSS_VALIDATION, invalid.firstError().type());
}
```

### Testing Flags and Key-Values

```java
@Test
void testFlagsAndKeyValues() {
    SlashCommand cmd = SlashCommand.builder("search")
        .arg("query", ArgParsers.stringParser())
        .flag(Flag.builder("silent").shortForm('s').build())
        .keyValue(KeyValue.ofInt("limit").defaultValue(10).build())
        .build();

    String[] args = {"hello", "-s", "--limit=5"};
    CommandParseResult result = cmd.parse(mockPlayer, "search", args);

    assertTrue(result.isSuccess());
    CommandContext ctx = result.context();
    assertEquals("hello", ctx.require("query", String.class));
    assertTrue(ctx.getFlag("silent"));
    assertEquals(5, ctx.getKeyValue("limit", Integer.class));
}
```

### Testing Optional Arguments with Defaults

```java
@Test
void testOptionalWithDefaults() {
    SlashCommand cmd = SlashCommand.builder("greet")
        .arg("name", ArgParsers.stringParser())
        .arg("times", ArgParsers.intParser(), ArgContext.builder()
            .optional(true)
            .defaultValue(1)
            .build())
        .build();

    // Without optional arg
    CommandParseResult result1 = cmd.parse(mockPlayer, "greet", new String[]{"World"});
    assertTrue(result1.isSuccess());
    assertEquals(1, result1.context().require("times", Integer.class));

    // With optional arg
    CommandParseResult result2 = cmd.parse(mockPlayer, "greet", new String[]{"World", "3"});
    assertTrue(result2.isSuccess());
    assertEquals(3, result2.context().require("times", Integer.class));
}
```

### Mocking CommandSender for Tests

```java
// Create a mock player with permissions
Player mockPlayer() {
    Player player = mock(Player.class);
    when(player.hasPermission(anyString())).thenReturn(true);
    when(player.getName()).thenReturn("TestPlayer");
    return player;
}

// Create a mock console sender
ConsoleCommandSender mockConsole() {
    ConsoleCommandSender console = mock(ConsoleCommandSender.class);
    when(console.hasPermission(anyString())).thenReturn(true);
    when(console.getName()).thenReturn("Console");
    return console;
}

@Test
void testPlayerOnlyCommand() {
    SlashCommand cmd = SlashCommand.builder("fly")
        .playerOnly(true)
        .build();

    // Console should fail
    CommandParseResult consoleResult = cmd.parse(mockConsole(), "fly", new String[]{});
    assertTrue(consoleResult.isFailure());
    assertEquals(ErrorType.PLAYER_ONLY, consoleResult.firstError().type());

    // Player should succeed
    CommandParseResult playerResult = cmd.parse(mockPlayer(), "fly", new String[]{});
    assertTrue(playerResult.isSuccess());
}
```

## Complete Feature Support Matrix

| SlashCommand Feature | `parse()` | `parse(options)` | Notes |
|---------------------|-----------|------------------|-------|
| **Access Control** ||||
| Permission check | ✅ | ✅ (skip-able) | Can skip for admin tools |
| Player-only check | ✅ | ✅ | Never skipped (type check) |
| Guards | ✅ | ✅ (skip-able) | Can skip for dry-run |
| Cooldowns (server) | ❌ | ✅ | When `checkCooldowns=true` |
| Cooldowns (user) | ❌ | ✅ | When `checkCooldowns=true` |
| Confirmation | ❌ | ✅ | When `checkConfirmation=true` |
| **Argument Parsing** ||||
| Positional args | ✅ | ✅ | Full support |
| Optional args | ✅ | ✅ | With default values |
| Greedy args | ✅ | ✅ | Captures remaining tokens |
| Conditional args | ✅ | ✅ | Based on context |
| Per-arg permission | ✅ | ✅ (skip-able) | Can skip for admin tools |
| Transformers | ✅ | ✅ | Full support |
| **Flags & Options** ||||
| Boolean flags | ✅ | ✅ | `-f`, `--flag`, `--no-flag` |
| Key-value pairs | ✅ | ✅ | `key=value`, `--key value` |
| Multi-value | ✅ | ✅ | `key=a,b,c` |
| **Validation** ||||
| Range validation | ✅ | ✅ | int, long, double, float |
| String validation | ✅ | ✅ | length, pattern |
| Custom validators | ✅ | ✅ | Via `Validator<T>` |
| Cross-arg validation | ✅ | ✅ | Full support |
| **Subcommands** ||||
| Subcommand routing | ❌ | ✅ | When `includeSubcommands=true` |
| Fuzzy matching | ❌ | ✅ | When subcommands enabled |
| **Error Handling** ||||
| Fail-fast | ✅ | ✅ | Default behavior |
| Collect all errors | ❌ | ✅ | When `collectAllErrors=true` |
| Did-you-mean | ✅ | ✅ | When `includeSuggestions=true` |
| **Input Processing** ||||
| Input sanitization | ✅ | ✅ | When command has it enabled |

## Best Practices

1. **Use `parse()` when:**
   - You need custom error handling
   - You want to validate before executing
   - You're implementing dry-run functionality
   - You need to log/track parsing errors
   - **You're writing unit tests**

2. **Use `parseStrict()` when:**
   - You want cooldown checks without automatic execution
   - You need the full validation pipeline

3. **Use `parse(options)` when:**
   - You need fine-grained control
   - You want to collect all errors
   - You need subcommand routing without execution
   - You're implementing admin tools that validate commands
   - **You're testing complex scenarios**

4. **Use `execute()` when:**
   - You want the standard behavior
   - Automatic error messaging is desired
   - You don't need custom error handling

## Thread Safety

The parse methods are thread-safe with the following considerations:

- `parse()` and `parse(options)` are pure functions (no side effects)
- `parseStrict()` checks cooldowns but doesn't update them
- `parseAndExecute()` updates cooldowns after successful parse
- Confirmation state is managed with `ConcurrentHashMap`

This makes the Parse API ideal for:
- Parallel test execution
- Async validation in background threads
- Preview/dry-run scenarios

---

## Advanced Features

### Parse Metrics

Track parsing performance with `ParseMetrics`. Enable metrics collection in `ParseOptions`:

```java
ParseOptions options = ParseOptions.builder()
    .collectMetrics(true)
    .build();

CommandParseResult result = command.parse(sender, label, args, options);
ParseMetrics metrics = result.metrics();

if (metrics.isEnabled()) {
    System.out.println("Total parse time: " + metrics.totalTimeMillis() + "ms");
    System.out.println("Permission check: " + metrics.permissionCheckTimeNanos() + "ns");
    System.out.println("Argument parsing: " + metrics.argumentParseTimeNanos() + "ns");
    System.out.println("Arguments parsed: " + metrics.argumentsParsed());
    System.out.println("Errors encountered: " + metrics.errorsEncountered());
}
```

### Async Parsing

Parse commands asynchronously using `CompletableFuture`:

```java
// Basic async parsing
command.parseAsync(sender, label, args)
    .thenAccept(result -> {
        if (result.isSuccess()) {
            // Handle on async thread
        }
    });

// Async parsing with options
command.parseAsync(sender, label, args, ParseOptions.STRICT)
    .thenAccept(result -> processResult(result));

// Async parse and execute
command.parseAndExecuteAsync(sender, label, args, (s, ctx) -> {
    // Execute on async thread
    // Remember to schedule back to main thread for Bukkit API calls
});
```

### Parse Simulation

Test how commands would parse for different senders:

```java
// Parse as a different sender
CommandParseResult result = command.parseAs(targetPlayer, "give", args);
if (result.hasAccessErrors()) {
    admin.sendMessage("Player lacks permission for this command");
}

// Parse for multiple senders at once
List<CommandSender> players = getOnlinePlayers();
Map<CommandSender, CommandParseResult> results = command.parseForAll(players, "give", args);

// Find all players who can execute this command
List<CommandSender> eligible = results.entrySet().stream()
    .filter(e -> e.getValue().isSuccess())
    .map(Map.Entry::getKey)
    .collect(Collectors.toList());

// Async version for large player lists
command.parseForAllAsync(players, label, args)
    .thenAccept(results -> {
        // Process results on async thread
    });
```

### Partial Parsing

Parse only a subset of arguments, useful for progressive validation:

```java
// Parse first 2 arguments only
PartialParseResult result = command.parsePartial(sender, label, args,
    PartialParseOptions.firstN(2));

// Parse until first error
PartialParseResult result = command.parseUntilError(sender, label, args);

// Get what was successfully parsed even with errors
Map<String, Object> parsed = result.parsedArguments();

// Check where parsing failed
if (result.hasErrors()) {
    int errorIndex = result.errorArgumentIndex();
    sender.sendMessage("Error at argument " + errorIndex);
}

// Full options control
PartialParseOptions options = PartialParseOptions.builder()
    .startAtArgument(1)
    .maxArguments(3)
    .stopOnFirstError(true)
    .skipPermissionChecks(true)
    .includePartialContext(true)
    .build();

PartialParseResult result = command.parsePartial(sender, label, args, options);
```

### Auto-Correction

Automatically correct typos in argument values:

```java
ParseOptions options = ParseOptions.builder()
    .enableAutoCorrection(true)
    .autoCorrectThreshold(0.8)  // 80% similarity required
    .maxAutoCorrections(3)      // Max 3 corrections per parse
    .build();

// If player types "diamnod" and "diamond" is a valid choice,
// it will be auto-corrected to "diamond"
CommandParseResult result = command.parse(sender, label, args, options);
```

### Validation Profiles

Define reusable validation rules:

```java
// Create a profile for admin commands
ValidationProfile adminProfile = ValidationProfile.builder()
    .name("admin")
    .requirePermission("admin.commands")
    .requirePlayer(false)
    .requireArgumentNonNull("target")
    .requirePositive("amount")
    .requireInRange("level", 1, 100)
    .requireNotBlank("reason")
    .addGlobalRule(
        result -> result.context().argument("amount") != null,
        "Amount is required for admin commands"
    )
    .build();

// Validate a result against the profile
CommandParseResult result = command.parse(sender, label, args);
List<CommandParseError> validationErrors = adminProfile.validate(result);

// Or apply the profile and get updated result
CommandParseResult validated = adminProfile.applyTo(result);

// Check validity
if (adminProfile.isValid(result)) {
    // Proceed with execution
}
```

### Result Utilities

Combine and transform parse results:

```java
// Combine multiple results
CommandParseResult combined = CommandParseResult.combine(result1, result2, result3);

// Combine instance method
CommandParseResult merged = result1.combineWith(result2);

// Transform errors
CommandParseResult transformed = result
    .mapErrors(error -> error.withMessage("Custom: " + error.message()))
    .prefixErrors("[Command] ");

// Filter errors
CommandParseResult filtered = result
    .filterErrors(e -> e.type() != ErrorType.INTERNAL)
    .excludeErrorType(ErrorType.PERMISSION)
    .onlyErrorType(ErrorType.VALIDATION);
```

### ParseResultBuilder (For Testing)

Build mock parse results for unit tests:

```java
// Create a successful result for testing
CommandParseResult mockResult = ParseResultBuilder.success()
    .withArgument("player", mockPlayer)
    .withArgument("amount", 10)
    .withFlag("silent", true)
    .withKeyValue("reason", "test")
    .withRawArgs("Notch", "10", "-s", "--reason=test")
    .build();

// Create a failure result for testing error handling
CommandParseResult errorResult = ParseResultBuilder.failure()
    .withParsingError("amount", "Invalid number")
    .withValidationError("player", "Player not found")
    .withRawArgs("invalid", "abc")
    .build();

// Copy and modify existing result
CommandParseResult modified = ParseResultBuilder.from(originalResult)
    .withArgument("amount", 20)
    .build();
```

---

## Complete API Reference

### CommandParseResult Methods

| Method | Description |
|--------|-------------|
| `isSuccess()` / `isFailure()` | Check parse outcome |
| `context()` / `contextOrThrow()` | Get parsed context |
| `optionalContext()` | Get context as Optional |
| `errors()` / `firstError()` | Get parsing errors |
| `errorCount()` / `hasErrors()` | Error statistics |
| `rawArgs()` | Get original arguments |
| `metrics()` | Get parsing metrics |
| `errorsByType()` / `errorsByCategory()` | Filter errors |
| `errorsForArgument()` | Get errors for specific arg |
| `hasAccessErrors()` / `hasInputErrors()` | Check error types |
| `ifSuccess()` / `ifFailure()` | Fluent callbacks |
| `executeIfSuccess()` | Execute action if success |
| `map()` / `mapOrDefault()` | Transform context |
| `orElse()` / `orElseThrow()` | Get context or default |
| `errorMessages()` / `joinedErrorMessages()` | Get error text |
| `formattedErrors()` | Get formatted errors |
| `sendErrorsTo()` / `sendFirstErrorTo()` | Send to sender |
| `mapErrors()` / `filterErrors()` | Transform errors |
| `excludeErrorType()` / `onlyErrorType()` | Filter by type |
| `mapErrorMessages()` / `prefixErrors()` | Modify messages |
| `combine()` / `combineWith()` | Merge results |

### SlashCommand Parse Methods

| Method | Description |
|--------|-------------|
| `parse(sender, label, args)` | Basic parsing |
| `parse(sender, label, args, options)` | Parsing with options |
| `parseStrict(sender, label, args)` | Parse with cooldowns |
| `parseAndExecute(sender, label, args, action)` | Parse and execute |
| `parseAsync(...)` | Async parsing variants |
| `parseAndExecuteAsync(...)` | Async parse+execute |
| `parseAs(sender, label, args)` | Parse as different sender |
| `parseForAll(senders, label, args)` | Parse for multiple senders |
| `parseForAllAsync(...)` | Async multi-sender parsing |
| `parsePartial(sender, label, args, options)` | Partial parsing |
| `parseFirstN(sender, label, args, count)` | Parse first N args |
| `parseUntilError(sender, label, args)` | Parse until error |

### ParseOptions

| Option | Default | Description |
|--------|---------|-------------|
| `checkCooldowns` | false | Check cooldowns |
| `includeSubcommands` | false | Route subcommands |
| `collectAllErrors` | false | Collect vs fail-fast |
| `includeSuggestions` | true | Did-you-mean hints |
| `checkConfirmation` | false | Require confirmation |
| `skipGuards` | false | Skip guard checks |
| `skipPermissionChecks` | false | Skip permissions |
| `enableAutoCorrection` | false | Auto-fix typos |
| `autoCorrectThreshold` | 0.8 | Similarity threshold |
| `maxAutoCorrections` | 3 | Max corrections |
| `collectMetrics` | false | Track performance |

### PartialParseOptions

| Option | Default | Description |
|--------|---------|-------------|
| `startAtArgument` | 0 | Start index |
| `maxArguments` | -1 | Max to parse (-1=all) |
| `stopOnFirstError` | false | Stop at first error |
| `skipPermissionChecks` | false | Skip permissions |
| `skipGuards` | false | Skip guards |
| `includePartialContext` | false | Include partial on failure |

---

## Argument Aliases

Arguments can have aliases, allowing them to be referenced by multiple names.

### Defining Aliases

```java
// Using ArgContext builder
SlashCommand.builder("give")
    .arg("player", playerParser(), ArgContext.builder()
        .aliases("p", "target", "t")
        .build())
    .arg("amount", intParser(), ArgContext.builder()
        .aliases("amt", "count", "n")
        .build())
    .build();

// Using Arg fluent API
Arg<Player> playerArg = Arg.of("player", playerParser())
    .withAliases("p", "target", "t");

Arg<Integer> amountArg = Arg.of("amount", intParser())
    .withAlias("amt")
    .withAlias("n");
```

### Using Aliases in CommandContext

```java
command.execute((sender, ctx) -> {
    // All these return the same value:
    Player target = ctx.get("player", Player.class);
    Player target = ctx.get("p", Player.class);
    Player target = ctx.get("target", Player.class);

    // Same for amount:
    Integer amount = ctx.get("amount", Integer.class);
    Integer amount = ctx.get("amt", Integer.class);
    Integer amount = ctx.get("n", Integer.class);

    // Check if a name is an alias
    boolean isAlias = ctx.isAlias("p"); // true

    // Get the primary name for an alias
    String primary = ctx.getPrimaryName("p"); // "player"
});
```

### Use Cases

1. **Short forms**: `player` → `p`, `amount` → `amt`
2. **Localization**: Same argument accessible via different language names
3. **Backwards compatibility**: Rename arguments without breaking existing code
4. **Developer convenience**: Multiple intuitive names for the same argument

### Arg Methods

| Method | Description |
|--------|-------------|
| `aliases()` | Get list of aliases |
| `hasAliases()` | Check if argument has aliases |
| `matchesNameOrAlias(name)` | Check if name matches primary or alias |
| `allNames()` | Get primary name + all aliases |
| `withAliases(...)` | Create copy with aliases |
| `withAlias(alias)` | Create copy with additional alias |

### CommandContext Methods

| Method | Description |
|--------|-------------|
| `aliasMap()` | Get alias → primary name mapping |
| `isAlias(name)` | Check if name is an alias |
| `getPrimaryName(alias)` | Get primary name for alias |
| `argument(name)` | Get value by name or alias (no type check) |
| `allArguments()` | Get all argument values |
