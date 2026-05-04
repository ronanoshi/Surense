package com.surense.common.ratelimit;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Pre-security per-IP rate limiter (Option B). Runs immediately after
 * {@link com.surense.common.logging.CorrelationIdFilter} so every 429 log line
 * carries {@code correlationId} on MDC.
 *
 * <p>Skips requests that already carry an {@code Authorization} header so
 * credentialed traffic is governed by {@link UserRateLimitFilter} instead
 * (Step 6 JWT). See README for rationale and the small anonymous-bypass caveat.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@RequiredArgsConstructor
@Slf4j
public class IpRateLimitFilter extends OncePerRequestFilter {

    private final RateLimitProperties props;
    private final BucketProvider bucketProvider;
    private final RateLimitResponseWriter rateLimitResponseWriter;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!props.enabled()) {
            filterChain.doFilter(request, response);
            return;
        }
        if (RateLimitRequestInspector.isCorsPreflight(request)) {
            filterChain.doFilter(request, response);
            return;
        }
        if (RateLimitRequestInspector.matchesAnyExemptPath(request.getRequestURI(), props.exemptPaths())
                || RateLimitRequestInspector.hasNonBlankAuthorizationHeader(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = ClientIpResolver.resolve(request);
        Bucket bucket = bucketProvider.resolve(RateLimitKey.unauthIp(clientIp));
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1L);
        if (probe.isConsumed()) {
            filterChain.doFilter(request, response);
            return;
        }

        long retryAfterSec = RateLimitRetryAfter.toSeconds(probe.getNanosToWaitForRefill());
        // TODO(step-13): throttle per-key so intentional floods don't spam WARN.
        log.warn("rateLimit.blocked scope=UNAUTH_IP path={} clientIp={} retryAfterSec={}",
                request.getRequestURI(), clientIp, retryAfterSec);
        rateLimitResponseWriter.writeTooManyRequests(request, response, retryAfterSec);
    }
}
