package com.surense.api.customers.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCustomerRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(max = 200) String displayName,
        @Size(max = 50) String phoneNumber,
        /** Optional: link an existing {@code CUSTOMER}-role login ({@code users.username}) to this CRM row. */
        @Size(max = 64) String linkedCustomerUsername) {
}
