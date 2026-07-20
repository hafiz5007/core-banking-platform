package com.bank.customer.openbanking;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OpenBankingConsentRepository extends JpaRepository<OpenBankingConsent, UUID> {
    Optional<OpenBankingConsent> findByConsentReference(String consentReference);

    boolean existsByConsentReference(String consentReference);
}
