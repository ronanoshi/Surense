package com.surense.infra.error.exception;

import com.surense.infra.error.ErrorCode;

/** Narrow authentication failures thrown by {@link com.surense.service.auth.AuthService}. */
public class AuthApiException extends ApiException {

    public AuthApiException(ErrorCode code) {
        super(code);
    }
}
