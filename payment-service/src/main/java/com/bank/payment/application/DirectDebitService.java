package com.bank.payment.application;

import com.bank.common.error.BusinessException;
import com.bank.common.error.ErrorCode;
import com.bank.common.error.ResourceNotFoundException;
import com.bank.common.money.Money;
import com.bank.payment.adapter.out.persistence.DirectDebitMandateRepository;
import com.bank.payment.application.PaymentService.InitiatePaymentCommand;
import com.bank.payment.domain.Payment;
import com.bank.payment.domain.PaymentType;
import com.bank.payment.domain.recurring.DirectDebitMandate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manages direct-debit mandates and collections. A collection pulls funds from the payer to the
 * payee through the intrabank engine, but only within an active mandate's ceiling (FR-PAY-005).
 */
@Service
public class DirectDebitService {

  private static final Logger log = LoggerFactory.getLogger(DirectDebitService.class);

  private final DirectDebitMandateRepository repository;
  private final PaymentService paymentService;

  public DirectDebitService(
      DirectDebitMandateRepository repository, PaymentService paymentService) {
    this.repository = repository;
    this.paymentService = paymentService;
  }

  @Transactional
  public DirectDebitMandate createMandate(
      String mandateReference, String payerAccount, String payeeAccount, Money maxAmount) {
    if (repository.existsByMandateReference(mandateReference)) {
      throw new BusinessException(
          ErrorCode.DUPLICATE_REQUEST, "Mandate reference already exists: " + mandateReference);
    }
    return repository.save(
        DirectDebitMandate.create(mandateReference, payerAccount, payeeAccount, maxAmount));
  }

  @Transactional(readOnly = true)
  public DirectDebitMandate getMandate(String mandateReference) {
    return repository
        .findByMandateReference(mandateReference)
        .orElseThrow(() -> new ResourceNotFoundException("Mandate not found: " + mandateReference));
  }

  @Transactional
  public DirectDebitMandate cancelMandate(String mandateReference) {
    DirectDebitMandate mandate = getMandate(mandateReference);
    mandate.cancel();
    return repository.save(mandate);
  }

  /**
   * Collect against a mandate. The {@code collectionReference} is used as the payment idempotency
   * key so a retried collection is never charged twice.
   */
  @Transactional
  public Payment collect(
      String mandateReference, Money amount, String collectionReference, String narrative) {
    DirectDebitMandate mandate = getMandate(mandateReference);
    if (!mandate.canCollect(amount)) {
      throw new BusinessException(
          ErrorCode.BUSINESS_RULE_VIOLATION,
          "Collection not permitted by mandate " + mandateReference);
    }
    Payment payment =
        paymentService.initiate(
            new InitiatePaymentCommand(
                "dd-" + collectionReference,
                PaymentType.INTRABANK,
                mandate.getPayerAccount(),
                mandate.getPayeeAccount(),
                amount,
                narrative == null ? "Direct debit " + mandateReference : narrative,
                null));
    log.info("Collected {} on mandate {} -> payment {}", amount, mandateReference, payment.getId());
    return payment;
  }
}
