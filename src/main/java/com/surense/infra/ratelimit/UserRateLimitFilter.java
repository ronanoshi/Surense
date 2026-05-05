package com.surense.infra.ratelimit;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Post-security per-userId rate limiter (Option B). Registered on the Spring
 * Security filter chain after {@code AnonymousAuthenticationFilter} so
 * {@link SecurityContextHolder} reflects the final authentication decision
 * before we key a bucket.
 */
@RequiredArgsConstructor
@Slf4j
public class UserRateLimitFilter extends OncePerRequestFilter {

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
        if (RateLimitRequestInspector.matchesAnyExemptPath(request.getRequestURI(), props.exemptPaths())) {
            filterChain.doFilter(request, response);
            return;
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            filterChain.doFilter(request, response);
            return;
        }

        String userId = resolveUserId(auth);
        Bucket bucket = bucketProvider.resolve(RateLimitKey.authUser(userId));
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1L);
        if (probe.isConsumed()) {
            filterChain.doFilter(request, response);
            return;
        }

        long retryAfterSec = RateLimitRetryAfter.toSeconds(probe.getNanosToWaitForRefill());
        // TODO(step-13): throttle per-key so intentional floods don't spam WARN.
        log.warn("rateLimit.blocked scope=AUTH_USER path={} userId={} retryAfterSec={}",
                request.getRequestURI(), userId, retryAfterSec);
        rateLimitResponseWriter.writeTooManyRequests(request, response, retryAfterSec);
    }

    private static String resolveUserId(Authentication auth) {
        Object principal = auth.getPrincipal();
        if (principal instanceof UserDetails ud) {
            return ud.getUsername();
        }
        return auth.getName();
    }
}
