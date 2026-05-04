package com.surense.common.ratelimit;

import io.github.bucket4j.TimeMeter;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

class RefreshTokenRateLimiterTest {

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

    private RefreshTokenRateLimiter limiter;

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
                new RateLimitProperties.LimitSpec(5, Duration.ofMinutes(15)),
                new RateLimitProperties.LimitSpec(2, Duration.ofMinutes(1)));
        BucketProvider provider = new BucketProvider(props, clock);
        limiter = new RefreshTokenRateLimiter(provider);
    }

    @Test
    void sameTokenSharesBucket() {
        Assertions.assertThat(limiter.tryConsume("opaque-refresh-token-1")).isTrue();
        Assertions.assertThat(limiter.tryConsume("opaque-refresh-token-1")).isTrue();
        Assertions.assertThat(limiter.tryConsume("opaque-refresh-token-1")).isFalse();
    }

    @Test
    void differentTokensAreIndependent() {
        Assertions.assertThat(limiter.tryConsume("token-a")).isTrue();
        Assertions.assertThat(limiter.tryConsume("token-a")).isTrue();
        Assertions.assertThat(limiter.tryConsume("token-a")).isFalse();

        Assertions.assertThat(limiter.tryConsume("token-b")).isTrue();
    }
}
