package com.surense.common.ratelimit;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Per-(username + client IP) limiter for failed login attempts only.
 *
 * <p>Step 6's {@code AuthService} calls {@link #recordFailedAttempt(String, String)}
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
     * @return {@code true} if the failure is accepted under the limit,
     *         {@code false} if the caller must abort with HTTP 429 before doing
     *         further work (avoid distinguishing "bad password" from "rate
     *         limited" in timing if you choose uniform responses in Step 6).
     */
    public boolean recordFailedAttempt(String username, String clientIp) {
        RateLimitKey key = RateLimitKey.login(username, clientIp);
        return bucketProvider.resolve(key).tryConsume(1L);
    }
}
