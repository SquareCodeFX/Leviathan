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

## Best Practices

1. **Use `parse()` when:**
   - You need custom error handling
   - You want to validate before executing
   - You're implementing dry-run functionality
   - You need to log/track parsing errors

2. **Use `parseStrict()` when:**
   - You want cooldown checks without automatic execution
   - You need the full validation pipeline

3. **Use `parse(options)` when:**
   - You need fine-grained control
   - You want to collect all errors
   - You need subcommand routing without execution
   - You're implementing admin tools that validate commands

4. **Use `execute()` when:**
   - You want the standard behavior
   - Automatic error messaging is desired
   - You don't need custom error handling
