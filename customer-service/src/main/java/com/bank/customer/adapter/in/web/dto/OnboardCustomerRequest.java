package com.bank.customer.adapter.in.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Request to onboard a customer (FR-CUS-001).
 *
 * @param dataProcessingConsent must be granted to proceed lawfully
 */
public record OnboardCustomerRequest(
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @NotNull @Past LocalDate dateOfBirth,
        @NotNull @Pattern(regexp = "^[A-Z]{2}$", message = "nationality must be an ISO-3166 alpha-2 code")
        String nationality,
        @NotBlank @Email @Size(max = 320) String email,
        @Size(max = 30) String phone,
        @Size(max = 50) String taxId,
        boolean dataProcessingConsent,
        @Size(max = 30) String channel) {
}
