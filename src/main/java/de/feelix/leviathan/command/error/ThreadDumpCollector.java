package de.feelix.leviathan.command.error;

import de.feelix.leviathan.annotations.NotNull;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.EnumMap;
import java.util.Map;

/**
 * Collector for thread-related diagnostic information.
 * <p>
 * This class provides methods to collect thread dumps, detect deadlocks,
 * and analyze thread states for debugging purposes.
 */
public final class ThreadDumpCollector {

    private static final int MAX_BLOCKED_THREADS_TO_SHOW = 5;
    private static final int STACK_TRACE_DEPTH = 3;

    // Lazily initialized ThreadMXBean
    private static volatile ThreadMXBean threadMxBean;

    private ThreadDumpCollector() {
        // Utility class
    }

    private static ThreadMXBean getThreadMxBean() {
        if (threadMxBean == null) {
            synchronized (ThreadDumpCollector.class) {
                if (threadMxBean == null) {
                    threadMxBean = ManagementFactory.getThreadMXBean();
                }
            }
        }
        return threadMxBean;
    }

    /**
     * Collects and appends thread dump information to the provided StringBuilder.
     *
     * @param report    the StringBuilder to append to
     * @param separator the separator string to use
     */
    public static void appendThreadDump(@NotNull StringBuilder report, @NotNull String separator) {
        report.append("\n").append(separator).append("\n");
        report.append("  THREAD DUMP (Current Thread + Active Threads)\n");
        report.append(separator).append("\n");

        try {
            appendCurrentThreadInfo(report);
            appendThreadSummary(report);
            appendDeadlockInfo(report);
            appendThreadsByState(report);
            appendBlockedThreads(report);
        } catch (Exception e) {
            report.append("  Unable to retrieve thread dump: ").append(e.getMessage()).append("\n");
        }
    }

    private static void appendCurrentThreadInfo(@NotNull StringBuilder report) {
        Thread currentThread = Thread.currentThread();
        report.append("\n  Current Thread:\n");
        report.append("    Name  : ").append(currentThread.getName()).append("\n");
        report.append("    ID    : ").append(currentThread.getId()).append("\n");
        report.append("    State : ").append(currentThread.getState()).append("\n");
        report.append("    Priority: ").append(currentThread.getPriority()).append("\n");
    }

    private static void appendThreadSummary(@NotNull StringBuilder report) {
        ThreadMXBean bean = getThreadMxBean();
        report.append("\n  Thread Summary:\n");
        report.append("    Active Threads : ").append(bean.getThreadCount()).append("\n");
        report.append("    Peak Threads   : ").append(bean.getPeakThreadCount()).append("\n");
        report.append("    Total Started  : ").append(bean.getTotalStartedThreadCount()).append("\n");
    }

    private static void appendDeadlockInfo(@NotNull StringBuilder report) {
        long[] deadlockedThreads = getThreadMxBean().findDeadlockedThreads();
        if (deadlockedThreads != null && deadlockedThreads.length > 0) {
            report.append("\n  DEADLOCK DETECTED!\n");
            report.append("    Deadlocked thread IDs: ");
            for (int i = 0; i < deadlockedThreads.length; i++) {
                if (i > 0) report.append(", ");
                report.append(deadlockedThreads[i]);
            }
            report.append("\n");

            // Provide additional deadlock details
            ThreadInfo[] deadlockInfo = getThreadMxBean().getThreadInfo(deadlockedThreads, true, true);
            for (ThreadInfo info : deadlockInfo) {
                if (info != null) {
                    report.append("    - ").append(info.getThreadName())
                            .append(" blocked on ").append(info.getLockName())
                            .append(" owned by ").append(info.getLockOwnerName()).append("\n");
                }
            }
        }
    }

    private static void appendThreadsByState(@NotNull StringBuilder report) {
        ThreadMXBean bean = getThreadMxBean();
        Map<Thread.State, Integer> stateCounts = new EnumMap<>(Thread.State.class);

        // Initialize all states to 0
        for (Thread.State state : Thread.State.values()) {
            stateCounts.put(state, 0);
        }

        // Count threads by state
        ThreadInfo[] allThreads = bean.getThreadInfo(bean.getAllThreadIds());
        for (ThreadInfo info : allThreads) {
            if (info != null) {
                stateCounts.merge(info.getThreadState(), 1, Integer::sum);
            }
        }

        report.append("\n  Threads by State:\n");
        for (Map.Entry<Thread.State, Integer> entry : stateCounts.entrySet()) {
            if (entry.getValue() > 0) {
                report.append("    ").append(String.format("%-15s: %d", entry.getKey(), entry.getValue())).append("\n");
            }
        }
    }

    private static void appendBlockedThreads(@NotNull StringBuilder report) {
        report.append("\n  Blocked/Waiting Threads (max ").append(MAX_BLOCKED_THREADS_TO_SHOW).append("):\n");

        ThreadMXBean bean = getThreadMxBean();
        ThreadInfo[] allThreads = bean.getThreadInfo(bean.getAllThreadIds(), STACK_TRACE_DEPTH);

        int count = 0;
        for (ThreadInfo info : allThreads) {
            if (info != null && isBlockedOrWaiting(info.getThreadState())) {
                appendBlockedThreadInfo(report, info);
                count++;
                if (count >= MAX_BLOCKED_THREADS_TO_SHOW) break;
            }
        }

        if (count == 0) {
            report.append("    (none)\n");
        }
    }

    private static boolean isBlockedOrWaiting(Thread.State state) {
        return state == Thread.State.BLOCKED || state == Thread.State.WAITING;
    }

    private static void appendBlockedThreadInfo(@NotNull StringBuilder report, @NotNull ThreadInfo info) {
        report.append("    - ").append(info.getThreadName())
                .append(" (").append(info.getThreadState()).append(")\n");

        if (info.getLockName() != null) {
            report.append("      Waiting on: ").append(info.getLockName()).append("\n");
        }
        if (info.getLockOwnerName() != null) {
            report.append("      Lock owner: ").append(info.getLockOwnerName()).append("\n");
        }
    }
}
