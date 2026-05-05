package com.surense.infra.error;

import com.surense.infra.i18n.MessageResolver;
import com.surense.infra.logging.CorrelationIdConstants;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Builds {@link ErrorResponse} instances with the same field population rules
 * as {@link GlobalExceptionHandler} — shared so servlet filters (429 rate limit)
 * and {@code @RestControllerAdvice} return byte-identical JSON.
 */
@Component
@RequiredArgsConstructor
public class ErrorResponseFactory {

    /** Empty placeholder for {@link MessageResolver#resolve(ErrorCode, Object...)}. */
    public static final Object[] NO_ARGS = new Object[0];

    private final MessageResolver messageResolver;

    public ErrorResponse build(ErrorCode code,
                               String requestPath,
                               Object[] messageArgs,
                               List<ErrorResponse.FieldError> fieldErrors) {
        Object[] args = messageArgs == null ? NO_ARGS : messageArgs;
        return new ErrorResponse(
                Instant.now(),
                code.getStatus().value(),
                code.getStatus().getReasonPhrase(),
                code.name(),
                messageResolver.resolve(code, args),
                requestPath,
                MDC.get(CorrelationIdConstants.MDC_KEY),
                fieldErrors);
    }
}
