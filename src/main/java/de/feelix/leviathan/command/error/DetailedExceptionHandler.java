package de.feelix.leviathan.command.error;

import de.feelix.leviathan.annotations.NotNull;
import de.feelix.leviathan.annotations.Nullable;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
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
 * Usage example:
 * <pre>{@code
 * SlashCommand command = SlashCommandBuilder.create("mycommand")
 *     .exceptionHandler(new DetailedExceptionHandler(plugin))
 *     .executes(ctx -> { ... })
 *     .build();
 * }</pre>
 */
public class DetailedExceptionHandler implements ExceptionHandler {

    private static final String SEPARATOR = "═".repeat(70);
    private static final String THIN_SEPARATOR = "─".repeat(70);
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");

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
     * @param logger            the logger to use for output
     * @param includeThreadDump whether to include thread dump information
     * @param includeJvmDetails whether to include JVM details
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

        // Log the complete report
        for (String line : report.toString().split("\n")) {
            logger.severe(line);
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
        report.append("\n Timestamp: ").append(DATE_FORMAT.format(new Date())).append("\n");
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
        report.append("  Category: ").append(categorizeError(errorType)).append("\n");
    }

    private void appendExceptionDetails(@NotNull StringBuilder report, @NotNull Throwable exception) {
        report.append("\n").append(THIN_SEPARATOR).append("\n");
        report.append("  EXCEPTION INFORMATION\n");
        report.append(THIN_SEPARATOR).append("\n");
        report.append("  Class   : ").append(exception.getClass().getName()).append("\n");
        report.append("  Message : ").append(exception.getMessage() != null ? exception.getMessage() : "No message").append("\n");

        // Print full stack trace
        report.append("\n  Stack Trace:\n");
        StringWriter sw = new StringWriter();
        exception.printStackTrace(new PrintWriter(sw));
        for (String line : sw.toString().split("\n")) {
            report.append("    ").append(line).append("\n");
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
                report.append(indent).append("  Message: ").append(cause.getMessage() != null ? cause.getMessage() : "No message").append("\n");

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

        // Error type specific suggestions
        switch (errorType) {
            case PERMISSION:
                report.append("  • The player lacks the required permission node\n");
                report.append("  • Check if the permission is registered correctly\n");
                report.append("  • Verify permission plugin configuration\n");
                break;
            case PLAYER_ONLY:
                report.append("  • This command can only be executed by players\n");
                report.append("  • Console or command blocks cannot use this command\n");
                report.append("  • Consider adding a non-player alternative if needed\n");
                break;
            case GUARD_FAILED:
                report.append("  • A guard condition was not met\n");
                report.append("  • Check if player meets all requirements (level, items, etc.)\n");
                report.append("  • Review guard logic for edge cases\n");
                break;
            case PARSING:
                report.append("  • Invalid argument format provided\n");
                report.append("  • Check if the argument type matches expected format\n");
                report.append("  • Ensure argument parser handles edge cases\n");
                break;
            case VALIDATION:
                report.append("  • Argument value failed validation\n");
                report.append("  • Value may be out of allowed range\n");
                report.append("  • Check validation rules and constraints\n");
                break;
            case CROSS_VALIDATION:
                report.append("  • Multiple arguments have conflicting values\n");
                report.append("  • Check cross-argument validation logic\n");
                report.append("  • Ensure argument combinations are valid\n");
                break;
            case EXECUTION:
                report.append("  • Error occurred during command execution logic\n");
                report.append("  • Check for null pointer dereferences\n");
                report.append("  • Verify external API calls and database connections\n");
                break;
            case TIMEOUT:
                report.append("  • Async command execution exceeded time limit\n");
                report.append("  • Consider increasing timeout duration\n");
                report.append("  • Optimize long-running operations\n");
                break;
            case USAGE:
                report.append("  • Incorrect number of arguments provided\n");
                report.append("  • Check command syntax and required arguments\n");
                report.append("  • Review optional vs required argument configuration\n");
                break;
            case INTERNAL_ERROR:
                report.append("  • Unexpected internal error occurred\n");
                report.append("  • This may indicate a bug in the command framework\n");
                report.append("  • Check for recent code changes that might cause issues\n");
                break;
            default:
                report.append("  • Unknown error type\n");
                break;
        }

        // Exception-specific suggestions
        appendExceptionSpecificSuggestions(report, exception);
    }

    private void appendExceptionSpecificSuggestions(@NotNull StringBuilder report, @NotNull Throwable exception) {
        String exceptionName = exception.getClass().getSimpleName();
        String message = exception.getMessage();

        report.append("\n  Based on exception type (").append(exceptionName).append("):\n");

        if (exception instanceof NullPointerException) {
            report.append("  • A null value was accessed where an object was expected\n");
            report.append("  • Check if all required dependencies are initialized\n");
            report.append("  • Verify method return values before using them\n");
            report.append("  • Consider using Optional or null checks\n");
        } else if (exception instanceof IllegalArgumentException) {
            report.append("  • An invalid argument was passed to a method\n");
            report.append("  • Check argument validation before method calls\n");
            report.append("  • Review the expected parameter constraints\n");
        } else if (exception instanceof IllegalStateException) {
            report.append("  • Object is in an invalid state for the operation\n");
            report.append("  • Check initialization order of components\n");
            report.append("  • Verify that prerequisites are met before operation\n");
        } else if (exception instanceof ClassCastException) {
            report.append("  • Type casting failed - incompatible types\n");
            report.append("  • Check generic type parameters\n");
            report.append("  • Verify object types before casting\n");
        } else if (exception instanceof IndexOutOfBoundsException) {
            report.append("  • Array or list index is out of valid range\n");
            report.append("  • Check array/list bounds before accessing\n");
            report.append("  • Verify loop conditions and index calculations\n");
        } else if (exception instanceof NumberFormatException) {
            report.append("  • String could not be parsed as a number\n");
            report.append("  • Validate input format before parsing\n");
            report.append("  • Check for empty strings or non-numeric characters\n");
        } else if (exception instanceof UnsupportedOperationException) {
            report.append("  • Operation is not supported by this implementation\n");
            report.append("  • Check if using an immutable collection\n");
            report.append("  • Verify API compatibility\n");
        } else if (exception instanceof SecurityException) {
            report.append("  • Security manager denied the operation\n");
            report.append("  • Check security policy configuration\n");
            report.append("  • Verify required permissions are granted\n");
        } else if (exceptionName.contains("SQL") || exceptionName.contains("Database")) {
            report.append("  • Database operation failed\n");
            report.append("  • Check database connection and credentials\n");
            report.append("  • Verify SQL syntax and table existence\n");
        } else if (exceptionName.contains("IO") || exceptionName.contains("File")) {
            report.append("  • I/O operation failed\n");
            report.append("  • Check file permissions and path validity\n");
            report.append("  • Verify disk space and file existence\n");
        } else if (exceptionName.contains("Timeout") || exceptionName.contains("Connection")) {
            report.append("  • Network or connection timeout occurred\n");
            report.append("  • Check network connectivity\n");
            report.append("  • Verify remote service availability\n");
        } else {
            report.append("  • Review the exception message for specific details\n");
            report.append("  • Check the stack trace for the error origin\n");
            report.append("  • Consult documentation for this exception type\n");
        }

        // Message-based suggestions
        if (message != null) {
            if (message.toLowerCase().contains("null")) {
                report.append("  • Exception message mentions 'null' - check for uninitialized variables\n");
            }
            if (message.toLowerCase().contains("not found") || message.toLowerCase().contains("missing")) {
                report.append("  • Something is missing - check configuration and dependencies\n");
            }
            if (message.toLowerCase().contains("denied") || message.toLowerCase().contains("permission")) {
                report.append("  • Access was denied - check permissions and access rights\n");
            }
        }
    }

    private void appendJvmDetails(@NotNull StringBuilder report) {
        report.append("\n").append(THIN_SEPARATOR).append("\n");
        report.append("  JVM DETAILS\n");
        report.append(THIN_SEPARATOR).append("\n");

        try {
            RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
            MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
            MemoryUsage heapUsage = memory.getHeapMemoryUsage();
            MemoryUsage nonHeapUsage = memory.getNonHeapMemoryUsage();

            // JVM Info
            report.append("  Java Version : ").append(System.getProperty("java.version")).append("\n");
            report.append("  Java Vendor  : ").append(System.getProperty("java.vendor")).append("\n");
            report.append("  JVM Name     : ").append(runtime.getVmName()).append("\n");
            report.append("  JVM Version  : ").append(runtime.getVmVersion()).append("\n");
            report.append("  OS           : ").append(System.getProperty("os.name"))
                    .append(" ").append(System.getProperty("os.version"))
                    .append(" (").append(System.getProperty("os.arch")).append(")\n");

            // Memory Info
            report.append("\n  Memory Usage:\n");
            report.append("    Heap Memory:\n");
            report.append("      Used     : ").append(formatBytes(heapUsage.getUsed())).append("\n");
            report.append("      Committed: ").append(formatBytes(heapUsage.getCommitted())).append("\n");
            report.append("      Max      : ").append(formatBytes(heapUsage.getMax())).append("\n");
            report.append("      Usage    : ").append(String.format("%.1f%%", (double) heapUsage.getUsed() / heapUsage.getMax() * 100)).append("\n");

            report.append("    Non-Heap Memory:\n");
            report.append("      Used     : ").append(formatBytes(nonHeapUsage.getUsed())).append("\n");
            report.append("      Committed: ").append(formatBytes(nonHeapUsage.getCommitted())).append("\n");

            // Runtime Info
            report.append("\n  Runtime Info:\n");
            report.append("    Uptime     : ").append(formatDuration(runtime.getUptime())).append("\n");
            report.append("    Start Time : ").append(DATE_FORMAT.format(new Date(runtime.getStartTime()))).append("\n");
            report.append("    Processors : ").append(Runtime.getRuntime().availableProcessors()).append("\n");

        } catch (Exception e) {
            report.append("  Unable to retrieve JVM details: ").append(e.getMessage()).append("\n");
        }
    }

    private void appendThreadDump(@NotNull StringBuilder report) {
        report.append("\n").append(THIN_SEPARATOR).append("\n");
        report.append("  THREAD DUMP (Current Thread + Active Threads)\n");
        report.append(THIN_SEPARATOR).append("\n");

        try {
            ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();

            // Current thread info
            Thread currentThread = Thread.currentThread();
            report.append("\n  Current Thread:\n");
            report.append("    Name  : ").append(currentThread.getName()).append("\n");
            report.append("    ID    : ").append(currentThread.getId()).append("\n");
            report.append("    State : ").append(currentThread.getState()).append("\n");
            report.append("    Priority: ").append(currentThread.getPriority()).append("\n");

            // Thread summary
            int threadCount = threadMXBean.getThreadCount();
            int peakCount = threadMXBean.getPeakThreadCount();
            long totalStarted = threadMXBean.getTotalStartedThreadCount();

            report.append("\n  Thread Summary:\n");
            report.append("    Active Threads : ").append(threadCount).append("\n");
            report.append("    Peak Threads   : ").append(peakCount).append("\n");
            report.append("    Total Started  : ").append(totalStarted).append("\n");

            // Deadlock detection
            long[] deadlockedThreads = threadMXBean.findDeadlockedThreads();
            if (deadlockedThreads != null && deadlockedThreads.length > 0) {
                report.append("\n  DEADLOCK DETECTED!\n");
                report.append("    Deadlocked thread IDs: ");
                for (long id : deadlockedThreads) {
                    report.append(id).append(" ");
                }
                report.append("\n");
            }

            // Top threads by state
            Map<Thread.State, Integer> stateCounts = new java.util.EnumMap<>(Thread.State.class);
            for (ThreadInfo info : threadMXBean.getThreadInfo(threadMXBean.getAllThreadIds())) {
                if (info != null) {
                    stateCounts.merge(info.getThreadState(), 1, Integer::sum);
                }
            }

            report.append("\n  Threads by State:\n");
            for (Map.Entry<Thread.State, Integer> entry : stateCounts.entrySet()) {
                report.append("    ").append(String.format("%-15s: %d", entry.getKey(), entry.getValue())).append("\n");
            }

            // List blocked threads (potential issues)
            report.append("\n  Blocked/Waiting Threads (max 5):\n");
            int count = 0;
            for (ThreadInfo info : threadMXBean.getThreadInfo(threadMXBean.getAllThreadIds(), 3)) {
                if (info != null && (info.getThreadState() == Thread.State.BLOCKED ||
                        info.getThreadState() == Thread.State.WAITING)) {
                    report.append("    - ").append(info.getThreadName())
                            .append(" (").append(info.getThreadState()).append(")\n");
                    if (info.getLockName() != null) {
                        report.append("      Waiting on: ").append(info.getLockName()).append("\n");
                    }
                    if (info.getLockOwnerName() != null) {
                        report.append("      Lock owner: ").append(info.getLockOwnerName()).append("\n");
                    }
                    count++;
                    if (count >= 5) break;
                }
            }
            if (count == 0) {
                report.append("    (none)\n");
            }

        } catch (Exception e) {
            report.append("  Unable to retrieve thread dump: ").append(e.getMessage()).append("\n");
        }
    }

    private void appendFooter(@NotNull StringBuilder report) {
        report.append("\n").append(SEPARATOR).append("\n");
        report.append("  END OF EXCEPTION REPORT\n");
        report.append(SEPARATOR).append("\n");
    }

    private String categorizeError(@NotNull ErrorType errorType) {
        return switch (errorType) {
            case PERMISSION, PLAYER_ONLY, GUARD_FAILED, ARGUMENT_PERMISSION -> "Access/Authorization Issue";
            case PARSING, VALIDATION, CROSS_VALIDATION, USAGE -> "Input/Argument Issue";
            case EXECUTION, INTERNAL_ERROR -> "Runtime/Execution Issue";
            case TIMEOUT -> "Performance Issue";
            default -> "Unknown Category";
        };
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        char unit = "KMGTPE".charAt(exp - 1);
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), unit);
    }

    private String formatDuration(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (days > 0) {
            return String.format("%dd %dh %dm %ds", days, hours % 24, minutes % 60, seconds % 60);
        } else if (hours > 0) {
            return String.format("%dh %dm %ds", hours, minutes % 60, seconds % 60);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, seconds % 60);
        } else {
            return String.format("%ds", seconds);
        }
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
