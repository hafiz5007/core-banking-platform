package com.bank.customer.openbanking;

import com.bank.common.error.BusinessException;
import com.bank.common.error.ErrorCode;
import com.bank.common.error.ResourceNotFoundException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Manages the Open Banking consent lifecycle: request -> authorise (with SCA) -> revoke/expire. */
@Service
public class OpenBankingConsentService {

    private static final Logger log = LoggerFactory.getLogger(OpenBankingConsentService.class);

    private final OpenBankingConsentRepository repository;

    public OpenBankingConsentService(OpenBankingConsentRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public OpenBankingConsent request(String consentReference, UUID customerId, String tpp,
                                      Set<ConsentScope> scopes, int validityDays) {
        if (repository.existsByConsentReference(consentReference)) {
            throw new BusinessException(ErrorCode.DUPLICATE_REQUEST,
                    "Consent reference already exists: " + consentReference);
        }
        Instant expiresAt = Instant.now().plus(validityDays, ChronoUnit.DAYS);
        OpenBankingConsent consent = OpenBankingConsent.request(
                consentReference, customerId, tpp, scopes, expiresAt);
        log.info("Open Banking consent {} requested for customer {} by {}", consentReference, customerId, tpp);
        return repository.save(consent);
    }

    @Transactional
    public OpenBankingConsent authorise(String consentReference, String scaReference) {
        OpenBankingConsent consent = get(consentReference);
        consent.authorise(scaReference);
        log.info("Open Banking consent {} authorised", consentReference);
        return repository.save(consent);
    }

    @Transactional
    public OpenBankingConsent revoke(String consentReference) {
        OpenBankingConsent consent = get(consentReference);
        consent.revoke();
        return repository.save(consent);
    }

    @Transactional
    public OpenBankingConsent get(String consentReference) {
        OpenBankingConsent consent = repository.findByConsentReference(consentReference)
                .orElseThrow(() -> new ResourceNotFoundException("Consent not found: " + consentReference));
        consent.refreshExpiry();
        return repository.save(consent);
    }
}
