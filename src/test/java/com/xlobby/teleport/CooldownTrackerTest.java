package com.xlobby.teleport;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

class CooldownTrackerTest {
    @Test
    void reportsRoundedUpRemainingSecondsAndExpiresEntries() {
        MutableClock clock = new MutableClock(1_000L);
        CooldownTracker tracker = new CooldownTracker(clock);
        UUID playerId = UUID.randomUUID();

        tracker.mark(playerId);
        assertEquals(3L, tracker.remainingSeconds(playerId, 3));

        clock.setMillis(2_500L);
        assertEquals(2L, tracker.remainingSeconds(playerId, 3));

        clock.setMillis(4_000L);
        assertEquals(0L, tracker.remainingSeconds(playerId, 3));
    }

    @Test
    void disabledCooldownNeverBlocks() {
        MutableClock clock = new MutableClock(1_000L);
        CooldownTracker tracker = new CooldownTracker(clock);
        UUID playerId = UUID.randomUUID();
        tracker.mark(playerId);

        assertEquals(0L, tracker.remainingSeconds(playerId, 0));
    }

    @Test
    void concurrentTeleportCompletionsDoNotLoseCooldowns() throws Exception {
        MutableClock clock = new MutableClock(1_000L);
        CooldownTracker tracker = new CooldownTracker(clock);
        UUID[] playerIds = new UUID[20_000];
        for (int index = 0; index < playerIds.length; index++) {
            playerIds[index] = UUID.randomUUID();
        }

        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(8)) {
            for (int worker = 0; worker < 8; worker++) {
                int first = worker * 2_500;
                executor.submit(() -> {
                    start.await();
                    for (int index = first; index < first + 2_500; index++) {
                        tracker.mark(playerIds[index]);
                    }
                    return null;
                });
            }
            start.countDown();
        }

        int retained = 0;
        for (UUID playerId : playerIds) {
            if (tracker.remainingSeconds(playerId, 10) == 10L) {
                retained++;
            }
        }
        assertEquals(playerIds.length, retained);
    }

    private static final class MutableClock extends Clock {
        private long millis;

        private MutableClock(long millis) {
            this.millis = millis;
        }

        void setMillis(long millis) {
            this.millis = millis;
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(millis);
        }
    }
}
