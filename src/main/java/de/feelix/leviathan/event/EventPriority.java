package de.feelix.leviathan.event;

/**
 * Listener call ordering. Higher priority runs earlier.
 */
public enum EventPriority {
    HIGHEST(400),
    HIGH(300),
    NORMAL(200),
    LOW(100),
    LOWEST(0);

    private final int weight;

    EventPriority(int weight) {
        this.weight = weight;
    }

    public int weight() {
        return weight;
    }
}
