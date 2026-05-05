package com.surense.infra.error;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * Unified error response shape returned by every endpoint on every failure.
 *
 * <p>Field design notes:
 * <ul>
 *   <li>{@code timestamp} — UTC instant when the response was assembled.</li>
 *   <li>{@code status} / {@code error} — numeric HTTP status and standard
 *       reason phrase (e.g. {@code 404} / {@code "Not Found"}).</li>
 *   <li>{@code code} — stable enum-like machine-readable code from
 *       {@link ErrorCode}. Clients branch on this, NOT on {@code message}.</li>
 *   <li>{@code message} — human-readable, locale-resolved text from
 *       {@code messages.properties}. Never embeds PII (always references
 *       resources by id, never by name/email/etc.).</li>
 *   <li>{@code path} — request URI that produced the error (e.g.
 *       {@code /api/v1/customers/42}). Pure routing data, no PII.</li>
 *   <li>{@code correlationId} — same UUID echoed on the {@code X-Correlation-Id}
 *       response header and stamped into every server log line for the
 *       request. Pasting it into a log search returns the full request flow.</li>
 *   <li>{@code fieldErrors} — present only on validation failures (400). Lists
 *       the offending fields and their messages. {@code rejectedValue} is
 *       deliberately omitted because user-supplied values may be PII.</li>
 * </ul>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String code,
        String message,
        String path,
        String correlationId,
        List<FieldError> fieldErrors) {

    /** A single field-level validation problem. */
    public record FieldError(String field, String message) {}
}
