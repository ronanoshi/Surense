package com.surense._dev;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end test of the cross-cutting infrastructure: each request flows
 * through the real {@code CorrelationIdFilter} → Spring Security filter chain
 * → {@code BoomController} → {@code GlobalExceptionHandler}, producing the
 * unified {@code ErrorResponse} JSON for every error type.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "surense.dev.boom-endpoint.enabled=true")
class BoomControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void ok_returns200() throws Exception {
        mockMvc.perform(get("/__test__/boom").param("type", "ok"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Correlation-Id"))
                .andExpect(jsonPath("$.ok").value(true));
    }

    @Test
    void echoesSuppliedCorrelationId() throws Exception {
        mockMvc.perform(get("/__test__/boom")
                        .param("type", "ok")
                        .header("X-Correlation-Id", "supplied-corr-12345"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Correlation-Id", "supplied-corr-12345"));
    }

    @Test
    void notfound_returns404WithUnifiedErrorResponse() throws Exception {
        mockMvc.perform(get("/__test__/boom").param("type", "notfound"))
                .andExpect(status().isNotFound())
                .andExpect(header().exists("X-Correlation-Id"))
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("User with id 42 was not found"))
                .andExpect(jsonPath("$.path").value("/__test__/boom"))
                .andExpect(jsonPath("$.correlationId").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors").doesNotExist());
    }

    @Test
    void conflict_returns409WithoutEchoingPiiValue() throws Exception {
        mockMvc.perform(get("/__test__/boom").param("type", "conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USERNAME_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.message").value("Username is already taken"));
    }

    @Test
    void badRequest_returns400() throws Exception {
        mockMvc.perform(get("/__test__/boom").param("type", "badrequest"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void notImplemented_returns501() throws Exception {
        mockMvc.perform(get("/__test__/boom").param("type", "notimplemented"))
                .andExpect(status().isNotImplemented())
                .andExpect(jsonPath("$.code").value("NOT_IMPLEMENTED"))
                .andExpect(jsonPath("$.message").value("This endpoint is not yet implemented"));
    }

    @Test
    void unhandled_returns500WithSanitizedMessage() throws Exception {
        mockMvc.perform(get("/__test__/boom").param("type", "unhandled"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"))
                // PII safety: the original IllegalStateException text contained
                // password=... and email=... — these MUST NOT leak to the client.
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret123"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("victim@example.com"))));
    }

    @Test
    void validation_returns400WithFieldErrors() throws Exception {
        mockMvc.perform(post("/__test__/boom/validate")
                        .contentType("application/json")
                        .content("{\"name\":\"\",\"email\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors").isArray())
                .andExpect(jsonPath("$.fieldErrors.length()").value(2));
    }

    @Test
    void malformedJson_returns400() throws Exception {
        mockMvc.perform(post("/__test__/boom/validate")
                        .contentType("application/json")
                        .content("{ this is not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }
}
