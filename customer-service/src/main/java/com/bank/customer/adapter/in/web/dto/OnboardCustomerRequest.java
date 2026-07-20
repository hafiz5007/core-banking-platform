package com.bank.customer.adapter.in.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;

/**
 * Request to onboard a customer (FR-CUS-001).
 *
 * @param dataProcessingConsent must be granted to proceed lawfully
 */
public record OnboardCustomerRequest(
        @NotBlank String firstName,
        @NotBlank String lastName,
        @NotNull @Past LocalDate dateOfBirth,
        @NotNull @Pattern(regexp = "^[A-Z]{2}$", message = "nationality must be an ISO-3166 alpha-2 code")
        String nationality,
        @NotBlank @Email String email,
        String phone,
        String taxId,
        boolean dataProcessingConsent,
        String channel) {
}
