package com.surense.common.error;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * The complete set of error codes returned by the API.
 *
 * <p>Each code carries:
 * <ul>
 *   <li>The HTTP status to return on the response.</li>
 *   <li>The {@code messages.properties} key used to resolve the human-readable
 *       (and possibly localized) message.</li>
 * </ul>
 *
 * <p>The string form of the enum constant (e.g. {@code "USER_NOT_FOUND"}) is
 * the stable, machine-readable {@code code} field in {@code ErrorResponse}.
 * Clients should branch on this value, never on the message text.
 */
@Getter
public enum ErrorCode {

    // 400 — bad request / validation
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "error.validation.failed"),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "error.request.malformed"),
    BAD_REQUEST(HttpStatus.BAD_REQUEST, "error.request.bad"),

    // 401 — authentication
    BAD_CREDENTIALS(HttpStatus.UNAUTHORIZED, "error.auth.badCredentials"),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "error.auth.invalidToken"),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "error.auth.tokenExpired"),
    REFRESH_TOKEN_REUSED(HttpStatus.UNAUTHORIZED, "error.auth.refreshReused"),

    // 403 — authorization
    FORBIDDEN(HttpStatus.FORBIDDEN, "error.auth.forbidden"),

    // 404 — not found
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "error.user.notFound"),
    TICKET_NOT_FOUND(HttpStatus.NOT_FOUND, "error.ticket.notFound"),
    CUSTOMER_NOT_FOUND(HttpStatus.NOT_FOUND, "error.customer.notFound"),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "error.resource.notFound"),

    // 409 — conflict
    USERNAME_ALREADY_EXISTS(HttpStatus.CONFLICT, "error.user.usernameTaken"),
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "error.user.emailTaken"),
    RESOURCE_CONFLICT(HttpStatus.CONFLICT, "error.resource.conflict"),

    // 429 — rate limiting (used Step 4b)
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "error.rate.limited"),

    // 501 — not implemented (stubs)
    NOT_IMPLEMENTED(HttpStatus.NOT_IMPLEMENTED, "error.notImplemented"),

    // 500 — catch-all (sanitized, no internal detail leaked)
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "error.internal");

    private final HttpStatus status;
    private final String messageKey;

    ErrorCode(HttpStatus status, String messageKey) {
        this.status = status;
        this.messageKey = messageKey;
    }
}
