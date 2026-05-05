package com.surense.infra.error;

import com.surense.infra.error.exception.ApiException;
import com.surense.infra.error.exception.RateLimitedException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.List;

/**
 * Centralized exception-to-response mapper for the entire API.
 *
 * <p>Every error path produces the same {@link ErrorResponse} JSON shape with
 * the correct HTTP status and a stable, machine-readable {@code code}. The
 * correlationId is read from MDC (populated by
 * {@link com.surense.infra.logging.CorrelationIdFilter}) so callers and
 * server logs can be cross-referenced for any single request.
 *
 * <h2>Resolution order (Spring picks the most specific match)</h2>
 * <ol>
 *   <li>{@link RateLimitedException} — 429 with {@code Retry-After} header.</li>
 *   <li>{@link ApiException} (and subclasses) — domain failures.</li>
 *   <li>Spring Security: {@link AccessDeniedException},
 *       {@link BadCredentialsException} — authentication / authorization.</li>
 *   <li>Spring web framework exceptions (validation, malformed body, etc.) —
 *       handled by overriding methods of {@link ResponseEntityExceptionHandler}.</li>
 *   <li>{@link Exception} — sanitized 500 catch-all that NEVER leaks internals
 *       to the client; full stack trace is logged with the correlationId.</li>
 * </ol>
 */
@RestControllerAdvice
@RequiredArgsConstructor
@Slf4j
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private final ErrorResponseFactory errorResponseFactory;

    // ---------- rate limiting ----------

    @ExceptionHandler(RateLimitedException.class)
    public ResponseEntity<ErrorResponse> handleRateLimited(RateLimitedException ex, HttpServletRequest req) {
        log.warn("error.rateLimited path={} retryAfterSec={}", req.getRequestURI(), ex.getRetryAfterSeconds());
        ErrorResponse body = errorResponseFactory.build(
                ErrorCode.RATE_LIMITED, req.getRequestURI(), ErrorResponseFactory.NO_ARGS, null);
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.RETRY_AFTER, Long.toString(ex.getRetryAfterSeconds()));
        return new ResponseEntity<>(body, headers, ErrorCode.RATE_LIMITED.getStatus());
    }

    // ---------- domain exceptions ----------

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApi(ApiException ex, HttpServletRequest req) {
        ErrorCode code = ex.getErrorCode();
        log.warn("error.handled code={} status={} path={}",
                code.name(), code.getStatus().value(), req.getRequestURI());
        return buildResponse(code, ex.getMessageArgs(), req.getRequestURI(), null);
    }

    // ---------- Spring Security exceptions ----------

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(
            AccessDeniedException ex, HttpServletRequest req) {
        log.warn("error.accessDenied path={}", req.getRequestURI());
        return buildResponse(ErrorCode.FORBIDDEN, ErrorResponseFactory.NO_ARGS, req.getRequestURI(), null);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(
            BadCredentialsException ex, HttpServletRequest req) {
        log.warn("error.badCredentials path={}", req.getRequestURI());
        return buildResponse(ErrorCode.BAD_CREDENTIALS, ErrorResponseFactory.NO_ARGS, req.getRequestURI(), null);
    }

    // ---------- Spring framework exceptions (overrides) ----------

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        List<ErrorResponse.FieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ErrorResponse.FieldError(
                        fe.getField(),
                        fe.getDefaultMessage() == null ? "invalid" : fe.getDefaultMessage()))
                .toList();
        log.warn("error.validation path={} fieldCount={}", path(request), fieldErrors.size());
        ErrorResponse body = errorResponseFactory.build(
                ErrorCode.VALIDATION_FAILED, path(request), ErrorResponseFactory.NO_ARGS, fieldErrors);
        return new ResponseEntity<>(body, ErrorCode.VALIDATION_FAILED.getStatus());
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        log.warn("error.malformedRequest path={}", path(request));
        ErrorResponse body = errorResponseFactory.build(
                ErrorCode.MALFORMED_REQUEST, path(request), ErrorResponseFactory.NO_ARGS, null);
        return new ResponseEntity<>(body, ErrorCode.MALFORMED_REQUEST.getStatus());
    }

    // ---------- catch-all ----------

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest req) {
        log.error("error.unhandled path={} exception={}",
                req.getRequestURI(), ex.getClass().getName(), ex);
        return buildResponse(ErrorCode.INTERNAL_ERROR, ErrorResponseFactory.NO_ARGS, req.getRequestURI(), null);
    }

    // ---------- helpers ----------

    private ResponseEntity<ErrorResponse> buildResponse(ErrorCode code,
                                                       Object[] args,
                                                       String path,
                                                       List<ErrorResponse.FieldError> fieldErrors) {
        Object[] safeArgs = args == null ? ErrorResponseFactory.NO_ARGS : args;
        ErrorResponse body = errorResponseFactory.build(code, path, safeArgs, fieldErrors);
        return new ResponseEntity<>(body, code.getStatus());
    }

    private String path(WebRequest request) {
        // ServletWebRequest produces "uri=/api/v1/foo"; strip the prefix.
        String description = request.getDescription(false);
        if (description != null && description.startsWith("uri=")) {
            return description.substring(4);
        }
        return description;
    }
}
