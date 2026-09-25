package com.bank.customer.adapter.in.web.dto;

import com.bank.customer.domain.Customer;
import com.bank.customer.domain.CustomerStatus;
import com.bank.customer.domain.KycStatus;
import com.bank.customer.domain.RiskRating;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Response view of a customer. */
public record CustomerResponse(
    UUID id,
    String organizationId,
    String cif,
    String firstName,
    String lastName,
    String nationality,
    String email,
    CustomerStatus status,
    KycStatus kycStatus,
    boolean screeningMatch,
    RiskRating riskRating,
    LocalDate kycRefreshDue,
    Instant createdAt) {

  public static CustomerResponse from(Customer c) {
    return new CustomerResponse(
        c.getId(),
        c.getOrganizationId(),
        c.getCif(),
        c.getFirstName(),
        c.getLastName(),
        c.getNationality(),
        c.getEmail(),
        c.getStatus(),
        c.getKycStatus(),
        c.isScreeningMatch(),
        c.getRiskRating(),
        c.getKycRefreshDue(),
        c.getCreatedAt());
  }
}
