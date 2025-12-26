package de.feelix.leviathan.command.performance;

import de.feelix.leviathan.annotations.NotNull;
import de.feelix.leviathan.annotations.Nullable;
import de.feelix.leviathan.util.Preconditions;

import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A thread-safe, high-performance object pool for reducing allocation overhead.
 * <p>
 * This pool manages reusable objects to minimize garbage collection pressure
 * in hot paths like command parsing and argument processing.
 * <p>
 * Features:
 * <ul>
 *   <li>Lock-free operations using ConcurrentLinkedDeque</li>
 *   <li>Configurable pool size limits</li>
 *   <li>Automatic object reset on return</li>
 *   <li>Statistics tracking for monitoring</li>
 *   <li>Graceful degradation when pool is exhausted</li>
 * </ul>
 * <p>
 * Example usage:
 * <pre>{@code
 * // Create a pool for StringBuilder with max 50 instances
 * ObjectPool<StringBuilder> pool = ObjectPool.create(
 *     StringBuilder::new,
 *     sb -> sb.setLength(0),  // Reset action
 *     50
 * );
 *
 * // Borrow and return
 * StringBuilder sb = pool.borrow();
 * try {
 *     sb.append("Hello");
 *     return sb.toString();
 * } finally {
 *     pool.release(sb);
 * }
 *
 * // Or use the functional API
 * String result = pool.withPooled(sb -> {
 *     sb.append("Hello");
 *     return sb.toString();
 * });
 * }</pre>
 *
 * @param <T> the type of objects managed by this pool
 */
public final class ObjectPool<T> {

    private final ConcurrentLinkedDeque<T> pool;
    private final Supplier<T> factory;
    private final Consumer<T> resetAction;
    private final int maxSize;

    // Statistics
    private final AtomicInteger currentSize;
    private final AtomicLong borrowCount;
    private final AtomicLong returnCount;
    private final AtomicLong creationCount;
    private final AtomicLong missCount;

    /**
     * Default maximum pool size.
     */
    public static final int DEFAULT_MAX_SIZE = 100;

    private ObjectPool(Supplier<T> factory, Consumer<T> resetAction, int maxSize) {
        this.pool = new ConcurrentLinkedDeque<>();
        this.factory = Preconditions.checkNotNull(factory, "factory");
        this.resetAction = resetAction; // Can be null
        this.maxSize = maxSize > 0 ? maxSize : DEFAULT_MAX_SIZE;
        this.currentSize = new AtomicInteger(0);
        this.borrowCount = new AtomicLong(0);
        this.returnCount = new AtomicLong(0);
        this.creationCount = new AtomicLong(0);
        this.missCount = new AtomicLong(0);
    }

    /**
     * Create a new object pool with default settings.
     *
     * @param factory the factory to create new objects
     * @param <T>     the object type
     * @return a new ObjectPool instance
     */
    public static <T> @NotNull ObjectPool<T> create(@NotNull Supplier<T> factory) {
        return new ObjectPool<>(factory, null, DEFAULT_MAX_SIZE);
    }

    /**
     * Create a new object pool with a reset action.
     *
     * @param factory     the factory to create new objects
     * @param resetAction the action to reset objects before returning to pool
     * @param <T>         the object type
     * @return a new ObjectPool instance
     */
    public static <T> @NotNull ObjectPool<T> create(@NotNull Supplier<T> factory,
                                                     @Nullable Consumer<T> resetAction) {
        return new ObjectPool<>(factory, resetAction, DEFAULT_MAX_SIZE);
    }

    /**
     * Create a new object pool with custom size limit.
     *
     * @param factory     the factory to create new objects
     * @param resetAction the action to reset objects before returning to pool
     * @param maxSize     maximum number of objects to keep in the pool
     * @param <T>         the object type
     * @return a new ObjectPool instance
     */
    public static <T> @NotNull ObjectPool<T> create(@NotNull Supplier<T> factory,
                                                     @Nullable Consumer<T> resetAction,
                                                     int maxSize) {
        return new ObjectPool<>(factory, resetAction, maxSize);
    }

    /**
     * Borrow an object from the pool.
     * <p>
     * If the pool is empty, a new object is created using the factory.
     * The caller is responsible for returning the object via {@link #release(Object)}.
     *
     * @return an object from the pool or a newly created one
     */
    public @NotNull T borrow() {
        borrowCount.incrementAndGet();
        T obj = pool.pollFirst();
        if (obj != null) {
            currentSize.decrementAndGet();
            return obj;
        }
        // Pool miss - create new object
        missCount.incrementAndGet();
        creationCount.incrementAndGet();
        return factory.get();
    }

    /**
     * Return an object to the pool.
     * <p>
     * If the pool is at capacity, the object is discarded.
     * If a reset action is configured, it is called before pooling.
     *
     * @param obj the object to return to the pool
     */
    public void release(@Nullable T obj) {
        if (obj == null) {
            return;
        }
        returnCount.incrementAndGet();

        // Reset the object if we have a reset action
        if (resetAction != null) {
            try {
                resetAction.accept(obj);
            } catch (Exception e) {
                // If reset fails, don't pool the object
                return;
            }
        }

        // Only add to pool if under capacity
        if (currentSize.get() < maxSize) {
            pool.offerFirst(obj);
            currentSize.incrementAndGet();
        }
        // Otherwise, let GC handle it
    }

    /**
     * Execute an action with a pooled object, automatically returning it afterward.
     * <p>
     * This is the preferred way to use the pool as it guarantees proper cleanup.
     *
     * @param action the action to execute with the pooled object
     * @param <R>    the result type
     * @return the result of the action
     */
    public <R> R withPooled(@NotNull java.util.function.Function<T, R> action) {
        Preconditions.checkNotNull(action, "action");
        T obj = borrow();
        try {
            return action.apply(obj);
        } finally {
            release(obj);
        }
    }

    /**
     * Execute an action with a pooled object (void variant).
     *
     * @param action the action to execute
     */
    public void withPooled(@NotNull Consumer<T> action) {
        Preconditions.checkNotNull(action, "action");
        T obj = borrow();
        try {
            action.accept(obj);
        } finally {
            release(obj);
        }
    }

    /**
     * Pre-populate the pool with objects.
     * <p>
     * Useful for warming up the pool to avoid initial allocation spikes.
     *
     * @param count the number of objects to pre-create
     */
    public void prewarm(int count) {
        int toCreate = Math.min(count, maxSize - currentSize.get());
        for (int i = 0; i < toCreate; i++) {
            T obj = factory.get();
            creationCount.incrementAndGet();
            if (currentSize.get() < maxSize) {
                pool.offerFirst(obj);
                currentSize.incrementAndGet();
            }
        }
    }

    /**
     * Clear all objects from the pool.
     */
    public void clear() {
        pool.clear();
        currentSize.set(0);
    }

    /**
     * Get the current number of objects in the pool.
     *
     * @return the pool size
     */
    public int size() {
        return currentSize.get();
    }

    /**
     * Get the maximum pool size.
     *
     * @return the max size
     */
    public int maxSize() {
        return maxSize;
    }

    /**
     * Check if the pool is empty.
     *
     * @return true if no objects are available
     */
    public boolean isEmpty() {
        return pool.isEmpty();
    }

    /**
     * Get pool statistics.
     *
     * @return a snapshot of pool statistics
     */
    public @NotNull PoolStats getStats() {
        return new PoolStats(
            currentSize.get(),
            maxSize,
            borrowCount.get(),
            returnCount.get(),
            creationCount.get(),
            missCount.get()
        );
    }

    /**
     * Reset all statistics counters.
     */
    public void resetStats() {
        borrowCount.set(0);
        returnCount.set(0);
        creationCount.set(0);
        missCount.set(0);
    }

    /**
     * Get the pool hit ratio (successful borrows from pool vs. total borrows).
     *
     * @return hit ratio between 0.0 and 1.0
     */
    public double getHitRatio() {
        long borrows = borrowCount.get();
        if (borrows == 0) return 1.0;
        long hits = borrows - missCount.get();
        return (double) hits / borrows;
    }

    @Override
    public String toString() {
        return "ObjectPool{" +
               "size=" + currentSize.get() +
               ", maxSize=" + maxSize +
               ", hitRatio=" + String.format("%.2f", getHitRatio()) +
               '}';
    }

    /**
     * Immutable statistics snapshot for the pool.
     */
    public static final class PoolStats {
        private final int currentSize;
        private final int maxSize;
        private final long borrowCount;
        private final long returnCount;
        private final long creationCount;
        private final long missCount;

        PoolStats(int currentSize, int maxSize, long borrowCount,
                  long returnCount, long creationCount, long missCount) {
            this.currentSize = currentSize;
            this.maxSize = maxSize;
            this.borrowCount = borrowCount;
            this.returnCount = returnCount;
            this.creationCount = creationCount;
            this.missCount = missCount;
        }

        public int getCurrentSize() { return currentSize; }
        public int getMaxSize() { return maxSize; }
        public long getBorrowCount() { return borrowCount; }
        public long getReturnCount() { return returnCount; }
        public long getCreationCount() { return creationCount; }
        public long getMissCount() { return missCount; }

        public double getHitRatio() {
            if (borrowCount == 0) return 1.0;
            return (double) (borrowCount - missCount) / borrowCount;
        }

        public double getUtilization() {
            return (double) currentSize / maxSize;
        }

        @Override
        public String toString() {
            return String.format(
                "PoolStats{size=%d/%d, borrows=%d, returns=%d, created=%d, misses=%d, hitRatio=%.2f}",
                currentSize, maxSize, borrowCount, returnCount, creationCount, missCount, getHitRatio()
            );
        }
    }
}
