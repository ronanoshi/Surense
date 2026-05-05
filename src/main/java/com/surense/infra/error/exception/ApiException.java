package com.surense.infra.error.exception;

import com.surense.infra.error.ErrorCode;
import lombok.Getter;

/**
 * Base class for all domain exceptions thrown by the application.
 *
 * <p>Carries an {@link ErrorCode} (which dictates HTTP status and the
 * {@code messages.properties} key) and optional message arguments used for
 * placeholder substitution (e.g. {@code "User with id {0} not found"}).
 *
 * <p>Domain code throws subclasses (e.g. {@link ResourceNotFoundException});
 * the global exception handler converts them into the unified
 * {@code ErrorResponse} JSON.
 */
@Getter
public abstract class ApiException extends RuntimeException {

    private final ErrorCode errorCode;
    private final transient Object[] messageArgs;

    protected ApiException(ErrorCode errorCode, Object... messageArgs) {
        super(errorCode.getMessageKey());
        this.errorCode = errorCode;
        this.messageArgs = messageArgs;
    }

    protected ApiException(ErrorCode errorCode, Throwable cause, Object... messageArgs) {
        super(errorCode.getMessageKey(), cause);
        this.errorCode = errorCode;
        this.messageArgs = messageArgs;
    }
}
