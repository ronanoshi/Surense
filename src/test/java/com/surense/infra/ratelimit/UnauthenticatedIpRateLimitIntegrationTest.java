package com.surense.infra.ratelimit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Opt-in rate-limit integration suite. The {@code test} profile disables the
 * servlet limiters by default so other {@code @SpringBootTest} classes do not
 * share an exhausted in-memory bucket — this class re-enables them explicitly.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "surense.rate-limit.enabled=true")
class UnauthenticatedIpRateLimitIntegrationTest {

    private static final String RATE_LIMIT_PROBE_PATH = "/api/v1/customers";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void unauthIpLimiter_blocks31stUnauthenticatedApiRequestWith429UnifiedBodyAndRetryAfter() throws Exception {
        for (int i = 0; i < 30; i++) {
            mockMvc.perform(get(RATE_LIMIT_PROBE_PATH))
                    .andExpect(status().isUnauthorized());
        }
        mockMvc.perform(get(RATE_LIMIT_PROBE_PATH))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(header().exists("X-Correlation-Id"))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.error").value("Too Many Requests"))
                .andExpect(jsonPath("$.message").value("Too many requests; please try again later"))
                .andExpect(jsonPath("$.path").value(RATE_LIMIT_PROBE_PATH))
                .andExpect(jsonPath("$.correlationId").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors").doesNotExist());
    }

    @Test
    void actuatorHealthRemainsReachableUnderStress() throws Exception {
        for (int i = 0; i < 50; i++) {
            mockMvc.perform(get("/actuator/health"))
                    .andExpect(status().isOk());
        }
    }
}
