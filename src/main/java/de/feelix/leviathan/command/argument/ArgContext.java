package de.feelix.leviathan.command.argument;

import de.feelix.leviathan.annotations.NotNull;
import de.feelix.leviathan.annotations.Nullable;
import de.feelix.leviathan.command.completion.DynamicCompletionContext;
import de.feelix.leviathan.util.Preconditions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
     * Optional dynamic completion provider. When present, FluentCommand will invoke it on tab-complete.
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
        public @NotNull Builder completionsDynamic(@Nullable DynamicCompletionProvider provider) { this.completionsDynamic = provider; return this; }
        
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
        
        public @NotNull ArgContext build() {
            return new ArgContext(optional, greedy, permission, completionsPredefined, completionsDynamic,
                intMin, intMax, longMin, longMax, doubleMin, doubleMax, floatMin, floatMax,
                                  stringMinLength, stringMaxLength, stringPattern, customValidators, didYouMean,
                                  defaultValue
            );
        }
    }
}
