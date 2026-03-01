package de.feelix.leviathan.command.performance;

/**
 * A cache entry holding a value and its expiration timestamp.
 * <p>
 * Used by {@link ArgumentCache} and {@link ResultCache} to store cached values
 * with time-to-live (TTL) support.
 *
 * @param <T> the type of the cached value
 */
final class CacheEntry<T> {
    final T value;
    final long expiresAt;

    CacheEntry(T value, long expiresAt) {
        this.value = value;
        this.expiresAt = expiresAt;
    }

    boolean isExpired() {
        return System.currentTimeMillis() > expiresAt;
    }
}
