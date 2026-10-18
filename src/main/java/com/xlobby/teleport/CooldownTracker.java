package com.xlobby.teleport;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CooldownTracker {
    private final Clock clock;
    private final Map<UUID, Long> lastUse = new ConcurrentHashMap<>();

    public CooldownTracker() {
        this(Clock.systemUTC());
    }

    CooldownTracker(Clock clock) {
        this.clock = clock;
    }

    public void mark(UUID playerId) {
        lastUse.put(playerId, clock.millis());
    }

    public long remainingSeconds(UUID playerId, int cooldownSeconds) {
        if (cooldownSeconds <= 0) {
            return 0L;
        }
        Long usedAt = lastUse.get(playerId);
        if (usedAt == null) {
            return 0L;
        }

        long remainingMillis = cooldownSeconds * 1000L - (clock.millis() - usedAt);
        if (remainingMillis <= 0L) {
            lastUse.remove(playerId);
            return 0L;
        }
        return (remainingMillis + 999L) / 1000L;
    }

    public void remove(UUID playerId) {
        lastUse.remove(playerId);
    }

    public void clear() {
        lastUse.clear();
    }
}
