package de.feelix.leviathan.command.wizard;

import de.feelix.leviathan.annotations.NotNull;

/**
 * Functional interface for wizard terminal actions.
 * <p>
 * A wizard action is executed when the user reaches a terminal option in the wizard.
 * It receives the complete wizard context with all collected variables.
 * <p>
 * Example:
 * <pre>{@code
 * WizardAction createKit = (context) -> {
 *     String kitName = context.getString("kit_name");
 *     String kitType = context.getString("kit_type");
 *     createPlayerKit(context.player(), kitName, kitType);
 *     context.player().sendMessage("Kit created: " + kitName);
 * };
 * }</pre>
 */
@FunctionalInterface
public interface WizardAction {

    /**
     * Execute the wizard action.
     *
     * @param context the wizard context with all collected data
     * @throws Exception if the action fails
     */
    void execute(@NotNull WizardContext context) throws Exception;

    /**
     * Chain another action to run after this one.
     *
     * @param after the action to run after
     * @return a chained action
     */
    default @NotNull WizardAction andThen(@NotNull WizardAction after) {
        return (context) -> {
            this.execute(context);
            after.execute(context);
        };
    }

    /**
     * Create an action that does nothing.
     *
     * @return a no-op action
     */
    static @NotNull WizardAction noOp() {
        return (context) -> {};
    }

    /**
     * Create an action that sends a message to the player.
     *
     * @param message the message to send
     * @return a message action
     */
    static @NotNull WizardAction message(@NotNull String message) {
        return (context) -> context.player().sendMessage(message);
    }

    /**
     * Create an action that closes the wizard with a message.
     *
     * @param message the completion message
     * @return a completion action
     */
    static @NotNull WizardAction complete(@NotNull String message) {
        return (context) -> {
            context.player().sendMessage(message);
            context.complete();
        };
    }
}
