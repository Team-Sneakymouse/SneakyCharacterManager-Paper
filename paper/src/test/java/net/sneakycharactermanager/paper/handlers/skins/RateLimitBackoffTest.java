package net.sneakycharactermanager.paper.handlers.skins;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RateLimitBackoffTest {

    private static final long NOW = 1_000_000L;

    @Test
    void retryAfterHeaderWins() {
        assertEquals(NOW + 30_000L, RateLimitBackoff.retryAt("30", NOW + 90_000L, 1, NOW));
    }

    @Test
    void fallsBackToRateLimitReset() {
        assertEquals(NOW + 45_000L, RateLimitBackoff.retryAt(null, NOW + 45_000L, 1, NOW));
        assertEquals(NOW + 45_000L, RateLimitBackoff.retryAt("soon", NOW + 45_000L, 1, NOW));
    }

    @Test
    void repeatedHitsDoubleUpToTheCap() {
        assertEquals(NOW + 5_000L, RateLimitBackoff.retryAt(null, 0, 1, NOW));
        assertEquals(NOW + 10_000L, RateLimitBackoff.retryAt(null, 0, 2, NOW));
        assertEquals(NOW + 20_000L, RateLimitBackoff.retryAt(null, 0, 3, NOW));
        assertEquals(NOW + RateLimitBackoff.MAX_DELAY_MS, RateLimitBackoff.retryAt(null, 0, 50, NOW));
    }

    @Test
    void delayIsBounded() {
        assertEquals(NOW + RateLimitBackoff.MAX_DELAY_MS, RateLimitBackoff.retryAt("86400", 0, 1, NOW));
        assertEquals(NOW + RateLimitBackoff.MIN_DELAY_MS, RateLimitBackoff.retryAt("0", 0, 1, NOW));
    }

    /** A rate-limited entry at the head of the queue, hit repeatedly, must not starve the entry behind it. */
    @Test
    void backedOffEntryDoesNotBlockTheNextOne() {
        long[] retryAt = {0, 0};
        List<Integer> queue = new ArrayList<>(List.of(0, 1));
        long now = NOW;
        for (int hit = 1; hit <= 3; hit++) {
            retryAt[0] = RateLimitBackoff.retryAt(null, 0, hit, now);
            final long t = now;
            assertEquals(1, RateLimitBackoff.firstReady(queue, i -> t >= retryAt[i]));
            now += 1_000L;
        }
        final long later = retryAt[0];
        queue.remove(Integer.valueOf(1));
        assertEquals(0, RateLimitBackoff.firstReady(queue, i -> later >= retryAt[i]));
        assertNull(RateLimitBackoff.firstReady(queue, i -> later - 1 >= retryAt[i]));
    }
}
