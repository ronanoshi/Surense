package com.surense.api.tickets.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateTicketRequest(
        @NotNull Long customerId,
        @NotBlank @Size(max = 255) String subject,
        @Size(max = 10_000) String body) {
}
