package de.feelix.leviathan.command.wizard;

import de.feelix.leviathan.annotations.NotNull;
import de.feelix.leviathan.annotations.Nullable;
import de.feelix.leviathan.util.Preconditions;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Defines a complete wizard with all its nodes.
 * <p>
 * A wizard definition is immutable and can be reused for multiple wizard sessions.
 * It contains:
 * <ul>
 *   <li>All wizard nodes</li>
 *   <li>The start node ID</li>
 *   <li>Configuration options (timeout, etc.)</li>
 * </ul>
 */
public final class WizardDefinition {

    private final String name;
    private final String description;
    private final Map<String, WizardNode> nodes;
    private final String startNodeId;
    private final long timeoutMillis;
    private final boolean allowCancel;
    private final boolean allowBack;
    private final String cancelMessage;
    private final String completeMessage;
    private final String timeoutMessage;

    private WizardDefinition(Builder builder) {
        this.name = builder.name;
        this.description = builder.description;
        this.nodes = Collections.unmodifiableMap(new LinkedHashMap<>(builder.nodes));
        this.startNodeId = builder.startNodeId;
        this.timeoutMillis = builder.timeoutMillis;
        this.allowCancel = builder.allowCancel;
        this.allowBack = builder.allowBack;
        this.cancelMessage = builder.cancelMessage;
        this.completeMessage = builder.completeMessage;
        this.timeoutMessage = builder.timeoutMessage;
    }

    /**
     * Create a new builder.
     *
     * @param name the wizard name
     * @return a new builder
     */
    public static @NotNull Builder builder(@NotNull String name) {
        return new Builder(name);
    }

    // ==================== Accessors ====================

    /**
     * @return the wizard name
     */
    public @NotNull String name() {
        return name;
    }

    /**
     * @return the wizard description
     */
    public @NotNull String description() {
        return description;
    }

    /**
     * @return all nodes in the wizard
     */
    public @NotNull Map<String, WizardNode> nodes() {
        return nodes;
    }

    /**
     * Get a node by ID.
     *
     * @param id the node ID
     * @return the node, or null if not found
     */
    public @Nullable WizardNode getNode(@NotNull String id) {
        return nodes.get(id);
    }

    /**
     * @return the start node
     */
    public @NotNull WizardNode startNode() {
        WizardNode node = nodes.get(startNodeId);
        if (node == null) {
            throw new IllegalStateException("Start node '" + startNodeId + "' not found");
        }
        return node;
    }

    /**
     * @return the start node ID
     */
    public @NotNull String startNodeId() {
        return startNodeId;
    }

    /**
     * @return the timeout in milliseconds (0 = no timeout)
     */
    public long timeoutMillis() {
        return timeoutMillis;
    }

    /**
     * @return true if cancellation is allowed
     */
    public boolean allowCancel() {
        return allowCancel;
    }

    /**
     * @return true if going back is allowed by default
     */
    public boolean allowBack() {
        return allowBack;
    }

    /**
     * @return the message shown when wizard is cancelled
     */
    public @NotNull String cancelMessage() {
        return cancelMessage;
    }

    /**
     * @return the message shown when wizard completes
     */
    public @NotNull String completeMessage() {
        return completeMessage;
    }

    /**
     * @return the message shown when wizard times out
     */
    public @NotNull String timeoutMessage() {
        return timeoutMessage;
    }

    /**
     * @return the number of nodes
     */
    public int nodeCount() {
        return nodes.size();
    }

    /**
     * Validate the wizard definition.
     *
     * @throws IllegalStateException if validation fails
     */
    public void validate() {
        if (nodes.isEmpty()) {
            throw new IllegalStateException("Wizard must have at least one node");
        }

        if (!nodes.containsKey(startNodeId)) {
            throw new IllegalStateException("Start node '" + startNodeId + "' not found");
        }

        // Validate all node references
        for (WizardNode node : nodes.values()) {
            for (WizardOption option : node.options()) {
                if (option.isNavigation()) {
                    String nextId = option.nextNodeId();
                    if (nextId != null && !nodes.containsKey(nextId)) {
                        throw new IllegalStateException(
                                "Node '" + node.id() + "' option '" + option.key() +
                                        "' references unknown node '" + nextId + "'");
                    }
                }
            }

            if (node.nextNodeId() != null && !nodes.containsKey(node.nextNodeId())) {
                throw new IllegalStateException(
                        "Node '" + node.id() + "' references unknown next node '" + node.nextNodeId() + "'");
            }

            if (node.skipToNodeId() != null && !nodes.containsKey(node.skipToNodeId())) {
                throw new IllegalStateException(
                        "Node '" + node.id() + "' skip references unknown node '" + node.skipToNodeId() + "'");
            }
        }
    }

    // ==================== Builder ====================

    /**
     * Builder for WizardDefinition.
     */
    public static final class Builder {
        private final String name;
        private String description = "";
        private final Map<String, WizardNode> nodes = new LinkedHashMap<>();
        private String startNodeId;
        private long timeoutMillis = 5 * 60 * 1000; // 5 minutes default
        private boolean allowCancel = true;
        private boolean allowBack = true;
        private String cancelMessage = "§cWizard cancelled.";
        private String completeMessage = "§aWizard completed!";
        private String timeoutMessage = "§cWizard timed out.";

        private Builder(@NotNull String name) {
            this.name = Preconditions.checkNotNull(name, "name");
        }

        /**
         * Set the description.
         *
         * @param description the description
         * @return this builder
         */
        public @NotNull Builder description(@NotNull String description) {
            this.description = Preconditions.checkNotNull(description, "description");
            return this;
        }

        /**
         * Add a node to the wizard.
         *
         * @param node the node
         * @return this builder
         */
        public @NotNull Builder node(@NotNull WizardNode node) {
            Preconditions.checkNotNull(node, "node");
            nodes.put(node.id(), node);
            // First node becomes start node by default
            if (startNodeId == null) {
                startNodeId = node.id();
            }
            return this;
        }

        /**
         * Add a question node.
         *
         * @param id           the node ID
         * @param questionText the question text
         * @param configurator configurator for the node
         * @return this builder
         */
        public @NotNull Builder question(@NotNull String id, @NotNull String questionText,
                                          @NotNull java.util.function.Consumer<WizardNode.Builder> configurator) {
            WizardNode.Builder nodeBuilder = WizardNode.question(id, questionText);
            configurator.accept(nodeBuilder);
            return node(nodeBuilder.build());
        }

        /**
         * Add an input node.
         *
         * @param id           the node ID
         * @param variableName the variable name
         * @param prompt       the prompt text
         * @param parser       the input parser
         * @param nextNodeId   the next node ID
         * @return this builder
         */
        public @NotNull Builder input(@NotNull String id, @NotNull String variableName,
                                       @NotNull String prompt, @NotNull de.feelix.leviathan.command.argument.ArgumentParser<?> parser,
                                       @NotNull String nextNodeId) {
            return node(WizardNode.input(id, variableName, parser)
                    .questionText(prompt)
                    .inputPrompt("Enter " + variableName + ":")
                    .nextNode(nextNodeId)
                    .build());
        }

        /**
         * Add a terminal node.
         *
         * @param id     the node ID
         * @param action the final action
         * @return this builder
         */
        public @NotNull Builder terminal(@NotNull String id, @NotNull WizardAction action) {
            return node(WizardNode.terminal(id, action)
                    .questionText("Completing wizard...")
                    .build());
        }

        /**
         * Set the start node.
         *
         * @param nodeId the start node ID
         * @return this builder
         */
        public @NotNull Builder startNode(@NotNull String nodeId) {
            this.startNodeId = Preconditions.checkNotNull(nodeId, "nodeId");
            return this;
        }

        /**
         * Set the timeout.
         *
         * @param timeout the timeout
         * @param unit    the time unit
         * @return this builder
         */
        public @NotNull Builder timeout(long timeout, @NotNull java.util.concurrent.TimeUnit unit) {
            this.timeoutMillis = unit.toMillis(timeout);
            return this;
        }

        /**
         * Set the timeout in milliseconds.
         *
         * @param timeoutMillis the timeout in milliseconds
         * @return this builder
         */
        public @NotNull Builder timeoutMillis(long timeoutMillis) {
            this.timeoutMillis = timeoutMillis;
            return this;
        }

        /**
         * Disable timeout.
         *
         * @return this builder
         */
        public @NotNull Builder noTimeout() {
            this.timeoutMillis = 0;
            return this;
        }

        /**
         * Set whether cancellation is allowed.
         *
         * @param allowCancel true to allow cancellation
         * @return this builder
         */
        public @NotNull Builder allowCancel(boolean allowCancel) {
            this.allowCancel = allowCancel;
            return this;
        }

        /**
         * Set whether going back is allowed by default.
         *
         * @param allowBack true to allow going back
         * @return this builder
         */
        public @NotNull Builder allowBack(boolean allowBack) {
            this.allowBack = allowBack;
            return this;
        }

        /**
         * Set the cancel message.
         *
         * @param message the message
         * @return this builder
         */
        public @NotNull Builder cancelMessage(@NotNull String message) {
            this.cancelMessage = Preconditions.checkNotNull(message, "message");
            return this;
        }

        /**
         * Set the complete message.
         *
         * @param message the message
         * @return this builder
         */
        public @NotNull Builder completeMessage(@NotNull String message) {
            this.completeMessage = Preconditions.checkNotNull(message, "message");
            return this;
        }

        /**
         * Set the timeout message.
         *
         * @param message the message
         * @return this builder
         */
        public @NotNull Builder timeoutMessage(@NotNull String message) {
            this.timeoutMessage = Preconditions.checkNotNull(message, "message");
            return this;
        }

        /**
         * Build the wizard definition.
         *
         * @return the wizard definition
         */
        public @NotNull WizardDefinition build() {
            if (startNodeId == null && !nodes.isEmpty()) {
                startNodeId = nodes.keySet().iterator().next();
            }
            WizardDefinition definition = new WizardDefinition(this);
            definition.validate();
            return definition;
        }
    }

    @Override
    public String toString() {
        return String.format("WizardDefinition[%s: %d nodes, start=%s]", name, nodes.size(), startNodeId);
    }
}
