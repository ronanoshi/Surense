package com.surense.infra.ratelimit;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Resolves the client IP for rate-limiting purposes.
 *
 * <p>Strategy: prefer the first entry of {@code X-Forwarded-For}, then fall
 * back to {@link HttpServletRequest#getRemoteAddr()}. This is documented as
 * an explicit deployment assumption in the README — when no trusted reverse
 * proxy strips/rewrites the header, clients can spoof their reported IP.
 *
 * <p>Used only for rate limiting; auth/identity decisions never look at this
 * value.
 */
public final class ClientIpResolver {

    static final String XFF_HEADER = "X-Forwarded-For";

    private ClientIpResolver() {
        // utility class
    }

    public static String resolve(HttpServletRequest request) {
        String xff = request.getHeader(XFF_HEADER);
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            String first = (comma >= 0 ? xff.substring(0, comma) : xff).trim();
            if (!first.isEmpty()) {
                return first;
            }
        }
        return request.getRemoteAddr();
    }
}
