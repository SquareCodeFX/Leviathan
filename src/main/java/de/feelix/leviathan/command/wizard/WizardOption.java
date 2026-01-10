package de.feelix.leviathan.command.wizard;

import de.feelix.leviathan.annotations.NotNull;
import de.feelix.leviathan.annotations.Nullable;
import de.feelix.leviathan.util.Preconditions;

import java.util.function.Predicate;

/**
 * Represents a selectable option in a wizard node.
 * <p>
 * Options can:
 * <ul>
 *   <li>Have display text and an internal key</li>
 *   <li>Lead to another node or execute a final action</li>
 *   <li>Be conditional based on player state or previous choices</li>
 *   <li>Include a description for help text</li>
 * </ul>
 */
public final class WizardOption {

    private final String key;
    private final String displayText;
    private final @Nullable String description;
    private final @Nullable String nextNodeId;
    private final @Nullable WizardAction action;
    private final @Nullable Predicate<WizardContext> condition;
    private final boolean requiresConfirmation;

    private WizardOption(Builder builder) {
        this.key = builder.key;
        this.displayText = builder.displayText;
        this.description = builder.description;
        this.nextNodeId = builder.nextNodeId;
        this.action = builder.action;
        this.condition = builder.condition;
        this.requiresConfirmation = builder.requiresConfirmation;
    }

    /**
     * Create a simple option that leads to another node.
     *
     * @param key        the option key (what user types)
     * @param nextNodeId the next node to navigate to
     * @return a new option
     */
    public static @NotNull WizardOption to(@NotNull String key, @NotNull String nextNodeId) {
        return builder(key).nextNode(nextNodeId).build();
    }

    /**
     * Create an option with display text that leads to another node.
     *
     * @param key         the option key
     * @param displayText the text to display
     * @param nextNodeId  the next node to navigate to
     * @return a new option
     */
    public static @NotNull WizardOption to(@NotNull String key, @NotNull String displayText,
                                            @NotNull String nextNodeId) {
        return builder(key).displayText(displayText).nextNode(nextNodeId).build();
    }

    /**
     * Create an option that executes an action (terminal option).
     *
     * @param key    the option key
     * @param action the action to execute
     * @return a new option
     */
    public static @NotNull WizardOption action(@NotNull String key, @NotNull WizardAction action) {
        return builder(key).action(action).build();
    }

    /**
     * Create a new builder.
     *
     * @param key the option key
     * @return a new builder
     */
    public static @NotNull Builder builder(@NotNull String key) {
        return new Builder(key);
    }

    // ==================== Accessors ====================

    /**
     * @return the option key (what user types to select)
     */
    public @NotNull String key() {
        return key;
    }

    /**
     * @return the display text (shown in the menu)
     */
    public @NotNull String displayText() {
        return displayText;
    }

    /**
     * @return the description, or null if not set
     */
    public @Nullable String description() {
        return description;
    }

    /**
     * @return the next node ID, or null if this is a terminal option
     */
    public @Nullable String nextNodeId() {
        return nextNodeId;
    }

    /**
     * @return the action to execute, or null if this leads to another node
     */
    public @Nullable WizardAction action() {
        return action;
    }

    /**
     * @return true if this option leads to another node
     */
    public boolean isNavigation() {
        return nextNodeId != null;
    }

    /**
     * @return true if this option executes an action
     */
    public boolean isTerminal() {
        return action != null;
    }

    /**
     * @return true if this option requires confirmation before execution
     */
    public boolean requiresConfirmation() {
        return requiresConfirmation;
    }

    /**
     * Check if this option is available in the given context.
     *
     * @param context the wizard context
     * @return true if the option is available
     */
    public boolean isAvailable(@NotNull WizardContext context) {
        if (condition == null) {
            return true;
        }
        try {
            return condition.test(context);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Format this option for display.
     *
     * @param index      the option index (1-based)
     * @param showNumber whether to show the number
     * @return the formatted display string
     */
    public @NotNull String format(int index, boolean showNumber) {
        StringBuilder sb = new StringBuilder();
        if (showNumber) {
            sb.append("§e[").append(index).append("] ");
        } else {
            sb.append("§e[").append(key).append("] ");
        }
        sb.append("§f").append(displayText);
        if (description != null) {
            sb.append(" §7- ").append(description);
        }
        return sb.toString();
    }

    // ==================== Builder ====================

    /**
     * Builder for WizardOption.
     */
    public static final class Builder {
        private final String key;
        private String displayText;
        private @Nullable String description;
        private @Nullable String nextNodeId;
        private @Nullable WizardAction action;
        private @Nullable Predicate<WizardContext> condition;
        private boolean requiresConfirmation = false;

        private Builder(@NotNull String key) {
            this.key = Preconditions.checkNotNull(key, "key");
            this.displayText = key; // Default display text is the key
        }

        /**
         * Set the display text.
         *
         * @param displayText the text to show
         * @return this builder
         */
        public @NotNull Builder displayText(@NotNull String displayText) {
            this.displayText = Preconditions.checkNotNull(displayText, "displayText");
            return this;
        }

        /**
         * Set the description.
         *
         * @param description the description
         * @return this builder
         */
        public @NotNull Builder description(@Nullable String description) {
            this.description = description;
            return this;
        }

        /**
         * Set the next node to navigate to.
         *
         * @param nodeId the node ID
         * @return this builder
         */
        public @NotNull Builder nextNode(@NotNull String nodeId) {
            this.nextNodeId = Preconditions.checkNotNull(nodeId, "nodeId");
            return this;
        }

        /**
         * Set the action to execute (makes this a terminal option).
         *
         * @param action the action
         * @return this builder
         */
        public @NotNull Builder action(@NotNull WizardAction action) {
            this.action = Preconditions.checkNotNull(action, "action");
            return this;
        }

        /**
         * Set a condition for this option to be available.
         *
         * @param condition the condition
         * @return this builder
         */
        public @NotNull Builder condition(@NotNull Predicate<WizardContext> condition) {
            this.condition = Preconditions.checkNotNull(condition, "condition");
            return this;
        }

        /**
         * Require confirmation before executing the action.
         *
         * @param requiresConfirmation whether confirmation is required
         * @return this builder
         */
        public @NotNull Builder requiresConfirmation(boolean requiresConfirmation) {
            this.requiresConfirmation = requiresConfirmation;
            return this;
        }

        /**
         * Build the option.
         *
         * @return the wizard option
         */
        public @NotNull WizardOption build() {
            if (nextNodeId == null && action == null) {
                throw new IllegalStateException("Option must have either a next node or an action");
            }
            if (nextNodeId != null && action != null) {
                throw new IllegalStateException("Option cannot have both a next node and an action");
            }
            return new WizardOption(this);
        }
    }

    @Override
    public String toString() {
        if (isNavigation()) {
            return String.format("WizardOption[%s -> %s]", key, nextNodeId);
        } else {
            return String.format("WizardOption[%s -> ACTION]", key);
        }
    }
}
