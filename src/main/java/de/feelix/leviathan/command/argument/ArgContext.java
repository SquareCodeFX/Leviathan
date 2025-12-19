package de.feelix.leviathan.command.argument;

import de.feelix.leviathan.annotations.NotNull;
import de.feelix.leviathan.annotations.Nullable;
import de.feelix.leviathan.command.completion.DynamicCompletionContext;
import de.feelix.leviathan.command.transform.Transformer;
import de.feelix.leviathan.util.Preconditions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

/**
 * Per-argument configuration container.
 * Holds metadata like optionality, per-argument permission, greedy flag,
 * tab-completion configuration, and validation rules.
 */
public final class ArgContext {

    /**
     * Functional interface for providing dynamic completions with full runtime context.
     */
    @FunctionalInterface
    public interface DynamicCompletionProvider {
        @NotNull
        List<String> provide(@NotNull DynamicCompletionContext ctx);

        /**
         * Create a dynamic completion provider that filters completions based on sender permissions.
         * Only completions where the sender has the specified permission will be shown.
         *
         * @param completions      the base list of completions
         * @param permissionPrefix permission prefix to check (e.g., "myplugin.item.")
         * @return a dynamic completion provider that filters by permission
         */
        static @NotNull DynamicCompletionProvider permissionFiltered(@NotNull List<String> completions,
                                                                     @NotNull String permissionPrefix) {
            Preconditions.checkNotNull(completions, "completions");
            Preconditions.checkNotNull(permissionPrefix, "permissionPrefix");
            return ctx -> {
                List<String> filtered = new ArrayList<>();
                for (String c : completions) {
                    if (ctx.sender().hasPermission(permissionPrefix + c)) {
                        filtered.add(c);
                    }
                }
                return filtered;
            };
        }

        /**
         * Create a dynamic completion provider that returns completions based on previously parsed arguments.
         * Useful for context-dependent completions.
         *
         * @param provider function that takes the dynamic context and returns completions
         * @return a dynamic completion provider
         */
        static @NotNull DynamicCompletionProvider contextBased(
            @NotNull java.util.function.Function<DynamicCompletionContext, List<String>> provider) {
            Preconditions.checkNotNull(provider, "provider");
            return provider::apply;
        }

        /**
         * Create a dynamic completion provider that combines multiple completion sources.
         * All completions from all sources are merged (duplicates removed).
         *
         * @param providers the completion providers to combine
         * @return a dynamic completion provider that merges all sources
         */
        static @NotNull DynamicCompletionProvider combined(@NotNull DynamicCompletionProvider... providers) {
            Preconditions.checkNotNull(providers, "providers");
            return ctx -> {
                java.util.Set<String> combined = new java.util.LinkedHashSet<>();
                for (DynamicCompletionProvider provider : providers) {
                    if (provider != null) {
                        combined.addAll(provider.provide(ctx));
                    }
                }
                return new ArrayList<>(combined);
            };
        }
    }

    /**
     * Functional interface for providing dynamic completions asynchronously with full runtime context.
     * <p>
     * This interface is designed for completion sources that require asynchronous operations,
     * such as database queries, HTTP requests, or other I/O-bound operations.
     * <p>
     * Example usage:
     * <pre>{@code
     * ArgContext.builder()
     *     .completionsDynamicAsync(ctx -> CompletableFuture.supplyAsync(() -> {
     *         // Fetch completions from database or external API
     *         return fetchCompletionsFromDatabase(ctx.sender());
     *     }))
     *     .build();
     * }</pre>
     */
    @FunctionalInterface
    public interface AsyncDynamicCompletionProvider {
        /**
         * Asynchronously provide completions based on the dynamic context.
         *
         * @param ctx the dynamic completion context containing sender, arguments, and other runtime info
         * @return a CompletableFuture that will complete with the list of completion suggestions
         */
        @NotNull
        CompletableFuture<List<String>> provideAsync(@NotNull DynamicCompletionContext ctx);

        /**
         * Create an async dynamic completion provider from a synchronous one.
         * The synchronous provider will be executed on the common ForkJoinPool.
         *
         * @param syncProvider the synchronous completion provider
         * @return an async completion provider wrapping the synchronous one
         */
        static @NotNull AsyncDynamicCompletionProvider fromSync(@NotNull DynamicCompletionProvider syncProvider) {
            Preconditions.checkNotNull(syncProvider, "syncProvider");
            return ctx -> CompletableFuture.supplyAsync(() -> syncProvider.provide(ctx));
        }

        /**
         * Create an async dynamic completion provider that combines multiple async providers.
         * All completions from all sources are merged (duplicates removed).
         *
         * @param providers the async completion providers to combine
         * @return an async dynamic completion provider that merges all sources
         */
        static @NotNull AsyncDynamicCompletionProvider combined(@NotNull AsyncDynamicCompletionProvider... providers) {
            Preconditions.checkNotNull(providers, "providers");
            return ctx -> {
                @SuppressWarnings("unchecked")
                CompletableFuture<List<String>>[] futures = new CompletableFuture[providers.length];
                for (int i = 0; i < providers.length; i++) {
                    futures[i] = providers[i] != null
                        ? providers[i].provideAsync(ctx)
                        : CompletableFuture.completedFuture(Collections.emptyList());
                }
                return CompletableFuture.allOf(futures).thenApply(v -> {
                    java.util.Set<String> combined = new java.util.LinkedHashSet<>();
                    for (CompletableFuture<List<String>> future : futures) {
                        combined.addAll(future.join());
                    }
                    return new ArrayList<>(combined);
                });
            };
        }
    }

    /**
     * Functional interface for providing predefined completions asynchronously.
     * <p>
     * This interface is designed for completion sources that require asynchronous operations
     * to fetch a static or semi-static list of completions, such as loading from a config file,
     * database, or external API.
     * <p>
     * Example usage:
     * <pre>{@code
     * ArgContext.builder()
     *     .completionsPredefinedAsync(() -> CompletableFuture.supplyAsync(() -> {
     *         // Fetch available options from database
     *         return loadOptionsFromDatabase();
     *     }))
     *     .build();
     * }</pre>
     */
    @FunctionalInterface
    public interface AsyncPredefinedCompletionSupplier {
        /**
         * Asynchronously supply the list of predefined completions.
         *
         * @return a CompletableFuture that will complete with the list of completion suggestions
         */
        @NotNull
        CompletableFuture<List<String>> supplyAsync();

        /**
         * Create an async predefined completion supplier from a static list.
         * The list will be returned immediately as a completed future.
         *
         * @param completions the static list of completions
         * @return an async supplier that returns the completions immediately
         */
        static @NotNull AsyncPredefinedCompletionSupplier fromList(@NotNull List<String> completions) {
            Preconditions.checkNotNull(completions, "completions");
            List<String> copy = new ArrayList<>(completions);
            return () -> CompletableFuture.completedFuture(copy);
        }

        /**
         * Create an async predefined completion supplier from a synchronous supplier.
         * The synchronous supplier will be executed on the common ForkJoinPool.
         *
         * @param supplier the synchronous supplier function
         * @return an async supplier wrapping the synchronous one
         */
        static @NotNull AsyncPredefinedCompletionSupplier fromSync(
            @NotNull java.util.function.Supplier<List<String>> supplier) {
            Preconditions.checkNotNull(supplier, "supplier");
            return () -> CompletableFuture.supplyAsync(supplier);
        }
    }

    /**
     * Functional interface for custom validation logic.
     *
     * @param <T> the type of value being validated
     */
    @FunctionalInterface
    public interface Validator<T> {
        /**
         * Validates the given value.
         *
         * @param value the value to validate
         * @return null if valid, or an error message if invalid
         */
        @Nullable
        String validate(@Nullable T value);
    }

    private final boolean optional;
    private final boolean greedy;
    private final @Nullable String permission;
    private final @NotNull List<String> completionsPredefined;
    /**
     * Optional dynamic completion provider. When present, SlashCommand will invoke it on tab-complete.
     */
    private final @Nullable DynamicCompletionProvider completionsDynamic;
    /**
     * Optional async dynamic completion provider. When present, SlashCommand will invoke it asynchronously on
     * tab-complete.
     */
    private final @Nullable AsyncDynamicCompletionProvider completionsDynamicAsync;
    /**
     * Optional async predefined completion supplier. When present, SlashCommand will invoke it asynchronously on
     * tab-complete.
     */
    private final @Nullable AsyncPredefinedCompletionSupplier completionsPredefinedAsync;

    // Validation fields
    private final @Nullable Integer intMin;
    private final @Nullable Integer intMax;
    private final @Nullable Long longMin;
    private final @Nullable Long longMax;
    private final @Nullable Double doubleMin;
    private final @Nullable Double doubleMax;
    private final @Nullable Float floatMin;
    private final @Nullable Float floatMax;
    private final @Nullable Integer stringMinLength;
    private final @Nullable Integer stringMaxLength;
    private final @Nullable Pattern stringPattern;
    private final List<Validator<?>> customValidators;

    // Did-You-Mean feature
    private final boolean didYouMean;

    // Default value
    private final @Nullable Object defaultValue;

    // Argument description for help/documentation
    private final @Nullable String description;

    // Argument aliases (alternative names for the argument)
    private final @NotNull List<String> aliases;

    // Transformer pipeline for post-parse value transformation
    private final @NotNull List<Transformer<?>> transformers;

    // Interactive prompting: prompt user for this argument if missing
    private final boolean interactive;

    // Argument group name (for grouping in help/errors)
    private final @Nullable String group;

    private ArgContext(boolean optional,
                       boolean greedy,
                       @Nullable String permission,
                       @Nullable List<String> completionsPredefined,
                       @Nullable DynamicCompletionProvider completionsDynamic,
                       @Nullable AsyncDynamicCompletionProvider completionsDynamicAsync,
                       @Nullable AsyncPredefinedCompletionSupplier completionsPredefinedAsync,
                       @Nullable Integer intMin,
                       @Nullable Integer intMax,
                       @Nullable Long longMin,
                       @Nullable Long longMax,
                       @Nullable Double doubleMin,
                       @Nullable Double doubleMax,
                       @Nullable Float floatMin,
                       @Nullable Float floatMax,
                       @Nullable Integer stringMinLength,
                       @Nullable Integer stringMaxLength,
                       @Nullable Pattern stringPattern,
                       @Nullable List<Validator<?>> customValidators,
                       boolean didYouMean,
                       @Nullable Object defaultValue,
                       @Nullable String description,
                       @Nullable List<String> aliases,
                       @Nullable List<Transformer<?>> transformers,
                       boolean interactive,
                       @Nullable String group) {
        this.optional = optional;
        this.greedy = greedy;
        this.permission = (permission == null || permission.isBlank()) ? null : permission;
        List<String> list = (completionsPredefined == null) ? List.of() : new ArrayList<>(completionsPredefined);
        this.completionsPredefined = Collections.unmodifiableList(list);
        this.completionsDynamic = completionsDynamic;
        this.completionsDynamicAsync = completionsDynamicAsync;
        this.completionsPredefinedAsync = completionsPredefinedAsync;

        // Validation fields
        this.intMin = intMin;
        this.intMax = intMax;
        this.longMin = longMin;
        this.longMax = longMax;
        this.doubleMin = doubleMin;
        this.doubleMax = doubleMax;
        this.floatMin = floatMin;
        this.floatMax = floatMax;
        this.stringMinLength = stringMinLength;
        this.stringMaxLength = stringMaxLength;
        this.stringPattern = stringPattern;
        List<Validator<?>> valList = (customValidators == null) ? List.of() : new ArrayList<>(customValidators);
        this.customValidators = Collections.unmodifiableList(valList);
        this.didYouMean = didYouMean;
        this.defaultValue = defaultValue;
        this.description = description;
        List<String> aliasList = (aliases == null) ? List.of() : new ArrayList<>(aliases);
        this.aliases = Collections.unmodifiableList(aliasList);
        List<Transformer<?>> transformerList = (transformers == null) ? List.of() : new ArrayList<>(transformers);
        this.transformers = Collections.unmodifiableList(transformerList);
        this.interactive = interactive;
        this.group = (group == null || group.isBlank()) ? null : group;
    }

    public static @NotNull Builder builder() {
        return new Builder();
    }

    public static @NotNull ArgContext defaultContext() {
        return new Builder().build();
    }

    public boolean optional() {
        return optional;
    }

    public boolean greedy() {
        return greedy;
    }

    public @Nullable String permission() {
        return permission;
    }

    public @NotNull List<String> completionsPredefined() {
        return completionsPredefined;
    }

    public @Nullable DynamicCompletionProvider completionsDynamic() {
        return completionsDynamic;
    }

    public @Nullable AsyncDynamicCompletionProvider completionsDynamicAsync() {
        return completionsDynamicAsync;
    }

    public @Nullable AsyncPredefinedCompletionSupplier completionsPredefinedAsync() {
        return completionsPredefinedAsync;
    }

    // Validation getters
    public @Nullable Integer intMin() {
        return intMin;
    }

    public @Nullable Integer intMax() {
        return intMax;
    }

    public @Nullable Long longMin() {
        return longMin;
    }

    public @Nullable Long longMax() {
        return longMax;
    }

    public @Nullable Double doubleMin() {
        return doubleMin;
    }

    public @Nullable Double doubleMax() {
        return doubleMax;
    }

    public @Nullable Float floatMin() {
        return floatMin;
    }

    public @Nullable Float floatMax() {
        return floatMax;
    }

    public @Nullable Integer stringMinLength() {
        return stringMinLength;
    }

    public @Nullable Integer stringMaxLength() {
        return stringMaxLength;
    }

    public @Nullable Pattern stringPattern() {
        return stringPattern;
    }

    public @NotNull List<Validator<?>> customValidators() {
        return customValidators;
    }

    public boolean didYouMean() {
        return didYouMean;
    }

    public @Nullable Object defaultValue() {
        return defaultValue;
    }

    /**
     * @return the description for this argument, used in help/documentation, or null if not set
     */
    public @Nullable String description() {
        return description;
    }

    /**
     * Get the list of aliases for this argument.
     * <p>
     * Aliases allow the argument to be referenced by alternative names in key-value syntax
     * (e.g., {@code player=Notch} or {@code p=Notch}) and when retrieving values from
     * the CommandContext.
     *
     * @return an immutable list of aliases (empty if none defined)
     */
    public @NotNull List<String> aliases() {
        return aliases;
    }

    /**
     * Check if this argument has any aliases defined.
     *
     * @return true if at least one alias is defined
     */
    public boolean hasAliases() {
        return !aliases.isEmpty();
    }

    /**
     * Check if the given name matches this argument's name or any of its aliases.
     *
     * @param name          the primary argument name
     * @param nameToCheck   the name to check against
     * @return true if nameToCheck matches the primary name or any alias
     */
    public boolean matchesNameOrAlias(@NotNull String name, @NotNull String nameToCheck) {
        if (name.equalsIgnoreCase(nameToCheck)) {
            return true;
        }
        for (String alias : aliases) {
            if (alias.equalsIgnoreCase(nameToCheck)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get the list of transformers for this argument.
     * <p>
     * Transformers are applied after parsing to modify the value before validation.
     *
     * @return an immutable list of transformers
     */
    public @NotNull List<Transformer<?>> transformers() {
        return transformers;
    }

    /**
     * Check if this argument has any transformers.
     *
     * @return true if at least one transformer is defined
     */
    public boolean hasTransformers() {
        return !transformers.isEmpty();
    }

    /**
     * Apply all transformers to a value.
     *
     * @param value the value to transform
     * @param <T>   the value type
     * @return the transformed value
     */
    @SuppressWarnings("unchecked")
    public <T> T applyTransformers(T value) {
        if (transformers.isEmpty() || value == null) {
            return value;
        }
        Object result = value;
        for (Transformer<?> transformer : transformers) {
            result = ((Transformer<Object>) transformer).transform(result);
        }
        return (T) result;
    }

    /**
     * Check if this argument supports interactive prompting.
     * <p>
     * When interactive mode is enabled and this argument is missing,
     * the system will prompt the user for input instead of failing.
     *
     * @return true if interactive prompting is enabled
     */
    public boolean interactive() {
        return interactive;
    }

    /**
     * Get the argument group name.
     * <p>
     * Arguments in the same group are displayed together in help
     * and can have group-level validation rules.
     *
     * @return the group name, or null if not grouped
     */
    public @Nullable String group() {
        return group;
    }

    /**
     * Check if this argument belongs to a group.
     *
     * @return true if a group is defined
     */
    public boolean hasGroup() {
        return group != null;
    }

    public static final class Builder {
        private boolean optional;
        private boolean greedy;
        private @Nullable String permission;
        private @NotNull List<String> completionsPredefined = new ArrayList<>();
        private @Nullable DynamicCompletionProvider completionsDynamic;
        private @Nullable AsyncDynamicCompletionProvider completionsDynamicAsync;
        private @Nullable AsyncPredefinedCompletionSupplier completionsPredefinedAsync;

        // Validation fields
        private @Nullable Integer intMin;
        private @Nullable Integer intMax;
        private @Nullable Long longMin;
        private @Nullable Long longMax;
        private @Nullable Double doubleMin;
        private @Nullable Double doubleMax;
        private @Nullable Float floatMin;
        private @Nullable Float floatMax;
        private @Nullable Integer stringMinLength;
        private @Nullable Integer stringMaxLength;
        private @Nullable Pattern stringPattern;
        private final List<Validator<?>> customValidators = new ArrayList<>();
        private boolean didYouMean = false;
        private @Nullable Object defaultValue;
        private @Nullable String description;
        private @NotNull List<String> aliases = new ArrayList<>();
        private @NotNull List<Transformer<?>> transformers = new ArrayList<>();
        private boolean interactive = false;
        private @Nullable String group;

        public @NotNull Builder optional(boolean optional) {
            this.optional = optional;
            return this;
        }

        public @NotNull Builder greedy(boolean greedy) {
            this.greedy = greedy;
            return this;
        }

        public @NotNull Builder permission(@Nullable String permission) {
            this.permission = permission;
            return this;
        }

        /**
         * Fluent alias for {@link #permission(String)}.
         * Makes the API read more naturally: {@code withPermission("admin.use")}
         */
        public @NotNull Builder withPermission(@Nullable String permission) {
            return permission(permission);
        }

        public @NotNull Builder completionsPredefined(@NotNull List<String> completions) {
            Preconditions.checkNotNull(completions, "completions");
            this.completionsPredefined = new ArrayList<>();
            for (String completion : completions) {
                if (completion != null) {
                    this.completionsPredefined.add(completion);
                }
            }
            return this;
        }

        /**
         * Fluent alias for {@link #completionsPredefined(List)}.
         * Makes the API read more naturally: {@code withCompletions(List.of("option1", "option2"))}
         */
        public @NotNull Builder withCompletions(@NotNull List<String> completions) {
            return completionsPredefined(completions);
        }

        public @NotNull Builder completionsDynamic(@Nullable DynamicCompletionProvider provider) {
            this.completionsDynamic = provider;
            return this;
        }

        /**
         * Fluent alias for {@link #completionsDynamic(DynamicCompletionProvider)}.
         * Makes the API read more naturally: {@code withDynamicCompletions(provider)}
         */
        public @NotNull Builder withDynamicCompletions(@Nullable DynamicCompletionProvider provider) {
            return completionsDynamic(provider);
        }

        /**
         * Set the async dynamic completion provider for this argument.
         * The provider will be called asynchronously when tab completions are requested.
         *
         * @param provider the async completion provider
         * @return this builder
         */
        public @NotNull Builder completionsDynamicAsync(@Nullable AsyncDynamicCompletionProvider provider) {
            this.completionsDynamicAsync = provider;
            return this;
        }

        /**
         * Fluent alias for {@link #completionsDynamicAsync(AsyncDynamicCompletionProvider)}.
         * Makes the API read more naturally: {@code withAsyncDynamicCompletions(provider)}
         */
        public @NotNull Builder withAsyncDynamicCompletions(@Nullable AsyncDynamicCompletionProvider provider) {
            return completionsDynamicAsync(provider);
        }

        /**
         * Set the async predefined completion supplier for this argument.
         * The supplier will be called asynchronously when tab completions are requested.
         *
         * @param supplier the async completion supplier
         * @return this builder
         */
        public @NotNull Builder completionsPredefinedAsync(@Nullable AsyncPredefinedCompletionSupplier supplier) {
            this.completionsPredefinedAsync = supplier;
            return this;
        }

        /**
         * Fluent alias for {@link #completionsPredefinedAsync(AsyncPredefinedCompletionSupplier)}.
         * Makes the API read more naturally: {@code withAsyncCompletions(supplier)}
         */
        public @NotNull Builder withAsyncCompletions(@Nullable AsyncPredefinedCompletionSupplier supplier) {
            return completionsPredefinedAsync(supplier);
        }

        // Integer range validation
        public @NotNull Builder intMin(@Nullable Integer min) {
            this.intMin = min;
            return this;
        }

        public @NotNull Builder intMax(@Nullable Integer max) {
            this.intMax = max;
            return this;
        }

        public @NotNull Builder intRange(@Nullable Integer min, @Nullable Integer max) {
            this.intMin = min;
            this.intMax = max;
            return this;
        }

        // Long range validation
        public @NotNull Builder longMin(@Nullable Long min) {
            this.longMin = min;
            return this;
        }

        public @NotNull Builder longMax(@Nullable Long max) {
            this.longMax = max;
            return this;
        }

        public @NotNull Builder longRange(@Nullable Long min, @Nullable Long max) {
            this.longMin = min;
            this.longMax = max;
            return this;
        }

        // Double range validation
        public @NotNull Builder doubleMin(@Nullable Double min) {
            this.doubleMin = min;
            return this;
        }

        public @NotNull Builder doubleMax(@Nullable Double max) {
            this.doubleMax = max;
            return this;
        }

        public @NotNull Builder doubleRange(@Nullable Double min, @Nullable Double max) {
            this.doubleMin = min;
            this.doubleMax = max;
            return this;
        }

        // Float range validation
        public @NotNull Builder floatMin(@Nullable Float min) {
            this.floatMin = min;
            return this;
        }

        public @NotNull Builder floatMax(@Nullable Float max) {
            this.floatMax = max;
            return this;
        }

        public @NotNull Builder floatRange(@Nullable Float min, @Nullable Float max) {
            this.floatMin = min;
            this.floatMax = max;
            return this;
        }

        // String validation
        public @NotNull Builder stringMinLength(@Nullable Integer minLength) {
            this.stringMinLength = minLength;
            return this;
        }

        public @NotNull Builder stringMaxLength(@Nullable Integer maxLength) {
            this.stringMaxLength = maxLength;
            return this;
        }

        public @NotNull Builder stringLengthRange(@Nullable Integer minLength, @Nullable Integer maxLength) {
            this.stringMinLength = minLength;
            this.stringMaxLength = maxLength;
            return this;
        }

        public @NotNull Builder stringPattern(@Nullable String regex) {
            this.stringPattern = (regex == null) ? null : Pattern.compile(regex);
            return this;
        }

        public @NotNull Builder stringPattern(@Nullable Pattern pattern) {
            this.stringPattern = pattern;
            return this;
        }

        // Custom validators
        public @NotNull Builder addValidator(@NotNull Validator<?> validator) {
            Preconditions.checkNotNull(validator, "validator");
            this.customValidators.add(validator);
            return this;
        }

        // Did-You-Mean feature
        public @NotNull Builder didYouMean(boolean didYouMean) {
            this.didYouMean = didYouMean;
            return this;
        }

        // Default value
        public @NotNull Builder defaultValue(@Nullable Object defaultValue) {
            this.defaultValue = defaultValue;
            return this;
        }

        // Description for help/documentation
        /**
         * Set a description for this argument. The description is displayed in help
         * messages and documentation.
         *
         * @param description the description text
         * @return this builder
         */
        public @NotNull Builder description(@Nullable String description) {
            this.description = description;
            return this;
        }

        /**
         * Fluent alias for {@link #description(String)}.
         * Makes the API read more naturally: {@code withDescription("The player to target")}
         *
         * @param description the description text
         * @return this builder
         */
        public @NotNull Builder withDescription(@Nullable String description) {
            return description(description);
        }

        // ==================== Argument Aliases ====================

        /**
         * Set aliases for this argument.
         * <p>
         * Aliases allow the argument to be referenced by alternative names in key-value syntax
         * and when retrieving values from the CommandContext.
         * <p>
         * Example:
         * <pre>{@code
         * ArgContext.builder()
         *     .aliases("p", "target", "t")
         *     .build();
         *
         * // All these work:
         * // /cmd player=Notch
         * // /cmd p=Notch
         * // /cmd target=Notch
         * // ctx.get("player") or ctx.get("p") or ctx.get("target")
         * }</pre>
         *
         * @param aliases the alternative names for this argument
         * @return this builder
         */
        public @NotNull Builder aliases(@NotNull String... aliases) {
            Preconditions.checkNotNull(aliases, "aliases");
            this.aliases = new ArrayList<>();
            for (String alias : aliases) {
                if (alias != null && !alias.isBlank()) {
                    this.aliases.add(alias);
                }
            }
            return this;
        }

        /**
         * Set aliases for this argument from a list.
         *
         * @param aliases the list of alternative names
         * @return this builder
         */
        public @NotNull Builder aliases(@NotNull List<String> aliases) {
            Preconditions.checkNotNull(aliases, "aliases");
            this.aliases = new ArrayList<>();
            for (String alias : aliases) {
                if (alias != null && !alias.isBlank()) {
                    this.aliases.add(alias);
                }
            }
            return this;
        }

        /**
         * Fluent alias for {@link #aliases(String...)}.
         * Makes the API read more naturally: {@code withAliases("p", "target")}
         *
         * @param aliases the alternative names for this argument
         * @return this builder
         */
        public @NotNull Builder withAliases(@NotNull String... aliases) {
            return aliases(aliases);
        }

        /**
         * Add a single alias to this argument.
         *
         * @param alias the alias to add
         * @return this builder
         */
        public @NotNull Builder addAlias(@NotNull String alias) {
            Preconditions.checkNotNull(alias, "alias");
            if (!alias.isBlank()) {
                this.aliases.add(alias);
            }
            return this;
        }

        // ==================== String Transformer Shortcuts ====================

        /**
         * Add a transformer that converts string input to lowercase.
         * <p>
         * Example: "HELLO" -> "hello"
         *
         * @return this builder
         */
        public @NotNull Builder transformLowercase() {
            return transformer(Transformer.lowercase());
        }

        /**
         * Add a transformer that converts string input to uppercase.
         * <p>
         * Example: "hello" -> "HELLO"
         *
         * @return this builder
         */
        public @NotNull Builder transformUppercase() {
            return transformer(Transformer.uppercase());
        }

        /**
         * Add a transformer that trims whitespace from string input.
         * <p>
         * Example: "  hello  " -> "hello"
         *
         * @return this builder
         */
        public @NotNull Builder transformTrim() {
            return transformer(Transformer.trim());
        }

        /**
         * Add a transformer that normalizes whitespace (trims and collapses multiple spaces).
         * <p>
         * Example: "  hello   world  " -> "hello world"
         *
         * @return this builder
         */
        public @NotNull Builder transformNormalizeWhitespace() {
            return transformer(Transformer.normalizeWhitespace());
        }

        /**
         * Add a transformer that capitalizes the first letter.
         * <p>
         * Example: "hello" -> "Hello"
         *
         * @return this builder
         */
        public @NotNull Builder transformCapitalize() {
            return transformer(Transformer.capitalize());
        }

        // ==================== String Validation Shortcuts ====================

        /**
         * Require the string to be a valid email format.
         *
         * @return this builder
         */
        public @NotNull Builder requireEmail() {
            return stringPattern("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$");
        }

        /**
         * Require the string to be alphanumeric only (letters and digits).
         *
         * @return this builder
         */
        public @NotNull Builder requireAlphanumeric() {
            return stringPattern("^[a-zA-Z0-9]+$");
        }

        /**
         * Require the string to be a valid identifier (letters, digits, underscores, starts with letter/underscore).
         *
         * @return this builder
         */
        public @NotNull Builder requireIdentifier() {
            return stringPattern("^[a-zA-Z_][a-zA-Z0-9_]*$");
        }

        /**
         * Require the string to be a valid Minecraft username (3-16 chars, letters/digits/underscores).
         *
         * @return this builder
         */
        public @NotNull Builder requireMinecraftUsername() {
            return stringPattern("^[a-zA-Z0-9_]{3,16}$");
        }

        /**
         * Require the string to be a valid URL.
         *
         * @return this builder
         */
        public @NotNull Builder requireUrl() {
            return stringPattern("^https?://[a-zA-Z0-9.-]+(?:/[^\\s]*)?$");
        }

        /**
         * Require the string to not contain any whitespace.
         *
         * @return this builder
         */
        public @NotNull Builder requireNoWhitespace() {
            return stringPattern("^\\S+$");
        }

        // Convenience methods for common completion patterns

        /**
         * Add a single completion suggestion to the predefined list.
         * Convenience method for adding completions one at a time.
         *
         * @param completion the completion suggestion to add
         * @return this builder
         */
        public @NotNull Builder addCompletion(@NotNull String completion) {
            Preconditions.checkNotNull(completion, "completion");
            this.completionsPredefined.add(completion);
            return this;
        }

        /**
         * Add multiple completion suggestions to the predefined list.
         * Convenience method for adding several completions at once.
         *
         * @param completions the completion suggestions to add
         * @return this builder
         */
        public @NotNull Builder addCompletions(@NotNull String... completions) {
            Preconditions.checkNotNull(completions, "completions");
            for (String completion : completions) {
                if (completion != null) {
                    this.completionsPredefined.add(completion);
                }
            }
            return this;
        }

        /**
         * Set completions from an enum class, providing all enum constant names as lowercase suggestions.
         * Convenience method for enum-based completions.
         *
         * @param enumClass the enum class to extract completions from
         * @return this builder
         */
        public @NotNull Builder completionsFromEnum(@NotNull Class<? extends Enum<?>> enumClass) {
            Preconditions.checkNotNull(enumClass, "enumClass");
            Enum<?>[] constants = enumClass.getEnumConstants();
            if (constants != null) {
                this.completionsPredefined = new ArrayList<>();
                for (Enum<?> constant : constants) {
                    this.completionsPredefined.add(constant.name().toLowerCase(Locale.ROOT));
                }
            }
            return this;
        }

        /**
         * Provide range hint completions for numeric arguments.
         * When the user is typing a number, this shows a hint like "[1-100]" to indicate the valid range.
         * Note: This is a hint only and doesn't restrict input during completion.
         *
         * @param min minimum value (inclusive)
         * @param max maximum value (inclusive)
         * @return this builder
         */
        public @NotNull Builder rangeHint(int min, int max) {
            this.completionsPredefined = new ArrayList<>();
            this.completionsPredefined.add("[" + min + "-" + max + "]");
            return this;
        }

        /**
         * Provide range hint completions for numeric arguments (long version).
         * When the user is typing a number, this shows a hint like "[1-1000000]" to indicate the valid range.
         * Note: This is a hint only and doesn't restrict input during completion.
         *
         * @param min minimum value (inclusive)
         * @param max maximum value (inclusive)
         * @return this builder
         */
        public @NotNull Builder rangeHint(long min, long max) {
            this.completionsPredefined = new ArrayList<>();
            this.completionsPredefined.add("[" + min + "-" + max + "]");
            return this;
        }

        /**
         * Provide range hint completions for numeric arguments (double version).
         * When the user is typing a number, this shows a hint like "[0.0-1.0]" to indicate the valid range.
         * Note: This is a hint only and doesn't restrict input during completion.
         *
         * @param min minimum value (inclusive)
         * @param max maximum value (inclusive)
         * @return this builder
         */
        public @NotNull Builder rangeHint(double min, double max) {
            this.completionsPredefined = new ArrayList<>();
            this.completionsPredefined.add("[" + min + "-" + max + "]");
            return this;
        }

        // ==================== Transformer Methods ====================

        /**
         * Add a transformer to the transformation pipeline.
         * <p>
         * Transformers are applied in order after parsing, before validation.
         *
         * @param transformer the transformer to add
         * @return this builder
         */
        public @NotNull Builder transformer(@NotNull Transformer<?> transformer) {
            Preconditions.checkNotNull(transformer, "transformer");
            this.transformers.add(transformer);
            return this;
        }

        /**
         * Fluent alias for {@link #transformer(Transformer)}.
         *
         * @param transformer the transformer to add
         * @return this builder
         */
        public @NotNull Builder withTransformer(@NotNull Transformer<?> transformer) {
            return transformer(transformer);
        }

        /**
         * Add multiple transformers to the pipeline.
         *
         * @param transformers the transformers to add
         * @return this builder
         */
        @SafeVarargs
        public final @NotNull Builder transformers(@NotNull Transformer<?>... transformers) {
            Preconditions.checkNotNull(transformers, "transformers");
            for (Transformer<?> t : transformers) {
                if (t != null) {
                    this.transformers.add(t);
                }
            }
            return this;
        }

        // ==================== Interactive Mode ====================

        /**
         * Enable or disable interactive prompting for this argument.
         * <p>
         * When enabled and this argument is missing, the system will
         * prompt the user for input instead of failing immediately.
         *
         * @param interactive true to enable interactive mode
         * @return this builder
         */
        public @NotNull Builder interactive(boolean interactive) {
            this.interactive = interactive;
            return this;
        }

        /**
         * Fluent alias for {@link #interactive(boolean)} with true.
         *
         * @return this builder
         */
        public @NotNull Builder interactive() {
            return interactive(true);
        }

        // ==================== Argument Groups ====================

        /**
         * Set the argument group name.
         * <p>
         * Arguments in the same group are displayed together in help output.
         *
         * @param group the group name
         * @return this builder
         */
        public @NotNull Builder group(@Nullable String group) {
            this.group = group;
            return this;
        }

        /**
         * Fluent alias for {@link #group(String)}.
         *
         * @param group the group name
         * @return this builder
         */
        public @NotNull Builder inGroup(@NotNull String group) {
            return group(group);
        }

        /**
         * Alias for {@link #completionsPredefined(List)}.
         * Set the predefined completions for this argument.
         *
         * @param completions the list of completion suggestions
         * @return this builder
         */
        public @NotNull Builder completions(@NotNull List<String> completions) {
            return completionsPredefined(completions);
        }

        /**
         * Copy all values from an existing ArgContext into this builder.
         * This is useful for creating modified copies of an existing context.
         *
         * @param context the context to copy values from
         * @return this builder
         */
        public @NotNull Builder from(@NotNull ArgContext context) {
            Preconditions.checkNotNull(context, "context");
            this.optional = context.optional();
            this.greedy = context.greedy();
            this.permission = context.permission();
            this.completionsPredefined = new ArrayList<>(context.completionsPredefined());
            this.completionsDynamic = context.completionsDynamic();
            this.completionsDynamicAsync = context.completionsDynamicAsync();
            this.completionsPredefinedAsync = context.completionsPredefinedAsync();
            this.intMin = context.intMin();
            this.intMax = context.intMax();
            this.longMin = context.longMin();
            this.longMax = context.longMax();
            this.doubleMin = context.doubleMin();
            this.doubleMax = context.doubleMax();
            this.floatMin = context.floatMin();
            this.floatMax = context.floatMax();
            this.stringMinLength = context.stringMinLength();
            this.stringMaxLength = context.stringMaxLength();
            this.stringPattern = context.stringPattern();
            this.customValidators.clear();
            this.customValidators.addAll(context.customValidators());
            this.didYouMean = context.didYouMean();
            this.defaultValue = context.defaultValue();
            this.description = context.description();
            this.aliases = new ArrayList<>(context.aliases());
            this.transformers = new ArrayList<>(context.transformers());
            this.interactive = context.interactive();
            this.group = context.group();
            return this;
        }

        public @NotNull ArgContext build() {
            return new ArgContext(
                optional, greedy, permission, completionsPredefined, completionsDynamic,
                completionsDynamicAsync, completionsPredefinedAsync,
                intMin, intMax, longMin, longMax, doubleMin, doubleMax, floatMin, floatMax,
                stringMinLength, stringMaxLength, stringPattern, customValidators, didYouMean,
                defaultValue, description, aliases, transformers, interactive, group
            );
        }
    }
}
