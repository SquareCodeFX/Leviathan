package de.feelix.leviathan.command.wizard;

import de.feelix.leviathan.annotations.NotNull;
import de.feelix.leviathan.annotations.Nullable;
import de.feelix.leviathan.command.argument.ArgumentParser;
import de.feelix.leviathan.command.argument.ParseResult;
import de.feelix.leviathan.util.Preconditions;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Represents a node in the wizard decision tree.
 * <p>
 * Nodes can be:
 * <ul>
 *   <li><b>Question nodes</b> - Present options for the user to choose from</li>
 *   <li><b>Input nodes</b> - Accept free-form input with validation</li>
 *   <li><b>Action nodes</b> - Execute code and navigate to next node</li>
 *   <li><b>Terminal nodes</b> - End the wizard with a final action</li>
 * </ul>
 */
public final class WizardNode {

    /**
     * Types of wizard nodes.
     */
    public enum NodeType {
        /**
         * Node that presents options to choose from.
         */
        QUESTION,

        /**
         * Node that accepts text input.
         */
        INPUT,

        /**
         * Node that executes an action.
         */
        ACTION,

        /**
         * Terminal node that ends the wizard.
         */
        TERMINAL
    }

    private final String id;
    private final NodeType type;
    private final String questionText;
    private final List<WizardOption> options;
    private final @Nullable String variableName;
    private final @Nullable ArgumentParser<?> inputParser;
    private final @Nullable String inputPrompt;
    private final @Nullable String nextNodeId;
    private final @Nullable WizardAction action;
    private final @Nullable Consumer<WizardContext> onEnter;
    private final @Nullable Predicate<WizardContext> skipCondition;
    private final @Nullable String skipToNodeId;
    private final boolean allowBack;
    private final boolean showNumbers;

    private WizardNode(Builder builder) {
        this.id = builder.id;
        this.type = builder.type;
        this.questionText = builder.questionText;
        this.options = Collections.unmodifiableList(new ArrayList<>(builder.options));
        this.variableName = builder.variableName;
        this.inputParser = builder.inputParser;
        this.inputPrompt = builder.inputPrompt;
        this.nextNodeId = builder.nextNodeId;
        this.action = builder.action;
        this.onEnter = builder.onEnter;
        this.skipCondition = builder.skipCondition;
        this.skipToNodeId = builder.skipToNodeId;
        this.allowBack = builder.allowBack;
        this.showNumbers = builder.showNumbers;
    }

    /**
     * Create a question node builder.
     *
     * @param id           the node ID
     * @param questionText the question to display
     * @return a new builder
     */
    public static @NotNull Builder question(@NotNull String id, @NotNull String questionText) {
        return new Builder(id).type(NodeType.QUESTION).questionText(questionText);
    }

    /**
     * Create an input node builder.
     *
     * @param id           the node ID
     * @param variableName the variable name to store input
     * @param parser       the input parser
     * @return a new builder
     */
    public static <T> @NotNull Builder input(@NotNull String id, @NotNull String variableName,
                                               @NotNull ArgumentParser<T> parser) {
        return new Builder(id).type(NodeType.INPUT).variableName(variableName).inputParser(parser);
    }

    /**
     * Create an action node builder.
     *
     * @param id     the node ID
     * @param action the action to execute
     * @return a new builder
     */
    public static @NotNull Builder action(@NotNull String id, @NotNull WizardAction action) {
        return new Builder(id).type(NodeType.ACTION).action(action);
    }

    /**
     * Create a terminal node builder.
     *
     * @param id     the node ID
     * @param action the final action to execute
     * @return a new builder
     */
    public static @NotNull Builder terminal(@NotNull String id, @NotNull WizardAction action) {
        return new Builder(id).type(NodeType.TERMINAL).action(action);
    }

    /**
     * Create a new builder.
     *
     * @param id the node ID
     * @return a new builder
     */
    public static @NotNull Builder builder(@NotNull String id) {
        return new Builder(id);
    }

    // ==================== Accessors ====================

    /**
     * @return the node ID
     */
    public @NotNull String id() {
        return id;
    }

    /**
     * @return the node type
     */
    public @NotNull NodeType type() {
        return type;
    }

    /**
     * @return the question text to display
     */
    public @NotNull String questionText() {
        return questionText;
    }

    /**
     * @return the list of options for question nodes
     */
    public @NotNull List<WizardOption> options() {
        return options;
    }

    /**
     * Get available options for the given context.
     *
     * @param context the wizard context
     * @return list of available options
     */
    public @NotNull List<WizardOption> availableOptions(@NotNull WizardContext context) {
        List<WizardOption> available = new ArrayList<>();
        for (WizardOption option : options) {
            if (option.isAvailable(context)) {
                available.add(option);
            }
        }
        return available;
    }

    /**
     * @return the variable name for input nodes, or null
     */
    public @Nullable String variableName() {
        return variableName;
    }

    /**
     * @return the input parser for input nodes, or null
     */
    public @Nullable ArgumentParser<?> inputParser() {
        return inputParser;
    }

    /**
     * @return the input prompt for input nodes, or null
     */
    public @Nullable String inputPrompt() {
        return inputPrompt;
    }

    /**
     * @return the next node ID for action nodes, or null
     */
    public @Nullable String nextNodeId() {
        return nextNodeId;
    }

    /**
     * @return the action to execute, or null
     */
    public @Nullable WizardAction action() {
        return action;
    }

    /**
     * @return true if going back is allowed from this node
     */
    public boolean allowBack() {
        return allowBack;
    }

    /**
     * @return true if numbers should be shown for options
     */
    public boolean showNumbers() {
        return showNumbers;
    }

    /**
     * @return true if this is a terminal node
     */
    public boolean isTerminal() {
        return type == NodeType.TERMINAL;
    }

    /**
     * @return true if this is a question node
     */
    public boolean isQuestion() {
        return type == NodeType.QUESTION;
    }

    /**
     * @return true if this is an input node
     */
    public boolean isInput() {
        return type == NodeType.INPUT;
    }

    /**
     * @return true if this is an action node
     */
    public boolean isAction() {
        return type == NodeType.ACTION;
    }

    // ==================== Execution ====================

    /**
     * Called when entering this node.
     *
     * @param context the wizard context
     */
    public void enter(@NotNull WizardContext context) {
        if (onEnter != null) {
            onEnter.accept(context);
        }
    }

    /**
     * Check if this node should be skipped.
     *
     * @param context the wizard context
     * @return true if the node should be skipped
     */
    public boolean shouldSkip(@NotNull WizardContext context) {
        return skipCondition != null && skipCondition.test(context);
    }

    /**
     * Get the node to skip to if this node is skipped.
     *
     * @return the skip target node ID, or null to use default next
     */
    public @Nullable String skipToNodeId() {
        return skipToNodeId;
    }

    /**
     * Find an option by key.
     *
     * @param key the option key
     * @return the option, or null if not found
     */
    public @Nullable WizardOption findOption(@NotNull String key) {
        for (WizardOption option : options) {
            if (option.key().equalsIgnoreCase(key)) {
                return option;
            }
        }
        return null;
    }

    /**
     * Find an option by index (1-based).
     *
     * @param index the option index
     * @return the option, or null if out of range
     */
    public @Nullable WizardOption findOptionByIndex(int index) {
        if (index < 1 || index > options.size()) {
            return null;
        }
        return options.get(index - 1);
    }

    /**
     * Parse input for an input node.
     *
     * @param input  the user input
     * @param player the player
     * @return the parse result
     */
    @SuppressWarnings("unchecked")
    public @NotNull ParseResult<?> parseInput(@NotNull String input, @NotNull Player player) {
        if (inputParser == null) {
            return ParseResult.failure("This node does not accept input");
        }
        return inputParser.parse(input, player);
    }

    /**
     * Display this node to a player.
     *
     * @param context the wizard context
     */
    public void display(@NotNull WizardContext context) {
        Player player = context.player();

        // Display question/prompt
        player.sendMessage("");
        player.sendMessage("§6§l" + questionText);

        if (type == NodeType.QUESTION) {
            // Display options
            List<WizardOption> available = availableOptions(context);
            for (int i = 0; i < available.size(); i++) {
                WizardOption option = available.get(i);
                player.sendMessage(option.format(i + 1, showNumbers));
            }
            player.sendMessage("");
            if (showNumbers) {
                player.sendMessage("§7Type the number or name of your choice.");
            } else {
                player.sendMessage("§7Type the name of your choice.");
            }
        } else if (type == NodeType.INPUT) {
            if (inputPrompt != null) {
                player.sendMessage("§7" + inputPrompt);
            }
            player.sendMessage("§7Type your answer:");
        }

        // Show back option if available
        if (allowBack && context.canGoBack()) {
            player.sendMessage("§8Type 'back' to go to the previous step.");
        }
        player.sendMessage("§8Type 'cancel' to exit the wizard.");
    }

    // ==================== Builder ====================

    /**
     * Builder for WizardNode.
     */
    public static final class Builder {
        private final String id;
        private NodeType type = NodeType.QUESTION;
        private String questionText = "";
        private final List<WizardOption> options = new ArrayList<>();
        private @Nullable String variableName;
        private @Nullable ArgumentParser<?> inputParser;
        private @Nullable String inputPrompt;
        private @Nullable String nextNodeId;
        private @Nullable WizardAction action;
        private @Nullable Consumer<WizardContext> onEnter;
        private @Nullable Predicate<WizardContext> skipCondition;
        private @Nullable String skipToNodeId;
        private boolean allowBack = true;
        private boolean showNumbers = true;

        private Builder(@NotNull String id) {
            this.id = Preconditions.checkNotNull(id, "id");
        }

        /**
         * Set the node type.
         *
         * @param type the node type
         * @return this builder
         */
        public @NotNull Builder type(@NotNull NodeType type) {
            this.type = Preconditions.checkNotNull(type, "type");
            return this;
        }

        /**
         * Set the question text.
         *
         * @param text the question text
         * @return this builder
         */
        public @NotNull Builder questionText(@NotNull String text) {
            this.questionText = Preconditions.checkNotNull(text, "text");
            return this;
        }

        /**
         * Add an option.
         *
         * @param option the option
         * @return this builder
         */
        public @NotNull Builder option(@NotNull WizardOption option) {
            this.options.add(Preconditions.checkNotNull(option, "option"));
            return this;
        }

        /**
         * Add a simple option that leads to another node.
         *
         * @param key        the option key
         * @param nextNodeId the next node ID
         * @return this builder
         */
        public @NotNull Builder option(@NotNull String key, @NotNull String nextNodeId) {
            return option(WizardOption.to(key, nextNodeId));
        }

        /**
         * Add an option with display text.
         *
         * @param key         the option key
         * @param displayText the display text
         * @param nextNodeId  the next node ID
         * @return this builder
         */
        public @NotNull Builder option(@NotNull String key, @NotNull String displayText, @NotNull String nextNodeId) {
            return option(WizardOption.to(key, displayText, nextNodeId));
        }

        /**
         * Add multiple options.
         *
         * @param options the options
         * @return this builder
         */
        public @NotNull Builder options(@NotNull WizardOption... options) {
            for (WizardOption option : options) {
                option(option);
            }
            return this;
        }

        /**
         * Set the variable name for input nodes.
         *
         * @param name the variable name
         * @return this builder
         */
        public @NotNull Builder variableName(@NotNull String name) {
            this.variableName = Preconditions.checkNotNull(name, "name");
            return this;
        }

        /**
         * Set the input parser.
         *
         * @param parser the parser
         * @return this builder
         */
        public @NotNull Builder inputParser(@NotNull ArgumentParser<?> parser) {
            this.inputParser = Preconditions.checkNotNull(parser, "parser");
            return this;
        }

        /**
         * Set the input prompt.
         *
         * @param prompt the prompt text
         * @return this builder
         */
        public @NotNull Builder inputPrompt(@Nullable String prompt) {
            this.inputPrompt = prompt;
            return this;
        }

        /**
         * Set the next node ID for action nodes.
         *
         * @param nodeId the next node ID
         * @return this builder
         */
        public @NotNull Builder nextNode(@NotNull String nodeId) {
            this.nextNodeId = Preconditions.checkNotNull(nodeId, "nodeId");
            return this;
        }

        /**
         * Set the action to execute.
         *
         * @param action the action
         * @return this builder
         */
        public @NotNull Builder action(@NotNull WizardAction action) {
            this.action = Preconditions.checkNotNull(action, "action");
            return this;
        }

        /**
         * Set a callback for when the node is entered.
         *
         * @param onEnter the callback
         * @return this builder
         */
        public @NotNull Builder onEnter(@NotNull Consumer<WizardContext> onEnter) {
            this.onEnter = Preconditions.checkNotNull(onEnter, "onEnter");
            return this;
        }

        /**
         * Set a condition for skipping this node.
         *
         * @param condition the skip condition
         * @return this builder
         */
        public @NotNull Builder skipWhen(@NotNull Predicate<WizardContext> condition) {
            this.skipCondition = Preconditions.checkNotNull(condition, "condition");
            return this;
        }

        /**
         * Set the node to skip to when this node is skipped.
         *
         * @param nodeId the skip target node ID
         * @return this builder
         */
        public @NotNull Builder skipTo(@NotNull String nodeId) {
            this.skipToNodeId = Preconditions.checkNotNull(nodeId, "nodeId");
            return this;
        }

        /**
         * Set whether going back is allowed.
         *
         * @param allowBack true to allow going back
         * @return this builder
         */
        public @NotNull Builder allowBack(boolean allowBack) {
            this.allowBack = allowBack;
            return this;
        }

        /**
         * Set whether to show numbers for options.
         *
         * @param showNumbers true to show numbers
         * @return this builder
         */
        public @NotNull Builder showNumbers(boolean showNumbers) {
            this.showNumbers = showNumbers;
            return this;
        }

        /**
         * Build the wizard node.
         *
         * @return the wizard node
         */
        public @NotNull WizardNode build() {
            return new WizardNode(this);
        }
    }

    @Override
    public String toString() {
        return String.format("WizardNode[%s: %s, options=%d]", id, type, options.size());
    }
}
