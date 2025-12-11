package de.feelix.leviathan.command.cooldown;


import lombok.Getter;

/**
 * <p>
 * Class: CooldownResult
 * </p>
 * <p>
 * Author: squarecode
 * </p>
 * <p>
 * Date: 26.10.2025
 * </p>
 */
public class CooldownResult {
    @Getter
    private final boolean onCooldown;
    private final long remainingMillis;

    public CooldownResult(boolean onCooldown, long remainingMillis) {
        this.onCooldown = onCooldown;
        this.remainingMillis = remainingMillis;
    }

    /**
     * @return true if the command is currently on cooldown
     */
    public boolean onCooldown() {
        return onCooldown;
    }

    /**
     * @return remaining cooldown time in milliseconds (0 if not on cooldown)
     */
    public long remainingMillis() {
        return remainingMillis;
    }

    static CooldownResult notOnCooldown() {
        return new CooldownResult(false, 0L);
    }

    static CooldownResult onCooldown(long remainingMillis) {
        return new CooldownResult(true, remainingMillis);
    }
}
