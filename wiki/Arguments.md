### Arguments

Leviathan provides a rich, type‑safe argument system. You declare positional arguments in order using `SlashCommandBuilder` methods. Parsing, validation, and tab completion are integrated.

#### Built‑in argument helpers

- Numeric: `argInt`, `argLong`, `argDouble`, `argFloat`
- Text: `argString`
- Boolean: `argBoolean`
- UUID: `argUUID`
- Bukkit types: `argPlayer`, `argOfflinePlayer`, `argWorld`, `argMaterial`
- Enums: `argEnum(name, Class<E>)`

Example:

```java
SlashCommand tp = SlashCommand.create("tp")
    .argPlayer("target")
    .argWorld("world")
    .executes(ctx -> {
        Player target = ctx.get("target", Player.class);
        World world = ctx.get("world", World.class);
        // ... teleport logic
    })
    .build();
```

#### Ranges and length constraints

- `argIntRange(name, min, max)`
- `argLongRange(name, min, max)`
- `argDoubleRange(name, min, max)`
- `argFloatRange(name, min, max)`
- `argStringLength(name, minLen, maxLen)`

These fail fast with a descriptive error if violated.

#### Choices and command choices

- `argChoices(name, Map<String,T> choices, String displayType)` — The user selects from keys; you receive the mapped value.
- `argCommandChoices(name, Map<String, SlashCommand> choices)` — Provide a list of subcommands as a choice.

Example:

```java
Map<String, Integer> levels = new LinkedHashMap<>();
levels.put("easy", 1);
levels.put("normal", 2);
levels.put("hard", 3);

SlashCommand set = SlashCommand.create("setdifficulty")
    .argChoices("mode", levels, "difficulty")
    .executes(ctx -> {
        int level = ctx.get("mode", Integer.class);
        // apply difficulty level
    })
    .build();
```

#### Conditional arguments

Use `argIf(name, parser, Predicate<CommandContext> condition)` to include an argument only if the condition matches.

```java
SlashCommand give = SlashCommand.create("give")
    .argPlayer("target")
    .argMaterial("item")
    .argIf("amount", ArgParsers.INT, ctx -> ctx.getFlag("bulk"))
    .flagLong("bulk", "bulk")
    .executes(ctx -> {
        int amount = ctx.getOrDefault("amount", Integer.class, 1);
        // ...
    })
    .build();
```

#### Page arguments

`argPage()`, `argPage(name)`, and `argPage(name, defaultPage)` integrate with the pagination utilities. The parsed value is an `int` page index respecting your configured defaults.

#### Custom parsers

Use `arg(name, ArgumentParser<T> parser)` to plug your own parser. Combine with `ArgContext` for fine‑grained control.

```java
SlashCommand custom = SlashCommand.create("custom")
    .arg("color", ArgParsers.enumParser(ChatColor.class))
    .executes(ctx -> {
        ChatColor c = ctx.get("color", ChatColor.class);
    })
    .build();
```

#### ArgContext

Most `arg*` methods have an overload with `ArgContext` allowing you to specify properties such as optionality, default value, greedy behavior, display name, custom completion list, and more.

##### Basic Properties

```java
ArgContext ctx = ArgContext.builder()
    .optional(true)           // Make argument optional
    .defaultValue(10)         // Default value if not provided
    .greedy(true)             // Consume all remaining tokens (string only, must be last)
    .permission("admin.use")  // Per-argument permission
    .didYouMean(true)         // Enable "did you mean?" suggestions
    .build();
```

##### Validation Constraints

ArgContext supports built-in validation for numeric and string types:

**Numeric ranges:**

```java
// Integer range
ArgContext intCtx = ArgContext.builder()
    .intRange(1, 100)
    .build();

// Or individual bounds
ArgContext intCtx2 = ArgContext.builder()
    .intMin(1)
    .intMax(100)
    .build();

// Long range
ArgContext longCtx = ArgContext.builder()
    .longRange(0L, 1_000_000L)
    .build();

// Double range
ArgContext doubleCtx = ArgContext.builder()
    .doubleRange(0.0, 1.0)
    .build();

// Float range
ArgContext floatCtx = ArgContext.builder()
    .floatRange(0.0f, 100.0f)
    .build();
```

**String constraints:**

```java
// Length constraints
ArgContext lenCtx = ArgContext.builder()
    .stringLengthRange(3, 16)  // 3-16 characters
    .build();

// Regex pattern
ArgContext patternCtx = ArgContext.builder()
    .stringPattern("[a-zA-Z0-9_]+")  // Alphanumeric and underscores only
    .build();

// Using compiled Pattern
ArgContext patternCtx2 = ArgContext.builder()
    .stringPattern(Pattern.compile("^[A-Z]{2,4}$"))
    .build();
```

#### Custom Validators

For complex validation logic beyond simple ranges and patterns, use custom validators.

##### Validator Interface

```java
@FunctionalInterface
public interface Validator<T> {
    /**
     * @param value the value to validate
     * @return null if valid, or an error message if invalid
     */
    @Nullable String validate(@Nullable T value);
}
```

##### Basic Usage

```java
// Validate username format
Validator<String> usernameValidator = value -> {
    if (value == null || value.isEmpty()) {
        return "Username cannot be empty";
    }
    if (value.length() < 3) {
        return "Username must be at least 3 characters";
    }
    if (value.length() > 16) {
        return "Username cannot exceed 16 characters";
    }
    if (!value.matches("^[a-zA-Z0-9_]+$")) {
        return "Username can only contain letters, numbers, and underscores";
    }
    if (bannedNames.contains(value.toLowerCase())) {
        return "This username is not allowed";
    }
    return null;  // Valid
};

SlashCommand register = SlashCommand.create("register")
    .argString("username", ArgContext.builder()
        .addValidator(usernameValidator)
        .build())
    .executes(ctx -> { /* ... */ })
    .build();
```

##### Multiple Validators

You can chain multiple validators — they run in order, and the first error stops validation:

```java
Validator<Integer> positiveValidator = value ->
    value == null || value <= 0 ? "Value must be positive" : null;

Validator<Integer> evenValidator = value ->
    value != null && value % 2 != 0 ? "Value must be even" : null;

Validator<Integer> maxValidator = value ->
    value != null && value > 1000 ? "Value cannot exceed 1000" : null;

ArgContext ctx = ArgContext.builder()
    .addValidator(positiveValidator)
    .addValidator(evenValidator)
    .addValidator(maxValidator)
    .build();

SlashCommand cmd = SlashCommand.create("setvalue")
    .argInt("value", ctx)
    .executes(ctx -> { /* ... */ })
    .build();
```

##### Practical Examples

**Email validator:**

```java
Validator<String> emailValidator = value -> {
    if (value == null) return "Email is required";
    if (!value.contains("@") || !value.contains(".")) {
        return "Invalid email format";
    }
    return null;
};
```

**Price validator (positive, max 2 decimals):**

```java
Validator<Double> priceValidator = value -> {
    if (value == null || value < 0) {
        return "Price must be non-negative";
    }
    String str = String.valueOf(value);
    int decimalPos = str.indexOf('.');
    if (decimalPos >= 0 && str.length() - decimalPos - 1 > 2) {
        return "Price can have at most 2 decimal places";
    }
    return null;
};
```

**Unique name validator (check database):**

```java
Validator<String> uniqueNameValidator = value -> {
    if (value == null) return "Name is required";
    if (database.nameExists(value)) {
        return "This name is already taken";
    }
    return null;
};
```

**Date range validator:**

```java
Validator<String> futureDateValidator = value -> {
    if (value == null) return "Date is required";
    try {
        LocalDate date = LocalDate.parse(value);
        if (date.isBefore(LocalDate.now())) {
            return "Date must be in the future";
        }
        return null;
    } catch (DateTimeParseException e) {
        return "Invalid date format (use YYYY-MM-DD)";
    }
};
```

##### Combining with Built-in Validation

Custom validators work alongside built-in constraints:

```java
ArgContext ctx = ArgContext.builder()
    // Built-in range check (runs first)
    .intRange(1, 100)
    // Custom validation (runs after range check passes)
    .addValidator(value -> {
        if (value != null && isPrime(value)) {
            return "Value cannot be a prime number";
        }
        return null;
    })
    .build();
```

For advanced tab-completion features including async completions and permission-filtered suggestions, see [Advanced Completions](Advanced-Completions.md).
