package com.surense.api.tickets.dto;

import com.surense.infra.persistence.tickets.entity.TicketStatus;
import java.time.Instant;

public record TicketResponse(
        Long id,
        Long customerId,
        String subject,
        String body,
        TicketStatus status,
        Long assignedToAgentId,
        Instant createdAt,
        Instant updatedAt) {
}
