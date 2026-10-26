package net.sneakycharactermanager.paper.handlers.skins;

import java.util.List;
import java.util.function.Predicate;

/**
 * Decides when a skin entry that MineSkin rate limited (HTTP 429) may be tried again.
 * Server-supplied retry information wins; otherwise the wait doubles per consecutive 429.
 */
final class RateLimitBackoff {

    static final long MIN_DELAY_MS = 1_000L;
    static final long BASE_DELAY_MS = 5_000L;
    static final long MAX_DELAY_MS = 120_000L;

    private RateLimitBackoff() {
    }

    /**
     * @param retryAfterHeader value of the Retry-After header in seconds, or null
     * @param resetAtMillis    epoch millis when MineSkin's rate limit window resets, or 0 if unknown
     * @param hits             consecutive 429s for this entry, starting at 1
     * @return epoch millis before which the entry must not be retried
     */
    static long retryAt(String retryAfterHeader, long resetAtMillis, int hits, long now) {
        long delay;
        Long retryAfter = parseSeconds(retryAfterHeader);
        if (retryAfter != null) {
            delay = retryAfter * 1000L;
        } else if (resetAtMillis > now) {
            delay = resetAtMillis - now;
        } else {
            int doublings = Math.min(Math.max(hits, 1) - 1, 10);
            delay = BASE_DELAY_MS << doublings;
        }
        return now + Math.max(MIN_DELAY_MS, Math.min(MAX_DELAY_MS, delay));
    }

    /** First entry in queue order that is ready, so a backed-off entry never blocks the ones behind it. */
    static <T> T firstReady(List<T> entries, Predicate<T> ready) {
        for (T entry : entries) {
            if (ready.test(entry)) return entry;
        }
        return null;
    }

    private static Long parseSeconds(String value) {
        if (value == null) return null;
        try {
            long seconds = Long.parseLong(value.trim());
            return seconds >= 0 ? seconds : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
