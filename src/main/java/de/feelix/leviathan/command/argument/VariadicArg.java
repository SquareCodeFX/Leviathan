package de.feelix.leviathan.command.argument;

import de.feelix.leviathan.annotations.NotNull;
import de.feelix.leviathan.annotations.Nullable;
import de.feelix.leviathan.util.Preconditions;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * A variadic argument that accepts multiple values of the same type and returns them as a List.
 * <p>
 * Variadic arguments are useful when you need to accept multiple values for a single argument,
 * such as multiple player names, multiple item types, or multiple coordinates.
 * <p>
 * The values can be separated by:
 * <ul>
 *   <li>Spaces (default) - each space-separated token is a value</li>
 *   <li>Custom delimiter - e.g., comma, semicolon</li>
 *   <li>Or collected from remaining command line arguments</li>
 * </ul>
 * <p>
 * Example usage:
 * <pre>{@code
 * // Accept multiple player names
 * VariadicArg<Player> players = VariadicArg.of("players", ArgParsers.playerParser());
 *
 * // Accept multiple integers with comma delimiter
 * VariadicArg<Integer> numbers = VariadicArg.of("numbers", ArgParsers.intParser(), ",");
 *
 * // With constraints
 * VariadicArg<String> tags = VariadicArg.<String>builder("tags")
 *     .parser(ArgParsers.stringParser())
 *     .minCount(1)
 *     .maxCount(5)
 *     .build();
 *
 * // In a command
 * SlashCommand.create("teleport")
 *     .argPlayer("target")
 *     .argVariadic(VariadicArg.of("witnesses", ArgParsers.playerParser()))
 *     .executes((sender, ctx) -> {
 *         Player target = ctx.get("target", Player.class);
 *         List<Player> witnesses = ctx.getList("witnesses", Player.class);
 *         // teleport logic
 *     })
 *     .build();
 * }</pre>
 *
 * @param <T> the type of each individual value in the list
 */
public final class VariadicArg<T> {

    // Cached pattern for whitespace splitting (avoids recompilation on every parse)
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");

    private final String name;
    private final ArgumentParser<T> elementParser;
    private final String delimiter;
    private final Pattern delimiterPattern; // Cached compiled delimiter pattern
    private final int minCount;
    private final int maxCount;
    private final boolean allowDuplicates;
    private final String description;
    private final ArgumentParser<List<T>> listParser;

    private VariadicArg(Builder<T> builder) {
        this.name = builder.name;
        this.elementParser = builder.elementParser;
        this.delimiter = builder.delimiter;
        // Pre-compile delimiter pattern once at construction time
        this.delimiterPattern = (builder.delimiter != null)
            ? Pattern.compile(Pattern.quote(builder.delimiter))
            : null;
        this.minCount = builder.minCount;
        this.maxCount = builder.maxCount;
        this.allowDuplicates = builder.allowDuplicates;
        this.description = builder.description;
        this.listParser = createListParser();
    }

    /**
     * Create a variadic argument with default settings (space-delimited).
     *
     * @param name          the argument name
     * @param elementParser parser for individual elements
     * @param <T>           the element type
     * @return a new variadic argument
     */
    public static <T> @NotNull VariadicArg<T> of(@NotNull String name, @NotNull ArgumentParser<T> elementParser) {
        return new Builder<T>(name).parser(elementParser).build();
    }

    /**
     * Create a variadic argument with a custom delimiter.
     *
     * @param name          the argument name
     * @param elementParser parser for individual elements
     * @param delimiter     the delimiter between values (e.g., ",", ";")
     * @param <T>           the element type
     * @return a new variadic argument
     */
    public static <T> @NotNull VariadicArg<T> of(@NotNull String name, @NotNull ArgumentParser<T> elementParser,
                                                  @NotNull String delimiter) {
        return new Builder<T>(name).parser(elementParser).delimiter(delimiter).build();
    }

    /**
     * Create a builder for a variadic argument with full customization.
     *
     * @param name the argument name
     * @param <T>  the element type
     * @return a new builder
     */
    public static <T> @NotNull Builder<T> builder(@NotNull String name) {
        return new Builder<>(name);
    }

    /**
     * Create a variadic argument for strings (space-delimited).
     *
     * @param name the argument name
     * @return a variadic string argument
     */
    public static @NotNull VariadicArg<String> strings(@NotNull String name) {
        return of(name, ArgParsers.stringParser());
    }

    /**
     * Create a variadic argument for integers (space-delimited).
     *
     * @param name the argument name
     * @return a variadic integer argument
     */
    public static @NotNull VariadicArg<Integer> integers(@NotNull String name) {
        return of(name, ArgParsers.intParser());
    }

    /**
     * Create a variadic argument for doubles (space-delimited).
     *
     * @param name the argument name
     * @return a variadic double argument
     */
    public static @NotNull VariadicArg<Double> doubles(@NotNull String name) {
        return of(name, ArgParsers.doubleParser());
    }

    /**
     * @return the argument name
     */
    public @NotNull String name() {
        return name;
    }

    /**
     * @return the parser for individual elements
     */
    public @NotNull ArgumentParser<T> elementParser() {
        return elementParser;
    }

    /**
     * @return the delimiter used to separate values, or null for space-delimited
     */
    public @Nullable String delimiter() {
        return delimiter;
    }

    /**
     * @return minimum number of values required (0 = no minimum)
     */
    public int minCount() {
        return minCount;
    }

    /**
     * @return maximum number of values allowed (Integer.MAX_VALUE = no limit)
     */
    public int maxCount() {
        return maxCount;
    }

    /**
     * @return true if duplicate values are allowed
     */
    public boolean allowDuplicates() {
        return allowDuplicates;
    }

    /**
     * @return the description, or null if not set
     */
    public @Nullable String description() {
        return description;
    }

    /**
     * Get the argument parser that returns a List of values.
     *
     * @return the list parser
     */
    public @NotNull ArgumentParser<List<T>> parser() {
        return listParser;
    }

    /**
     * Convert this variadic argument to an Arg with default configuration.
     * The argument will be marked as greedy to consume all remaining tokens.
     *
     * @return a new Arg instance
     */
    public @NotNull Arg<List<T>> toArg() {
        return new Arg<>(name, listParser, ArgContext.builder()
                .description(description)
                .greedy(delimiter == null) // Greedy if space-delimited
                .build());
    }

    /**
     * Convert this variadic argument to an Arg with custom context.
     *
     * @param context the argument context
     * @return a new Arg instance
     */
    public @NotNull Arg<List<T>> toArg(@NotNull ArgContext context) {
        Preconditions.checkNotNull(context, "context");
        return new Arg<>(name, listParser, ArgContext.builder()
                .from(context)
                .greedy(delimiter == null && !context.greedy()) // Greedy if space-delimited unless explicitly set
                .build());
    }

    private ArgumentParser<List<T>> createListParser() {
        return new ArgumentParser<List<T>>() {
            @Override
            public @NotNull ParseResult<List<T>> parse(@NotNull String input, @NotNull CommandSender sender) {
                if (input == null || input.trim().isEmpty()) {
                    if (minCount > 0) {
                        return ParseResult.failure("At least " + minCount + " value(s) required");
                    }
                    return ParseResult.success(Collections.emptyList());
                }

                // Split input by delimiter using cached patterns (avoids Pattern recompilation)
                String[] parts;
                if (delimiterPattern != null) {
                    parts = delimiterPattern.split(input);
                } else {
                    // Space-delimited (already split by Bukkit, but may be rejoined for greedy)
                    parts = WHITESPACE_PATTERN.split(input);
                }

                List<T> results = new ArrayList<>();
                List<String> errors = new ArrayList<>();

                for (String part : parts) {
                    String trimmed = part.trim();
                    if (trimmed.isEmpty()) {
                        continue;
                    }

                    ParseResult<T> result = elementParser.parse(trimmed, sender);
                    if (result.isSuccess()) {
                        T value = result.value().orElse(null);
                        if (value != null) {
                            // Check for duplicates if not allowed
                            if (!allowDuplicates && results.contains(value)) {
                                errors.add("Duplicate value: " + trimmed);
                            } else {
                                results.add(value);
                            }
                        }
                    } else {
                        errors.add("Invalid value '" + trimmed + "': " + result.error().orElse("unknown error"));
                    }
                }

                // Check constraints
                if (!errors.isEmpty()) {
                    return ParseResult.failure(String.join("; ", errors));
                }

                if (results.size() < minCount) {
                    return ParseResult.failure("At least " + minCount + " value(s) required, got " + results.size());
                }

                if (results.size() > maxCount) {
                    return ParseResult.failure("At most " + maxCount + " value(s) allowed, got " + results.size());
                }

                return ParseResult.success(Collections.unmodifiableList(results));
            }

            @Override
            public @NotNull List<String> tabComplete(@NotNull String partial, @NotNull CommandSender sender) {
                // For tab completion, provide suggestions for the last element
                String lastElement = partial;
                if (delimiter != null && partial.contains(delimiter)) {
                    int lastIndex = partial.lastIndexOf(delimiter);
                    lastElement = partial.substring(lastIndex + delimiter.length()).trim();
                }
                return elementParser.tabComplete(lastElement, sender);
            }

            @Override
            public @NotNull String getTypeName() {
                return "list<" + elementParser.getTypeName() + ">";
            }
        };
    }

    /**
     * Builder for {@link VariadicArg}.
     *
     * @param <T> the element type
     */
    public static final class Builder<T> {
        private final String name;
        private ArgumentParser<T> elementParser;
        private String delimiter = null; // null = space-delimited
        private int minCount = 0;
        private int maxCount = Integer.MAX_VALUE;
        private boolean allowDuplicates = true;
        private String description;

        private Builder(@NotNull String name) {
            this.name = Preconditions.checkNotNull(name, "name");
        }

        /**
         * Set the parser for individual elements.
         *
         * @param parser the element parser
         * @return this builder
         */
        public @NotNull Builder<T> parser(@NotNull ArgumentParser<T> parser) {
            this.elementParser = Preconditions.checkNotNull(parser, "parser");
            return this;
        }

        /**
         * Set a custom delimiter between values.
         * <p>
         * Default is null (space-delimited, consuming all remaining arguments).
         *
         * @param delimiter the delimiter (e.g., ",", ";", "|")
         * @return this builder
         */
        public @NotNull Builder<T> delimiter(@Nullable String delimiter) {
            this.delimiter = delimiter;
            return this;
        }

        /**
         * Set the minimum number of values required.
         *
         * @param count minimum count (default: 0)
         * @return this builder
         */
        public @NotNull Builder<T> minCount(int count) {
            if (count < 0) {
                throw new IllegalArgumentException("minCount cannot be negative");
            }
            this.minCount = count;
            return this;
        }

        /**
         * Set the maximum number of values allowed.
         *
         * @param count maximum count (default: Integer.MAX_VALUE)
         * @return this builder
         */
        public @NotNull Builder<T> maxCount(int count) {
            if (count < 1) {
                throw new IllegalArgumentException("maxCount must be at least 1");
            }
            this.maxCount = count;
            return this;
        }

        /**
         * Set the exact number of values required.
         *
         * @param count exact count
         * @return this builder
         */
        public @NotNull Builder<T> exactCount(int count) {
            if (count < 1) {
                throw new IllegalArgumentException("count must be at least 1");
            }
            this.minCount = count;
            this.maxCount = count;
            return this;
        }

        /**
         * Set whether duplicate values are allowed.
         *
         * @param allow true to allow duplicates (default: true)
         * @return this builder
         */
        public @NotNull Builder<T> allowDuplicates(boolean allow) {
            this.allowDuplicates = allow;
            return this;
        }

        /**
         * Disallow duplicate values.
         *
         * @return this builder
         */
        public @NotNull Builder<T> noDuplicates() {
            return allowDuplicates(false);
        }

        /**
         * Set the description for this argument.
         *
         * @param description the description
         * @return this builder
         */
        public @NotNull Builder<T> description(@Nullable String description) {
            this.description = description;
            return this;
        }

        /**
         * Build the variadic argument.
         *
         * @return a new VariadicArg instance
         * @throws IllegalStateException if no parser was set
         */
        public @NotNull VariadicArg<T> build() {
            if (elementParser == null) {
                throw new IllegalStateException("Element parser must be set");
            }
            if (minCount > maxCount) {
                throw new IllegalStateException("minCount cannot be greater than maxCount");
            }
            return new VariadicArg<>(this);
        }
    }
}
