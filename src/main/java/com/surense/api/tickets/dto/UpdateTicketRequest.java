package com.surense.api.tickets.dto;

import com.surense.infra.persistence.tickets.entity.TicketStatus;
import jakarta.validation.constraints.Size;

public record UpdateTicketRequest(
        @Size(max = 255) String subject,
        @Size(max = 10_000) String body,
        TicketStatus status,
        Long assignedToAgentId) {
}
