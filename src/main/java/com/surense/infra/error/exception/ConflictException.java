package com.surense.infra.error.exception;

import com.surense.infra.error.ErrorCode;

/**
 * Thrown when the request conflicts with existing state (e.g. duplicate
 * unique key, simultaneous-modification race). Maps to HTTP 409.
 *
 * <p>Note: error messages must NOT echo the conflicting value if it could be
 * PII. Use {@link #usernameTaken()} / {@link #emailTaken()} which produce
 * generic messages.
 */
public class ConflictException extends ApiException {

    public ConflictException(ErrorCode code, Object... args) {
        super(code, args);
    }

    public static ConflictException usernameTaken() {
        return new ConflictException(ErrorCode.USERNAME_ALREADY_EXISTS);
    }

    public static ConflictException emailTaken() {
        return new ConflictException(ErrorCode.EMAIL_ALREADY_EXISTS);
    }

    public static ConflictException generic() {
        return new ConflictException(ErrorCode.RESOURCE_CONFLICT);
    }

    public static ConflictException customerLoginAlreadyLinked() {
        return new ConflictException(ErrorCode.CUSTOMER_LOGIN_ALREADY_LINKED);
    }
}
