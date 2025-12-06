package de.feelix.leviathan.command.message;

import de.feelix.leviathan.annotations.NotNull;

/**
 * Default English implementation of {@link MessageProvider}.
 * <p>
 * Provides the standard English messages that were previously hardcoded
 * throughout the SlashCommand API.
 */
public class DefaultMessageProvider implements MessageProvider {

    // Permission and Access Messages

    @Override
    public @NotNull String noPermission() {
        return "§cYou don't have permission to use this command.";
    }

    @Override
    public @NotNull String playerOnly() {
        return "§cThis command can only be used by players.";
    }

    @Override
    public @NotNull String guardFailed() {
        return "§cYou cannot use this command.";
    }

    @Override
    public @NotNull String argumentPermissionDenied(@NotNull String argumentName) {
        return "§cYou don't have permission to use argument '" + argumentName + "'.";
    }

    @Override
    public @NotNull String requiresType(@NotNull String typeName) {
        return "§cThis command requires a " + typeName + ".";
    }

    // Cooldown Messages

    @Override
    public @NotNull String serverCooldown(@NotNull String formattedTime) {
        return "§cThis command is on cooldown. Please wait " + formattedTime + ".";
    }

    @Override
    public @NotNull String userCooldown(@NotNull String formattedTime) {
        return "§cYou must wait " + formattedTime + " before using this command again.";
    }

    // Usage and Parsing Messages

    @Override
    public @NotNull String insufficientArguments(@NotNull String commandPath, @NotNull String usage) {
        return "§cUsage: /" + commandPath + " " + usage;
    }

    @Override
    public @NotNull String tooManyArguments(@NotNull String commandPath, @NotNull String usage) {
        return "§cToo many arguments. Usage: /" + commandPath + " " + usage;
    }

    @Override
    public @NotNull String invalidArgumentValue(@NotNull String argumentName, @NotNull String expectedType,
                                                @NotNull String errorDetail) {
        return "§cInvalid value for '" + argumentName + "' (expected " + expectedType + "): " + errorDetail;
    }

    @Override
    public @NotNull String didYouMean(@NotNull String suggestions) {
        return "§cDid you mean: " + suggestions + "?";
    }

    // Validation Messages

    @Override
    public @NotNull String validationFailed(@NotNull String argumentName, @NotNull String validationError) {
        return "§cInvalid value for '" + argumentName + "': " + validationError;
    }

    @Override
    public @NotNull String crossValidationFailed(@NotNull String errorDetail) {
        return "§cValidation error: " + errorDetail;
    }

    @Override
    public @NotNull String numericTooSmall(@NotNull String min, @NotNull String actual) {
        return "must be at least " + min + " (got " + actual + ")";
    }

    @Override
    public @NotNull String numericTooLarge(@NotNull String max, @NotNull String actual) {
        return "must be at most " + max + " (got " + actual + ")";
    }

    @Override
    public @NotNull String stringTooShort(int minLength, int actualLength) {
        return "length must be at least " + minLength + " (got " + actualLength + ")";
    }

    @Override
    public @NotNull String stringTooLong(int maxLength, int actualLength) {
        return "length must be at most " + maxLength + " (got " + actualLength + ")";
    }

    @Override
    public @NotNull String stringPatternMismatch(@NotNull String pattern) {
        return "does not match required pattern: " + pattern;
    }

    // Internal Error Messages

    @Override
    public @NotNull String subcommandInternalError(@NotNull String subcommandName) {
        return "§cInternal error while executing subcommand '" + subcommandName + "'.";
    }

    @Override
    public @NotNull String argumentConditionError(@NotNull String argumentName) {
        return "§cInternal error while evaluating condition for argument '" + argumentName + "'.";
    }

    @Override
    public @NotNull String argumentParsingError(@NotNull String argumentName) {
        return "§cInternal error while parsing argument '" + argumentName + "'.";
    }

    @Override
    public @NotNull String argumentTransformationError(@NotNull String argumentName) {
        return "§cInternal error while transforming argument '" + argumentName + "'.";
    }

    @Override
    public @NotNull String argumentValidationError(@NotNull String argumentName) {
        return "§cInternal error while validating argument '" + argumentName + "'.";
    }

    @Override
    public @NotNull String crossValidationInternalError() {
        return "§cInternal error during cross-argument validation.";
    }

    @Override
    public @NotNull String commandTimeout(long timeoutMillis) {
        return "§cCommand timed out after " + timeoutMillis + " ms.";
    }

    @Override
    public @NotNull String executionError() {
        return "§cAn internal error occurred while executing this command.";
    }

    @Override
    public @NotNull String exceptionHandlerError(@NotNull String exceptionMessage) {
        return "§cError in exception handler: " + exceptionMessage;
    }

    // Help Messages

    @Override
    public @NotNull String helpSubCommandsHeader(@NotNull String commandName, @NotNull String commandPath) {
        return "§b" + commandName + " SubCommands: §7(/" + commandPath + " …)\n";
    }

    @Override
    public @NotNull String helpSubCommandPrefix(@NotNull String subcommandName) {
        return "§3> §a" + subcommandName;
    }

    @Override
    public @NotNull String helpUsageSeparator() {
        return " §8- §7";
    }

    @Override
    public @NotNull String helpDescriptionSeparator() {
        return " §8- §7";
    }

    @Override
    public @NotNull String helpUsage(@NotNull String commandPath, @NotNull String usage) {
        if (usage.isEmpty()) {
            return "§cUsage: /" + commandPath;
        } else {
            return "§cUsage: /" + commandPath + " " + usage;
        }
    }

    // Guard Messages

    @Override
    public @NotNull String guardPermission(@NotNull String permission) {
        return "§cYou lack permission: " + permission;
    }

    @Override
    public @NotNull String guardInWorld(@NotNull String worldName) {
        return "§cYou must be in world '" + worldName + "'.";
    }

    @Override
    public @NotNull String guardGameMode(@NotNull String gameModeName) {
        return "§cYou must be in " + gameModeName + " mode.";
    }

    @Override
    public @NotNull String guardOpOnly() {
        return "§cThis command is only available to operators.";
    }

    @Override
    public @NotNull String guardLevelRange(int minLevel, int maxLevel) {
        return "§cYou must be between level " + minLevel + " and " + maxLevel + " to use this command.";
    }

    @Override
    public @NotNull String guardMinLevel(int minLevel) {
        return "§cYou must be at least level " + minLevel + " to use this command.";
    }

    @Override
    public @NotNull String guardHealthAbove(double minHealth) {
        return "§cYou need more than " + minHealth + " health to use this command.";
    }

    @Override
    public @NotNull String guardFoodLevelAbove(int minFoodLevel) {
        return "§cYou need more than " + minFoodLevel + " food level to use this command.";
    }

    @Override
    public @NotNull String guardFlying() {
        return "§cYou must be flying to use this command.";
    }

    // Pagination Messages

    @Override
    public @NotNull String paginationPageInfo(int currentPage, int totalPages, long totalItems) {
        return "§7Page §f" + currentPage + "§7/§f" + totalPages + " §7(§f" + totalItems + " §7items)";
    }

    @Override
    public @NotNull String paginationInvalidPage(int requestedPage, int totalPages) {
        return "§cInvalid page: " + requestedPage + ". Please enter a page between 1 and " + totalPages + ".";
    }

    @Override
    public @NotNull String paginationEmpty() {
        return "§7No items found.";
    }

    @Override
    public @NotNull String paginationPreviousHint(@NotNull String command) {
        return "§8[§e" + command + "§8 <- Previous]";
    }

    @Override
    public @NotNull String paginationNextHint(@NotNull String command) {
        return "§8[Next -> §e" + command + "§8]";
    }

    @Override
    public @NotNull String paginationHeader(@NotNull String title) {
        return "§6=== " + title + " ===";
    }

    @Override
    public @NotNull String paginationFooter(int currentPage, int totalPages) {
        return "§7Page §f" + currentPage + "§7/§f" + totalPages;
    }

    @Override
    public @NotNull String paginationPageWindow(
        @NotNull java.util.List<Integer> visiblePages,
        int currentPage,
        int totalPages,
        boolean showStartEllipsis,
        boolean showEndEllipsis,
        @NotNull String ellipsis,
        @NotNull String pageSeparator,
        @NotNull String currentPagePrefix,
        @NotNull String currentPageSuffix
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("(");

        boolean first = true;

        if (showStartEllipsis) {
            sb.append("1 ").append(ellipsis).append(" ");
        }

        for (int page : visiblePages) {
            if (!first) {
                sb.append(pageSeparator);
            }
            first = false;

            if (page == currentPage) {
                sb.append(currentPagePrefix)
                    .append(page)
                    .append(currentPageSuffix);
            } else {
                sb.append(page);
            }
        }

        if (showEndEllipsis) {
            sb.append(" ").append(ellipsis).append(" ").append(totalPages);
        }

        sb.append(")");
        return sb.toString();
    }
}
