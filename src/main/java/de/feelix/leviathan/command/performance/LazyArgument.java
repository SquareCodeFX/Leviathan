package de.feelix.leviathan.command.performance;

import de.feelix.leviathan.annotations.NotNull;
import de.feelix.leviathan.annotations.Nullable;
import de.feelix.leviathan.command.argument.ArgumentParser;
import de.feelix.leviathan.command.argument.ParseResult;
import de.feelix.leviathan.util.Preconditions;
import org.bukkit.command.CommandSender;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * A lazily-evaluated argument that defers computation until the value is actually needed.
 * <p>
 * This improves performance when:
 * <ul>
 *   <li>Some arguments may not be used in all code paths</li>
 *   <li>Parsing is expensive (e.g., database lookups, complex validation)</li>
 *   <li>Commands have many optional arguments</li>
 * </ul>
 * <p>
 * The value is computed only once and then cached for subsequent accesses.
 * <p>
 * Example usage:
 * <pre>{@code
 * // Create a lazy argument from parsing
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
 * @param <T> the type of the value
 */
public abstract class LazyArgument<T> {

    /**
     * Protected constructor for subclasses.
     */
    protected LazyArgument() {
    }

    // ==================== Factory Methods ====================

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
        Preconditions.checkNotNull(parser, "parser");
        Preconditions.checkNotNull(sender, "sender");
        return new ParsedLazyArgument<>(rawValue, parser, sender);
    }

    /**
     * Create a lazy argument that always returns a constant value (pre-computed).
     *
     * @param value the pre-computed value
     * @param <T>   the value type
     * @return a lazy argument with the value already set
     */
    public static <T> @NotNull LazyArgument<T> ofValue(@Nullable T value) {
        return new ConstantLazyArgument<>(value);
    }

    /**
     * Create an empty lazy argument.
     *
     * @param <T> the value type
     * @return an empty lazy argument
     */
    public static <T> @NotNull LazyArgument<T> empty() {
        return new ConstantLazyArgument<>(null);
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
        return new SupplierLazyArgument<>(supplier);
    }

    // ==================== Abstract Methods ====================

    /**
     * Get the value.
     * <p>
     * The value is computed only once; subsequent calls return the cached result.
     *
     * @return the value, or null if computation failed or value is null
     */
    public abstract @Nullable T get();

    /**
     * Check if the value has been computed yet.
     *
     * @return true if computation has occurred
     */
    public abstract boolean isComputed();

    /**
     * Check if computation resulted in an error.
     *
     * @return true if there was an error
     */
    public abstract boolean hasError();

    /**
     * Get the error message if computation failed.
     *
     * @return the error message, or null if no error
     */
    public abstract @Nullable String getErrorMessage();

    /**
     * Get the raw value before parsing (if applicable).
     *
     * @return the raw string value, or null if not applicable
     */
    public abstract @Nullable String getRawValue();

    // ==================== Common Methods ====================

    /**
     * Check if a value is present (non-null and no error).
     *
     * @return true if value is present
     */
    public boolean isPresent() {
        return get() != null && !hasError();
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
     * Get the value, throwing if computation failed or value is null.
     *
     * @return the value
     * @throws IllegalStateException if computation failed or value is null
     */
    public @NotNull T require() {
        if (hasError()) {
            throw new IllegalStateException("Argument computation failed: " + getErrorMessage());
        }
        T value = get();
        if (value == null) {
            throw new IllegalStateException("Argument value is null");
        }
        return value;
    }

    /**
     * Get the value or a default if computation failed or value is null.
     *
     * @param defaultValue the default value
     * @return the value or default
     */
    public T orElse(@Nullable T defaultValue) {
        T value = get();
        return value != null ? value : defaultValue;
    }

    /**
     * Get the value or compute a default if computation failed or value is null.
     *
     * @param supplier the supplier for the default value
     * @return the value or computed default
     */
    public T orElseGet(@NotNull Supplier<T> supplier) {
        Preconditions.checkNotNull(supplier, "supplier");
        T value = get();
        return value != null ? value : supplier.get();
    }

    /**
     * Get the value as an Optional.
     *
     * @return Optional containing the value if present
     */
    public @NotNull Optional<T> optional() {
        return Optional.ofNullable(get());
    }

    /**
     * Execute an action if the value is present.
     *
     * @param action the action to execute
     * @return this for chaining
     */
    public @NotNull LazyArgument<T> ifPresent(@NotNull Consumer<T> action) {
        Preconditions.checkNotNull(action, "action");
        T value = get();
        if (value != null && !hasError()) {
            action.accept(value);
        }
        return this;
    }

    /**
     * Execute an action if computation failed.
     *
     * @param action the action to execute with the error message
     * @return this for chaining
     */
    public @NotNull LazyArgument<T> ifError(@NotNull Consumer<String> action) {
        Preconditions.checkNotNull(action, "action");
        if (hasError()) {
            action.accept(getErrorMessage());
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
        return fromSupplier(() -> {
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
        return fromSupplier(() -> {
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
    public @NotNull LazyArgument<T> filter(@NotNull Predicate<T> predicate) {
        Preconditions.checkNotNull(predicate, "predicate");
        return fromSupplier(() -> {
            T val = get();
            return (val != null && predicate.test(val)) ? val : null;
        });
    }

    /**
     * Force computation now instead of waiting for value access.
     *
     * @return this for chaining
     */
    public @NotNull LazyArgument<T> compute() {
        get(); // Trigger computation
        return this;
    }

    // ==================== Deprecated Alias ====================

    /**
     * @deprecated Use {@link #isComputed()} instead
     */
    @Deprecated
    public boolean isParsed() {
        return isComputed();
    }

    /**
     * @deprecated Use {@link #compute()} instead
     */
    @Deprecated
    public @NotNull LazyArgument<T> parse() {
        return compute();
    }

    // ==================== Implementation: Parsed Argument ====================

    /**
     * Implementation for arguments that need to be parsed.
     */
    private static final class ParsedLazyArgument<T> extends LazyArgument<T> {
        private final String rawValue;
        private final ArgumentParser<T> parser;
        private final CommandSender sender;

        // Lazy evaluation state
        private volatile boolean computed = false;
        private volatile T value;
        private volatile String errorMessage;
        private volatile boolean hasError = false;

        ParsedLazyArgument(@Nullable String rawValue,
                           @NotNull ArgumentParser<T> parser,
                           @NotNull CommandSender sender) {
            this.rawValue = rawValue;
            this.parser = parser;
            this.sender = sender;
        }

        @Override
        public @Nullable T get() {
            ensureComputed();
            return value;
        }

        @Override
        public boolean isComputed() {
            return computed;
        }

        @Override
        public boolean hasError() {
            ensureComputed();
            return hasError;
        }

        @Override
        public @Nullable String getErrorMessage() {
            ensureComputed();
            return errorMessage;
        }

        @Override
        public @Nullable String getRawValue() {
            return rawValue;
        }

        /**
         * Ensure the value is computed (thread-safe double-checked locking).
         */
        private void ensureComputed() {
            if (!computed) {
                synchronized (this) {
                    if (!computed) {
                        doCompute();
                        computed = true;
                    }
                }
            }
        }

        /**
         * Perform the actual parsing.
         */
        private void doCompute() {
            if (rawValue == null) {
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
            if (!computed) {
                return "LazyArgument.Parsed{unparsed, raw='" + rawValue + "'}";
            }
            if (hasError) {
                return "LazyArgument.Parsed{error='" + errorMessage + "'}";
            }
            return "LazyArgument.Parsed{value=" + value + "}";
        }
    }

    // ==================== Implementation: Constant Value ====================

    /**
     * Implementation for pre-computed constant values.
     */
    private static final class ConstantLazyArgument<T> extends LazyArgument<T> {
        private final T value;

        ConstantLazyArgument(@Nullable T value) {
            this.value = value;
        }

        @Override
        public @Nullable T get() {
            return value;
        }

        @Override
        public boolean isComputed() {
            return true;
        }

        @Override
        public boolean hasError() {
            return false;
        }

        @Override
        public @Nullable String getErrorMessage() {
            return null;
        }

        @Override
        public @Nullable String getRawValue() {
            return null;
        }

        @Override
        public boolean isPresent() {
            return value != null;
        }

        @Override
        public String toString() {
            return "LazyArgument.Constant{value=" + value + "}";
        }
    }

    // ==================== Implementation: Supplier-based ====================

    /**
     * Implementation for supplier-based lazy computation.
     */
    private static final class SupplierLazyArgument<T> extends LazyArgument<T> {
        private final Supplier<T> supplier;

        private volatile boolean computed = false;
        private volatile T value;
        private volatile String errorMessage;
        private volatile boolean hasError = false;

        SupplierLazyArgument(@NotNull Supplier<T> supplier) {
            this.supplier = supplier;
        }

        @Override
        public @Nullable T get() {
            ensureComputed();
            return value;
        }

        @Override
        public boolean isComputed() {
            return computed;
        }

        @Override
        public boolean hasError() {
            ensureComputed();
            return hasError;
        }

        @Override
        public @Nullable String getErrorMessage() {
            ensureComputed();
            return errorMessage;
        }

        @Override
        public @Nullable String getRawValue() {
            return null;
        }

        /**
         * Ensure the value is computed (thread-safe double-checked locking).
         */
        private void ensureComputed() {
            if (!computed) {
                synchronized (this) {
                    if (!computed) {
                        doCompute();
                        computed = true;
                    }
                }
            }
        }

        /**
         * Perform the actual computation.
         */
        private void doCompute() {
            try {
                value = supplier.get();
                hasError = false;
            } catch (Exception e) {
                value = null;
                hasError = true;
                errorMessage = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            }
        }

        @Override
        public String toString() {
            if (!computed) {
                return "LazyArgument.Supplier{not computed}";
            }
            if (hasError) {
                return "LazyArgument.Supplier{error='" + errorMessage + "'}";
            }
            return "LazyArgument.Supplier{value=" + value + "}";
        }
    }
}
