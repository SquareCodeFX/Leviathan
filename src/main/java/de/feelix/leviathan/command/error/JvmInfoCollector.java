package de.feelix.leviathan.command.error;

import de.feelix.leviathan.annotations.NotNull;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.RuntimeMXBean;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Collector for JVM-related diagnostic information.
 * <p>
 * This class caches static JVM information (Java version, vendor, OS, etc.)
 * for performance optimization, while collecting dynamic information (memory usage,
 * uptime) on each call.
 */
public final class JvmInfoCollector {

    // Cached static JVM information (immutable after JVM start)
    private static final String JAVA_VERSION = System.getProperty("java.version");
    private static final String JAVA_VENDOR = System.getProperty("java.vendor");
    private static final String OS_INFO = buildOsInfo();
    private static final int AVAILABLE_PROCESSORS = Runtime.getRuntime().availableProcessors();

    // Lazily initialized beans
    private static volatile RuntimeMXBean runtimeMxBean;
    private static volatile MemoryMXBean memoryMxBean;

    // Cached values from RuntimeMXBean (immutable after JVM start)
    private static volatile String vmName;
    private static volatile String vmVersion;
    private static volatile long startTime;

    private JvmInfoCollector() {
        // Utility class
    }

    private static String buildOsInfo() {
        return System.getProperty("os.name") + " " +
                System.getProperty("os.version") + " (" +
                System.getProperty("os.arch") + ")";
    }

    private static RuntimeMXBean getRuntimeMxBean() {
        if (runtimeMxBean == null) {
            synchronized (JvmInfoCollector.class) {
                if (runtimeMxBean == null) {
                    runtimeMxBean = ManagementFactory.getRuntimeMXBean();
                    vmName = runtimeMxBean.getVmName();
                    vmVersion = runtimeMxBean.getVmVersion();
                    startTime = runtimeMxBean.getStartTime();
                }
            }
        }
        return runtimeMxBean;
    }

    private static MemoryMXBean getMemoryMxBean() {
        if (memoryMxBean == null) {
            synchronized (JvmInfoCollector.class) {
                if (memoryMxBean == null) {
                    memoryMxBean = ManagementFactory.getMemoryMXBean();
                }
            }
        }
        return memoryMxBean;
    }

    /**
     * Collects and appends JVM details to the provided StringBuilder.
     *
     * @param report    the StringBuilder to append to
     * @param separator the separator string to use
     * @param dateFormat the date format for timestamps
     */
    public static void appendJvmDetails(@NotNull StringBuilder report,
                                        @NotNull String separator,
                                        @NotNull SimpleDateFormat dateFormat) {
        report.append("\n").append(separator).append("\n");
        report.append("  JVM DETAILS\n");
        report.append(separator).append("\n");

        try {
            appendStaticInfo(report);
            appendMemoryInfo(report);
            appendRuntimeInfo(report, dateFormat);
        } catch (Exception e) {
            report.append("  Unable to retrieve JVM details: ").append(e.getMessage()).append("\n");
        }
    }

    private static void appendStaticInfo(@NotNull StringBuilder report) {
        // Ensure RuntimeMXBean is initialized for cached values
        getRuntimeMxBean();

        report.append("  Java Version : ").append(JAVA_VERSION).append("\n");
        report.append("  Java Vendor  : ").append(JAVA_VENDOR).append("\n");
        report.append("  JVM Name     : ").append(vmName).append("\n");
        report.append("  JVM Version  : ").append(vmVersion).append("\n");
        report.append("  OS           : ").append(OS_INFO).append("\n");
    }

    private static void appendMemoryInfo(@NotNull StringBuilder report) {
        MemoryUsage heapUsage = getMemoryMxBean().getHeapMemoryUsage();
        MemoryUsage nonHeapUsage = getMemoryMxBean().getNonHeapMemoryUsage();

        report.append("\n  Memory Usage:\n");
        report.append("    Heap Memory:\n");
        report.append("      Used     : ").append(formatBytes(heapUsage.getUsed())).append("\n");
        report.append("      Committed: ").append(formatBytes(heapUsage.getCommitted())).append("\n");
        report.append("      Max      : ").append(formatBytes(heapUsage.getMax())).append("\n");

        long maxHeap = heapUsage.getMax();
        if (maxHeap > 0) {
            double usagePercent = (double) heapUsage.getUsed() / maxHeap * 100;
            report.append("      Usage    : ").append(String.format("%.1f%%", usagePercent)).append("\n");
        }

        report.append("    Non-Heap Memory:\n");
        report.append("      Used     : ").append(formatBytes(nonHeapUsage.getUsed())).append("\n");
        report.append("      Committed: ").append(formatBytes(nonHeapUsage.getCommitted())).append("\n");
    }

    private static void appendRuntimeInfo(@NotNull StringBuilder report,
                                          @NotNull SimpleDateFormat dateFormat) {
        RuntimeMXBean runtime = getRuntimeMxBean();

        report.append("\n  Runtime Info:\n");
        report.append("    Uptime     : ").append(formatDuration(runtime.getUptime())).append("\n");
        report.append("    Start Time : ").append(dateFormat.format(new Date(startTime))).append("\n");
        report.append("    Processors : ").append(AVAILABLE_PROCESSORS).append("\n");
    }

    /**
     * Formats bytes into a human-readable string with appropriate unit.
     *
     * @param bytes the number of bytes
     * @return formatted string (e.g., "1.5 GB")
     */
    @NotNull
    public static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        char unit = "KMGTPE".charAt(exp - 1);
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), unit);
    }

    /**
     * Formats milliseconds into a human-readable duration string.
     *
     * @param millis the duration in milliseconds
     * @return formatted string (e.g., "2d 5h 30m 15s")
     */
    @NotNull
    public static String formatDuration(long millis) {
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
}
