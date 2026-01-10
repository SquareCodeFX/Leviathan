package de.feelix.leviathan.command.batch;

import de.feelix.leviathan.annotations.NotNull;

/**
 * Functional interface for batch command actions.
 * <p>
 * A batch action is executed for each target in the batch. It receives:
 * <ul>
 *   <li>The current target being processed</li>
 *   <li>The batch context with access to sender, other args, and shared state</li>
 * </ul>
 * <p>
 * Example usage:
 * <pre>{@code
 * BatchAction<Player> healAction = (player, ctx) -> {
 *     int amount = ctx.argInt("amount", 20);
 *     player.setHealth(Math.min(player.getHealth() + amount, player.getMaxHealth()));
 * };
 * }</pre>
 *
 * @param <T> the type of target entity
 */
@FunctionalInterface
public interface BatchAction<T> {

    /**
     * Execute the batch action for a single target.
     *
     * @param target  the current target
     * @param context the batch context
     * @throws Exception if the action fails
     */
    void execute(@NotNull T target, @NotNull BatchContext<T> context) throws Exception;

    /**
     * Create a batch action that wraps another action with before/after hooks.
     *
     * @param before the action to run before (can be null)
     * @param after  the action to run after (can be null)
     * @return a wrapped batch action
     */
    default @NotNull BatchAction<T> wrap(
            @NotNull BatchAction<T> before,
            @NotNull BatchAction<T> after) {
        return (target, context) -> {
            if (before != null) {
                before.execute(target, context);
            }
            this.execute(target, context);
            if (after != null) {
                after.execute(target, context);
            }
        };
    }

    /**
     * Chain another action to run after this one.
     *
     * @param after the action to run after
     * @return a chained batch action
     */
    default @NotNull BatchAction<T> andThen(@NotNull BatchAction<T> after) {
        return (target, context) -> {
            this.execute(target, context);
            after.execute(target, context);
        };
    }

    /**
     * Create a conditional batch action that only executes if a condition is met.
     *
     * @param condition the condition to check
     * @return a conditional batch action
     */
    default @NotNull BatchAction<T> when(@NotNull BatchPredicate<T> condition) {
        return (target, context) -> {
            if (condition.test(target, context)) {
                this.execute(target, context);
            }
        };
    }

    /**
     * Create a batch action that catches and handles exceptions.
     *
     * @param handler the exception handler
     * @return a safe batch action
     */
    default @NotNull BatchAction<T> catching(@NotNull BatchExceptionHandler<T> handler) {
        return (target, context) -> {
            try {
                this.execute(target, context);
            } catch (Exception e) {
                handler.handle(target, context, e);
            }
        };
    }

    /**
     * Functional interface for conditions in batch operations.
     *
     * @param <T> the target type
     */
    @FunctionalInterface
    interface BatchPredicate<T> {
        /**
         * Test the condition.
         *
         * @param target  the target
         * @param context the batch context
         * @return true if the condition is met
         */
        boolean test(@NotNull T target, @NotNull BatchContext<T> context);
    }

    /**
     * Functional interface for exception handling in batch operations.
     *
     * @param <T> the target type
     */
    @FunctionalInterface
    interface BatchExceptionHandler<T> {
        /**
         * Handle an exception.
         *
         * @param target    the target that caused the exception
         * @param context   the batch context
         * @param exception the exception
         * @throws Exception if the exception should be rethrown
         */
        void handle(@NotNull T target, @NotNull BatchContext<T> context, @NotNull Exception exception) throws Exception;
    }
}
