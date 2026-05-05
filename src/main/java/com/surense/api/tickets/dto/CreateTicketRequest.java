package com.surense.api.tickets.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateTicketRequest(
        /** Required when the caller is {@code ADMIN}; ignored for {@code CUSTOMER} (customer inferred from login). */
        Long customerId,
        @NotBlank @Size(max = 255) String subject,
        @Size(max = 10_000) String body) {
}
