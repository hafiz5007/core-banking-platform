package com.bank.customer.application.port;

import com.bank.customer.domain.KycStatus;

/**
 * Outbound port to an external identity/document verification provider (FR-CUS-002). The domain
 * depends on this interface, not on any concrete provider, so the provider can be swapped freely.
 */
public interface KycVerificationPort {

  KycVerificationResult verify(KycVerificationRequest request);

  record KycVerificationRequest(
      String firstName, String lastName, String nationality, String taxId) {}

  /**
   * @param status verification outcome
   * @param evidenceRef opaque reference to the evidence held by the provider (no PII)
   */
  record KycVerificationResult(KycStatus status, String evidenceRef) {}
}
