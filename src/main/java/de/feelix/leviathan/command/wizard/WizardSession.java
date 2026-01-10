package de.feelix.leviathan.command.wizard;

import de.feelix.leviathan.annotations.NotNull;
import de.feelix.leviathan.annotations.Nullable;
import de.feelix.leviathan.command.argument.ParseResult;
import de.feelix.leviathan.command.core.CommandContext;
import de.feelix.leviathan.util.Preconditions;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * An active wizard session for a player.
 * <p>
 * The session manages:
 * <ul>
 *   <li>Current node tracking</li>
 *   <li>User input handling</li>
 *   <li>Navigation between nodes</li>
 *   <li>Timeout management</li>
 *   <li>Completion and cancellation</li>
 * </ul>
 */
public final class WizardSession {

    private final UUID sessionId;
    private final JavaPlugin plugin;
    private final Player player;
    private final WizardDefinition definition;
    private final WizardContext context;
    private final long startTimeMillis;
    private WizardNode currentNode;
    private boolean active = true;
    private boolean awaitingConfirmation = false;
    private @Nullable WizardOption pendingOption;

    private WizardSession(JavaPlugin plugin, Player player, WizardDefinition definition,
                          @Nullable CommandContext commandContext) {
        this.sessionId = UUID.randomUUID();
        this.plugin = Preconditions.checkNotNull(plugin, "plugin");
        this.player = Preconditions.checkNotNull(player, "player");
        this.definition = Preconditions.checkNotNull(definition, "definition");
        this.context = commandContext != null
                ? WizardContext.create(plugin, player, commandContext)
                : WizardContext.create(plugin, player);
        this.startTimeMillis = System.currentTimeMillis();
        this.currentNode = definition.startNode();
    }

    /**
     * Start a new wizard session.
     *
     * @param plugin     the plugin instance
     * @param player     the player
     * @param definition the wizard definition
     * @return the new session
     */
    public static @NotNull WizardSession start(@NotNull JavaPlugin plugin, @NotNull Player player,
                                                 @NotNull WizardDefinition definition) {
        return start(plugin, player, definition, null);
    }

    /**
     * Start a new wizard session with command context.
     *
     * @param plugin         the plugin instance
     * @param player         the player
     * @param definition     the wizard definition
     * @param commandContext the original command context
     * @return the new session
     */
    public static @NotNull WizardSession start(@NotNull JavaPlugin plugin, @NotNull Player player,
                                                 @NotNull WizardDefinition definition,
                                                 @Nullable CommandContext commandContext) {
        WizardSession session = new WizardSession(plugin, player, definition, commandContext);
        session.enterCurrentNode();
        return session;
    }

    // ==================== Accessors ====================

    /**
     * @return the session ID
     */
    public @NotNull UUID sessionId() {
        return sessionId;
    }

    /**
     * @return the plugin instance
     */
    public @NotNull JavaPlugin plugin() {
        return plugin;
    }

    /**
     * @return the player
     */
    public @NotNull Player player() {
        return player;
    }

    /**
     * @return the wizard definition
     */
    public @NotNull WizardDefinition definition() {
        return definition;
    }

    /**
     * @return the wizard context
     */
    public @NotNull WizardContext context() {
        return context;
    }

    /**
     * @return the current node
     */
    public @NotNull WizardNode currentNode() {
        return currentNode;
    }

    /**
     * @return true if the session is active
     */
    public boolean isActive() {
        return active;
    }

    /**
     * @return the elapsed time in milliseconds
     */
    public long elapsedTimeMillis() {
        return System.currentTimeMillis() - startTimeMillis;
    }

    /**
     * @return true if the session has timed out
     */
    public boolean isTimedOut() {
        return definition.timeoutMillis() > 0 && elapsedTimeMillis() >= definition.timeoutMillis();
    }

    // ==================== Input Handling ====================

    /**
     * Handle user input.
     *
     * @param input the user input
     * @return true if the input was handled
     */
    public boolean handleInput(@NotNull String input) {
        Preconditions.checkNotNull(input, "input");

        if (!active) {
            return false;
        }

        // Check timeout
        if (isTimedOut()) {
            timeout();
            return true;
        }

        String trimmed = input.trim();

        // Handle confirmation
        if (awaitingConfirmation) {
            return handleConfirmation(trimmed);
        }

        // Handle special commands
        if (definition.allowCancel() && "cancel".equalsIgnoreCase(trimmed)) {
            cancel();
            return true;
        }

        if (definition.allowBack() && currentNode.allowBack() && "back".equalsIgnoreCase(trimmed)) {
            goBack();
            return true;
        }

        // Handle based on node type
        switch (currentNode.type()) {
            case QUESTION:
                return handleQuestionInput(trimmed);
            case INPUT:
                return handleTextInput(trimmed);
            case ACTION:
            case TERMINAL:
                // Action nodes don't accept user input
                return false;
            default:
                return false;
        }
    }

    /**
     * Handle input for a question node.
     */
    private boolean handleQuestionInput(@NotNull String input) {
        WizardOption option = null;

        // Try to find by key
        option = currentNode.findOption(input);

        // Try to find by number
        if (option == null && currentNode.showNumbers()) {
            try {
                int index = Integer.parseInt(input);
                option = currentNode.findOptionByIndex(index);
            } catch (NumberFormatException ignored) {
            }
        }

        if (option == null) {
            player.sendMessage("§cInvalid option. Please try again.");
            return true;
        }

        // Check if option is available
        if (!option.isAvailable(context)) {
            player.sendMessage("§cThis option is not available.");
            return true;
        }

        // Check if confirmation is required
        if (option.requiresConfirmation()) {
            awaitingConfirmation = true;
            pendingOption = option;
            player.sendMessage("§eAre you sure? Type 'yes' to confirm or 'no' to cancel.");
            return true;
        }

        // Execute the option
        executeOption(option);
        return true;
    }

    /**
     * Handle input for an input node.
     */
    private boolean handleTextInput(@NotNull String input) {
        if (currentNode.inputParser() == null) {
            player.sendMessage("§cThis node cannot accept input.");
            return true;
        }

        ParseResult<?> result = currentNode.parseInput(input, player);

        if (!result.isSuccess()) {
            player.sendMessage("§cInvalid input: " + result.error().orElse("Unknown error"));
            return true;
        }

        // Store the value
        String varName = currentNode.variableName();
        if (varName != null && result.value().isPresent()) {
            context.set(varName, result.value().get());
            context.recordChoice(currentNode.id(), input, result.value().get());
        }

        // Navigate to next node
        String nextId = currentNode.nextNodeId();
        if (nextId != null) {
            navigateTo(nextId);
        } else {
            // No next node - complete the wizard
            complete();
        }

        return true;
    }

    /**
     * Handle confirmation input.
     */
    private boolean handleConfirmation(@NotNull String input) {
        awaitingConfirmation = false;

        if ("yes".equalsIgnoreCase(input) || "y".equalsIgnoreCase(input) || "confirm".equalsIgnoreCase(input)) {
            if (pendingOption != null) {
                executeOption(pendingOption);
                pendingOption = null;
            }
        } else {
            pendingOption = null;
            player.sendMessage("§7Cancelled. Please choose again.");
            currentNode.display(context);
        }

        return true;
    }

    /**
     * Execute a selected option.
     */
    private void executeOption(@NotNull WizardOption option) {
        // Record the choice
        context.recordChoice(currentNode.id(), option.key(), null);

        if (option.isNavigation()) {
            navigateTo(option.nextNodeId());
        } else if (option.isTerminal()) {
            try {
                WizardAction action = option.action();
                if (action != null) {
                    action.execute(context);
                }
                complete();
            } catch (Exception e) {
                player.sendMessage("§cAn error occurred: " + e.getMessage());
                plugin.getLogger().warning("Wizard action failed: " + e.getMessage());
            }
        }
    }

    // ==================== Navigation ====================

    /**
     * Navigate to a specific node.
     *
     * @param nodeId the node ID
     */
    public void navigateTo(@NotNull String nodeId) {
        Preconditions.checkNotNull(nodeId, "nodeId");

        WizardNode node = definition.getNode(nodeId);
        if (node == null) {
            player.sendMessage("§cInternal error: Node not found.");
            plugin.getLogger().warning("Wizard node not found: " + nodeId);
            return;
        }

        currentNode = node;
        enterCurrentNode();
    }

    /**
     * Enter the current node.
     */
    private void enterCurrentNode() {
        // Record navigation
        context.recordNavigation(currentNode.id());

        // Check if node should be skipped
        if (currentNode.shouldSkip(context)) {
            String skipTo = currentNode.skipToNodeId();
            if (skipTo != null) {
                navigateTo(skipTo);
            } else if (currentNode.nextNodeId() != null) {
                navigateTo(currentNode.nextNodeId());
            }
            return;
        }

        // Call onEnter callback
        currentNode.enter(context);

        // Handle action nodes automatically
        if (currentNode.isAction()) {
            WizardAction action = currentNode.action();
            if (action != null) {
                try {
                    action.execute(context);
                } catch (Exception e) {
                    player.sendMessage("§cAn error occurred: " + e.getMessage());
                    return;
                }
            }

            String nextId = currentNode.nextNodeId();
            if (nextId != null) {
                navigateTo(nextId);
            } else {
                complete();
            }
            return;
        }

        // Handle terminal nodes
        if (currentNode.isTerminal()) {
            WizardAction action = currentNode.action();
            if (action != null) {
                try {
                    action.execute(context);
                } catch (Exception e) {
                    player.sendMessage("§cAn error occurred: " + e.getMessage());
                }
            }
            complete();
            return;
        }

        // Display the node
        currentNode.display(context);
    }

    /**
     * Go back to the previous node.
     */
    public void goBack() {
        String previousId = context.goBack();
        if (previousId != null) {
            WizardNode node = definition.getNode(previousId);
            if (node != null) {
                currentNode = node;
                currentNode.display(context);
            }
        } else {
            player.sendMessage("§7Cannot go back - you're at the beginning.");
        }
    }

    // ==================== Completion ====================

    /**
     * Complete the wizard.
     */
    public void complete() {
        if (!active) return;
        active = false;
        player.sendMessage(definition.completeMessage());
        context.complete();
    }

    /**
     * Cancel the wizard.
     */
    public void cancel() {
        if (!active) return;
        active = false;
        player.sendMessage(definition.cancelMessage());
        context.cancel();
    }

    /**
     * Handle timeout.
     */
    public void timeout() {
        if (!active) return;
        active = false;
        player.sendMessage(definition.timeoutMessage());
        context.cancel();
    }

    /**
     * Set a completion callback.
     *
     * @param callback the callback
     * @return this session for chaining
     */
    public @NotNull WizardSession onComplete(@NotNull Consumer<WizardContext> callback) {
        context.onComplete(callback);
        return this;
    }

    /**
     * Set a cancellation callback.
     *
     * @param callback the callback
     * @return this session for chaining
     */
    public @NotNull WizardSession onCancel(@NotNull Runnable callback) {
        context.onCancel(callback);
        return this;
    }

    @Override
    public String toString() {
        return String.format("WizardSession[%s: player=%s, node=%s, active=%s]",
                definition.name(), player.getName(), currentNode.id(), active);
    }
}
