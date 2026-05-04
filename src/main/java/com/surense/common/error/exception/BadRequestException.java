package com.surense.common.error.exception;

import com.surense.common.error.ErrorCode;

/**
 * Thrown when the request is syntactically valid but semantically incorrect
 * (e.g. business-rule violation that isn't a validation-annotation issue).
 * Maps to HTTP 400.
 */
public class BadRequestException extends ApiException {

    public BadRequestException(ErrorCode code, Object... args) {
        super(code, args);
    }

    public static BadRequestException generic() {
        return new BadRequestException(ErrorCode.BAD_REQUEST);
    }
}
