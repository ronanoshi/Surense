package com.surense.infra.ratelimit;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ClientIpResolverTest {

    @Test
    void usesRemoteAddrWhenNoXffHeader() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("203.0.113.5");

        Assertions.assertThat(ClientIpResolver.resolve(req)).isEqualTo("203.0.113.5");
    }

    @Test
    void usesXffWhenPresent() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("10.0.0.1");
        req.addHeader(ClientIpResolver.XFF_HEADER, "203.0.113.5");

        Assertions.assertThat(ClientIpResolver.resolve(req)).isEqualTo("203.0.113.5");
    }

    @Test
    void usesFirstHopFromMultiHopXff() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("10.0.0.1");
        req.addHeader(ClientIpResolver.XFF_HEADER, "203.0.113.5, 198.51.100.7, 10.0.0.1");

        Assertions.assertThat(ClientIpResolver.resolve(req)).isEqualTo("203.0.113.5");
    }

    @Test
    void trimsWhitespaceAroundFirstHop() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("10.0.0.1");
        req.addHeader(ClientIpResolver.XFF_HEADER, "  203.0.113.5  , 198.51.100.7");

        Assertions.assertThat(ClientIpResolver.resolve(req)).isEqualTo("203.0.113.5");
    }

    @Test
    void blankXffFallsBackToRemoteAddr() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("10.0.0.1");
        req.addHeader(ClientIpResolver.XFF_HEADER, "   ");

        Assertions.assertThat(ClientIpResolver.resolve(req)).isEqualTo("10.0.0.1");
    }

    @Test
    void supportsIpv6InXff() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("10.0.0.1");
        req.addHeader(ClientIpResolver.XFF_HEADER, "2001:db8::1, 198.51.100.7");

        Assertions.assertThat(ClientIpResolver.resolve(req)).isEqualTo("2001:db8::1");
    }
}
