package de.feelix.leviathan.command.wizard;

import de.feelix.leviathan.annotations.NotNull;
import de.feelix.leviathan.annotations.Nullable;
import de.feelix.leviathan.command.core.CommandContext;
import de.feelix.leviathan.util.Preconditions;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.function.Consumer;

/**
 * Context for wizard execution, holding all collected variables and navigation state.
 * <p>
 * The context provides:
 * <ul>
 *   <li>Access to the player and plugin</li>
 *   <li>All variables collected during the wizard</li>
 *   <li>Navigation history (path taken through the wizard)</li>
 *   <li>The original command context</li>
 *   <li>Completion and cancellation callbacks</li>
 * </ul>
 */
public final class WizardContext {

    private final JavaPlugin plugin;
    private final Player player;
    private final @Nullable CommandContext commandContext;
    private final Map<String, Object> variables;
    private final List<String> navigationPath;
    private final List<WizardChoice> choices;
    private final long startTimeMillis;
    private boolean completed = false;
    private boolean cancelled = false;
    private @Nullable Consumer<WizardContext> onComplete;
    private @Nullable Runnable onCancel;

    private WizardContext(JavaPlugin plugin, Player player, @Nullable CommandContext commandContext) {
        this.plugin = Preconditions.checkNotNull(plugin, "plugin");
        this.player = Preconditions.checkNotNull(player, "player");
        this.commandContext = commandContext;
        this.variables = new LinkedHashMap<>();
        this.navigationPath = new ArrayList<>();
        this.choices = new ArrayList<>();
        this.startTimeMillis = System.currentTimeMillis();
    }

    /**
     * Create a new wizard context.
     *
     * @param plugin the plugin instance
     * @param player the player
     * @return a new context
     */
    public static @NotNull WizardContext create(@NotNull JavaPlugin plugin, @NotNull Player player) {
        return new WizardContext(plugin, player, null);
    }

    /**
     * Create a new wizard context with command context.
     *
     * @param plugin         the plugin instance
     * @param player         the player
     * @param commandContext the original command context
     * @return a new context
     */
    public static @NotNull WizardContext create(@NotNull JavaPlugin plugin, @NotNull Player player,
                                                  @NotNull CommandContext commandContext) {
        return new WizardContext(plugin, player, commandContext);
    }

    // ==================== Core Accessors ====================

    /**
     * @return the plugin instance
     */
    public @NotNull JavaPlugin plugin() {
        return plugin;
    }

    /**
     * @return the player in the wizard
     */
    public @NotNull Player player() {
        return player;
    }

    /**
     * @return the original command context, or null if not started from a command
     */
    public @Nullable CommandContext commandContext() {
        return commandContext;
    }

    /**
     * @return the elapsed time since wizard start in milliseconds
     */
    public long elapsedTimeMillis() {
        return System.currentTimeMillis() - startTimeMillis;
    }

    // ==================== Variable Management ====================

    /**
     * Set a variable value.
     *
     * @param name  the variable name
     * @param value the value
     * @return this context for chaining
     */
    public @NotNull WizardContext set(@NotNull String name, @NotNull Object value) {
        Preconditions.checkNotNull(name, "name");
        Preconditions.checkNotNull(value, "value");
        variables.put(name, value);
        return this;
    }

    /**
     * Get a variable value.
     *
     * @param name the variable name
     * @param type the expected type
     * @param <T>  the value type
     * @return the value, or null if not present or wrong type
     */
    @SuppressWarnings("unchecked")
    public @Nullable <T> T get(@NotNull String name, @NotNull Class<T> type) {
        Preconditions.checkNotNull(name, "name");
        Preconditions.checkNotNull(type, "type");
        Object value = variables.get(name);
        if (value != null && type.isInstance(value)) {
            return (T) value;
        }
        return null;
    }

    /**
     * Get a variable value with a default.
     *
     * @param name         the variable name
     * @param type         the expected type
     * @param defaultValue the default value
     * @param <T>          the value type
     * @return the value or the default
     */
    public @NotNull <T> T getOrDefault(@NotNull String name, @NotNull Class<T> type, @NotNull T defaultValue) {
        T value = get(name, type);
        return value != null ? value : defaultValue;
    }

    /**
     * Get a string variable.
     *
     * @param name the variable name
     * @return the string value, or null
     */
    public @Nullable String getString(@NotNull String name) {
        return get(name, String.class);
    }

    /**
     * Get a string variable with a default.
     *
     * @param name         the variable name
     * @param defaultValue the default value
     * @return the string value or the default
     */
    public @NotNull String getString(@NotNull String name, @NotNull String defaultValue) {
        String value = getString(name);
        return value != null ? value : defaultValue;
    }

    /**
     * Get an integer variable.
     *
     * @param name         the variable name
     * @param defaultValue the default value
     * @return the integer value or the default
     */
    public int getInt(@NotNull String name, int defaultValue) {
        Integer value = get(name, Integer.class);
        return value != null ? value : defaultValue;
    }

    /**
     * Get a boolean variable.
     *
     * @param name         the variable name
     * @param defaultValue the default value
     * @return the boolean value or the default
     */
    public boolean getBoolean(@NotNull String name, boolean defaultValue) {
        Boolean value = get(name, Boolean.class);
        return value != null ? value : defaultValue;
    }

    /**
     * Check if a variable exists.
     *
     * @param name the variable name
     * @return true if the variable exists
     */
    public boolean has(@NotNull String name) {
        Preconditions.checkNotNull(name, "name");
        return variables.containsKey(name);
    }

    /**
     * Remove a variable.
     *
     * @param name the variable name
     * @return the removed value, or null
     */
    public @Nullable Object remove(@NotNull String name) {
        Preconditions.checkNotNull(name, "name");
        return variables.remove(name);
    }

    /**
     * @return an unmodifiable view of all variables
     */
    public @NotNull Map<String, Object> allVariables() {
        return Collections.unmodifiableMap(variables);
    }

    /**
     * @return the number of variables
     */
    public int variableCount() {
        return variables.size();
    }

    // ==================== Navigation History ====================

    /**
     * Record navigation to a node.
     *
     * @param nodeId the node ID
     */
    public void recordNavigation(@NotNull String nodeId) {
        Preconditions.checkNotNull(nodeId, "nodeId");
        navigationPath.add(nodeId);
    }

    /**
     * Record a choice made by the user.
     *
     * @param nodeId    the node where the choice was made
     * @param optionKey the option key chosen
     * @param value     the value associated with the choice
     */
    public void recordChoice(@NotNull String nodeId, @NotNull String optionKey, @Nullable Object value) {
        choices.add(new WizardChoice(nodeId, optionKey, value));
    }

    /**
     * @return the navigation path (list of visited node IDs)
     */
    public @NotNull List<String> navigationPath() {
        return Collections.unmodifiableList(navigationPath);
    }

    /**
     * @return the list of choices made
     */
    public @NotNull List<WizardChoice> choices() {
        return Collections.unmodifiableList(choices);
    }

    /**
     * @return the current node ID (last in navigation path), or null if not started
     */
    public @Nullable String currentNodeId() {
        return navigationPath.isEmpty() ? null : navigationPath.get(navigationPath.size() - 1);
    }

    /**
     * @return the previous node ID, or null if at start
     */
    public @Nullable String previousNodeId() {
        if (navigationPath.size() < 2) {
            return null;
        }
        return navigationPath.get(navigationPath.size() - 2);
    }

    /**
     * Check if the user can go back.
     *
     * @return true if there is a previous node to go back to
     */
    public boolean canGoBack() {
        return navigationPath.size() > 1;
    }

    /**
     * Go back to the previous node.
     *
     * @return the previous node ID, or null if at start
     */
    public @Nullable String goBack() {
        if (!canGoBack()) {
            return null;
        }
        navigationPath.remove(navigationPath.size() - 1);
        if (!choices.isEmpty()) {
            choices.remove(choices.size() - 1);
        }
        return currentNodeId();
    }

    // ==================== Completion State ====================

    /**
     * Mark the wizard as completed.
     */
    public void complete() {
        if (!completed && !cancelled) {
            completed = true;
            if (onComplete != null) {
                onComplete.accept(this);
            }
        }
    }

    /**
     * Mark the wizard as cancelled.
     */
    public void cancel() {
        if (!cancelled && !completed) {
            cancelled = true;
            if (onCancel != null) {
                onCancel.run();
            }
        }
    }

    /**
     * @return true if the wizard is completed
     */
    public boolean isCompleted() {
        return completed;
    }

    /**
     * @return true if the wizard is cancelled
     */
    public boolean isCancelled() {
        return cancelled;
    }

    /**
     * @return true if the wizard is still active
     */
    public boolean isActive() {
        return !completed && !cancelled;
    }

    /**
     * Set the completion callback.
     *
     * @param onComplete the callback
     * @return this context for chaining
     */
    public @NotNull WizardContext onComplete(@NotNull Consumer<WizardContext> onComplete) {
        this.onComplete = Preconditions.checkNotNull(onComplete, "onComplete");
        return this;
    }

    /**
     * Set the cancellation callback.
     *
     * @param onCancel the callback
     * @return this context for chaining
     */
    public @NotNull WizardContext onCancel(@NotNull Runnable onCancel) {
        this.onCancel = Preconditions.checkNotNull(onCancel, "onCancel");
        return this;
    }

    // ==================== Utility Methods ====================

    /**
     * Send a message to the player.
     *
     * @param message the message
     */
    public void sendMessage(@NotNull String message) {
        player.sendMessage(message);
    }

    /**
     * Send multiple messages to the player.
     *
     * @param messages the messages
     */
    public void sendMessages(@NotNull String... messages) {
        for (String message : messages) {
            player.sendMessage(message);
        }
    }

    /**
     * Generate a summary of the wizard state.
     *
     * @return a summary string
     */
    public @NotNull String summary() {
        StringBuilder sb = new StringBuilder();
        sb.append("§6Wizard Summary:\n");
        sb.append("§7Path: ").append(String.join(" → ", navigationPath)).append("\n");
        sb.append("§7Variables:\n");
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            sb.append("  §f").append(entry.getKey()).append("§7: §e").append(entry.getValue()).append("\n");
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return String.format("WizardContext[player=%s, variables=%d, path=%d, active=%s]",
                player.getName(), variables.size(), navigationPath.size(), isActive());
    }

    // ==================== Inner Classes ====================

    /**
     * Represents a choice made during the wizard.
     */
    public static final class WizardChoice {
        private final String nodeId;
        private final String optionKey;
        private final @Nullable Object value;

        WizardChoice(@NotNull String nodeId, @NotNull String optionKey, @Nullable Object value) {
            this.nodeId = nodeId;
            this.optionKey = optionKey;
            this.value = value;
        }

        public @NotNull String nodeId() {
            return nodeId;
        }

        public @NotNull String optionKey() {
            return optionKey;
        }

        public @Nullable Object value() {
            return value;
        }

        @Override
        public String toString() {
            return String.format("Choice[%s: %s = %s]", nodeId, optionKey, value);
        }
    }
}
