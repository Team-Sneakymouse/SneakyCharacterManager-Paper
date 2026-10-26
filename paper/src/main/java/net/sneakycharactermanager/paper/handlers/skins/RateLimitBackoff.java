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
     * @param retryAfterHeader value of Retry-After (delta seconds or HTTP date), or null
     * @param resetAtMillis    epoch millis when MineSkin's rate limit window resets, or 0 if unknown
     * @param hits             consecutive 429s for this entry, starting at 1
     * @return epoch millis before which the entry must not be retried
     */
    static long retryAt(String retryAfterHeader, long resetAtMillis, int hits, long now) {
        Long seconds = parseSeconds(retryAfterHeader);
        if (seconds != null) {
            long delay = seconds > Long.MAX_VALUE / 1000L ? Long.MAX_VALUE : seconds * 1000L;
            return addDelay(now, Math.max(MIN_DELAY_MS, delay));
        }
        if (retryAfterHeader != null) {
            try {
                long deadline = java.time.ZonedDateTime.parse(retryAfterHeader.trim(),
                        java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli();
                return Math.max(addDelay(now, MIN_DELAY_MS), deadline);
            } catch (java.time.DateTimeException | ArithmeticException ignored) {
                // Invalid metadata falls back to the known reset or bounded exponential wait.
            }
        }
        if (resetAtMillis > now) return Math.max(addDelay(now, MIN_DELAY_MS), resetAtMillis);
        int doublings = Math.min(Math.max(hits, 1) - 1, 10);
        return addDelay(now, Math.min(MAX_DELAY_MS, BASE_DELAY_MS << doublings));
    }

    private static long addDelay(long now, long delay) {
        return now > Long.MAX_VALUE - delay ? Long.MAX_VALUE : now + delay;
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
