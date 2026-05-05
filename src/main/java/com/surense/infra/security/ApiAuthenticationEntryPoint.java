package com.surense.infra.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.surense.infra.error.ErrorCode;
import com.surense.infra.error.ErrorResponse;
import com.surense.infra.error.ErrorResponseFactory;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * JSON 401 responses for API routes when authentication is missing or invalid.
 */
@Component
@RequiredArgsConstructor
public class ApiAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ErrorResponseFactory errorResponseFactory;
    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) {
        ErrorCode code = ErrorCode.INVALID_TOKEN;
        if (authException.getCause() instanceof ExpiredJwtException) {
            code = ErrorCode.TOKEN_EXPIRED;
        }
        ErrorResponse body = errorResponseFactory.build(code, request.getRequestURI(),
                ErrorResponseFactory.NO_ARGS, null);
        response.setStatus(code.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        try {
            objectMapper.writeValue(response.getOutputStream(), body);
        } catch (Exception ignored) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        }
    }
}
