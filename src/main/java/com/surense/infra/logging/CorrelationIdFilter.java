package com.surense.infra.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Reads or generates a correlation id for every incoming request and propagates
 * it through SLF4J's MDC so every log line for the request can be correlated.
 *
 * <ul>
 *   <li>If the request carries a non-blank {@code X-Correlation-Id} header, that
 *       value is used (allowing upstream services to set their own id).</li>
 *   <li>Otherwise a fresh UUID v4 is generated.</li>
 *   <li>The id is set on MDC for the whole request lifetime and echoed back on
 *       the response header so callers know which id was used.</li>
 *   <li>MDC is always cleared in {@code finally} to prevent leakage across
 *       reused worker threads.</li>
 * </ul>
 *
 * <p>Registered at {@link Ordered#HIGHEST_PRECEDENCE} so it runs before the
 * Spring Security filter chain, ensuring even unauthenticated rejections carry
 * a correlation id.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String correlationId = resolveCorrelationId(request);
        try {
            MDC.put(CorrelationIdConstants.MDC_KEY, correlationId);
            response.setHeader(CorrelationIdConstants.HEADER, correlationId);
            chain.doFilter(request, response);
        } finally {
            MDC.remove(CorrelationIdConstants.MDC_KEY);
        }
    }

    private String resolveCorrelationId(HttpServletRequest request) {
        String supplied = request.getHeader(CorrelationIdConstants.HEADER);
        if (supplied != null && !supplied.isBlank()) {
            return supplied.trim();
        }
        return UUID.randomUUID().toString();
    }
}
