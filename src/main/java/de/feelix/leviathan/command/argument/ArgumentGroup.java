package de.feelix.leviathan.command.argument;

import de.feelix.leviathan.annotations.NotNull;
import de.feelix.leviathan.annotations.Nullable;
import de.feelix.leviathan.util.Preconditions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents a logical grouping of related arguments.
 * <p>
 * Argument groups allow you to:
 * <ul>
 *   <li>Organize related arguments together in help output</li>
 *   <li>Show grouped error messages when validation fails</li>
 *   <li>Apply group-level constraints (e.g., "at least one required")</li>
 * </ul>
 * <p>
 * Example usage:
 * <pre>{@code
 * ArgumentGroup outputGroup = ArgumentGroup.builder("Output Options")
 *     .description("Options for controlling output format")
 *     .members("format", "output", "verbose", "quiet")
 *     .atLeastOne(true)  // At least one output option required
 *     .build();
 *
 * SlashCommand.create("export")
 *     .argString("file")
 *     .argString("format").optional(true)
 *     .argString("output").optional(true)
 *     .flag("verbose")
 *     .flag("quiet")
 *     .argumentGroup(outputGroup)
 *     .build();
 * }</pre>
 */
public final class ArgumentGroup {

    private final @NotNull String name;
    private final @Nullable String description;
    private final @NotNull List<String> memberNames;
    private final boolean atLeastOneRequired;
    private final boolean mutuallyExclusive;
    private final boolean allRequired;

    private ArgumentGroup(Builder builder) {
        this.name = builder.name;
        this.description = builder.description;
        this.memberNames = Collections.unmodifiableList(new ArrayList<>(builder.memberNames));
        this.atLeastOneRequired = builder.atLeastOneRequired;
        this.mutuallyExclusive = builder.mutuallyExclusive;
        this.allRequired = builder.allRequired;
    }

    /**
     * Create a new ArgumentGroup builder.
     *
     * @param name the display name for this group
     * @return a new builder
     */
    public static @NotNull Builder builder(@NotNull String name) {
        return new Builder(name);
    }

    /**
     * Create a simple argument group with just a name and members.
     *
     * @param name    the display name for this group
     * @param members the argument/flag names in this group
     * @return a new ArgumentGroup
     */
    public static @NotNull ArgumentGroup of(@NotNull String name, @NotNull String... members) {
        return builder(name).members(members).build();
    }

    /**
     * Create a mutually exclusive group (only one member can be provided).
     *
     * @param name    the display name for this group
     * @param members the argument/flag names in this group
     * @return a new ArgumentGroup with mutual exclusivity
     */
    public static @NotNull ArgumentGroup mutuallyExclusive(@NotNull String name, @NotNull String... members) {
        return builder(name).members(members).mutuallyExclusive(true).build();
    }

    /**
     * Create a group where at least one member is required.
     *
     * @param name    the display name for this group
     * @param members the argument/flag names in this group
     * @return a new ArgumentGroup with at-least-one requirement
     */
    public static @NotNull ArgumentGroup atLeastOne(@NotNull String name, @NotNull String... members) {
        return builder(name).members(members).atLeastOne(true).build();
    }

    /**
     * Get the display name of this group.
     *
     * @return the group name
     */
    public @NotNull String name() {
        return name;
    }

    /**
     * Get the description of this group.
     *
     * @return the description, or null if not set
     */
    public @Nullable String description() {
        return description;
    }

    /**
     * Get the names of arguments/flags in this group.
     *
     * @return immutable list of member names
     */
    public @NotNull List<String> memberNames() {
        return memberNames;
    }

    /**
     * Check if this group requires at least one member to be provided.
     *
     * @return true if at least one is required
     */
    public boolean isAtLeastOneRequired() {
        return atLeastOneRequired;
    }

    /**
     * Check if this group's members are mutually exclusive.
     *
     * @return true if only one member can be provided
     */
    public boolean isMutuallyExclusive() {
        return mutuallyExclusive;
    }

    /**
     * Check if all members in this group are required.
     *
     * @return true if all members are required
     */
    public boolean isAllRequired() {
        return allRequired;
    }

    /**
     * Check if a given argument/flag name is a member of this group.
     *
     * @param name the name to check
     * @return true if the name is a member of this group
     */
    public boolean contains(@NotNull String name) {
        Preconditions.checkNotNull(name, "name");
        return memberNames.contains(name);
    }

    /**
     * Get the number of members in this group.
     *
     * @return member count
     */
    public int size() {
        return memberNames.size();
    }

    /**
     * Check if this group is empty.
     *
     * @return true if no members
     */
    public boolean isEmpty() {
        return memberNames.isEmpty();
    }

    @Override
    public String toString() {
        return "ArgumentGroup{" +
               "name='" + name + '\'' +
               ", members=" + memberNames +
               ", atLeastOneRequired=" + atLeastOneRequired +
               ", mutuallyExclusive=" + mutuallyExclusive +
               '}';
    }

    /**
     * Builder for ArgumentGroup.
     */
    public static final class Builder {
        private final @NotNull String name;
        private @Nullable String description;
        private final List<String> memberNames = new ArrayList<>();
        private boolean atLeastOneRequired = false;
        private boolean mutuallyExclusive = false;
        private boolean allRequired = false;

        private Builder(@NotNull String name) {
            Preconditions.checkNotNull(name, "name");
            Preconditions.checkArgument(!name.isBlank(), "name cannot be blank");
            this.name = name;
        }

        /**
         * Set the description for this group.
         *
         * @param description the description text
         * @return this builder
         */
        public @NotNull Builder description(@Nullable String description) {
            this.description = description;
            return this;
        }

        /**
         * Add member argument/flag names to this group.
         *
         * @param names the names to add
         * @return this builder
         */
        public @NotNull Builder members(@NotNull String... names) {
            Preconditions.checkNotNull(names, "names");
            for (String name : names) {
                if (name != null && !name.isBlank()) {
                    memberNames.add(name);
                }
            }
            return this;
        }

        /**
         * Add member argument/flag names from a list.
         *
         * @param names the names to add
         * @return this builder
         */
        public @NotNull Builder members(@NotNull List<String> names) {
            Preconditions.checkNotNull(names, "names");
            for (String name : names) {
                if (name != null && !name.isBlank()) {
                    memberNames.add(name);
                }
            }
            return this;
        }

        /**
         * Add a single member to this group.
         *
         * @param name the member name
         * @return this builder
         */
        public @NotNull Builder addMember(@NotNull String name) {
            Preconditions.checkNotNull(name, "name");
            if (!name.isBlank()) {
                memberNames.add(name);
            }
            return this;
        }

        /**
         * Set whether at least one member must be provided.
         *
         * @param required true if at least one is required
         * @return this builder
         */
        public @NotNull Builder atLeastOne(boolean required) {
            this.atLeastOneRequired = required;
            return this;
        }

        /**
         * Set whether members are mutually exclusive.
         *
         * @param exclusive true if only one can be provided
         * @return this builder
         */
        public @NotNull Builder mutuallyExclusive(boolean exclusive) {
            this.mutuallyExclusive = exclusive;
            return this;
        }

        /**
         * Set whether all members are required.
         *
         * @param required true if all must be provided
         * @return this builder
         */
        public @NotNull Builder allRequired(boolean required) {
            this.allRequired = required;
            return this;
        }

        /**
         * Build the ArgumentGroup.
         *
         * @return the built ArgumentGroup
         */
        public @NotNull ArgumentGroup build() {
            return new ArgumentGroup(this);
        }
    }
}
