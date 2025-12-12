package de.feelix.leviathan.command.help;

import de.feelix.leviathan.annotations.NotNull;
import de.feelix.leviathan.annotations.Nullable;
import de.feelix.leviathan.command.argument.Arg;
import de.feelix.leviathan.command.flag.Flag;
import de.feelix.leviathan.command.flag.KeyValue;
import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.Map;

/**
 * Interface for customizing help message formatting.
 * <p>
 * Implement this interface to provide custom formatting for command help messages,
 * allowing full control over colors, layout, and content.
 * <p>
 * Example implementation:
 * <pre>{@code
 * public class MyHelpFormatter implements HelpFormatter {
 *     @Override
 *     public String formatHeader(String commandPath, String description) {
 *         return "§6=== " + commandPath + " ===\n§7" + description;
 *     }
 *     // ... implement other methods
 * }
 *
 * SlashCommand.create("mycommand")
 *     .helpFormatter(new MyHelpFormatter())
 *     .build();
 * }</pre>
 */
public interface HelpFormatter {

    /**
     * Format the header section of the help message.
     *
     * @param commandPath full command path (e.g., "mycommand subcommand")
     * @param description command description
     * @return formatted header string
     */
    @NotNull
    String formatHeader(@NotNull String commandPath, @Nullable String description);

    /**
     * Format the usage line.
     *
     * @param commandPath  full command path
     * @param usagePattern usage pattern (e.g., {@code "<player> <amount> [--force]"})
     * @return formatted usage string
     */
    @NotNull
    String formatUsage(@NotNull String commandPath, @NotNull String usagePattern);

    /**
     * Format a single argument entry.
     *
     * @param arg      the argument definition
     * @param index    argument index (0-based)
     * @param isLast   whether this is the last argument
     * @return formatted argument string
     */
    @NotNull
    String formatArgument(@NotNull Arg<?> arg, int index, boolean isLast);

    /**
     * Format a single flag entry.
     *
     * @param flag  the flag definition
     * @param index flag index (0-based)
     * @return formatted flag string
     */
    @NotNull
    String formatFlag(@NotNull Flag flag, int index);

    /**
     * Format a single key-value entry.
     *
     * @param keyValue the key-value definition
     * @param index    key-value index (0-based)
     * @return formatted key-value string
     */
    @NotNull
    String formatKeyValue(@NotNull KeyValue<?> keyValue, int index);

    /**
     * Format a subcommand entry.
     *
     * @param name        subcommand name
     * @param description subcommand description (may be null)
     * @param aliases     subcommand aliases
     * @return formatted subcommand string
     */
    @NotNull
    String formatSubcommand(@NotNull String name, @Nullable String description, @NotNull List<String> aliases);

    /**
     * Format the section separator between different parts (args, flags, subcommands).
     *
     * @param sectionName name of the section (e.g., "Arguments", "Flags", "Subcommands")
     * @return formatted section separator
     */
    @NotNull
    String formatSectionHeader(@NotNull String sectionName);

    /**
     * Format the footer of the help message.
     *
     * @param commandPath full command path
     * @return formatted footer string
     */
    @NotNull
    String formatFooter(@NotNull String commandPath);

    /**
     * Assemble the complete help message from individual parts.
     *
     * @param header        formatted header
     * @param usage         formatted usage
     * @param arguments     list of formatted argument strings
     * @param flags         list of formatted flag strings
     * @param keyValues     list of formatted key-value strings
     * @param subcommands   list of formatted subcommand strings
     * @param footer        formatted footer
     * @return complete help message
     */
    @NotNull
    String assembleHelp(@NotNull String header,
                        @NotNull String usage,
                        @NotNull List<String> arguments,
                        @NotNull List<String> flags,
                        @NotNull List<String> keyValues,
                        @NotNull List<String> subcommands,
                        @NotNull String footer);

    // ==================== Default Implementation ====================

    /**
     * Get the default help formatter using Minecraft color codes.
     *
     * @return default formatter instance
     */
    static @NotNull HelpFormatter defaultFormatter() {
        return new DefaultHelpFormatter();
    }

    /**
     * Get a minimal help formatter without colors.
     *
     * @return plain text formatter instance
     */
    static @NotNull HelpFormatter plainFormatter() {
        return new PlainHelpFormatter();
    }

    /**
     * Default implementation using Minecraft color codes.
     */
    class DefaultHelpFormatter implements HelpFormatter {

        @Override
        public @NotNull String formatHeader(@NotNull String commandPath, @Nullable String description) {
            StringBuilder sb = new StringBuilder();
            sb.append("§6§l").append(commandPath.toUpperCase()).append("§r\n");
            if (description != null && !description.isEmpty()) {
                sb.append("§7").append(description).append("\n");
            }
            return sb.toString();
        }

        @Override
        public @NotNull String formatUsage(@NotNull String commandPath, @NotNull String usagePattern) {
            return "§eUsage: §f/" + commandPath + " " + usagePattern + "\n";
        }

        @Override
        public @NotNull String formatArgument(@NotNull Arg<?> arg, int index, boolean isLast) {
            StringBuilder sb = new StringBuilder();
            sb.append("  §b");
            if (arg.optional()) {
                sb.append("[").append(arg.name()).append("]");
            } else {
                sb.append("<").append(arg.name()).append(">");
            }
            sb.append("§7 - ");
            String desc = arg.context().description();
            sb.append(desc != null ? desc : arg.parser().getTypeName());
            if (arg.optional()) {
                sb.append(" §8(optional)");
            }
            sb.append("\n");
            return sb.toString();
        }

        @Override
        public @NotNull String formatFlag(@NotNull Flag flag, int index) {
            StringBuilder sb = new StringBuilder();
            sb.append("  §a");
            if (flag.shortForm() != null) {
                sb.append("-").append(flag.shortForm());
                if (flag.longForm() != null) {
                    sb.append(", --").append(flag.longForm());
                }
            } else if (flag.longForm() != null) {
                sb.append("--").append(flag.longForm());
            }
            sb.append("§7 - ").append(flag.description() != null ? flag.description() : "flag");
            sb.append("\n");
            return sb.toString();
        }

        @Override
        public @NotNull String formatKeyValue(@NotNull KeyValue<?> keyValue, int index) {
            StringBuilder sb = new StringBuilder();
            sb.append("  §d--").append(keyValue.key()).append("=<value>");
            sb.append("§7 - ").append(keyValue.description() != null ? keyValue.description() : keyValue.parser().getTypeName());
            if (keyValue.required()) {
                sb.append(" §c(required)");
            }
            sb.append("\n");
            return sb.toString();
        }

        @Override
        public @NotNull String formatSubcommand(@NotNull String name, @Nullable String description, @NotNull List<String> aliases) {
            StringBuilder sb = new StringBuilder();
            sb.append("  §e").append(name);
            if (!aliases.isEmpty()) {
                sb.append(" §8(").append(String.join(", ", aliases)).append(")");
            }
            if (description != null && !description.isEmpty()) {
                sb.append("§7 - ").append(description);
            }
            sb.append("\n");
            return sb.toString();
        }

        @Override
        public @NotNull String formatSectionHeader(@NotNull String sectionName) {
            return "\n§6" + sectionName + ":\n";
        }

        @Override
        public @NotNull String formatFooter(@NotNull String commandPath) {
            return "§8Use /" + commandPath + " help <subcommand> for more info";
        }

        @Override
        public @NotNull String assembleHelp(@NotNull String header,
                                            @NotNull String usage,
                                            @NotNull List<String> arguments,
                                            @NotNull List<String> flags,
                                            @NotNull List<String> keyValues,
                                            @NotNull List<String> subcommands,
                                            @NotNull String footer) {
            StringBuilder sb = new StringBuilder();
            sb.append(header);
            sb.append(usage);

            if (!arguments.isEmpty()) {
                sb.append(formatSectionHeader("Arguments"));
                arguments.forEach(sb::append);
            }

            if (!flags.isEmpty()) {
                sb.append(formatSectionHeader("Flags"));
                flags.forEach(sb::append);
            }

            if (!keyValues.isEmpty()) {
                sb.append(formatSectionHeader("Options"));
                keyValues.forEach(sb::append);
            }

            if (!subcommands.isEmpty()) {
                sb.append(formatSectionHeader("Subcommands"));
                subcommands.forEach(sb::append);
            }

            if (!footer.isEmpty()) {
                sb.append("\n").append(footer);
            }

            return sb.toString();
        }
    }

    /**
     * Plain text implementation without colors.
     */
    class PlainHelpFormatter implements HelpFormatter {

        @Override
        public @NotNull String formatHeader(@NotNull String commandPath, @Nullable String description) {
            StringBuilder sb = new StringBuilder();
            sb.append("=== ").append(commandPath.toUpperCase()).append(" ===\n");
            if (description != null && !description.isEmpty()) {
                sb.append(description).append("\n");
            }
            return sb.toString();
        }

        @Override
        public @NotNull String formatUsage(@NotNull String commandPath, @NotNull String usagePattern) {
            return "Usage: /" + commandPath + " " + usagePattern + "\n";
        }

        @Override
        public @NotNull String formatArgument(@NotNull Arg<?> arg, int index, boolean isLast) {
            StringBuilder sb = new StringBuilder();
            sb.append("  ");
            if (arg.optional()) {
                sb.append("[").append(arg.name()).append("]");
            } else {
                sb.append("<").append(arg.name()).append(">");
            }
            sb.append(" - ");
            String desc = arg.context().description();
            sb.append(desc != null ? desc : arg.parser().getTypeName());
            if (arg.optional()) {
                sb.append(" (optional)");
            }
            sb.append("\n");
            return sb.toString();
        }

        @Override
        public @NotNull String formatFlag(@NotNull Flag flag, int index) {
            StringBuilder sb = new StringBuilder();
            sb.append("  ");
            if (flag.shortForm() != null) {
                sb.append("-").append(flag.shortForm());
                if (flag.longForm() != null) {
                    sb.append(", --").append(flag.longForm());
                }
            } else if (flag.longForm() != null) {
                sb.append("--").append(flag.longForm());
            }
            sb.append(" - ").append(flag.description() != null ? flag.description() : "flag");
            sb.append("\n");
            return sb.toString();
        }

        @Override
        public @NotNull String formatKeyValue(@NotNull KeyValue<?> keyValue, int index) {
            StringBuilder sb = new StringBuilder();
            sb.append("  --").append(keyValue.key()).append("=<value>");
            sb.append(" - ").append(keyValue.description() != null ? keyValue.description() : keyValue.parser().getTypeName());
            if (keyValue.required()) {
                sb.append(" (required)");
            }
            sb.append("\n");
            return sb.toString();
        }

        @Override
        public @NotNull String formatSubcommand(@NotNull String name, @Nullable String description, @NotNull List<String> aliases) {
            StringBuilder sb = new StringBuilder();
            sb.append("  ").append(name);
            if (!aliases.isEmpty()) {
                sb.append(" (").append(String.join(", ", aliases)).append(")");
            }
            if (description != null && !description.isEmpty()) {
                sb.append(" - ").append(description);
            }
            sb.append("\n");
            return sb.toString();
        }

        @Override
        public @NotNull String formatSectionHeader(@NotNull String sectionName) {
            return "\n" + sectionName + ":\n";
        }

        @Override
        public @NotNull String formatFooter(@NotNull String commandPath) {
            return "Use /" + commandPath + " help <subcommand> for more info";
        }

        @Override
        public @NotNull String assembleHelp(@NotNull String header,
                                            @NotNull String usage,
                                            @NotNull List<String> arguments,
                                            @NotNull List<String> flags,
                                            @NotNull List<String> keyValues,
                                            @NotNull List<String> subcommands,
                                            @NotNull String footer) {
            StringBuilder sb = new StringBuilder();
            sb.append(header);
            sb.append(usage);

            if (!arguments.isEmpty()) {
                sb.append(formatSectionHeader("Arguments"));
                arguments.forEach(sb::append);
            }

            if (!flags.isEmpty()) {
                sb.append(formatSectionHeader("Flags"));
                flags.forEach(sb::append);
            }

            if (!keyValues.isEmpty()) {
                sb.append(formatSectionHeader("Options"));
                keyValues.forEach(sb::append);
            }

            if (!subcommands.isEmpty()) {
                sb.append(formatSectionHeader("Subcommands"));
                subcommands.forEach(sb::append);
            }

            if (!footer.isEmpty()) {
                sb.append("\n").append(footer);
            }

            return sb.toString();
        }
    }
}
