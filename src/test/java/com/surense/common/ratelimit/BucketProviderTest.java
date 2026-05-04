package com.surense.common.ratelimit;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.TimeMeter;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

class BucketProviderTest {

    /** Controllable clock so refill behavior is deterministic. */
    static final class MockTimeMeter implements TimeMeter {
        private long nanos = 0L;

        public void advance(Duration d) {
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

    private MockTimeMeter clock;
    private BucketProvider provider;

    @BeforeEach
    void setUp() {
        clock = new MockTimeMeter();
        // Small, easy-to-reason-about limits for the unit test.
        RateLimitProperties props = new RateLimitProperties(
                100,
                Duration.ofHours(1),
                List.of(),
                true,
                new RateLimitProperties.LimitSpec(3, Duration.ofMinutes(1)),   // unauth-ip
                new RateLimitProperties.LimitSpec(5, Duration.ofMinutes(1)),   // auth-user
                new RateLimitProperties.LimitSpec(2, Duration.ofMinutes(15)),  // login
                new RateLimitProperties.LimitSpec(4, Duration.ofMinutes(1))    // refresh
        );
        provider = new BucketProvider(props, clock);
    }

    @Test
    void sameKeyReturnsSameBucketInstance() {
        Bucket b1 = provider.resolve(RateLimitKey.unauthIp("1.2.3.4"));
        Bucket b2 = provider.resolve(RateLimitKey.unauthIp("1.2.3.4"));

        Assertions.assertThat(b1).isSameAs(b2);
    }

    @Test
    void differentValuesUnderSameTypeAreIndependent() {
        Bucket alice = provider.resolve(RateLimitKey.unauthIp("1.2.3.4"));
        Bucket bob = provider.resolve(RateLimitKey.unauthIp("5.6.7.8"));

        // Drain alice's bucket entirely; bob should still have full capacity.
        Assertions.assertThat(alice.tryConsume(3)).isTrue();
        Assertions.assertThat(alice.tryConsume(1)).isFalse();

        Assertions.assertThat(bob.tryConsume(3)).isTrue();
    }

    @Test
    void differentTypesAreIndependent() {
        // Same string value, different limit type → separate buckets.
        Bucket ipBucket = provider.resolve(new RateLimitKey(LimitType.UNAUTH_IP, "shared"));
        Bucket userBucket = provider.resolve(new RateLimitKey(LimitType.AUTH_USER, "shared"));

        Assertions.assertThat(ipBucket).isNotSameAs(userBucket);
    }

    @Test
    void capacityIsRespected() {
        Bucket bucket = provider.resolve(RateLimitKey.unauthIp("1.2.3.4"));

        for (int i = 0; i < 3; i++) {
            Assertions.assertThat(bucket.tryConsume(1))
                    .as("token %d should be granted", i + 1)
                    .isTrue();
        }
        Assertions.assertThat(bucket.tryConsume(1))
                .as("4th token should be rejected")
                .isFalse();
    }

    @Test
    void refillIsTimeDriven() {
        Bucket bucket = provider.resolve(RateLimitKey.unauthIp("1.2.3.4"));
        // Drain it.
        bucket.tryConsume(3);
        Assertions.assertThat(bucket.tryConsume(1)).isFalse();

        // Advance past the full refill window — bucket should be back to capacity.
        clock.advance(Duration.ofMinutes(1));
        for (int i = 0; i < 3; i++) {
            Assertions.assertThat(bucket.tryConsume(1)).isTrue();
        }
        Assertions.assertThat(bucket.tryConsume(1)).isFalse();
    }

    @Test
    void capacityVariesByLimitType() {
        Bucket ip = provider.resolve(new RateLimitKey(LimitType.UNAUTH_IP, "x"));      // cap 3
        Bucket user = provider.resolve(new RateLimitKey(LimitType.AUTH_USER, "x"));    // cap 5
        Bucket login = provider.resolve(new RateLimitKey(LimitType.LOGIN, "x"));       // cap 2
        Bucket refresh = provider.resolve(new RateLimitKey(LimitType.REFRESH, "x"));   // cap 4

        // Each bucket starts at its own capacity; consuming N+1 should fail.
        Assertions.assertThat(ip.tryConsume(3)).isTrue();
        Assertions.assertThat(ip.tryConsume(1)).isFalse();

        Assertions.assertThat(user.tryConsume(5)).isTrue();
        Assertions.assertThat(user.tryConsume(1)).isFalse();

        Assertions.assertThat(login.tryConsume(2)).isTrue();
        Assertions.assertThat(login.tryConsume(1)).isFalse();

        Assertions.assertThat(refresh.tryConsume(4)).isTrue();
        Assertions.assertThat(refresh.tryConsume(1)).isFalse();
    }
}
