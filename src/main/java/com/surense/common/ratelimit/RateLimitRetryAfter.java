package com.surense.common.ratelimit;

import java.util.concurrent.TimeUnit;

/**
 * Converts Bucket4j's nanosecond wait hint into a whole number of seconds for
 * the {@code Retry-After} response header (always {@code >= 1}).
 */
public final class RateLimitRetryAfter {

    private RateLimitRetryAfter() {
        // utility class
    }

    public static long toSeconds(long nanosToWaitForRefill) {
        if (nanosToWaitForRefill <= 0L) {
            return 1L;
        }
        long sec = TimeUnit.NANOSECONDS.toSeconds(nanosToWaitForRefill);
        long remainder = nanosToWaitForRefill - TimeUnit.SECONDS.toNanos(sec);
        if (sec < 1L) {
            return 1L;
        }
        return remainder > 0L ? sec + 1L : sec;
    }
}
