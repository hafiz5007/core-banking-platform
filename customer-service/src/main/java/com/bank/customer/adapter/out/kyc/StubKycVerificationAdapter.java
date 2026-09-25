package com.bank.customer.adapter.out.kyc;

import com.bank.customer.application.port.KycVerificationPort;
import com.bank.customer.domain.KycStatus;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Stub KYC provider for local development and tests. Deterministic rules stand in for a real
 * external call so the onboarding flow is fully exercisable end-to-end: a surname of "REFER"
 * returns REFERRED, "FAIL" returns FAILED, everything else VERIFIED.
 *
 * <p>Replace with a real provider adapter (HTTP client + webhook) in a later iteration.
 */
@Component
public class StubKycVerificationAdapter implements KycVerificationPort {

  @Override
  public KycVerificationResult verify(KycVerificationRequest request) {
    String surname = request.lastName() == null ? "" : request.lastName().toUpperCase();
    KycStatus status =
        switch (surname) {
          case "REFER" -> KycStatus.REFERRED;
          case "FAIL" -> KycStatus.FAILED;
          default -> KycStatus.VERIFIED;
        };
    String evidenceRef = status == KycStatus.VERIFIED ? "kyc-" + UUID.randomUUID() : null;
    return new KycVerificationResult(status, evidenceRef);
  }
}
