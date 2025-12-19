package de.feelix.leviathan.util;

import de.feelix.leviathan.annotations.NotNull;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.IntSupplier;

/**
 * Reusable utility for lazy cleanup operations in caches and managers.
 * <p>
 * This class provides a standardized way to perform periodic cleanup operations
 * without requiring background threads. Cleanup is triggered after a configurable
 * number of operations, distributing the cleanup cost across multiple calls.
 * <p>
 * Thread-safe: Uses atomic operations for operation counting.
 * <p>
 * Example usage:
 * <pre>{@code
 * // Create a cleanup provider that triggers every 50 operations
 * LazyCleanupProvider cleanup = LazyCleanupProvider.withInterval(50);
 *
 * // In your cache access method:
 * public Object get(String key) {
 *     cleanup.maybeCleanup(this::evictExpiredEntries);
 *     return cache.get(key);
 * }
 * }</pre>
 *
 * Performance characteristics:
 * <ul>
 *   <li>Operation counting uses atomic increment (very fast)</li>
 *   <li>Modulo check is only performed on increment result</li>
 *   <li>Cleanup only triggered every N operations</li>
 * </ul>
 */
public final class LazyCleanupProvider {

    private final AtomicLong operationCount;
    private final int cleanupInterval;
    private final AtomicLong lastCleanupTime;
    private final AtomicLong totalCleanedEntries;

    /**
     * Default cleanup interval (every 50 operations).
     */
    public static final int DEFAULT_INTERVAL = 50;

    private LazyCleanupProvider(int cleanupInterval) {
        if (cleanupInterval <= 0) {
            throw new IllegalArgumentException("cleanupInterval must be positive");
        }
        this.cleanupInterval = cleanupInterval;
        this.operationCount = new AtomicLong(0);
        this.lastCleanupTime = new AtomicLong(0);
        this.totalCleanedEntries = new AtomicLong(0);
    }

    /**
     * Create a cleanup provider with the default interval (50 operations).
     *
     * @return a new LazyCleanupProvider
     */
    public static @NotNull LazyCleanupProvider createDefault() {
        return new LazyCleanupProvider(DEFAULT_INTERVAL);
    }

    /**
     * Create a cleanup provider with a custom interval.
     *
     * @param interval number of operations between cleanups
     * @return a new LazyCleanupProvider
     */
    public static @NotNull LazyCleanupProvider withInterval(int interval) {
        return new LazyCleanupProvider(interval);
    }

    /**
     * Record an operation and potentially trigger cleanup.
     * <p>
     * This method should be called on every cache access operation.
     * When the operation count reaches the cleanup interval, the provided
     * cleanup action is executed.
     *
     * @param cleanupAction the cleanup action to execute; should return the number of entries cleaned
     */
    public void maybeCleanup(@NotNull IntSupplier cleanupAction) {
        Preconditions.checkNotNull(cleanupAction, "cleanupAction");
        long ops = operationCount.incrementAndGet();
        if (ops % cleanupInterval == 0) {
            int cleaned = cleanupAction.getAsInt();
            if (cleaned > 0) {
                totalCleanedEntries.addAndGet(cleaned);
            }
            lastCleanupTime.set(System.currentTimeMillis());
        }
    }

    /**
     * Record an operation and potentially trigger cleanup (void action variant).
     * <p>
     * Use this when the cleanup action doesn't need to report how many entries were cleaned.
     *
     * @param cleanupAction the cleanup action to execute
     */
    public void maybeCleanup(@NotNull Runnable cleanupAction) {
        Preconditions.checkNotNull(cleanupAction, "cleanupAction");
        long ops = operationCount.incrementAndGet();
        if (ops % cleanupInterval == 0) {
            cleanupAction.run();
            lastCleanupTime.set(System.currentTimeMillis());
        }
    }

    /**
     * Force a cleanup operation regardless of the operation count.
     *
     * @param cleanupAction the cleanup action to execute
     * @return the number of entries cleaned
     */
    public int forceCleanup(@NotNull IntSupplier cleanupAction) {
        Preconditions.checkNotNull(cleanupAction, "cleanupAction");
        int cleaned = cleanupAction.getAsInt();
        if (cleaned > 0) {
            totalCleanedEntries.addAndGet(cleaned);
        }
        lastCleanupTime.set(System.currentTimeMillis());
        return cleaned;
    }

    /**
     * Get the current operation count.
     *
     * @return the number of operations since creation
     */
    public long getOperationCount() {
        return operationCount.get();
    }

    /**
     * Get the configured cleanup interval.
     *
     * @return the number of operations between cleanups
     */
    public int getCleanupInterval() {
        return cleanupInterval;
    }

    /**
     * Get the timestamp of the last cleanup operation.
     *
     * @return timestamp in milliseconds, or 0 if no cleanup has occurred
     */
    public long getLastCleanupTime() {
        return lastCleanupTime.get();
    }

    /**
     * Get the total number of entries cleaned since creation.
     *
     * @return total cleaned entries count
     */
    public long getTotalCleanedEntries() {
        return totalCleanedEntries.get();
    }

    /**
     * Reset all statistics and counters.
     */
    public void reset() {
        operationCount.set(0);
        lastCleanupTime.set(0);
        totalCleanedEntries.set(0);
    }

    /**
     * Check if cleanup should be triggered based on operation count.
     * <p>
     * This is a read-only check that doesn't increment the counter.
     * Useful for conditional cleanup logic.
     *
     * @return true if the current operation count would trigger cleanup
     */
    public boolean shouldCleanup() {
        return operationCount.get() % cleanupInterval == 0;
    }

    @Override
    public String toString() {
        return "LazyCleanupProvider{" +
               "interval=" + cleanupInterval +
               ", operations=" + operationCount.get() +
               ", totalCleaned=" + totalCleanedEntries.get() +
               '}';
    }
}
