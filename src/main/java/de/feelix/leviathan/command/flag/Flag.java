package de.feelix.leviathan.command.flag;

import de.feelix.leviathan.annotations.NotNull;
import de.feelix.leviathan.annotations.Nullable;
import de.feelix.leviathan.exceptions.CommandConfigurationException;
import de.feelix.leviathan.util.Preconditions;

/**
 * Describes a command flag (switch/option) that can be toggled on or off.
 * <p>
 * Flags support multiple formats:
 * <ul>
 *   <li>Short form: {@code -s} (single character)</li>
 *   <li>Long form: {@code --silent}</li>
 *   <li>Combined short flags: {@code -sf} (equivalent to {@code -s -f})</li>
 *   <li>Negation: {@code --no-confirm} sets the flag to false</li>
 * </ul>
 * <p>
 * Flags always have a default value of {@code false} unless explicitly set otherwise.
 * Instances are immutable and can be safely reused across commands.
 */
public final class Flag {
    private final String name;
    private final @Nullable Character shortForm;
    private final @Nullable String longForm;
    private final @Nullable String description;
    private final boolean defaultValue;
    private final boolean supportsNegation;
    private final @Nullable String permission;

    private Flag(@NotNull String name,
                 @Nullable Character shortForm,
                 @Nullable String longForm,
                 @Nullable String description,
                 boolean defaultValue,
                 boolean supportsNegation,
                 @Nullable String permission) {
        this.name = Preconditions.checkNotNull(name, "name");
        if (this.name.isBlank()) {
            throw new CommandConfigurationException("Flag name must not be blank");
        }
        if (this.name.chars().anyMatch(Character::isWhitespace)) {
            throw new CommandConfigurationException("Flag name must not contain whitespace: '" + this.name + "'");
        }
        if (shortForm == null && longForm == null) {
            throw new CommandConfigurationException("Flag '" + name + "' must have at least a short form or long form");
        }
        if (shortForm != null && !Character.isLetter(shortForm)) {
            throw new CommandConfigurationException("Flag short form must be a letter: '" + shortForm + "'");
        }
        if (longForm != null && longForm.isBlank()) {
            throw new CommandConfigurationException("Flag long form must not be blank");
        }
        if (longForm != null && longForm.contains(" ")) {
            throw new CommandConfigurationException("Flag long form must not contain spaces: '" + longForm + "'");
        }
        this.shortForm = shortForm;
        this.longForm = longForm;
        this.description = description;
        this.defaultValue = defaultValue;
        this.supportsNegation = supportsNegation;
        this.permission = (permission == null || permission.isBlank()) ? null : permission;
    }

    /**
     * Create a new flag builder with the given name.
     *
     * @param name the flag name used to retrieve the value from context
     * @return a new flag builder
     */
    public static @NotNull Builder builder(@NotNull String name) {
        return new Builder(name);
    }

    /**
     * Create a simple flag with short form only.
     *
     * @param name      the flag name used to retrieve the value from context
     * @param shortForm the single character short form (e.g., 's' for -s)
     * @return a new flag instance
     */
    public static @NotNull Flag ofShort(@NotNull String name, char shortForm) {
        return builder(name).shortForm(shortForm).build();
    }

    /**
     * Create a simple flag with long form only.
     *
     * @param name     the flag name used to retrieve the value from context
     * @param longForm the long form without dashes (e.g., "silent" for --silent)
     * @return a new flag instance
     */
    public static @NotNull Flag ofLong(@NotNull String name, @NotNull String longForm) {
        return builder(name).longForm(longForm).build();
    }

    /**
     * Create a flag with both short and long forms.
     *
     * @param name      the flag name used to retrieve the value from context
     * @param shortForm the single character short form (e.g., 's' for -s)
     * @param longForm  the long form without dashes (e.g., "silent" for --silent)
     * @return a new flag instance
     */
    public static @NotNull Flag of(@NotNull String name, char shortForm, @NotNull String longForm) {
        return builder(name).shortForm(shortForm).longForm(longForm).build();
    }

    /**
     * @return the flag name as used in the {@code CommandContext}
     */
    public @NotNull String name() {
        return name;
    }

    /**
     * @return the short form character, or null if not defined
     */
    public @Nullable Character shortForm() {
        return shortForm;
    }

    /**
     * @return the long form string (without dashes), or null if not defined
     */
    public @Nullable String longForm() {
        return longForm;
    }

    /**
     * @return the human-readable description of this flag, or null if not set
     */
    public @Nullable String description() {
        return description;
    }

    /**
     * @return the default value when the flag is not present (default: false)
     */
    public boolean defaultValue() {
        return defaultValue;
    }

    /**
     * @return true if the flag supports negation (--no-xxx syntax)
     */
    public boolean supportsNegation() {
        return supportsNegation;
    }

    /**
     * @return the required permission to use this flag, or null if none
     */
    public @Nullable String permission() {
        return permission;
    }

    /**
     * Check if this flag matches the given short form character.
     *
     * @param c the character to check
     * @return true if this flag's short form matches
     */
    public boolean matchesShort(char c) {
        return shortForm != null && shortForm == c;
    }

    /**
     * Check if this flag matches the given long form string.
     *
     * @param form the long form to check (without dashes)
     * @return true if this flag's long form matches (case-insensitive)
     */
    public boolean matchesLong(@NotNull String form) {
        return longForm != null && longForm.equalsIgnoreCase(form);
    }

    /**
     * Check if this flag matches the given negated long form.
     * For example, if longForm is "confirm", this matches "no-confirm".
     *
     * @param form the form to check (without dashes, should start with "no-")
     * @return true if this is a negated form of the flag
     */
    public boolean matchesNegatedLong(@NotNull String form) {
        if (!supportsNegation || longForm == null) {
            return false;
        }
        String expected = "no-" + longForm;
        return expected.equalsIgnoreCase(form);
    }

    /**
     * Builder for creating {@link Flag} instances with a fluent API.
     */
    public static final class Builder {
        private final String name;
        private @Nullable Character shortForm;
        private @Nullable String longForm;
        private @Nullable String description;
        private boolean defaultValue = false;
        private boolean supportsNegation = true;
        private @Nullable String permission;

        private Builder(@NotNull String name) {
            this.name = name;
        }

        /**
         * Set the short form for this flag.
         *
         * @param shortForm single character (e.g., 's' for -s)
         * @return this builder
         */
        public @NotNull Builder shortForm(char shortForm) {
            this.shortForm = shortForm;
            return this;
        }

        /**
         * Set the long form for this flag.
         *
         * @param longForm the long form without dashes (e.g., "silent" for --silent)
         * @return this builder
         */
        public @NotNull Builder longForm(@NotNull String longForm) {
            this.longForm = longForm;
            return this;
        }

        /**
         * Set a human-readable description for this flag.
         *
         * @param description the description
         * @return this builder
         */
        public @NotNull Builder description(@Nullable String description) {
            this.description = description;
            return this;
        }

        /**
         * Set the default value for this flag when not present.
         *
         * @param defaultValue the default value (default: false)
         * @return this builder
         */
        public @NotNull Builder defaultValue(boolean defaultValue) {
            this.defaultValue = defaultValue;
            return this;
        }

        /**
         * Set whether this flag supports the negation syntax (--no-xxx).
         *
         * @param supportsNegation true to support negation (default: true)
         * @return this builder
         */
        public @NotNull Builder supportsNegation(boolean supportsNegation) {
            this.supportsNegation = supportsNegation;
            return this;
        }

        /**
         * Set the required permission to use this flag.
         *
         * @param permission the permission node, or null for no permission required
         * @return this builder
         */
        public @NotNull Builder permission(@Nullable String permission) {
            this.permission = permission;
            return this;
        }

        /**
         * Build the flag instance.
         *
         * @return a new immutable Flag instance
         * @throws CommandConfigurationException if the configuration is invalid
         */
        public @NotNull Flag build() {
            return new Flag(name, shortForm, longForm, description, defaultValue, supportsNegation, permission);
        }
    }
}
