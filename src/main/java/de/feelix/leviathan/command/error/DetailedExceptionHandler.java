package de.feelix.leviathan.command.error;

import de.feelix.leviathan.annotations.NotNull;
import de.feelix.leviathan.annotations.Nullable;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.logging.Logger;

/**
 * A comprehensive exception handler that prints detailed diagnostic information
 * to the console when errors occur during command execution.
 * <p>
 * This handler provides:
 * <ul>
 *   <li>Full stack traces with cause chain analysis</li>
 *   <li>JVM details (version, vendor, memory usage)</li>
 *   <li>Thread dump for debugging deadlocks and threading issues</li>
 *   <li>Contextual suggestions for why the error might have occurred</li>
 *   <li>Timestamp and error categorization</li>
 * </ul>
 * <p>
 * The handler delegates to specialized collectors for modularity and performance:
 * <ul>
 *   <li>{@link JvmInfoCollector} - JVM and memory information with caching</li>
 *   <li>{@link ThreadDumpCollector} - Thread analysis and deadlock detection</li>
 *   <li>{@link ExceptionSuggestionRegistry} - Exception-specific diagnostic suggestions</li>
 *   <li>{@link ErrorType} - Error categorization with embedded suggestions</li>
 * </ul>
 * <p>
 * Usage example:
 * <pre>{@code
 * SlashCommand command = SlashCommandBuilder.create("mycommand")
 *     .exceptionHandler(new DetailedExceptionHandler(plugin))
 *     .executes(ctx -> { ... })
 *     .build();
 * }</pre>
 *
 * @see ExceptionHandler
 * @see JvmInfoCollector
 * @see ThreadDumpCollector
 * @see ExceptionSuggestionRegistry
 */
public class DetailedExceptionHandler implements ExceptionHandler {

    private static final String SEPARATOR = "═".repeat(70);
    private static final String THIN_SEPARATOR = "─".repeat(70);
    // Thread-safe: DateTimeFormatter is immutable and thread-safe, unlike SimpleDateFormat
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private final Logger logger;
    private final boolean includeThreadDump;
    private final boolean includeJvmDetails;
    private final boolean includeSuggestions;

    /**
     * Creates a new DetailedExceptionHandler with all features enabled.
     *
     * @param plugin the plugin instance for logging
     */
    public DetailedExceptionHandler(@NotNull JavaPlugin plugin) {
        this(plugin.getLogger(), true, true, true);
    }

    /**
     * Creates a new DetailedExceptionHandler with a custom logger.
     *
     * @param logger the logger to use for output
     */
    public DetailedExceptionHandler(@NotNull Logger logger) {
        this(logger, true, true, true);
    }

    /**
     * Creates a new DetailedExceptionHandler with configurable features.
     *
     * @param logger             the logger to use for output
     * @param includeThreadDump  whether to include thread dump information
     * @param includeJvmDetails  whether to include JVM details
     * @param includeSuggestions whether to include error suggestions
     */
    public DetailedExceptionHandler(@NotNull Logger logger,
                                    boolean includeThreadDump,
                                    boolean includeJvmDetails,
                                    boolean includeSuggestions) {
        this.logger = logger;
        this.includeThreadDump = includeThreadDump;
        this.includeJvmDetails = includeJvmDetails;
        this.includeSuggestions = includeSuggestions;
    }

    /**
     * Creates a builder for configuring a DetailedExceptionHandler.
     *
     * @param plugin the plugin instance
     * @return a new builder instance
     */
    public static Builder builder(@NotNull JavaPlugin plugin) {
        return new Builder(plugin.getLogger());
    }

    /**
     * Creates a builder for configuring a DetailedExceptionHandler.
     *
     * @param logger the logger to use
     * @return a new builder instance
     */
    public static Builder builder(@NotNull Logger logger) {
        return new Builder(logger);
    }

    @Override
    public boolean handle(@NotNull CommandSender sender,
                          @NotNull ErrorType errorType,
                          @Nullable String message,
                          @Nullable Throwable exception) {
        StringBuilder report = new StringBuilder();

        appendHeader(report, errorType);
        appendTimestamp(report);
        appendErrorDetails(report, errorType, message, sender);

        if (exception != null) {
            appendExceptionDetails(report, exception);
            appendCauseChain(report, exception);

            if (includeSuggestions) {
                appendSuggestions(report, errorType, exception);
            }
        }

        if (includeJvmDetails) {
            appendJvmDetails(report);
        }

        if (includeThreadDump && exception != null) {
            appendThreadDump(report);
        }

        appendFooter(report);

        // Log the complete report (handle all newline types)
        for (String line : report.toString().split("\\r?\\n|\\r")) {
            if (!line.isEmpty()) {
                logger.severe(line);
            }
        }

        // Return false to allow default error message to be sent to user
        return false;
    }

    private void appendHeader(@NotNull StringBuilder report, @NotNull ErrorType errorType) {
        report.append("\n").append(SEPARATOR).append("\n");
        report.append("  EXCEPTION REPORT - ").append(errorType.name()).append("\n");
        report.append(SEPARATOR).append("\n");
    }

    private void appendTimestamp(@NotNull StringBuilder report) {
        // Thread-safe: DateTimeFormatter.format() is thread-safe
        report.append("\n Timestamp: ").append(DATE_FORMAT.format(ZonedDateTime.now())).append("\n");
    }

    private void appendErrorDetails(@NotNull StringBuilder report,
                                    @NotNull ErrorType errorType,
                                    @Nullable String message,
                                    @NotNull CommandSender sender) {
        report.append("\n").append(THIN_SEPARATOR).append("\n");
        report.append("  ERROR DETAILS\n");
        report.append(THIN_SEPARATOR).append("\n");
        report.append("  Type    : ").append(errorType.name()).append("\n");
        report.append("  Message : ").append(message != null ? message : "No message provided").append("\n");
        report.append("  Sender  : ").append(sender.getName()).append("\n");
        report.append("  Category: ").append(errorType.getCategoryDescription()).append("\n");
    }

    private void appendExceptionDetails(@NotNull StringBuilder report, @NotNull Throwable exception) {
        report.append("\n").append(THIN_SEPARATOR).append("\n");
        report.append("  EXCEPTION INFORMATION\n");
        report.append(THIN_SEPARATOR).append("\n");
        report.append("  Class   : ").append(exception.getClass().getName()).append("\n");
        report.append("  Message : ")
            .append(exception.getMessage() != null ? exception.getMessage() : "No message")
            .append("\n");

        // Print full stack trace
        report.append("\n  Stack Trace:\n");
        StringWriter sw = new StringWriter();
        exception.printStackTrace(new PrintWriter(sw));
        // Handle all newline types (\n, \r\n, \r)
        for (String line : sw.toString().split("\\r?\\n|\\r")) {
            if (!line.isEmpty()) {
                report.append("    ").append(line).append("\n");
            }
        }
    }

    private void appendCauseChain(@NotNull StringBuilder report, @NotNull Throwable exception) {
        Throwable cause = exception.getCause();
        int depth = 0;
        int maxDepth = 10; // Prevent infinite loops

        if (cause != null) {
            report.append("\n").append(THIN_SEPARATOR).append("\n");
            report.append("  CAUSE CHAIN ANALYSIS\n");
            report.append(THIN_SEPARATOR).append("\n");

            while (cause != null && depth < maxDepth) {
                String indent = "  " + "  ".repeat(depth);
                report.append(indent).append("↳ Caused by: ").append(cause.getClass().getName()).append("\n");
                report.append(indent)
                    .append("  Message: ")
                    .append(cause.getMessage() != null ? cause.getMessage() : "No message")
                    .append("\n");

                StackTraceElement[] stackTrace = cause.getStackTrace();
                if (stackTrace.length > 0) {
                    report.append(indent).append("  At: ").append(stackTrace[0]).append("\n");
                }

                cause = cause.getCause();
                depth++;
            }

            if (depth >= maxDepth) {
                report.append("  ... cause chain truncated (exceeded ").append(maxDepth).append(" levels)\n");
            }
        }
    }

    private void appendSuggestions(@NotNull StringBuilder report,
                                   @NotNull ErrorType errorType,
                                   @NotNull Throwable exception) {
        report.append("\n").append(THIN_SEPARATOR).append("\n");
        report.append("  POSSIBLE CAUSES & SUGGESTIONS\n");
        report.append(THIN_SEPARATOR).append("\n");

        // Error type specific suggestions (from enhanced enum)
        appendSuggestionList(report, errorType.getSuggestions());

        // Exception-specific suggestions (from registry)
        String exceptionName = exception.getClass().getSimpleName();
        report.append("\n  Based on exception type (").append(exceptionName).append("):\n");

        List<String> exceptionSuggestions = ExceptionSuggestionRegistry.getSuggestionsForException(exception);
        appendSuggestionList(report, exceptionSuggestions);

        // Message-based suggestions (from registry)
        String message = exception.getMessage();
        if (message != null && !message.isEmpty()) {
            List<String> messageSuggestions = ExceptionSuggestionRegistry.getMessageBasedSuggestions(message);
            appendSuggestionList(report, messageSuggestions);
        }
    }

    private void appendSuggestionList(@NotNull StringBuilder report, @NotNull List<String> suggestions) {
        for (String suggestion : suggestions) {
            report.append("  • ").append(suggestion).append("\n");
        }
    }

    private void appendJvmDetails(@NotNull StringBuilder report) {
        JvmInfoCollector.appendJvmDetails(report, THIN_SEPARATOR, DATE_FORMAT);
    }

    private void appendThreadDump(@NotNull StringBuilder report) {
        ThreadDumpCollector.appendThreadDump(report, THIN_SEPARATOR);
    }

    private void appendFooter(@NotNull StringBuilder report) {
        report.append("\n").append(SEPARATOR).append("\n");
        report.append("  END OF EXCEPTION REPORT\n");
        report.append(SEPARATOR).append("\n");
    }

    /**
     * Builder for creating customized DetailedExceptionHandler instances.
     */
    public static class Builder {
        private final Logger logger;
        private boolean includeThreadDump = true;
        private boolean includeJvmDetails = true;
        private boolean includeSuggestions = true;

        private Builder(@NotNull Logger logger) {
            this.logger = logger;
        }

        /**
         * Sets whether to include thread dump information.
         *
         * @param include true to include thread dump
         * @return this builder
         */
        public Builder includeThreadDump(boolean include) {
            this.includeThreadDump = include;
            return this;
        }

        /**
         * Sets whether to include JVM details.
         *
         * @param include true to include JVM details
         * @return this builder
         */
        public Builder includeJvmDetails(boolean include) {
            this.includeJvmDetails = include;
            return this;
        }

        /**
         * Sets whether to include error suggestions.
         *
         * @param include true to include suggestions
         * @return this builder
         */
        public Builder includeSuggestions(boolean include) {
            this.includeSuggestions = include;
            return this;
        }

        /**
         * Builds the DetailedExceptionHandler with the configured options.
         *
         * @return a new DetailedExceptionHandler instance
         */
        public DetailedExceptionHandler build() {
            return new DetailedExceptionHandler(logger, includeThreadDump, includeJvmDetails, includeSuggestions);
        }
    }
}
