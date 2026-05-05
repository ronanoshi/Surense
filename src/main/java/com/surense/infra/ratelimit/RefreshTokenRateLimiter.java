package com.surense.infra.ratelimit;

import com.surense.infra.security.TokenHasher;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

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
     * @return empty if allowed; if present, rate limited — value is
     *         {@code Retry-After} seconds
     */
    public Optional<Long> tryConsumeRefresh(String refreshToken) {
        String hash = TokenHasher.sha256Hex(refreshToken);
        Bucket bucket = bucketProvider.resolve(RateLimitKey.refresh(hash));
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1L);
        if (probe.isConsumed()) {
            return Optional.empty();
        }
        return Optional.of(RateLimitRetryAfter.toSeconds(probe.getNanosToWaitForRefill()));
    }
}
