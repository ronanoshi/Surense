package com.surense.infra.ratelimit;

import io.github.bucket4j.TimeMeter;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

class LoginRateLimiterTest {

    static final class MockTimeMeter implements TimeMeter {
        private long nanos;

        void advance(Duration d) {
            nanos += d.toNanos();
        }

        @Override
        public long currentTimeNanos() {
            return nanos;
        }

        @Override
        public boolean isWallClockBased() {
            return false;
        }
    }

    private LoginRateLimiter limiter;

    @BeforeEach
    void setUp() {
        MockTimeMeter clock = new MockTimeMeter();
        RateLimitProperties props = new RateLimitProperties(
                100,
                Duration.ofHours(1),
                List.of(),
                true,
                new RateLimitProperties.LimitSpec(30, Duration.ofMinutes(1)),
                new RateLimitProperties.LimitSpec(60, Duration.ofMinutes(1)),
                new RateLimitProperties.LimitSpec(2, Duration.ofMinutes(15)),
                new RateLimitProperties.LimitSpec(10, Duration.ofMinutes(1)));
        BucketProvider provider = new BucketProvider(props, clock);
        limiter = new LoginRateLimiter(provider);
    }

    @Test
    void recordsFailedAttemptsUntilCapacity() {
        Assertions.assertThat(limiter.recordFailedLogin("alice", "1.2.3.4")).isEmpty();
        Assertions.assertThat(limiter.recordFailedLogin("alice", "1.2.3.4")).isEmpty();
        Assertions.assertThat(limiter.recordFailedLogin("alice", "1.2.3.4")).isPresent();
    }

    @Test
    void differentClientIpUsesSeparateBucket() {
        Assertions.assertThat(limiter.recordFailedLogin("alice", "1.2.3.4")).isEmpty();
        Assertions.assertThat(limiter.recordFailedLogin("alice", "1.2.3.4")).isEmpty();
        Assertions.assertThat(limiter.recordFailedLogin("alice", "1.2.3.4")).isPresent();

        Assertions.assertThat(limiter.recordFailedLogin("alice", "9.9.9.9")).isEmpty();
    }

    @Test
    void usernameIsCaseFoldedForKeying() {
        Assertions.assertThat(limiter.recordFailedLogin("Alice", "1.2.3.4")).isEmpty();
        Assertions.assertThat(limiter.recordFailedLogin("alice", "1.2.3.4")).isEmpty();
        Assertions.assertThat(limiter.recordFailedLogin("ALICE", "1.2.3.4")).isPresent();
    }
}
