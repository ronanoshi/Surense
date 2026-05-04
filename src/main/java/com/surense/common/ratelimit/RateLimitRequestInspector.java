package com.surense.common.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.util.AntPathMatcher;

import java.util.List;

/**
 * Shared request inspection for the rate-limit servlet filters.
 */
public final class RateLimitRequestInspector {

    private static final AntPathMatcher PATH = new AntPathMatcher();

    private RateLimitRequestInspector() {
        // utility class
    }

    public static boolean isCorsPreflight(HttpServletRequest request) {
        return "OPTIONS".equalsIgnoreCase(request.getMethod());
    }

    public static boolean matchesAnyExemptPath(String requestUri, List<String> antPatterns) {
        if (requestUri == null) {
            return false;
        }
        for (String pattern : antPatterns) {
            if (PATH.match(pattern, requestUri)) {
                return true;
            }
        }
        return false;
    }

    /**
     * When any {@code Authorization} header is present, the pre-security IP
     * limiter skips — the post-security user limiter (Step 6 JWT) owns the
     * budget for credentialed traffic. See README for the trust / bypass note.
     */
    public static boolean hasNonBlankAuthorizationHeader(HttpServletRequest request) {
        String auth = request.getHeader(HttpHeaders.AUTHORIZATION);
        return auth != null && !auth.isBlank();
    }
}
