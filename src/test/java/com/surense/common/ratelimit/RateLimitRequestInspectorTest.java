package com.surense.common.ratelimit;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

class RateLimitRequestInspectorTest {

    @Test
    void detectsOptionsPreflight() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setMethod("OPTIONS");
        Assertions.assertThat(RateLimitRequestInspector.isCorsPreflight(req)).isTrue();
    }

    @Test
    void matchesHealthExemptPatterns() {
        List<String> patterns = List.of("/actuator/health", "/actuator/health/**");
        Assertions.assertThat(RateLimitRequestInspector.matchesAnyExemptPath("/actuator/health", patterns))
                .isTrue();
        Assertions.assertThat(RateLimitRequestInspector.matchesAnyExemptPath("/actuator/health/liveness", patterns))
                .isTrue();
        Assertions.assertThat(RateLimitRequestInspector.matchesAnyExemptPath("/__test__/boom", patterns))
                .isFalse();
    }

    @Test
    void detectsNonBlankAuthorizationHeader() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        Assertions.assertThat(RateLimitRequestInspector.hasNonBlankAuthorizationHeader(req)).isFalse();
        req.addHeader("Authorization", "Bearer unit-test-token");
        Assertions.assertThat(RateLimitRequestInspector.hasNonBlankAuthorizationHeader(req)).isTrue();
    }
}
