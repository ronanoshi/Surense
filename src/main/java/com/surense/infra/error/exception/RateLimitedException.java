package com.surense.infra.error.exception;

import com.surense.infra.error.ErrorCode;
import lombok.Getter;

/**
 * Thrown by in-controller rate limiters (login, refresh) when a caller has
 * exhausted their bucket. The pre-controller {@code IpRateLimitFilter} and
 * {@code UserRateLimitFilter} write the 429 response directly without
 * throwing — exceptions don't propagate cleanly through the servlet filter
 * chain — but they share the same {@link ErrorCode#RATE_LIMITED} contract.
 *
 * <p>{@link #getRetryAfterSeconds()} is surfaced on the {@code Retry-After}
 * HTTP header by {@code GlobalExceptionHandler}.
 */
@Getter
public class RateLimitedException extends ApiException {

    private final long retryAfterSeconds;

    public RateLimitedException(long retryAfterSeconds) {
        super(ErrorCode.RATE_LIMITED);
        this.retryAfterSeconds = Math.max(retryAfterSeconds, 1);
    }
}
