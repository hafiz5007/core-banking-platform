package com.bank.customer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.bank.customer.adapter.out.persistence.ConsentRepository;
import com.bank.customer.adapter.out.persistence.CustomerRepository;
import com.bank.customer.application.CustomerService.OnboardCommand;
import com.bank.customer.application.port.KycVerificationPort;
import com.bank.customer.application.port.KycVerificationPort.KycVerificationResult;
import com.bank.customer.application.port.ScreeningPort;
import com.bank.customer.application.port.ScreeningPort.ScreeningResult;
import com.bank.customer.domain.Consent;
import com.bank.customer.domain.Customer;
import com.bank.customer.domain.CustomerStatus;
import com.bank.customer.domain.KycStatus;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/** Unit tests for the onboarding orchestration, with all outbound ports mocked. */
class CustomerServiceTest {

  private CustomerRepository customerRepository;
  private ConsentRepository consentRepository;
  private KycVerificationPort kycPort;
  private ScreeningPort screeningPort;
  private CustomerService service;

  @BeforeEach
  void setUp() {
    customerRepository = Mockito.mock(CustomerRepository.class);
    consentRepository = Mockito.mock(ConsentRepository.class);
    kycPort = Mockito.mock(KycVerificationPort.class);
    screeningPort = Mockito.mock(ScreeningPort.class);
    ChangeLogRecorder changeLog = Mockito.mock(ChangeLogRecorder.class);
    service =
        new CustomerService(
            customerRepository, consentRepository, kycPort, screeningPort, changeLog);

    when(customerRepository.existsByCif(any())).thenReturn(false);
    when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));
    when(consentRepository.save(any(Consent.class))).thenAnswer(inv -> inv.getArgument(0));
  }

  private OnboardCommand command() {
    return new OnboardCommand(
        "Ada",
        "Lovelace",
        LocalDate.of(1990, 1, 1),
        "GB",
        "ada@example.com",
        "+441234567890",
        "TAX123",
        true,
        "WEB");
  }

  @Test
  void verifiedAndCleanCustomerIsActivated() {
    when(kycPort.verify(any())).thenReturn(new KycVerificationResult(KycStatus.VERIFIED, "kyc-1"));
    when(screeningPort.screen(any())).thenReturn(new ScreeningResult(false, null));

    Customer result = service.onboard(command());

    assertThat(result.getStatus()).isEqualTo(CustomerStatus.ACTIVE);
    assertThat(result.getKycStatus()).isEqualTo(KycStatus.VERIFIED);
    assertThat(result.getCif()).isNotBlank();
    assertThat(result.getRiskRating()).isNotNull();
    assertThat(result.getKycRefreshDue()).isNotNull();
  }

  @Test
  void screeningMatchBlocksCustomer() {
    when(kycPort.verify(any())).thenReturn(new KycVerificationResult(KycStatus.VERIFIED, "kyc-1"));
    when(screeningPort.screen(any())).thenReturn(new ScreeningResult(true, "case-1"));

    Customer result = service.onboard(command());

    assertThat(result.getStatus()).isEqualTo(CustomerStatus.BLOCKED);
    assertThat(result.isScreeningMatch()).isTrue();
    assertThat(result.getScreeningCaseRef()).isEqualTo("case-1");
  }

  @Test
  void referredKycLeavesCustomerPending() {
    when(kycPort.verify(any())).thenReturn(new KycVerificationResult(KycStatus.REFERRED, null));
    when(screeningPort.screen(any())).thenReturn(new ScreeningResult(false, null));

    Customer result = service.onboard(command());

    assertThat(result.getStatus()).isEqualTo(CustomerStatus.PENDING);
    assertThat(result.getKycStatus()).isEqualTo(KycStatus.REFERRED);
  }
}
