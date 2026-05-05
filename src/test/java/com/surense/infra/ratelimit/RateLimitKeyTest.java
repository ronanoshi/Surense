package com.surense.infra.ratelimit;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class RateLimitKeyTest {

    @Test
    void twoKeysWithSameTypeAndValueAreEqual() {
        Assertions.assertThat(RateLimitKey.unauthIp("1.2.3.4"))
                .isEqualTo(RateLimitKey.unauthIp("1.2.3.4"));
    }

    @Test
    void differentTypesProduceDifferentKeysEvenForSameValue() {
        // Important property: the IP "1.2.3.4" on the unauthenticated limiter
        // and the IP "1.2.3.4" inside a login key must NOT share a bucket.
        RateLimitKey ipKey = RateLimitKey.unauthIp("1.2.3.4");
        RateLimitKey loginKey = RateLimitKey.login("alice", "1.2.3.4");

        Assertions.assertThat(ipKey).isNotEqualTo(loginKey);
    }

    @Test
    void loginKeyLowercasesUsername() {
        RateLimitKey lower = RateLimitKey.login("alice", "1.2.3.4");
        RateLimitKey upper = RateLimitKey.login("ALICE", "1.2.3.4");

        Assertions.assertThat(lower).isEqualTo(upper);
    }

    @Test
    void blankValueRejected() {
        Assertions.assertThatThrownBy(() -> new RateLimitKey(LimitType.UNAUTH_IP, "  "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nullsRejected() {
        Assertions.assertThatThrownBy(() -> new RateLimitKey(null, "v"))
                .isInstanceOf(NullPointerException.class);
        Assertions.assertThatThrownBy(() -> new RateLimitKey(LimitType.UNAUTH_IP, null))
                .isInstanceOf(NullPointerException.class);
    }
}
