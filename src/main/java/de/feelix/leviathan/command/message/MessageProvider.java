package de.feelix.leviathan.command.message;

import de.feelix.leviathan.annotations.NotNull;

/**
 * Provides customizable messages for the SlashCommand API.
 * <p>
 * All methods return formatted strings that will be sent to command senders
 * when various events occur during command processing.
 * <p>
 * Implement this interface to provide custom messages in different languages
 * or with custom formatting.
 */
public interface MessageProvider {

    // Permission and Access Messages

    /**
     * Message when sender lacks permission to use the command.
     *
     * @return the permission denied message
     */
    @NotNull
    String noPermission();

    /**
     * Message when a non-player tries to use a player-only command.
     *
     * @return the player-only message
     */
    @NotNull
    String playerOnly();

    /**
     * Alias for {@link #playerOnly()}.
     * Message when a non-player tries to use a player-only command.
     *
     * @return the player-only message
     */
    @NotNull
    default String playersOnly() {
        return playerOnly();
    }

    /**
     * Message when an unknown subcommand is provided.
     *
     * @param subcommand the unknown subcommand that was entered
     * @return the unknown subcommand message
     */
    @NotNull
    default String unknownSubcommand(@NotNull String subcommand) {
        return "§cUnknown subcommand: " + subcommand;
    }

    /**
     * Generic message when a guard check fails but no specific error is available.
     *
     * @return the generic guard failure message
     */
    @NotNull
    String guardFailed();

    /**
     * Message when sender lacks permission to use a specific argument.
     *
     * @param argumentName the name of the argument
     * @return the argument permission denied message
     */
    @NotNull
    String argumentPermissionDenied(@NotNull String argumentName);

    /**
     * Message when command requires a specific sender type.
     *
     * @param typeName the simple name of the required type
     * @return the type requirement message
     */
    @NotNull
    String requiresType(@NotNull String typeName);

    // Cooldown Messages

    /**
     * Message when command is on server-wide cooldown.
     *
     * @param formattedTime the formatted remaining cooldown time
     * @return the server cooldown message
     */
    @NotNull
    String serverCooldown(@NotNull String formattedTime);

    /**
     * Message when user must wait before using command again.
     *
     * @param formattedTime the formatted remaining cooldown time
     * @return the user cooldown message
     */
    @NotNull
    String userCooldown(@NotNull String formattedTime);

    // Usage and Parsing Messages

    /**
     * Message when insufficient arguments are provided.
     *
     * @param commandPath the full command path
     * @param usage       the usage string
     * @return the usage message
     */
    @NotNull
    String insufficientArguments(@NotNull String commandPath, @NotNull String usage);

    /**
     * Message when too many arguments are provided.
     *
     * @param commandPath the full command path
     * @param usage       the usage string
     * @return the too many arguments message
     */
    @NotNull
    String tooManyArguments(@NotNull String commandPath, @NotNull String usage);

    /**
     * Message when argument value is invalid.
     *
     * @param argumentName the name of the argument
     * @param expectedType the expected type name
     * @param errorDetail  the specific parsing error detail
     * @return the invalid argument message
     */
    @NotNull
    String invalidArgumentValue(@NotNull String argumentName, @NotNull String expectedType,
                                @NotNull String errorDetail);

    /**
     * "Did you mean" suggestion message.
     *
     * @param suggestions comma-separated list of suggestions
     * @return the suggestion message
     */
    @NotNull
    String didYouMean(@NotNull String suggestions);

    // Validation Messages

    /**
     * Message when argument fails validation.
     *
     * @param argumentName    the name of the argument
     * @param validationError the validation error detail
     * @return the validation error message
     */
    @NotNull
    String validationFailed(@NotNull String argumentName, @NotNull String validationError);

    /**
     * Message when cross-argument validation fails.
     *
     * @param errorDetail the validation error detail
     * @return the cross-validation error message
     */
    @NotNull
    String crossValidationFailed(@NotNull String errorDetail);

    /**
     * Validation message when numeric value is below minimum.
     *
     * @param min    the minimum allowed value
     * @param actual the actual value provided
     * @return the validation message
     */
    @NotNull
    String numericTooSmall(@NotNull String min, @NotNull String actual);

    /**
     * Validation message when numeric value exceeds maximum.
     *
     * @param max    the maximum allowed value
     * @param actual the actual value provided
     * @return the validation message
     */
    @NotNull
    String numericTooLarge(@NotNull String max, @NotNull String actual);

    /**
     * Validation message when string is too short.
     *
     * @param minLength    the minimum required length
     * @param actualLength the actual string length
     * @return the validation message
     */
    @NotNull
    String stringTooShort(int minLength, int actualLength);

    /**
     * Validation message when string is too long.
     *
     * @param maxLength    the maximum allowed length
     * @param actualLength the actual string length
     * @return the validation message
     */
    @NotNull
    String stringTooLong(int maxLength, int actualLength);

    /**
     * Validation message when string doesn't match required pattern.
     *
     * @param pattern the regex pattern
     * @return the validation message
     */
    @NotNull
    String stringPatternMismatch(@NotNull String pattern);

    // Internal Error Messages

    /**
     * Message when internal error occurs during subcommand execution.
     *
     * @param subcommandName the name of the subcommand
     * @return the error message
     */
    @NotNull
    String subcommandInternalError(@NotNull String subcommandName);

    /**
     * Message when internal error occurs during argument condition evaluation.
     *
     * @param argumentName the name of the argument
     * @return the error message
     */
    @NotNull
    String argumentConditionError(@NotNull String argumentName);

    /**
     * Message when internal error occurs during argument parsing.
     *
     * @param argumentName the name of the argument
     * @return the error message
     */
    @NotNull
    String argumentParsingError(@NotNull String argumentName);

    /**
     * Message when internal error occurs during argument transformation.
     *
     * @param argumentName the name of the argument
     * @return the error message
     */
    @NotNull
    String argumentTransformationError(@NotNull String argumentName);

    /**
     * Message when internal error occurs during argument validation.
     *
     * @param argumentName the name of the argument
     * @return the error message
     */
    @NotNull
    String argumentValidationError(@NotNull String argumentName);

    /**
     * Message when internal error occurs during cross-argument validation.
     *
     * @return the error message
     */
    @NotNull
    String crossValidationInternalError();

    /**
     * Message when command execution times out.
     *
     * @param timeoutMillis the timeout duration in milliseconds
     * @return the timeout message
     */
    @NotNull
    String commandTimeout(long timeoutMillis);

    /**
     * Generic message when internal error occurs during command execution.
     *
     * @return the execution error message
     */
    @NotNull
    String executionError();

    /**
     * Message when an unhandled internal error occurs during command processing.
     * This is used for unexpected exceptions that escape all other error handling.
     *
     * @return the internal error message
     */
    @NotNull
    String internalError();

    /**
     * Message when exception handler itself throws an exception.
     *
     * @param exceptionMessage the exception message
     * @return the error message
     */
    @NotNull
    String exceptionHandlerError(@NotNull String exceptionMessage);

    // Help Messages

    /**
     * Help header for commands with subcommands.
     *
     * @param commandName the formatted command name
     * @param commandPath the full command path
     * @return the help header
     */
    @NotNull
    String helpSubCommandsHeader(@NotNull String commandName, @NotNull String commandPath);

    /**
     * Prefix for each subcommand in help listing.
     *
     * @param subcommandName the subcommand name
     * @return the subcommand prefix
     */
    @NotNull
    String helpSubCommandPrefix(@NotNull String subcommandName);

    /**
     * Separator between subcommand name and usage in help.
     *
     * @return the separator string
     */
    @NotNull
    String helpUsageSeparator();

    /**
     * Separator between usage and description in help.
     *
     * @return the separator string
     */
    @NotNull
    String helpDescriptionSeparator();

    /**
     * Usage message for commands without subcommands.
     *
     * @param commandPath the full command path
     * @param usage       the usage string (empty if no arguments)
     * @return the usage message
     */
    @NotNull
    String helpUsage(@NotNull String commandPath, @NotNull String usage);

    // Guard Messages (for use by Guard factory methods)

    /**
     * Message when sender lacks a specific permission (for Guard.permission()).
     *
     * @param permission the permission node
     * @return the error message
     */
    @NotNull
    String guardPermission(@NotNull String permission);

    /**
     * Message when player must be in specific world (for Guard.inWorld()).
     *
     * @param worldName the world name
     * @return the error message
     */
    @NotNull
    String guardInWorld(@NotNull String worldName);

    /**
     * Message when player must be in specific game mode (for Guard.inGameMode()).
     *
     * @param gameModeName the game mode name
     * @return the error message
     */
    @NotNull
    String guardGameMode(@NotNull String gameModeName);

    /**
     * Message when command is operator-only (for Guard.opOnly()).
     *
     * @return the error message
     */
    @NotNull
    String guardOpOnly();

    /**
     * Message when player must be in level range (for Guard.levelRange()).
     *
     * @param minLevel the minimum level
     * @param maxLevel the maximum level
     * @return the error message
     */
    @NotNull
    String guardLevelRange(int minLevel, int maxLevel);

    /**
     * Message when player needs minimum level (for Guard.minLevel()).
     *
     * @param minLevel the minimum level
     * @return the error message
     */
    @NotNull
    String guardMinLevel(int minLevel);

    /**
     * Message when player needs more health (for Guard.healthAbove()).
     *
     * @param minHealth the minimum health
     * @return the error message
     */
    @NotNull
    String guardHealthAbove(double minHealth);

    /**
     * Message when player needs more food (for Guard.foodLevelAbove()).
     *
     * @param minFoodLevel the minimum food level
     * @return the error message
     */
    @NotNull
    String guardFoodLevelAbove(int minFoodLevel);

    /**
     * Message when player must be flying (for Guard.isFlying()).
     *
     * @return the error message
     */
    @NotNull
    String guardFlying();

    // Confirmation Messages

    /**
     * Message requesting the user to repeat the command to confirm execution.
     * Displayed when awaitConfirmation is enabled and the command is executed for the first time.
     *
     * @return the confirmation request message
     */
    @NotNull
    String awaitConfirmation();

    // Pagination Messages

    /**
     * Page info display format.
     *
     * @param currentPage the current page number
     * @param totalPages  the total number of pages
     * @param totalItems  the total number of items
     * @return the formatted page info string
     */
    @NotNull
    String paginationPageInfo(int currentPage, int totalPages, long totalItems);

    /**
     * Message when requested page is out of range.
     *
     * @param requestedPage the page that was requested
     * @param totalPages    the total number of pages available
     * @return the error message
     */
    @NotNull
    String paginationInvalidPage(int requestedPage, int totalPages);

    /**
     * Message when there are no items to display.
     *
     * @return the empty message
     */
    @NotNull
    String paginationEmpty();

    /**
     * Navigation hint for previous page.
     *
     * @param command the command to go to previous page
     * @return the navigation hint
     */
    @NotNull
    String paginationPreviousHint(@NotNull String command);

    /**
     * Navigation hint for next page.
     *
     * @param command the command to go to next page
     * @return the navigation hint
     */
    @NotNull
    String paginationNextHint(@NotNull String command);

    /**
     * Header for paginated list output.
     *
     * @param title the list title
     * @return the formatted header
     */
    @NotNull
    String paginationHeader(@NotNull String title);

    /**
     * Footer for paginated list output with compact page window overview.
     * <p>
     * Combines page info and page window navigation into a single formatted footer.
     * Example output: {@code §7Page §f3§7/§f10 (1 ... 2 | _3_ | 4 ... 10)}
     *
     * @param visiblePages      list of page numbers currently visible in the navigation window
     * @param currentPage       the current page number
     * @param totalPages        the total number of pages
     * @param showStartEllipsis whether to show ellipsis at the start (indicating pages before visible range)
     * @param showEndEllipsis   whether to show ellipsis at the end (indicating pages after visible range)
     * @param ellipsis          the ellipsis string to use (e.g., "...")
     * @param pageSeparator     the separator between page numbers (e.g., " | ")
     * @param currentPagePrefix prefix for the current page (e.g., "_")
     * @param currentPageSuffix suffix for the current page (e.g., "_")
     * @return the formatted footer with page window
     */
    @NotNull
    String paginationFooter(
        @NotNull java.util.List<Integer> visiblePages,
        int currentPage,
        int totalPages,
        boolean showStartEllipsis,
        boolean showEndEllipsis,
        @NotNull String ellipsis,
        @NotNull String pageSeparator,
        @NotNull String currentPagePrefix,
        @NotNull String currentPageSuffix
    );

    /**
     * Summary message for paginated results showing item range.
     * Example output: {@code Showing 1-10 of 50 items (Page 1 of 5)}
     *
     * @param startIndex   the start index (1-based)
     * @param endIndex     the end index (1-based)
     * @param totalItems   the total number of items
     * @param currentPage  the current page number
     * @param totalPages   the total number of pages
     * @return the formatted summary string
     */
    @NotNull
    String paginationSummary(long startIndex, long endIndex, long totalItems, int currentPage, int totalPages);

    /**
     * Page info with navigation hints for commands.
     * Example output: {@code §7Page §f1§7/§f5 §8[§e/list 0§8 <- -> §e/list 2§8]}
     *
     * @param currentPage the current page number
     * @param totalPages  the total number of pages
     * @param commandBase the base command for navigation (e.g., "/list")
     * @param hasPreviousPage whether there is a previous page
     * @param hasNextPage whether there is a next page
     * @return the formatted page info with navigation hints
     */
    @NotNull
    String paginationPageInfoWithNavigation(int currentPage, int totalPages, @NotNull String commandBase,
                                            boolean hasPreviousPage, boolean hasNextPage);

    /**
     * Navigation bar showing page numbers with arrows.
     * Example output: {@code §7<< §e§l[1]§r§7 2 3 §8... §710 §e>>}
     *
     * @param visiblePages  list of visible page numbers
     * @param currentPage   the current page number
     * @param totalPages    the total number of pages
     * @param showStartEllipsis whether to show ellipsis at the start
     * @param showEndEllipsis whether to show ellipsis at the end
     * @param config        pagination config for symbols (previousSymbol, nextSymbol, ellipsis)
     * @return the formatted navigation bar
     */
    @NotNull
    String paginationNavigationBar(@NotNull java.util.List<Integer> visiblePages, int currentPage, int totalPages,
                                   boolean showStartEllipsis, boolean showEndEllipsis,
                                   @NotNull de.feelix.leviathan.command.pagination.config.PaginationConfig config);

    // Interactive Prompting Messages

    /**
     * Prefix for interactive prompt messages.
     *
     * @return the prompt prefix (e.g., "§e[Interactive] §f")
     */
    @NotNull
    String interactivePromptPrefix();

    /**
     * Message prompting user for an argument value.
     *
     * @param argumentName the name of the argument
     * @param description  the argument description (may be null)
     * @return the prompt message
     */
    @NotNull
    String interactivePromptForArgument(@NotNull String argumentName, @NotNull String description);

    /**
     * Message showing available options during interactive prompting.
     *
     * @param options comma-separated list of options
     * @return the options message
     */
    @NotNull
    String interactivePromptOptions(@NotNull String options);

    /**
     * Message showing how to cancel an interactive session.
     *
     * @param cancelWord the word to type to cancel (e.g., "cancel")
     * @return the cancel hint message
     */
    @NotNull
    String interactivePromptCancelHint(@NotNull String cancelWord);

    /**
     * Message when interactive session times out.
     *
     * @return the timeout message
     */
    @NotNull
    String interactiveSessionTimeout();

    /**
     * Message when interactive session is cancelled by user.
     *
     * @return the cancelled message
     */
    @NotNull
    String interactiveSessionCancelled();

    /**
     * Message when a value is accepted during interactive prompting.
     *
     * @return the value accepted message
     */
    @NotNull
    String interactiveValueAccepted();

    /**
     * Message when user tries to skip a required argument.
     *
     * @return the skip not allowed message
     */
    @NotNull
    String interactiveSkipNotAllowed();

    /**
     * Message when all interactive values are collected and command will execute.
     *
     * @return the completion message
     */
    @NotNull
    String interactiveSessionComplete();

    /**
     * Message when user provides invalid input during interactive prompting.
     *
     * @return the invalid input message
     */
    @NotNull
    String interactiveInvalidInput();

    // Argument Group Validation Messages

    /**
     * Message when mutually exclusive arguments are provided together.
     *
     * @param groupName   the name of the argument group
     * @param members     comma-separated list of group members
     * @param provided    comma-separated list of provided arguments
     * @return the error message
     */
    @NotNull
    String argumentGroupMutuallyExclusive(@NotNull String groupName, @NotNull String members, @NotNull String provided);

    /**
     * Message when at least one argument from a group is required but none provided.
     *
     * @param groupName the name of the argument group
     * @param members   comma-separated list of group members
     * @return the error message
     */
    @NotNull
    String argumentGroupAtLeastOneRequired(@NotNull String groupName, @NotNull String members);

    /**
     * Message when all arguments in a group are required but only some provided.
     *
     * @param groupName the name of the argument group
     * @param members   comma-separated list of group members
     * @return the error message
     */
    @NotNull
    String argumentGroupAllRequired(@NotNull String groupName, @NotNull String members);

    // Interactive Prompting Error Messages

    /**
     * Message when parsing fails during interactive input.
     *
     * @param errorMessage the error message from the parser
     * @return the formatted error message
     */
    @NotNull
    String interactiveParseError(@NotNull String errorMessage);

    /**
     * Separator between options in interactive prompt (e.g., "§7, §f").
     *
     * @return the options separator
     */
    @NotNull
    String interactiveOptionsSeparator();

    // Validation Aggregator Messages

    /**
     * Header for validation errors list.
     *
     * @return the validation errors header
     */
    @NotNull
    String validationErrorsHeader();

    /**
     * Format a single validation error with field name and message.
     *
     * @param fieldName the field/argument name
     * @param message   the validation error message
     * @return the formatted error
     */
    @NotNull
    String validationErrorFormat(@NotNull String fieldName, @NotNull String message);

    // Help Formatter Messages

    /**
     * Header for command help display.
     *
     * @param commandPath the full command path
     * @return the formatted header
     */
    @NotNull
    String helpCommandHeader(@NotNull String commandPath);

    /**
     * Description line in help display.
     *
     * @param description the command description
     * @return the formatted description
     */
    @NotNull
    String helpDescription(@NotNull String description);

    /**
     * Usage line in help display.
     *
     * @param commandPath  the full command path
     * @param usagePattern the usage pattern
     * @return the formatted usage line
     */
    @NotNull
    String helpUsageLine(@NotNull String commandPath, @NotNull String usagePattern);

    /**
     * Prefix for argument descriptions in help.
     *
     * @return the argument prefix (e.g., "§7 - ")
     */
    @NotNull
    String helpArgumentPrefix();

    /**
     * Hint for getting more help about subcommands.
     *
     * @param commandPath the base command path
     * @return the help hint message
     */
    @NotNull
    String helpSubcommandHint(@NotNull String commandPath);

    // Help Section Headers

    /**
     * Section header for arguments in help display.
     *
     * @return the arguments section header (e.g., "§e§lArguments:")
     */
    @NotNull
    String helpSectionArguments();

    /**
     * Section header for flags in help display.
     *
     * @return the flags section header (e.g., "§e§lFlags:")
     */
    @NotNull
    String helpSectionFlags();

    /**
     * Section header for options/key-values in help display.
     *
     * @return the options section header (e.g., "§e§lOptions:")
     */
    @NotNull
    String helpSectionOptions();

    /**
     * Indicator for optional arguments/options in help.
     *
     * @return the optional indicator (e.g., "§8(optional)")
     */
    @NotNull
    String helpOptionalIndicator();

    /**
     * Indicator for required arguments/options in help.
     *
     * @return the required indicator (e.g., "§c(required)")
     */
    @NotNull
    String helpRequiredIndicator();

    /**
     * Indicator for default value in help.
     *
     * @param defaultValue the default value
     * @return the default indicator (e.g., "§8(default: value)")
     */
    @NotNull
    String helpDefaultIndicator(@NotNull String defaultValue);

    /**
     * Separator between argument name and type in help.
     *
     * @return the type separator (e.g., " §8- §f")
     */
    @NotNull
    String helpTypeSeparator();

    // Tab Completion Hints

    /**
     * Format for tab completion hint with description.
     *
     * @param description the description text
     * @return the formatted hint (e.g., " §7(description)")
     */
    @NotNull
    String tabCompletionHint(@NotNull String description);

    /**
     * Format for tab completion default value indicator.
     *
     * @return the default indicator for tab completion (e.g., " §7(default)")
     */
    @NotNull
    String tabCompletionDefaultHint();

    // Quoted String Parsing Messages

    /**
     * Message when quoted string parsing fails (e.g., unclosed quote).
     *
     * @param errorDetail the specific error (e.g., "Unclosed double quote")
     * @return the error message
     */
    @NotNull
    String quotedStringError(@NotNull String errorDetail);

    // ==================== Batch Operation Messages ====================

    /**
     * Message when batch size exceeds maximum.
     *
     * @param size    the actual batch size
     * @param maxSize the maximum allowed size
     * @return the error message
     */
    @NotNull
    default String batchSizeExceeded(int size, int maxSize) {
        return "§cBatch size §f" + size + " §cexceeds maximum of §f" + maxSize + "§c.";
    }

    /**
     * Progress message during batch execution.
     *
     * @param current  the current item number
     * @param total    the total number of items
     * @param success  the number of successful items
     * @param failed   the number of failed items
     * @return the progress message
     */
    @NotNull
    default String batchProgress(int current, int total, int success, int failed) {
        return String.format("§7[%d/%d] §aSuccess: %d §c| Failed: %d", current, total, success, failed);
    }

    /**
     * Summary message after batch completion.
     *
     * @param success     the number of successful items
     * @param total       the total number of items
     * @param timeMillis  the total execution time in milliseconds
     * @return the summary message
     */
    @NotNull
    default String batchComplete(int success, int total, double timeMillis) {
        return String.format("§aBatch complete: §f%d§7/§f%d §asuccessful §8(%.2fms)", success, total, timeMillis);
    }

    /**
     * Message when batch is cancelled.
     *
     * @param processed the number of items processed before cancellation
     * @param total     the total number of items
     * @return the cancellation message
     */
    @NotNull
    default String batchCancelled(int processed, int total) {
        return String.format("§cBatch cancelled after processing §f%d§7/§f%d §citems.", processed, total);
    }

    /**
     * Message when batch times out.
     *
     * @param processed the number of items processed before timeout
     * @param total     the total number of items
     * @return the timeout message
     */
    @NotNull
    default String batchTimeout(int processed, int total) {
        return String.format("§cBatch timed out after processing §f%d§7/§f%d §citems.", processed, total);
    }

    /**
     * Header for batch failure details.
     *
     * @return the failure header
     */
    @NotNull
    default String batchFailureHeader() {
        return "§c§lFailures:";
    }

    /**
     * Format for a single batch failure.
     *
     * @param targetName   the target name
     * @param errorMessage the error message
     * @return the formatted failure message
     */
    @NotNull
    default String batchFailureItem(@NotNull String targetName, @NotNull String errorMessage) {
        return "§c  - " + targetName + "§7: " + errorMessage;
    }

    // ==================== Wizard Messages ====================

    /**
     * Prefix for wizard messages.
     *
     * @return the wizard prefix
     */
    @NotNull
    default String wizardPrefix() {
        return "§d[Wizard] §f";
    }

    /**
     * Message when a wizard session starts.
     *
     * @param wizardName the wizard name
     * @return the start message
     */
    @NotNull
    default String wizardStarted(@NotNull String wizardName) {
        return wizardPrefix() + "Starting §b" + wizardName + "§f...";
    }

    /**
     * Message when a wizard completes successfully.
     *
     * @return the completion message
     */
    @NotNull
    default String wizardCompleted() {
        return "§a[Wizard] §fWizard completed!";
    }

    /**
     * Message when a wizard is cancelled.
     *
     * @return the cancellation message
     */
    @NotNull
    default String wizardCancelled() {
        return "§c[Wizard] §fWizard cancelled.";
    }

    /**
     * Message when a wizard times out.
     *
     * @return the timeout message
     */
    @NotNull
    default String wizardTimeout() {
        return "§c[Wizard] §fWizard timed out.";
    }

    /**
     * Prompt for user to enter a choice.
     *
     * @return the choice prompt
     */
    @NotNull
    default String wizardChoicePrompt() {
        return "§7Type the number or name of your choice.";
    }

    /**
     * Hint for going back to previous step.
     *
     * @return the back hint
     */
    @NotNull
    default String wizardBackHint() {
        return "§8Type 'back' to go to the previous step.";
    }

    /**
     * Hint for cancelling the wizard.
     *
     * @return the cancel hint
     */
    @NotNull
    default String wizardCancelHint() {
        return "§8Type 'cancel' to exit the wizard.";
    }

    /**
     * Message when an invalid option is selected.
     *
     * @return the invalid option message
     */
    @NotNull
    default String wizardInvalidOption() {
        return "§cInvalid option. Please try again.";
    }

    /**
     * Message when an option is not available.
     *
     * @return the unavailable option message
     */
    @NotNull
    default String wizardOptionUnavailable() {
        return "§cThis option is not available.";
    }

    /**
     * Confirmation prompt for wizard actions.
     *
     * @return the confirmation prompt
     */
    @NotNull
    default String wizardConfirmationPrompt() {
        return "§eAre you sure? Type 'yes' to confirm or 'no' to cancel.";
    }

    /**
     * Message when confirmation is cancelled.
     *
     * @return the confirmation cancelled message
     */
    @NotNull
    default String wizardConfirmationCancelled() {
        return "§7Cancelled. Please choose again.";
    }

    /**
     * Message when user cannot go back.
     *
     * @return the cannot go back message
     */
    @NotNull
    default String wizardCannotGoBack() {
        return "§7Cannot go back - you're at the beginning.";
    }

    /**
     * Message when wizard encounters an error.
     *
     * @param errorMessage the error message
     * @return the error message
     */
    @NotNull
    default String wizardError(@NotNull String errorMessage) {
        return "§c[Wizard] Error: §7" + errorMessage;
    }

    /**
     * Format for wizard option display.
     *
     * @param index       the option index (1-based)
     * @param key         the option key
     * @param displayText the display text
     * @param description the description (may be null)
     * @return the formatted option
     */
    @NotNull
    default String wizardOptionFormat(int index, @NotNull String key, @NotNull String displayText,
                                       @NotNull String description) {
        StringBuilder sb = new StringBuilder();
        sb.append("§e[").append(index).append("] §f").append(displayText);
        if (description != null && !description.isEmpty()) {
            sb.append(" §7- ").append(description);
        }
        return sb.toString();
    }

    /**
     * Message when wizard is attempted by a non-player.
     *
     * @return the error message
     */
    @NotNull
    default String wizardPlayerOnly() {
        return "§cWizards can only be used by players.";
    }

    /**
     * Message when batch operation has no targets.
     *
     * @return the error message
     */
    @NotNull
    default String batchEmptyTargets() {
        return "§cNo targets specified for batch operation.";
    }

    /**
     * Message when batch operation starts.
     *
     * @param targetCount the number of targets
     * @return the start message
     */
    @NotNull
    default String batchStarted(int targetCount) {
        return "§7Processing §f" + targetCount + " §7targets...";
    }

    /**
     * Simple completion message for batch operation.
     *
     * @param success the number of successful operations
     * @param failed  the number of failed operations
     * @return the completion message
     */
    @NotNull
    default String batchComplete(int success, int failed) {
        if (failed == 0) {
            return "§aBatch complete: §f" + success + " §asuccessful.";
        }
        return String.format("§aBatch complete: §f%d §asuccessful, §c%d §afailed.", success, failed);
    }

    /**
     * Message when batch operation has partial success.
     *
     * @param success the number of successful operations
     * @param failed  the number of failed operations
     * @return the partial success message
     */
    @NotNull
    default String batchPartialSuccess(int success, int failed) {
        return String.format("§eBatch partially complete: §f%d §asuccessful, §c%d §efailed.", success, failed);
    }

    /**
     * Message when batch operation fails completely.
     *
     * @param failed the number of failed operations
     * @return the failure message
     */
    @NotNull
    default String batchFailed(int failed) {
        return "§cBatch operation failed: §f" + failed + " §cerrors.";
    }
}
