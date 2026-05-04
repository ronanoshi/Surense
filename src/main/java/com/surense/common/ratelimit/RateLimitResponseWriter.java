package com.surense.common.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.surense.common.error.ErrorCode;
import com.surense.common.error.ErrorResponse;
import com.surense.common.error.ErrorResponseFactory;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Writes a 429 response with the unified {@link ErrorResponse} JSON body and
 * a {@code Retry-After} header — shared by {@link IpRateLimitFilter} and
 * {@link UserRateLimitFilter}.
 */
@Component
@RequiredArgsConstructor
public class RateLimitResponseWriter {

    private final ErrorResponseFactory errorResponseFactory;
    private final ObjectMapper objectMapper;

    public void writeTooManyRequests(HttpServletRequest request,
                                     HttpServletResponse response,
                                     long retryAfterSeconds) throws IOException {
        ErrorResponse body = errorResponseFactory.build(
                ErrorCode.RATE_LIMITED,
                request.getRequestURI(),
                ErrorResponseFactory.NO_ARGS,
                null);
        response.resetBuffer();
        response.setStatus(ErrorCode.RATE_LIMITED.getStatus().value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds));
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
