package com.surense.infra.ratelimit;

/**
 * The four named limiters configured by {@link RateLimitProperties}.
 *
 * <p>Each value is paired with a {@code LimitSpec} (capacity + refill window)
 * and used as the type discriminator on {@link RateLimitKey} so that, for
 * example, the IP {@code 1.2.3.4} on the unauthenticated limiter and the
 * IP {@code 1.2.3.4} on the login limiter live in separate buckets.
 */
public enum LimitType {

    /** Per-IP bucket for unauthenticated requests. */
    UNAUTH_IP,

    /** Per-userId bucket for authenticated requests. */
    AUTH_USER,

    /** Per-(username + IP) bucket for failed login attempts (Step 6). */
    LOGIN,

    /** Per-refresh-token bucket (keyed on a hash of the token, Step 6). */
    REFRESH
}
