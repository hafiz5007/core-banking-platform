package com.bank.customer.application;

import com.bank.common.error.ResourceNotFoundException;
import com.bank.customer.adapter.out.persistence.ConsentRepository;
import com.bank.customer.adapter.out.persistence.CustomerRepository;
import com.bank.customer.application.port.KycVerificationPort;
import com.bank.customer.application.port.KycVerificationPort.KycVerificationRequest;
import com.bank.customer.application.port.KycVerificationPort.KycVerificationResult;
import com.bank.customer.application.port.ScreeningPort;
import com.bank.customer.application.port.ScreeningPort.ScreeningRequest;
import com.bank.customer.application.port.ScreeningPort.ScreeningResult;
import com.bank.customer.domain.Consent;
import com.bank.customer.domain.ConsentType;
import com.bank.customer.domain.Customer;
import com.bank.customer.domain.RiskRating;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates customer onboarding (FR-CUS-001..006): create the party, run KYC verification and
 * sanctions/PEP screening through outbound ports, assign a risk rating, and resolve the final
 * state.
 */
@Service
public class CustomerService {

  private static final Logger log = LoggerFactory.getLogger(CustomerService.class);
  private static final SecureRandom RANDOM = new SecureRandom();

  private final CustomerRepository customerRepository;
  private final ConsentRepository consentRepository;
  private final KycVerificationPort kycVerificationPort;
  private final ScreeningPort screeningPort;
  private final ChangeLogRecorder changeLog;

  public CustomerService(
      CustomerRepository customerRepository,
      ConsentRepository consentRepository,
      KycVerificationPort kycVerificationPort,
      ScreeningPort screeningPort,
      ChangeLogRecorder changeLog) {
    this.customerRepository = customerRepository;
    this.consentRepository = consentRepository;
    this.kycVerificationPort = kycVerificationPort;
    this.screeningPort = screeningPort;
    this.changeLog = changeLog;
  }

  @Transactional
  public Customer onboard(OnboardCommand cmd) {
    Customer customer =
        Customer.register(
            com.bank.common.tenant.TenantContext.getOrDefault(),
            generateUniqueCif(),
            cmd.firstName(),
            cmd.lastName(),
            cmd.dateOfBirth(),
            cmd.nationality(),
            cmd.email(),
            cmd.phone(),
            cmd.taxId());

    // 1. KYC verification.
    KycVerificationResult kyc =
        kycVerificationPort.verify(
            new KycVerificationRequest(
                cmd.firstName(), cmd.lastName(), cmd.nationality(), cmd.taxId()));
    customer.recordKyc(kyc.status(), kyc.evidenceRef());

    // 2. Sanctions / PEP screening — block on a positive match.
    ScreeningResult screening =
        screeningPort.screen(
            new ScreeningRequest(cmd.firstName(), cmd.lastName(), cmd.nationality()));
    customer.applyScreening(screening.match(), screening.caseRef());

    // 3. Risk rating + KYC refresh schedule.
    RiskRating rating =
        RiskRatingPolicy.rate(cmd.nationality(), cmd.taxId() == null || cmd.taxId().isBlank());
    customer.assignRiskRating(rating);

    // 4. Resolve final onboarding state.
    customer.resolveOnboarding();

    Customer saved = customerRepository.save(customer);

    // 5. Capture the mandatory data-processing consent if supplied.
    if (cmd.dataProcessingConsent()) {
      consentRepository.save(
          new Consent(saved.getId(), ConsentType.DATA_PROCESSING, true, cmd.channel()));
    }

    changeLog.record(
        "Customer",
        saved.getId().toString(),
        com.bank.customer.domain.ChangeType.CREATE,
        null,
        "Onboarded customer " + saved.getCif() + " status " + saved.getStatus());

    log.info(
        "Onboarded customer cif={} status={} kyc={} screeningMatch={} risk={} org={}",
        saved.getCif(),
        saved.getStatus(),
        saved.getKycStatus(),
        saved.isScreeningMatch(),
        saved.getRiskRating(),
        saved.getOrganizationId());
    return saved;
  }

  @Transactional(readOnly = true)
  public Customer get(UUID id) {
    return customerRepository
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Customer not found: " + id));
  }

  @Transactional(readOnly = true)
  public List<Consent> consentsFor(UUID customerId) {
    get(customerId); // ensures existence (404 otherwise)
    return consentRepository.findByCustomerIdOrderByRecordedAtDesc(customerId);
  }

  @Transactional
  public Consent recordConsent(UUID customerId, ConsentType type, boolean granted, String channel) {
    get(customerId);
    Consent consent = new Consent(customerId, type, granted, channel);
    return consentRepository.save(consent);
  }

  private String generateUniqueCif() {
    String candidate;
    do {
      candidate = String.format("%010d", Math.abs(RANDOM.nextLong()) % 10_000_000_000L);
    } while (customerRepository.existsByCif(candidate));
    return candidate;
  }

  /** Command capturing everything needed to onboard a customer. */
  public record OnboardCommand(
      String firstName,
      String lastName,
      LocalDate dateOfBirth,
      String nationality,
      String email,
      String phone,
      String taxId,
      boolean dataProcessingConsent,
      String channel) {}
}
