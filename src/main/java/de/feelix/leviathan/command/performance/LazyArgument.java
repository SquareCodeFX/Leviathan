package de.feelix.leviathan.command.performance;

import de.feelix.leviathan.annotations.NotNull;
import de.feelix.leviathan.annotations.Nullable;
import de.feelix.leviathan.command.argument.ArgumentParser;
import de.feelix.leviathan.command.argument.ParseResult;
import de.feelix.leviathan.util.Preconditions;
import org.bukkit.command.CommandSender;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A lazily-parsed argument that defers parsing until the value is actually needed.
 * <p>
 * This improves performance when:
 * <ul>
 *   <li>Some arguments may not be used in all code paths</li>
 *   <li>Parsing is expensive (e.g., database lookups, complex validation)</li>
 *   <li>Commands have many optional arguments</li>
 * </ul>
 * <p>
 * The value is parsed only once and then cached for subsequent accesses.
 * <p>
 * Example usage:
 * <pre>{@code
 * // Create a lazy argument
 * LazyArgument<Player> lazyPlayer = LazyArgument.of(
 *     "Notch",
 *     ArgParsers.playerParser(),
 *     sender
 * );
 *
 * // Value is not parsed yet
 * if (someCondition) {
 *     // Now it's parsed
 *     Player player = lazyPlayer.get();
 * }
 *
 * // Or use functional approach
 * lazyPlayer.ifPresent(player -> player.sendMessage("Hello!"));
 * String name = lazyPlayer.map(Player::getName).orElse("Unknown");
 * }</pre>
 *
 * @param <T> the type of the parsed value
 */
public final class LazyArgument<T> {

    private final String rawValue;
    private final ArgumentParser<T> parser;
    private final CommandSender sender;

    // Lazy evaluation state
    private volatile boolean parsed = false;
    private volatile T value;
    private volatile String errorMessage;
    private volatile boolean hasError = false;

    private LazyArgument(String rawValue, ArgumentParser<T> parser, CommandSender sender) {
        this.rawValue = rawValue;
        this.parser = Preconditions.checkNotNull(parser, "parser");
        this.sender = Preconditions.checkNotNull(sender, "sender");
    }

    /**
     * Create a lazy argument from a raw value and parser.
     *
     * @param rawValue the raw string value to parse
     * @param parser   the parser to use
     * @param sender   the command sender for context
     * @param <T>      the value type
     * @return a new LazyArgument instance
     */
    public static <T> @NotNull LazyArgument<T> of(@Nullable String rawValue,
                                                   @NotNull ArgumentParser<T> parser,
                                                   @NotNull CommandSender sender) {
        return new LazyArgument<>(rawValue, parser, sender);
    }

    /**
     * Create a lazy argument that always returns a constant value (pre-parsed).
     *
     * @param value the pre-parsed value
     * @param <T>   the value type
     * @return a lazy argument with the value already set
     */
    public static <T> @NotNull LazyArgument<T> ofValue(@Nullable T value) {
        LazyArgument<T> lazy = new LazyArgument<>(null, null, null) {
            @Override
            public @Nullable T get() {
                return value;
            }

            @Override
            public boolean isParsed() {
                return true;
            }

            @Override
            public boolean hasError() {
                return false;
            }

            @Override
            public boolean isPresent() {
                return value != null;
            }
        };
        return lazy;
    }

    /**
     * Create an empty lazy argument.
     *
     * @param <T> the value type
     * @return an empty lazy argument
     */
    public static <T> @NotNull LazyArgument<T> empty() {
        return ofValue(null);
    }

    /**
     * Create a lazy argument from a supplier (deferred computation).
     *
     * @param supplier the supplier to compute the value
     * @param <T>      the value type
     * @return a lazy argument that uses the supplier
     */
    public static <T> @NotNull LazyArgument<T> fromSupplier(@NotNull Supplier<T> supplier) {
        Preconditions.checkNotNull(supplier, "supplier");
        return new LazyArgument<T>(null, null, null) {
            private volatile boolean computed = false;
            private volatile T computedValue;

            @Override
            public @Nullable T get() {
                if (!computed) {
                    synchronized (this) {
                        if (!computed) {
                            computedValue = supplier.get();
                            computed = true;
                        }
                    }
                }
                return computedValue;
            }

            @Override
            public boolean isParsed() {
                return computed;
            }

            @Override
            public boolean hasError() {
                return false;
            }
        };
    }

    /**
     * Parse and get the value.
     * <p>
     * The value is parsed only once; subsequent calls return the cached result.
     *
     * @return the parsed value, or null if parsing failed or raw value was null
     */
    public @Nullable T get() {
        ensureParsed();
        return value;
    }

    /**
     * Get the value, throwing if parsing failed or value is null.
     *
     * @return the parsed value
     * @throws IllegalStateException if parsing failed or value is null
     */
    public @NotNull T require() {
        ensureParsed();
        if (hasError) {
            throw new IllegalStateException("Argument parsing failed: " + errorMessage);
        }
        if (value == null) {
            throw new IllegalStateException("Argument value is null");
        }
        return value;
    }

    /**
     * Get the value or a default if parsing failed or value is null.
     *
     * @param defaultValue the default value
     * @return the parsed value or default
     */
    public T orElse(@Nullable T defaultValue) {
        ensureParsed();
        return value != null ? value : defaultValue;
    }

    /**
     * Get the value or compute a default if parsing failed or value is null.
     *
     * @param supplier the supplier for the default value
     * @return the parsed value or computed default
     */
    public T orElseGet(@NotNull Supplier<T> supplier) {
        Preconditions.checkNotNull(supplier, "supplier");
        ensureParsed();
        return value != null ? value : supplier.get();
    }

    /**
     * Get the value as an Optional.
     *
     * @return Optional containing the value if present
     */
    public @NotNull Optional<T> optional() {
        ensureParsed();
        return Optional.ofNullable(value);
    }

    /**
     * Execute an action if the value is present.
     *
     * @param action the action to execute
     * @return this for chaining
     */
    public @NotNull LazyArgument<T> ifPresent(@NotNull java.util.function.Consumer<T> action) {
        Preconditions.checkNotNull(action, "action");
        ensureParsed();
        if (value != null) {
            action.accept(value);
        }
        return this;
    }

    /**
     * Execute an action if parsing failed.
     *
     * @param action the action to execute with the error message
     * @return this for chaining
     */
    public @NotNull LazyArgument<T> ifError(@NotNull java.util.function.Consumer<String> action) {
        Preconditions.checkNotNull(action, "action");
        ensureParsed();
        if (hasError) {
            action.accept(errorMessage);
        }
        return this;
    }

    /**
     * Map the value if present.
     *
     * @param mapper the mapping function
     * @param <R>    the result type
     * @return a new LazyArgument with the mapped value
     */
    public <R> @NotNull LazyArgument<R> map(@NotNull Function<T, R> mapper) {
        Preconditions.checkNotNull(mapper, "mapper");
        return LazyArgument.fromSupplier(() -> {
            T val = get();
            return val != null ? mapper.apply(val) : null;
        });
    }

    /**
     * Flat-map the value if present.
     *
     * @param mapper the mapping function
     * @param <R>    the result type
     * @return a new LazyArgument with the mapped value
     */
    public <R> @NotNull LazyArgument<R> flatMap(@NotNull Function<T, LazyArgument<R>> mapper) {
        Preconditions.checkNotNull(mapper, "mapper");
        return LazyArgument.fromSupplier(() -> {
            T val = get();
            if (val == null) return null;
            LazyArgument<R> result = mapper.apply(val);
            return result != null ? result.get() : null;
        });
    }

    /**
     * Filter the value based on a predicate.
     *
     * @param predicate the predicate to test
     * @return this if value matches, empty otherwise
     */
    public @NotNull LazyArgument<T> filter(@NotNull java.util.function.Predicate<T> predicate) {
        Preconditions.checkNotNull(predicate, "predicate");
        return LazyArgument.fromSupplier(() -> {
            T val = get();
            return (val != null && predicate.test(val)) ? val : null;
        });
    }

    /**
     * Check if the value has been parsed yet.
     *
     * @return true if parsing has occurred
     */
    public boolean isParsed() {
        return parsed;
    }

    /**
     * Check if parsing resulted in an error.
     *
     * @return true if there was a parse error
     */
    public boolean hasError() {
        ensureParsed();
        return hasError;
    }

    /**
     * Check if a value is present (non-null and no error).
     *
     * @return true if value is present
     */
    public boolean isPresent() {
        ensureParsed();
        return value != null && !hasError;
    }

    /**
     * Check if the value is absent (null or error).
     *
     * @return true if value is absent
     */
    public boolean isAbsent() {
        return !isPresent();
    }

    /**
     * Get the error message if parsing failed.
     *
     * @return the error message, or null if no error
     */
    public @Nullable String getErrorMessage() {
        ensureParsed();
        return errorMessage;
    }

    /**
     * Get the raw value before parsing.
     *
     * @return the raw string value
     */
    public @Nullable String getRawValue() {
        return rawValue;
    }

    /**
     * Force parsing now instead of waiting for value access.
     *
     * @return this for chaining
     */
    public @NotNull LazyArgument<T> parse() {
        ensureParsed();
        return this;
    }

    /**
     * Ensure the value is parsed (thread-safe double-checked locking).
     */
    private void ensureParsed() {
        if (!parsed) {
            synchronized (this) {
                if (!parsed) {
                    doParse();
                    parsed = true;
                }
            }
        }
    }

    /**
     * Perform the actual parsing.
     */
    private void doParse() {
        if (rawValue == null || parser == null || sender == null) {
            value = null;
            return;
        }

        try {
            ParseResult<T> result = parser.parse(rawValue, sender);
            if (result.isSuccess()) {
                value = result.value().orElse(null);
                hasError = false;
            } else {
                value = null;
                hasError = true;
                errorMessage = result.error().orElse("Parse error");
            }
        } catch (Exception e) {
            value = null;
            hasError = true;
            errorMessage = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        }
    }

    @Override
    public String toString() {
        if (!parsed) {
            return "LazyArgument{unparsed, raw='" + rawValue + "'}";
        }
        if (hasError) {
            return "LazyArgument{error='" + errorMessage + "'}";
        }
        return "LazyArgument{value=" + value + "}";
    }
}
