package com.surense.infra.logging;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @BeforeEach
    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void usesSuppliedCorrelationIdWhenHeaderPresent() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        req.addHeader(CorrelationIdConstants.HEADER, "supplied-corr-id");

        filter.doFilter(req, res, chain);

        assertThat(res.getHeader(CorrelationIdConstants.HEADER)).isEqualTo("supplied-corr-id");
        verify(chain, times(1)).doFilter(req, res);
    }

    @Test
    void generatesUuidWhenHeaderMissing() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        String header = res.getHeader(CorrelationIdConstants.HEADER);
        assertThat(header).isNotBlank();
        // Validates v4-style UUID shape; throws if the value is not a UUID at all.
        assertThat(UUID.fromString(header)).isNotNull();
    }

    @Test
    void generatesUuidWhenHeaderBlank() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        req.addHeader(CorrelationIdConstants.HEADER, "   ");

        filter.doFilter(req, res, chain);

        String header = res.getHeader(CorrelationIdConstants.HEADER);
        assertThat(header).isNotBlank();
        assertThat(header.trim()).isNotEmpty();
        assertThat(UUID.fromString(header)).isNotNull();
    }

    @Test
    void clearsMdcAfterRequest() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        assertThat(MDC.get(CorrelationIdConstants.MDC_KEY)).isNull();
    }

    @Test
    void mdcContainsCorrelationIdDuringRequest() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse res = new MockHttpServletResponse();
        req.addHeader(CorrelationIdConstants.HEADER, "during-request-id");
        AtomicReference<String> seenInsideChain = new AtomicReference<>();
        FilterChain chain = (request, response) -> seenInsideChain.set(MDC.get(CorrelationIdConstants.MDC_KEY));

        filter.doFilter(req, res, chain);

        assertThat(seenInsideChain.get()).isEqualTo("during-request-id");
        assertThat(MDC.get(CorrelationIdConstants.MDC_KEY)).isNull();
    }

    @Test
    void clearsMdcEvenWhenChainThrows() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = (request, response) -> {
            throw new RuntimeException("downstream failure");
        };

        try {
            filter.doFilter(req, res, chain);
        } catch (Exception ignored) {
            // expected
        }

        assertThat(MDC.get(CorrelationIdConstants.MDC_KEY)).isNull();
    }
}
