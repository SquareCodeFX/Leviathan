package de.feelix.leviathan.command.argument;

import de.feelix.leviathan.annotations.NotNull;
import de.feelix.leviathan.annotations.Nullable;
import de.feelix.leviathan.command.completion.DynamicCompletionContext;
import de.feelix.leviathan.util.Preconditions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
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
        @NotNull List<String> provide(@NotNull DynamicCompletionContext ctx);
        
        /**
         * Create a dynamic completion provider that filters completions based on sender permissions.
         * Only completions where the sender has the specified permission will be shown.
         *
         * @param completions the base list of completions
         * @param permissionPrefix permission prefix to check (e.g., "myplugin.item.")
         * @return a dynamic completion provider that filters by permission
         */
        static @NotNull DynamicCompletionProvider permissionFiltered(@NotNull List<String> completions, @NotNull String permissionPrefix) {
            Preconditions.checkNotNull(completions, "completions");
            Preconditions.checkNotNull(permissionPrefix, "permissionPrefix");
            return ctx -> completions.stream()
                .filter(c -> ctx.sender().hasPermission(permissionPrefix + c))
                .collect(java.util.stream.Collectors.toList());
        }
        
        /**
         * Create a dynamic completion provider that returns completions based on previously parsed arguments.
         * Useful for context-dependent completions.
         *
         * @param provider function that takes the dynamic context and returns completions
         * @return a dynamic completion provider
         */
        static @NotNull DynamicCompletionProvider contextBased(@NotNull java.util.function.Function<DynamicCompletionContext, List<String>> provider) {
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
     * Functional interface for custom validation logic.
     * @param <T> the type of value being validated
     */
    @FunctionalInterface
    public interface Validator<T> {
        /**
         * Validates the given value.
         * @param value the value to validate
         * @return null if valid, or an error message if invalid
         */
        @Nullable String validate(@Nullable T value);
    }

    private final boolean optional;
    private final boolean greedy;
    private final @Nullable String permission;
    private final @NotNull List<String> completionsPredefined;
    /**
     * Optional dynamic completion provider. When present, SlashCommand will invoke it on tab-complete.
     */
    private final @Nullable DynamicCompletionProvider completionsDynamic;

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

    private ArgContext(boolean optional,
                       boolean greedy,
                       @Nullable String permission,
                       @Nullable List<String> completionsPredefined,
                       @Nullable DynamicCompletionProvider completionsDynamic,
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
                       @Nullable Object defaultValue) {
        this.optional = optional;
        this.greedy = greedy;
        this.permission = (permission == null || permission.isBlank()) ? null : permission;
        List<String> list = (completionsPredefined == null) ? List.of() : new ArrayList<>(completionsPredefined);
        this.completionsPredefined = Collections.unmodifiableList(list);
        this.completionsDynamic = completionsDynamic;
        
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
    }

    public static @NotNull Builder builder() {
        return new Builder();
    }

    public static @NotNull ArgContext defaultContext() {
        return new Builder().build();
    }

    public boolean optional() { return optional; }
    public boolean greedy() { return greedy; }
    public @Nullable String permission() { return permission; }
    public @NotNull List<String> completionsPredefined() { return completionsPredefined; }
    public @Nullable DynamicCompletionProvider completionsDynamic() { return completionsDynamic; }

    // Validation getters
    public @Nullable Integer intMin() { return intMin; }
    public @Nullable Integer intMax() { return intMax; }
    public @Nullable Long longMin() { return longMin; }
    public @Nullable Long longMax() { return longMax; }
    public @Nullable Double doubleMin() { return doubleMin; }
    public @Nullable Double doubleMax() { return doubleMax; }
    public @Nullable Float floatMin() { return floatMin; }
    public @Nullable Float floatMax() { return floatMax; }
    public @Nullable Integer stringMinLength() { return stringMinLength; }
    public @Nullable Integer stringMaxLength() { return stringMaxLength; }
    public @Nullable Pattern stringPattern() { return stringPattern; }
    public @NotNull List<Validator<?>> customValidators() { return customValidators; }
    public boolean didYouMean() { return didYouMean; }

    public @Nullable Object defaultValue() {
        return defaultValue;
    }

    public static final class Builder {
        private boolean optional;
        private boolean greedy;
        private @Nullable String permission;
        private @NotNull List<String> completionsPredefined = new ArrayList<>();
        private @Nullable DynamicCompletionProvider completionsDynamic;
        
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

        public @NotNull Builder optional(boolean optional) { this.optional = optional; return this; }
        public @NotNull Builder greedy(boolean greedy) { this.greedy = greedy; return this; }
        public @NotNull Builder permission(@Nullable String permission) { this.permission = permission; return this; }
        
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
        
        public @NotNull Builder completionsDynamic(@Nullable DynamicCompletionProvider provider) { this.completionsDynamic = provider; return this; }
        
        /**
         * Fluent alias for {@link #completionsDynamic(DynamicCompletionProvider)}.
         * Makes the API read more naturally: {@code withDynamicCompletions(provider)}
         */
        public @NotNull Builder withDynamicCompletions(@Nullable DynamicCompletionProvider provider) { 
            return completionsDynamic(provider); 
        }
        
        // Integer range validation
        public @NotNull Builder intMin(@Nullable Integer min) { this.intMin = min; return this; }
        public @NotNull Builder intMax(@Nullable Integer max) { this.intMax = max; return this; }
        public @NotNull Builder intRange(@Nullable Integer min, @Nullable Integer max) { 
            this.intMin = min; 
            this.intMax = max; 
            return this; 
        }
        
        // Long range validation
        public @NotNull Builder longMin(@Nullable Long min) { this.longMin = min; return this; }
        public @NotNull Builder longMax(@Nullable Long max) { this.longMax = max; return this; }
        public @NotNull Builder longRange(@Nullable Long min, @Nullable Long max) { 
            this.longMin = min; 
            this.longMax = max; 
            return this; 
        }
        
        // Double range validation
        public @NotNull Builder doubleMin(@Nullable Double min) { this.doubleMin = min; return this; }
        public @NotNull Builder doubleMax(@Nullable Double max) { this.doubleMax = max; return this; }
        public @NotNull Builder doubleRange(@Nullable Double min, @Nullable Double max) { 
            this.doubleMin = min; 
            this.doubleMax = max; 
            return this; 
        }
        
        // Float range validation
        public @NotNull Builder floatMin(@Nullable Float min) { this.floatMin = min; return this; }
        public @NotNull Builder floatMax(@Nullable Float max) { this.floatMax = max; return this; }
        public @NotNull Builder floatRange(@Nullable Float min, @Nullable Float max) { 
            this.floatMin = min; 
            this.floatMax = max; 
            return this; 
        }
        
        // String validation
        public @NotNull Builder stringMinLength(@Nullable Integer minLength) { this.stringMinLength = minLength; return this; }
        public @NotNull Builder stringMaxLength(@Nullable Integer maxLength) { this.stringMaxLength = maxLength; return this; }
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
        
        public @NotNull ArgContext build() {
            return new ArgContext(optional, greedy, permission, completionsPredefined, completionsDynamic,
                intMin, intMax, longMin, longMax, doubleMin, doubleMax, floatMin, floatMax,
                                  stringMinLength, stringMaxLength, stringPattern, customValidators, didYouMean,
                                  defaultValue
            );
        }
    }
}
