package com.surense.common.ratelimit;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Per-refresh-token limiter (Step 6). Keys buckets on a SHA-256 hash of the
 * raw refresh token so the secret value never becomes a Caffeine cache key.
 */
@Component
@RequiredArgsConstructor
public class RefreshTokenRateLimiter {

    private final BucketProvider bucketProvider;

    /**
     * Consumes one token from the refresh bucket for the given opaque token.
     *
     * @return {@code true} if under the limit, {@code false} if the caller
     *         must respond with 429.
     */
    public boolean tryConsume(String refreshToken) {
        String hash = sha256Hex(refreshToken);
        return bucketProvider.resolve(RateLimitKey.refresh(hash)).tryConsume(1L);
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
