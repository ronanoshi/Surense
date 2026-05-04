package com.surense.common.error;

import com.surense.common.error.exception.BadRequestException;
import com.surense.common.error.exception.ConflictException;
import com.surense.common.error.exception.NotImplementedException;
import com.surense.common.error.exception.RateLimitedException;
import com.surense.common.error.exception.ResourceNotFoundException;
import com.surense.common.i18n.MessageResolver;
import com.surense.common.logging.CorrelationIdConstants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        ResourceBundleMessageSource ms = new ResourceBundleMessageSource();
        ms.setBasename("messages");
        ms.setDefaultEncoding("UTF-8");
        ms.setUseCodeAsDefaultMessage(false);
        MessageResolver messageResolver = new MessageResolver(ms);
        handler = new GlobalExceptionHandler(new ErrorResponseFactory(messageResolver));
        LocaleContextHolder.setLocale(Locale.ENGLISH);
        MDC.put(CorrelationIdConstants.MDC_KEY, "test-corr-id");
    }

    @AfterEach
    void tearDown() {
        LocaleContextHolder.resetLocaleContext();
        MDC.clear();
    }

    @Test
    void handleRateLimited_maps429WithRetryAfterHeader() {
        RateLimitedException ex = new RateLimitedException(42);
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/foo");

        ResponseEntity<ErrorResponse> resp = handler.handleRateLimited(ex, req);

        assertThat(resp.getStatusCode().value()).isEqualTo(429);
        assertThat(resp.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("42");
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().code()).isEqualTo("RATE_LIMITED");
        assertThat(resp.getBody().message()).isEqualTo("Too many requests; please try again later");
        assertThat(resp.getBody().path()).isEqualTo("/api/v1/foo");
        assertThat(resp.getBody().correlationId()).isEqualTo("test-corr-id");
    }

    @Test
    void handleApi_resourceNotFound_maps404WithCorrectCodeAndMessage() {
        ResourceNotFoundException ex = ResourceNotFoundException.user(42);
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/customers/42");

        ResponseEntity<ErrorResponse> resp = handler.handleApi(ex, req);

        assertThat(resp.getStatusCode().value()).isEqualTo(404);
        ErrorResponse body = resp.getBody();
        assertThat(body).isNotNull();
        assertThat(body.code()).isEqualTo("USER_NOT_FOUND");
        assertThat(body.status()).isEqualTo(404);
        assertThat(body.error()).isEqualTo("Not Found");
        assertThat(body.message()).isEqualTo("User with id 42 was not found");
        assertThat(body.path()).isEqualTo("/api/v1/customers/42");
        assertThat(body.correlationId()).isEqualTo("test-corr-id");
        assertThat(body.fieldErrors()).isNull();
        assertThat(body.timestamp()).isNotNull();
    }

    @Test
    void handleApi_conflict_maps409WithGenericMessage() {
        ConflictException ex = ConflictException.usernameTaken();
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/customers");

        ResponseEntity<ErrorResponse> resp = handler.handleApi(ex, req);

        assertThat(resp.getStatusCode().value()).isEqualTo(409);
        assertThat(resp.getBody().code()).isEqualTo("USERNAME_ALREADY_EXISTS");
        // PII safety: message must NOT echo the conflicting username value.
        assertThat(resp.getBody().message()).isEqualTo("Username is already taken");
    }

    @Test
    void handleApi_notImplemented_maps501() {
        NotImplementedException ex = new NotImplementedException();
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/agents");

        ResponseEntity<ErrorResponse> resp = handler.handleApi(ex, req);

        assertThat(resp.getStatusCode().value()).isEqualTo(501);
        assertThat(resp.getBody().code()).isEqualTo("NOT_IMPLEMENTED");
        assertThat(resp.getBody().message()).isEqualTo("This endpoint is not yet implemented");
    }

    @Test
    void handleApi_badRequest_maps400() {
        BadRequestException ex = BadRequestException.generic();
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/tickets");

        ResponseEntity<ErrorResponse> resp = handler.handleApi(ex, req);

        assertThat(resp.getStatusCode().value()).isEqualTo(400);
        assertThat(resp.getBody().code()).isEqualTo("BAD_REQUEST");
    }

    @Test
    void handleAccessDenied_maps403() {
        AccessDeniedException ex = new AccessDeniedException("denied");
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/customers");

        ResponseEntity<ErrorResponse> resp = handler.handleAccessDenied(ex, req);

        assertThat(resp.getStatusCode().value()).isEqualTo(403);
        assertThat(resp.getBody().code()).isEqualTo("FORBIDDEN");
        assertThat(resp.getBody().message()).isEqualTo("You do not have permission to perform this action");
    }

    @Test
    void handleBadCredentials_maps401() {
        BadCredentialsException ex = new BadCredentialsException("bad");
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/auth/login");

        ResponseEntity<ErrorResponse> resp = handler.handleBadCredentials(ex, req);

        assertThat(resp.getStatusCode().value()).isEqualTo(401);
        assertThat(resp.getBody().code()).isEqualTo("BAD_CREDENTIALS");
    }

    @Test
    void handleUnexpected_sanitizesTo500WithoutLeakingInternals() {
        IllegalStateException ex = new IllegalStateException(
                "internal SQL exploded: connection to user='admin' password='secret123' lost");
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/tickets/3");

        ResponseEntity<ErrorResponse> resp = handler.handleUnexpected(ex, req);

        assertThat(resp.getStatusCode().value()).isEqualTo(500);
        ErrorResponse body = resp.getBody();
        assertThat(body.code()).isEqualTo("INTERNAL_ERROR");
        assertThat(body.message()).isEqualTo("An unexpected error occurred");
        // Critical: client must NEVER see the original exception text.
        assertThat(body.message()).doesNotContain("SQL", "password", "secret123", "admin");
    }

    @Test
    void responseAlwaysCarriesCurrentCorrelationId() {
        MDC.put(CorrelationIdConstants.MDC_KEY, "another-corr-id");
        ResourceNotFoundException ex = ResourceNotFoundException.ticket(7);
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/tickets/7");

        ResponseEntity<ErrorResponse> resp = handler.handleApi(ex, req);

        assertThat(resp.getBody().correlationId()).isEqualTo("another-corr-id");
    }
}
