package com.surense.infra.ratelimit;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

class RateLimitRetryAfterTest {

    @Test
    void nonPositiveNanosYieldOneSecond() {
        Assertions.assertThat(RateLimitRetryAfter.toSeconds(-1)).isEqualTo(1);
        Assertions.assertThat(RateLimitRetryAfter.toSeconds(0)).isEqualTo(1);
    }

    @Test
    void subSecondWaitsRoundUpToOneSecond() {
        long halfSec = TimeUnit.MILLISECONDS.toNanos(500);
        Assertions.assertThat(RateLimitRetryAfter.toSeconds(halfSec)).isEqualTo(1);
    }

    @Test
    void exactWholeSecondsPreserved() {
        Assertions.assertThat(RateLimitRetryAfter.toSeconds(TimeUnit.SECONDS.toNanos(3))).isEqualTo(3);
    }

    @Test
    void fractionalSecondsCeilUp() {
        long nanos = TimeUnit.MILLISECONDS.toNanos(1500);
        Assertions.assertThat(RateLimitRetryAfter.toSeconds(nanos)).isEqualTo(2);
    }
}
