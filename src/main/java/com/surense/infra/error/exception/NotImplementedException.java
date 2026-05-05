package com.surense.infra.error.exception;

import com.surense.infra.error.ErrorCode;

/**
 * Thrown by stub endpoints whose contract is declared but whose implementation
 * is deferred. Maps to HTTP 501.
 */
public class NotImplementedException extends ApiException {

    public NotImplementedException() {
        super(ErrorCode.NOT_IMPLEMENTED);
    }
}
