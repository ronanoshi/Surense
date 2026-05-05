package com.surense.infra.ratelimit;

import java.util.Objects;

/**
 * Identity of a single rate-limit bucket: which limiter ({@link LimitType})
 * and which subject ({@code value} — an IP, a userId, a hashed token, etc.).
 *
 * <p>Used as the key in the Caffeine-backed bucket store. Two requests that
 * map to the same {@link LimitType} and {@code value} share a bucket; any
 * other combination is independent.
 */
public record RateLimitKey(LimitType type, String value) {

    public RateLimitKey {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("RateLimitKey value must not be blank");
        }
    }

    public static RateLimitKey unauthIp(String ip) {
        return new RateLimitKey(LimitType.UNAUTH_IP, ip);
    }

    public static RateLimitKey authUser(String userId) {
        return new RateLimitKey(LimitType.AUTH_USER, userId);
    }

    public static RateLimitKey login(String username, String clientIp) {
        return new RateLimitKey(LimitType.LOGIN, username.toLowerCase() + "|" + clientIp);
    }

    public static RateLimitKey refresh(String tokenHash) {
        return new RateLimitKey(LimitType.REFRESH, tokenHash);
    }
}
