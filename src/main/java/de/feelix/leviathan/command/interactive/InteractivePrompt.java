package de.feelix.leviathan.command.interactive;

import de.feelix.leviathan.annotations.NotNull;
import de.feelix.leviathan.annotations.Nullable;
import de.feelix.leviathan.command.argument.Arg;
import de.feelix.leviathan.command.core.CommandContext;
import de.feelix.leviathan.util.Preconditions;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Interactive prompting system for gathering missing command arguments from users.
 * <p>
 * Instead of failing immediately when required arguments are missing, this system
 * can prompt users for input in a conversational manner.
 * <p>
 * Example usage:
 * <pre>{@code
 * SlashCommand.create("ban")
 *     .argPlayer("target").interactive(true)
 *     .argString("reason").interactive(true).optional(true)
 *     .executes((sender, ctx) -> {
 *         // Arguments gathered interactively if not provided
 *     })
 *     .build();
 * }</pre>
 */
public final class InteractivePrompt {

    // Active sessions: player UUID -> session
    private static final Map<UUID, PromptSession> activeSessions = new ConcurrentHashMap<>();

    // Session timeout in seconds
    private static final long DEFAULT_TIMEOUT_SECONDS = 60;

    private InteractivePrompt() {
        throw new AssertionError("Utility class");
    }

    /**
     * Start a new interactive prompt session for a player.
     *
     * @param plugin      the plugin instance
     * @param player      the player to prompt
     * @param missingArgs the arguments that need to be collected
     * @param onComplete  callback when all arguments are collected
     * @param onCancel    callback when session is cancelled
     * @return the created session
     */
    public static @NotNull PromptSession startSession(
            @NotNull JavaPlugin plugin,
            @NotNull Player player,
            @NotNull List<Arg<?>> missingArgs,
            @NotNull Consumer<Map<String, Object>> onComplete,
            @NotNull Runnable onCancel) {

        Preconditions.checkNotNull(plugin, "plugin");
        Preconditions.checkNotNull(player, "player");
        Preconditions.checkNotNull(missingArgs, "missingArgs");
        Preconditions.checkNotNull(onComplete, "onComplete");
        Preconditions.checkNotNull(onCancel, "onCancel");

        // Cancel any existing session
        cancelSession(player);

        PromptSession session = new PromptSession(plugin, player, missingArgs, onComplete, onCancel);
        activeSessions.put(player.getUniqueId(), session);
        session.start();

        return session;
    }

    /**
     * Check if a player has an active prompt session.
     *
     * @param player the player to check
     * @return true if player has an active session
     */
    public static boolean hasActiveSession(@NotNull Player player) {
        Preconditions.checkNotNull(player, "player");
        PromptSession session = activeSessions.get(player.getUniqueId());
        return session != null && session.isActive();
    }

    /**
     * Get the active session for a player.
     *
     * @param player the player
     * @return the active session, or null if none
     */
    public static @Nullable PromptSession getSession(@NotNull Player player) {
        Preconditions.checkNotNull(player, "player");
        return activeSessions.get(player.getUniqueId());
    }

    /**
     * Handle chat input for an active session.
     *
     * @param player  the player who sent the message
     * @param message the chat message
     * @return true if the message was handled by an active session
     */
    public static boolean handleInput(@NotNull Player player, @NotNull String message) {
        Preconditions.checkNotNull(player, "player");
        Preconditions.checkNotNull(message, "message");

        PromptSession session = activeSessions.get(player.getUniqueId());
        if (session == null || !session.isActive()) {
            return false;
        }

        return session.handleInput(message);
    }

    /**
     * Cancel a player's active session.
     *
     * @param player the player
     * @return true if a session was cancelled
     */
    public static boolean cancelSession(@NotNull Player player) {
        Preconditions.checkNotNull(player, "player");
        PromptSession session = activeSessions.remove(player.getUniqueId());
        if (session != null && session.isActive()) {
            session.cancel();
            return true;
        }
        return false;
    }

    /**
     * Clean up all sessions for a disconnected player.
     *
     * @param player the player who disconnected
     */
    public static void cleanupPlayer(@NotNull Player player) {
        Preconditions.checkNotNull(player, "player");
        activeSessions.remove(player.getUniqueId());
    }

    /**
     * Represents an interactive prompt session.
     */
    public static final class PromptSession {
        private final JavaPlugin plugin;
        private final Player player;
        private final List<Arg<?>> missingArgs;
        private final Consumer<Map<String, Object>> onComplete;
        private final Runnable onCancel;
        private final Map<String, Object> collectedValues;
        private final long startTime;
        private final long timeoutMillis;

        private int currentArgIndex = 0;
        private boolean active = false;
        private boolean cancelled = false;

        private PromptSession(JavaPlugin plugin, Player player, List<Arg<?>> missingArgs,
                              Consumer<Map<String, Object>> onComplete, Runnable onCancel) {
            this.plugin = plugin;
            this.player = player;
            this.missingArgs = new ArrayList<>(missingArgs);
            this.onComplete = onComplete;
            this.onCancel = onCancel;
            this.collectedValues = new LinkedHashMap<>();
            this.startTime = System.currentTimeMillis();
            this.timeoutMillis = DEFAULT_TIMEOUT_SECONDS * 1000;
        }

        /**
         * Start the interactive session.
         */
        void start() {
            if (missingArgs.isEmpty()) {
                complete();
                return;
            }

            active = true;
            promptCurrentArg();

            // Schedule timeout check
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (isActive() && isTimedOut()) {
                    timeout();
                }
            }, timeoutMillis / 50); // Convert to ticks
        }

        /**
         * Prompt for the current argument.
         */
        private void promptCurrentArg() {
            if (currentArgIndex >= missingArgs.size()) {
                complete();
                return;
            }

            Arg<?> arg = missingArgs.get(currentArgIndex);
            String prompt = buildPromptMessage(arg);
            player.sendMessage(prompt);
        }

        /**
         * Build the prompt message for an argument.
         */
        private @NotNull String buildPromptMessage(@NotNull Arg<?> arg) {
            StringBuilder sb = new StringBuilder();
            sb.append("§e[Interactive] §fPlease enter a value for §b").append(arg.name());

            if (arg.context().description() != null) {
                sb.append(" §7(").append(arg.context().description()).append(")");
            }

            sb.append("§f:");

            // Show completions if available
            List<String> completions = arg.context().completionsPredefined();
            if (!completions.isEmpty() && completions.size() <= 10) {
                sb.append("\n§7Options: §f").append(String.join("§7, §f", completions));
            }

            sb.append("\n§7Type '§ccancel§7' to abort.");

            return sb.toString();
        }

        /**
         * Handle user input for this session.
         *
         * @param input the user input
         * @return true if input was handled
         */
        boolean handleInput(@NotNull String input) {
            if (!active || cancelled) {
                return false;
            }

            // Check for cancel command
            if ("cancel".equalsIgnoreCase(input.trim())) {
                cancel();
                return true;
            }

            // Check for skip command (for optional args)
            if ("skip".equalsIgnoreCase(input.trim())) {
                Arg<?> currentArg = missingArgs.get(currentArgIndex);
                if (currentArg.context().optional()) {
                    currentArgIndex++;
                    promptCurrentArg();
                    return true;
                } else {
                    player.sendMessage("§cThis argument is required and cannot be skipped.");
                    return true;
                }
            }

            // Try to parse the input
            Arg<?> currentArg = missingArgs.get(currentArgIndex);
            try {
                Object parsed = parseInput(currentArg, input.trim());
                if (parsed != null) {
                    collectedValues.put(currentArg.name(), parsed);
                    currentArgIndex++;

                    if (currentArgIndex >= missingArgs.size()) {
                        complete();
                    } else {
                        player.sendMessage("§a✓ §7Value accepted.");
                        promptCurrentArg();
                    }
                } else {
                    player.sendMessage("§cInvalid input. Please try again.");
                }
            } catch (Exception e) {
                player.sendMessage("§cError: " + e.getMessage());
            }

            return true;
        }

        /**
         * Parse user input for an argument.
         */
        @SuppressWarnings("unchecked")
        private @Nullable Object parseInput(@NotNull Arg<?> arg, @NotNull String input) {
            try {
                var result = arg.parser().parse(player, input);
                if (result.isSuccess()) {
                    return result.value();
                }
                return null;
            } catch (Exception e) {
                return null;
            }
        }

        /**
         * Complete the session successfully.
         */
        private void complete() {
            active = false;
            activeSessions.remove(player.getUniqueId());
            player.sendMessage("§a[Interactive] §fAll values collected. Executing command...");
            onComplete.accept(collectedValues);
        }

        /**
         * Cancel the session.
         */
        void cancel() {
            if (cancelled) return;
            cancelled = true;
            active = false;
            activeSessions.remove(player.getUniqueId());
            player.sendMessage("§c[Interactive] §fSession cancelled.");
            onCancel.run();
        }

        /**
         * Handle session timeout.
         */
        private void timeout() {
            if (cancelled || !active) return;
            cancelled = true;
            active = false;
            activeSessions.remove(player.getUniqueId());
            player.sendMessage("§c[Interactive] §fSession timed out.");
            onCancel.run();
        }

        /**
         * Check if the session is active.
         */
        public boolean isActive() {
            return active && !cancelled;
        }

        /**
         * Check if the session has timed out.
         */
        public boolean isTimedOut() {
            return System.currentTimeMillis() - startTime > timeoutMillis;
        }

        /**
         * Get the player for this session.
         */
        public @NotNull Player player() {
            return player;
        }

        /**
         * Get the current argument being prompted.
         */
        public @Nullable Arg<?> currentArg() {
            if (currentArgIndex < missingArgs.size()) {
                return missingArgs.get(currentArgIndex);
            }
            return null;
        }

        /**
         * Get all collected values so far.
         */
        public @NotNull Map<String, Object> collectedValues() {
            return Collections.unmodifiableMap(collectedValues);
        }

        /**
         * Get the progress (0.0 to 1.0).
         */
        public double progress() {
            if (missingArgs.isEmpty()) return 1.0;
            return (double) currentArgIndex / missingArgs.size();
        }
    }

    /**
     * Configuration for interactive prompting behavior.
     */
    public static final class Config {
        private final boolean enabled;
        private final long timeoutSeconds;
        private final String promptPrefix;
        private final String cancelWord;
        private final String skipWord;

        private Config(Builder builder) {
            this.enabled = builder.enabled;
            this.timeoutSeconds = builder.timeoutSeconds;
            this.promptPrefix = builder.promptPrefix;
            this.cancelWord = builder.cancelWord;
            this.skipWord = builder.skipWord;
        }

        public static @NotNull Builder builder() {
            return new Builder();
        }

        public static @NotNull Config defaults() {
            return builder().build();
        }

        public boolean enabled() { return enabled; }
        public long timeoutSeconds() { return timeoutSeconds; }
        public @NotNull String promptPrefix() { return promptPrefix; }
        public @NotNull String cancelWord() { return cancelWord; }
        public @NotNull String skipWord() { return skipWord; }

        public static final class Builder {
            private boolean enabled = true;
            private long timeoutSeconds = DEFAULT_TIMEOUT_SECONDS;
            private String promptPrefix = "§e[Interactive] §f";
            private String cancelWord = "cancel";
            private String skipWord = "skip";

            public @NotNull Builder enabled(boolean enabled) {
                this.enabled = enabled;
                return this;
            }

            public @NotNull Builder timeout(long seconds) {
                this.timeoutSeconds = seconds;
                return this;
            }

            public @NotNull Builder promptPrefix(@NotNull String prefix) {
                this.promptPrefix = prefix;
                return this;
            }

            public @NotNull Builder cancelWord(@NotNull String word) {
                this.cancelWord = word;
                return this;
            }

            public @NotNull Builder skipWord(@NotNull String word) {
                this.skipWord = word;
                return this;
            }

            public @NotNull Config build() {
                return new Config(this);
            }
        }
    }
}
