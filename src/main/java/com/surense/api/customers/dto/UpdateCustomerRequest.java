package com.surense.api.customers.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateCustomerRequest(
        @NotBlank @Size(max = 200) String displayName,
        @Size(max = 50) String phoneNumber,
        /** When set by {@code ADMIN} or owning {@code AGENT}, links this CRM row to that {@code CUSTOMER}-role login. */
        @Size(max = 64) String linkedCustomerUsername) {
}
