package de.feelix.leviathan.command.message;

import de.feelix.leviathan.annotations.NotNull;

/**
 * Default English implementation of {@link MessageProvider}.
 * <p>
 * Provides the standard English messages that were previously hardcoded
 * throughout the SlashCommand API.
 * <p>
 * Color scheme:
 * <ul>
 *   <li>§c - Error/invalid (red)</li>
 *   <li>§a - Success/valid (green)</li>
 *   <li>§8 - Brackets, separators (dark gray)</li>
 *   <li>§f - Primary text (white)</li>
 *   <li>§7 - Secondary text (gray)</li>
 * </ul>
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
        return "§cYou don't have permission to use argument §8'§f" + argumentName + "§8'§c.";
    }

    @Override
    public @NotNull String requiresType(@NotNull String typeName) {
        return "§cThis command requires a §f" + typeName + "§c.";
    }

    // Cooldown Messages

    @Override
    public @NotNull String serverCooldown(@NotNull String formattedTime) {
        return "§cThis command is on cooldown. Please wait §f" + formattedTime + "§c.";
    }

    @Override
    public @NotNull String userCooldown(@NotNull String formattedTime) {
        return "§cYou must wait §f" + formattedTime + " §cbefore using this command again.";
    }

    // Usage and Parsing Messages

    @Override
    public @NotNull String insufficientArguments(@NotNull String commandPath, @NotNull String usage) {
        return "§cUsage: §f/" + commandPath + " " + usage;
    }

    @Override
    public @NotNull String tooManyArguments(@NotNull String commandPath, @NotNull String usage) {
        return "§cToo many arguments. Usage: §f/" + commandPath + " " + usage;
    }

    @Override
    public @NotNull String invalidArgumentValue(@NotNull String argumentName, @NotNull String expectedType,
                                                @NotNull String errorDetail) {
        return "§cInvalid value for §8'§f" + argumentName + "§8' §7(§fexpected " + expectedType + "§7)§c: §7" + errorDetail;
    }

    @Override
    public @NotNull String didYouMean(@NotNull String suggestions) {
        return "§7Did you mean: §f" + suggestions + "§7?";
    }

    // Validation Messages

    @Override
    public @NotNull String validationFailed(@NotNull String argumentName, @NotNull String validationError) {
        return "§cInvalid value for §8'§f" + argumentName + "§8'§c: §7" + validationError;
    }

    @Override
    public @NotNull String crossValidationFailed(@NotNull String errorDetail) {
        return "§cValidation error: §7" + errorDetail;
    }

    @Override
    public @NotNull String numericTooSmall(@NotNull String min, @NotNull String actual) {
        return "must be at least §f" + min + " §7(§fgot " + actual + "§7)";
    }

    @Override
    public @NotNull String numericTooLarge(@NotNull String max, @NotNull String actual) {
        return "must be at most §f" + max + " §7(§fgot " + actual + "§7)";
    }

    @Override
    public @NotNull String stringTooShort(int minLength, int actualLength) {
        return "length must be at least §f" + minLength + " §7(§fgot " + actualLength + "§7)";
    }

    @Override
    public @NotNull String stringTooLong(int maxLength, int actualLength) {
        return "length must be at most §f" + maxLength + " §7(§fgot " + actualLength + "§7)";
    }

    @Override
    public @NotNull String stringPatternMismatch(@NotNull String pattern) {
        return "does not match required pattern: §f" + pattern;
    }

    // Internal Error Messages

    @Override
    public @NotNull String subcommandInternalError(@NotNull String subcommandName) {
        return "§cInternal error while executing subcommand §8'§f" + subcommandName + "§8'§c.";
    }

    @Override
    public @NotNull String argumentConditionError(@NotNull String argumentName) {
        return "§cInternal error while evaluating condition for argument §8'§f" + argumentName + "§8'§c.";
    }

    @Override
    public @NotNull String argumentParsingError(@NotNull String argumentName) {
        return "§cInternal error while parsing argument §8'§f" + argumentName + "§8'§c.";
    }

    @Override
    public @NotNull String argumentTransformationError(@NotNull String argumentName) {
        return "§cInternal error while transforming argument §8'§f" + argumentName + "§8'§c.";
    }

    @Override
    public @NotNull String argumentValidationError(@NotNull String argumentName) {
        return "§cInternal error while validating argument §8'§f" + argumentName + "§8'§c.";
    }

    @Override
    public @NotNull String crossValidationInternalError() {
        return "§cInternal error during cross-argument validation.";
    }

    @Override
    public @NotNull String commandTimeout(long timeoutMillis) {
        return "§cCommand timed out after §f" + timeoutMillis + " §cms.";
    }

    @Override
    public @NotNull String executionError() {
        return "§cAn internal error occurred while executing this command.";
    }

    @Override
    public @NotNull String internalError() {
        return "§cAn unexpected internal error occurred. Please contact an administrator.";
    }

    @Override
    public @NotNull String exceptionHandlerError(@NotNull String exceptionMessage) {
        return "§cError in exception handler: §7" + exceptionMessage;
    }

    // Help Messages

    @Override
    public @NotNull String helpSubCommandsHeader(@NotNull String commandName, @NotNull String commandPath) {
        return "§f" + commandName + " SubCommands: §7(§f/" + commandPath + " …§7)\n";
    }

    @Override
    public @NotNull String helpSubCommandPrefix(@NotNull String subcommandName) {
        return "§8> §a" + subcommandName;
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
            return "§cUsage: §f/" + commandPath;
        } else {
            return "§cUsage: §f/" + commandPath + " " + usage;
        }
    }

    // Guard Messages

    @Override
    public @NotNull String guardPermission(@NotNull String permission) {
        return "§cYou lack permission: §f" + permission;
    }

    @Override
    public @NotNull String guardInWorld(@NotNull String worldName) {
        return "§cYou must be in world §8'§f" + worldName + "§8'§c.";
    }

    @Override
    public @NotNull String guardGameMode(@NotNull String gameModeName) {
        return "§cYou must be in §f" + gameModeName + " §cmode.";
    }

    @Override
    public @NotNull String guardOpOnly() {
        return "§cThis command is only available to operators.";
    }

    @Override
    public @NotNull String guardLevelRange(int minLevel, int maxLevel) {
        return "§cYou must be between level §f" + minLevel + " §cand §f" + maxLevel + " §cto use this command.";
    }

    @Override
    public @NotNull String guardMinLevel(int minLevel) {
        return "§cYou must be at least level §f" + minLevel + " §cto use this command.";
    }

    @Override
    public @NotNull String guardHealthAbove(double minHealth) {
        return "§cYou need more than §f" + minHealth + " §chealth to use this command.";
    }

    @Override
    public @NotNull String guardFoodLevelAbove(int minFoodLevel) {
        return "§cYou need more than §f" + minFoodLevel + " §cfood level to use this command.";
    }

    @Override
    public @NotNull String guardFlying() {
        return "§cYou must be flying to use this command.";
    }

    // Confirmation Messages

    @Override
    public @NotNull String awaitConfirmation() {
        return "§cPlease repeat the command to confirm it.";
    }

    // Pagination Messages

    @Override
    public @NotNull String paginationPageInfo(int currentPage, int totalPages, long totalItems) {
        return "§7Page §f" + currentPage + "§8/§f" + totalPages + " §8(§f" + totalItems + " §7items§8)";
    }

    @Override
    public @NotNull String paginationInvalidPage(int requestedPage, int totalPages) {
        return "§cInvalid page: §f" + requestedPage + "§c. Please enter a page between §f1 §cand §f" + totalPages + "§c.";
    }

    @Override
    public @NotNull String paginationEmpty() {
        return "§7No items found.";
    }

    @Override
    public @NotNull String paginationPreviousHint(@NotNull String command) {
        return "§8[§f" + command + " §8<- §7Previous§8]";
    }

    @Override
    public @NotNull String paginationNextHint(@NotNull String command) {
        return "§8[§7Next §8-> §f" + command + "§8]";
    }

    @Override
    public @NotNull String paginationHeader(@NotNull String title) {
        return "§8=== §f" + title + " §8===";
    }

    @Override
    public @NotNull String paginationFooter(
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

        // Page info part
        sb.append("§7Page §f").append(currentPage).append("§8/§f").append(totalPages);

        // Page window part
        sb.append(" §8(");

        boolean first = true;

        if (showStartEllipsis) {
            sb.append("§f1 §8").append(ellipsis).append(" ");
        }

        for (int page : visiblePages) {
            if (!first) {
                sb.append("§8").append(pageSeparator);
            }
            first = false;

            if (page == currentPage) {
                sb.append(currentPagePrefix)
                    .append(page)
                    .append(currentPageSuffix);
            } else {
                sb.append("§7").append(page);
            }
        }

        if (showEndEllipsis) {
            sb.append(" §8").append(ellipsis).append(" §f").append(totalPages);
        }

        sb.append("§8)");
        return sb.toString();
    }

    @Override
    public @NotNull String paginationSummary(long startIndex, long endIndex, long totalItems,
                                             int currentPage, int totalPages) {
        return String.format("§7Showing §f%d§8-§f%d §7of §f%d §7items §8(§7Page §f%d§8/§f%d§8)",
                             startIndex, endIndex, totalItems, currentPage, totalPages);
    }

    @Override
    public @NotNull String paginationPageInfoWithNavigation(int currentPage, int totalPages,
                                                            @NotNull String commandBase,
                                                            boolean hasPreviousPage, boolean hasNextPage) {
        StringBuilder sb = new StringBuilder();
        sb.append("§7Page §f").append(currentPage).append("§8/§f").append(totalPages);

        if (hasPreviousPage || hasNextPage) {
            sb.append(" §8[");
            if (hasPreviousPage) {
                sb.append("§f").append(commandBase).append(" ").append(currentPage - 1).append(" §8<- ");
            }
            if (hasNextPage) {
                sb.append("§8-> §f").append(commandBase).append(" ").append(currentPage + 1);
            }
            sb.append("§8]");
        }

        return sb.toString();
    }

    @Override
    public @NotNull String paginationNavigationBar(@NotNull java.util.List<Integer> visiblePages,
                                                   int currentPage, int totalPages,
                                                   boolean showStartEllipsis, boolean showEndEllipsis,
                                                   @NotNull de.feelix.leviathan.command.pagination.config.PaginationConfig config) {
        StringBuilder sb = new StringBuilder();

        // Previous arrow
        if (currentPage > 1) {
            sb.append("§f").append(config.getPreviousSymbol()).append(" ");
        } else {
            sb.append("§8").append(config.getPreviousSymbol()).append(" ");
        }

        // Show start ellipsis if needed
        if (showStartEllipsis) {
            sb.append("§f1 §8").append(config.getEllipsis()).append(" ");
        }

        // Page numbers
        for (int i = 0; i < visiblePages.size(); i++) {
            int page = visiblePages.get(i);
            if (i > 0) {
                sb.append(" ");
            }
            if (page == currentPage) {
                sb.append("§8[§a").append(page).append("§8]");
            } else {
                sb.append("§7").append(page);
            }
        }

        // Show end ellipsis if needed
        if (showEndEllipsis) {
            sb.append(" §8").append(config.getEllipsis()).append(" §f").append(totalPages);
        }

        // Next arrow
        if (currentPage < totalPages) {
            sb.append(" §f").append(config.getNextSymbol());
        } else {
            sb.append(" §8").append(config.getNextSymbol());
        }

        return sb.toString();
    }
}
