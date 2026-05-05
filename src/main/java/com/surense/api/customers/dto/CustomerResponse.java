package com.surense.api.customers.dto;

import java.time.Instant;

public record CustomerResponse(
        Long id,
        String email,
        String displayName,
        String phoneNumber,
        Long createdByAgentId,
        Instant createdAt,
        Instant updatedAt) {
}
