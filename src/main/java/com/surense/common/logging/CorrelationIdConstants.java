package com.surense.common.logging;

/**
 * Constants for correlation-id and MDC propagation across the request pipeline.
 *
 * <p>The correlation id is the central tool for tracing a single request across
 * every log line it produces. It is read from (or generated for) every incoming
 * HTTP request, echoed back on the response, and placed on SLF4J's MDC so that
 * the configured Logback pattern / JSON encoder includes it on every log entry.
 */
public final class CorrelationIdConstants {

    /** HTTP header carrying the correlation id (request and response). */
    public static final String HEADER = "X-Correlation-Id";

    /** MDC key holding the correlation id for the current request. */
    public static final String MDC_KEY = "correlationId";

    /** MDC key holding the authenticated user id. Populated in Step 6. */
    public static final String MDC_USER_ID = "userId";

    /** MDC key holding the authenticated user role. Populated in Step 6. */
    public static final String MDC_USER_ROLE = "userRole";

    private CorrelationIdConstants() {
        // utility class
    }
}
