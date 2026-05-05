package com.surense.infra.ratelimit;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Per-(username + client IP) limiter for failed login attempts only.
 *
 * <p>{@link com.surense.service.auth.AuthService} calls {@link #recordFailedLogin(String, String)}
 * <strong>only after</strong> credentials have been rejected — successful logins
 * never consume tokens, which is deliberate (defensible in an interview as
 * "we throttle attackers, not users who typo once").
 */
@Component
@RequiredArgsConstructor
public class LoginRateLimiter {

    private final BucketProvider bucketProvider;

    /**
     * Records one failed login against the shared bucket for
     * {@code username + clientIp}.
     *
     * @return empty if the failure was recorded; if non-empty, the client is
     *         over the limit and the value is {@code Retry-After} seconds
     */
    public Optional<Long> recordFailedLogin(String username, String clientIp) {
        RateLimitKey key = RateLimitKey.login(username, clientIp);
        Bucket bucket = bucketProvider.resolve(key);
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1L);
        if (probe.isConsumed()) {
            return Optional.empty();
        }
        return Optional.of(
                RateLimitRetryAfter.toSeconds(probe.getNanosToWaitForRefill()));
    }
}
