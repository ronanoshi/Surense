package com.surense.infra.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.surense.infra.error.ErrorCode;
import com.surense.infra.error.ErrorResponse;
import com.surense.infra.error.ErrorResponseFactory;
import com.surense.infra.persistence.auth.entity.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Stateless bearer JWT authentication for {@code /api/v1/**} routes (except
 * {@code /api/v1/auth/*}). Runs before {@code AnonymousAuthenticationFilter}.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenService jwtTokenService;
    private final ErrorResponseFactory errorResponseFactory;
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (!path.startsWith("/api/v1/")) {
            return true;
        }
        return path.startsWith("/api/v1/auth/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws IOException, jakarta.servlet.ServletException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(header) || !header.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }
        String raw = header.substring(BEARER_PREFIX.length()).trim();
        if (!StringUtils.hasText(raw)) {
            filterChain.doFilter(request, response);
            return;
        }
        try {
            Claims claims = jwtTokenService.parseAndValidate(raw);
            long userId = jwtTokenService.parseUserId(claims);
            String username = claims.get("username", String.class);
            String roleName = claims.get("role", String.class);
            if (username == null || roleName == null) {
                writeUnauthorized(request, response, ErrorCode.INVALID_TOKEN);
                return;
            }
            UserRole role = UserRole.valueOf(roleName);
            UserPrincipal principal = new UserPrincipal(userId, username, role);
            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(auth);
        } catch (IllegalArgumentException e) {
            writeUnauthorized(request, response, ErrorCode.INVALID_TOKEN);
            return;
        } catch (ExpiredJwtException e) {
            writeUnauthorized(request, response, ErrorCode.TOKEN_EXPIRED);
            return;
        } catch (JwtException e) {
            writeUnauthorized(request, response, ErrorCode.INVALID_TOKEN);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private void writeUnauthorized(HttpServletRequest request,
                                   HttpServletResponse response,
                                   ErrorCode code) throws IOException {
        ErrorResponse body = errorResponseFactory.build(code, request.getRequestURI(),
                ErrorResponseFactory.NO_ARGS, null);
        response.setStatus(code.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
