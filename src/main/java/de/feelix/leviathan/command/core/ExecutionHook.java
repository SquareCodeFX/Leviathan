package de.feelix.leviathan.command.core;

import de.feelix.leviathan.annotations.NotNull;
import de.feelix.leviathan.annotations.Nullable;
import org.bukkit.command.CommandSender;

/**
 * Hook interface for intercepting command execution at various stages.
 * <p>
 * Hooks can be used for:
 * <ul>
 *   <li>Logging and auditing</li>
 *   <li>Additional validation</li>
 *   <li>Metrics collection</li>
 *   <li>Resource management</li>
 *   <li>Transaction-like behavior</li>
 * </ul>
 * <p>
 * Example usage:
 * <pre>{@code
 * SlashCommand.create("example")
 *     .beforeExecution((sender, ctx) -> {
 *         logger.info("Command started by " + sender.getName());
 *         return true; // Allow execution
 *     })
 *     .afterExecution((sender, ctx, result) -> {
 *         logger.info("Command completed with result: " + result);
 *     })
 *     .executes((sender, ctx) -> {
 *         // Command logic
 *     })
 *     .build();
 * }</pre>
 */
public final class ExecutionHook {

    private ExecutionHook() {
        // Utility class
    }

    /**
     * Hook executed before command execution.
     * <p>
     * Returning {@code false} will abort the command execution and send
     * an optional error message to the sender.
     */
    @FunctionalInterface
    public interface Before {
        /**
         * Called before the command executes.
         *
         * @param sender  the command sender
         * @param context the parsed command context
         * @return result indicating whether to proceed with execution
         */
        @NotNull BeforeResult execute(@NotNull CommandSender sender, @NotNull CommandContext context);

        /**
         * Create a simple before hook that always allows execution.
         *
         * @param action action to perform before execution
         * @return a before hook
         */
        static Before of(@NotNull java.util.function.BiConsumer<CommandSender, CommandContext> action) {
            return (sender, context) -> {
                action.accept(sender, context);
                return BeforeResult.proceed();
            };
        }

        /**
         * Create a validating before hook that can abort execution.
         *
         * @param validator predicate that returns true to allow execution
         * @param errorMessage message to show if validation fails (can be null)
         * @return a before hook
         */
        static Before validating(@NotNull java.util.function.BiPredicate<CommandSender, CommandContext> validator,
                                  @Nullable String errorMessage) {
            return (sender, context) -> {
                if (validator.test(sender, context)) {
                    return BeforeResult.proceed();
                }
                return BeforeResult.abort(errorMessage);
            };
        }
    }

    /**
     * Result of a before execution hook.
     */
    public static final class BeforeResult {
        private final boolean proceed;
        private final @Nullable String errorMessage;

        private BeforeResult(boolean proceed, @Nullable String errorMessage) {
            this.proceed = proceed;
            this.errorMessage = errorMessage;
        }

        /**
         * Create a result that allows execution to proceed.
         */
        public static @NotNull BeforeResult proceed() {
            return new BeforeResult(true, null);
        }

        /**
         * Create a result that aborts execution with no message.
         */
        public static @NotNull BeforeResult abort() {
            return new BeforeResult(false, null);
        }

        /**
         * Create a result that aborts execution with an error message.
         *
         * @param message the error message to display (can be null)
         */
        public static @NotNull BeforeResult abort(@Nullable String message) {
            return new BeforeResult(false, message);
        }

        /**
         * @return true if execution should proceed
         */
        public boolean shouldProceed() {
            return proceed;
        }

        /**
         * @return error message to display if aborting, may be null
         */
        public @Nullable String errorMessage() {
            return errorMessage;
        }
    }

    /**
     * Hook executed after command execution.
     * <p>
     * This hook is always executed, regardless of whether the command
     * succeeded or failed. Use the {@link AfterContext} to determine
     * the execution outcome.
     */
    @FunctionalInterface
    public interface After {
        /**
         * Called after the command executes.
         *
         * @param sender  the command sender
         * @param context the parsed command context
         * @param result  information about the execution result
         */
        void execute(@NotNull CommandSender sender, @NotNull CommandContext context, @NotNull AfterContext result);

        /**
         * Create a simple after hook.
         *
         * @param action action to perform after execution
         * @return an after hook
         */
        static After of(@NotNull java.util.function.BiConsumer<CommandSender, CommandContext> action) {
            return (sender, context, result) -> action.accept(sender, context);
        }

        /**
         * Create an after hook that only runs on success.
         *
         * @param action action to perform on successful execution
         * @return an after hook
         */
        static After onSuccess(@NotNull java.util.function.BiConsumer<CommandSender, CommandContext> action) {
            return (sender, context, result) -> {
                if (result.isSuccess()) {
                    action.accept(sender, context);
                }
            };
        }

        /**
         * Create an after hook that only runs on failure.
         *
         * @param action action to perform on failed execution
         * @return an after hook
         */
        static After onFailure(
            @NotNull TriConsumer<CommandSender, CommandContext, Throwable> action) {
            return (sender, context, result) -> {
                if (!result.isSuccess() && result.exception() != null) {
                    action.accept(sender, context, result.exception());
                }
            };
        }
    }

    /**
     * Context information provided to after-execution hooks.
     */
    public static final class AfterContext {
        private final boolean success;
        private final @Nullable Throwable exception;
        private final long executionTimeMillis;

        private AfterContext(boolean success, @Nullable Throwable exception, long executionTimeMillis) {
            this.success = success;
            this.exception = exception;
            this.executionTimeMillis = executionTimeMillis;
        }

        /**
         * Create a successful execution result.
         *
         * @param executionTimeMillis time taken to execute in milliseconds
         */
        public static @NotNull AfterContext success(long executionTimeMillis) {
            return new AfterContext(true, null, executionTimeMillis);
        }

        /**
         * Create a failed execution result.
         *
         * @param exception           the exception that caused the failure
         * @param executionTimeMillis time taken before failure in milliseconds
         */
        public static @NotNull AfterContext failure(@NotNull Throwable exception, long executionTimeMillis) {
            return new AfterContext(false, exception, executionTimeMillis);
        }

        /**
         * @return true if the command executed successfully
         */
        public boolean isSuccess() {
            return success;
        }

        /**
         * @return true if the command failed
         */
        public boolean isFailure() {
            return !success;
        }

        /**
         * @return the exception that caused failure, or null if successful
         */
        public @Nullable Throwable exception() {
            return exception;
        }

        /**
         * @return execution time in milliseconds
         */
        public long executionTimeMillis() {
            return executionTimeMillis;
        }
    }

    /**
     * Tri-consumer functional interface for callbacks with three arguments.
     */
    @FunctionalInterface
    public interface TriConsumer<A, B, C> {
        void accept(A a, B b, C c);
    }
}
