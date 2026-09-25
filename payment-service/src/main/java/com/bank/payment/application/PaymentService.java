package com.bank.payment.application;

import com.bank.common.error.BusinessException;
import com.bank.common.error.ErrorCode;
import com.bank.common.error.ResourceNotFoundException;
import com.bank.common.money.Money;
import com.bank.payment.adapter.out.persistence.PaymentRepository;
import com.bank.payment.application.port.LedgerPort;
import com.bank.payment.application.port.PaymentEventPublisher;
import com.bank.payment.application.port.ScreeningPort;
import com.bank.payment.application.port.ScreeningPort.ScreeningResult;
import com.bank.payment.application.rail.PaymentRailException;
import com.bank.payment.application.rail.PaymentRailHandler;
import com.bank.payment.domain.ChangeType;
import com.bank.payment.domain.Payment;
import com.bank.payment.domain.PaymentStatus;
import com.bank.payment.domain.PaymentType;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Orchestrates the payment saga (FR-PAY-001/004/008). Common concerns — idempotency, validation,
 * screening, event publication and compensation — live here; the rail-specific money movement and
 * any external scheme submission are delegated to a {@link PaymentRailHandler} chosen by the {@link
 * RoutingEngine}.
 *
 * <p>Steps run as discrete persisted transitions, not one big transaction, because the ledger
 * posting is a cross-service effect that must be undone by an explicit compensating reversal —
 * never by a database rollback the ledger would not see.
 */
@Service
public class PaymentService {

  private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
  private static final Set<PaymentStatus> RETURNABLE =
      EnumSet.of(PaymentStatus.CONFIRMED, PaymentStatus.SETTLED);

  private final PaymentRepository paymentRepository;
  private final LedgerPort ledgerPort;
  private final ScreeningPort screeningPort;
  private final PaymentEventPublisher eventPublisher;
  private final RoutingEngine routingEngine;
  private final ChangeLogRecorder changeLog;

  public PaymentService(
      PaymentRepository paymentRepository,
      LedgerPort ledgerPort,
      ScreeningPort screeningPort,
      PaymentEventPublisher eventPublisher,
      RoutingEngine routingEngine,
      ChangeLogRecorder changeLog) {
    this.paymentRepository = paymentRepository;
    this.ledgerPort = ledgerPort;
    this.screeningPort = screeningPort;
    this.eventPublisher = eventPublisher;
    this.routingEngine = routingEngine;
    this.changeLog = changeLog;
  }

  public Payment initiate(InitiatePaymentCommand cmd) {
    // 1. Idempotency: a replayed key returns the original payment, no new effect.
    var existing = paymentRepository.findByIdempotencyKey(cmd.idempotencyKey());
    if (existing.isPresent()) {
      log.info(
          "Idempotent replay of payment key={} -> {}",
          cmd.idempotencyKey(),
          existing.get().getId());
      return existing.get();
    }

    // Select the rail up front so an unavailable rail fails before anything is created.
    PaymentRailHandler handler = routingEngine.handlerFor(cmd.type());

    Payment payment =
        Payment.received(
            cmd.idempotencyKey(),
            cmd.type(),
            cmd.debtorAccount(),
            cmd.creditorAccount(),
            cmd.amount(),
            cmd.narrative(),
            cmd.targetCurrencyCode());
    payment = paymentRepository.save(payment);
    changeLog.record(
        "Payment",
        payment.getId().toString(),
        ChangeType.CREATE,
        null,
        "Initiated " + cmd.type() + " payment " + cmd.idempotencyKey());

    // 2. Validate — a failure is recorded and surfaced; no funds move.
    try {
      PaymentValidator.validate(cmd);
      payment.markValidated();
      payment = paymentRepository.save(payment);
    } catch (BusinessException e) {
      payment.markRejected(e.getMessage());
      paymentRepository.save(payment);
      throw e;
    }

    // 3. Screen.
    ScreeningResult screening = screeningPort.screen(cmd.creditorAccount(), cmd.amount());
    if (screening.blocked()) {
      payment.markRejected("Screening hold: " + screening.reason());
      paymentRepository.save(payment);
      throw new BusinessException(
          ErrorCode.BUSINESS_RULE_VIOLATION, "Payment blocked by screening: " + screening.reason());
    }
    payment.markScreened();
    payment = paymentRepository.save(payment);

    // 4. Fulfil on the chosen rail (ledger posting + any scheme submission).
    try {
      handler.execute(payment);
      payment = paymentRepository.save(payment);
    } catch (PaymentRailException ex) {
      // The handler already reversed its ledger posting.
      payment.markCompensated(ex.getReversalEntryId(), ex.getMessage());
      payment = paymentRepository.save(payment);
      log.warn("Compensated payment {}: {}", payment.getId(), ex.getMessage());
      return payment;
    } catch (RuntimeException ex) {
      // Ledger post itself failed (nothing posted) — reject.
      payment.markRejected("Fulfilment failed: " + ex.getMessage());
      paymentRepository.save(payment);
      throw new BusinessException(
          ErrorCode.BUSINESS_RULE_VIOLATION, "Payment fulfilment failed for " + payment.getId());
    }

    // 5. Publish + confirm. If publication fails after posting, compensate via reversal.
    try {
      eventPublisher.publish(payment, "PaymentPosted");
      payment.markConfirmed();
      payment = paymentRepository.save(payment);
      log.info(
          "Confirmed payment {} ({} {}) via {}",
          payment.getId(),
          payment.getAmount(),
          payment.getCurrencyCode(),
          payment.getType());
    } catch (RuntimeException e) {
      UUID reversalId =
          ledgerPort.reverse(payment.getLedgerEntryId(), "rev-" + cmd.idempotencyKey());
      payment.markCompensated(reversalId, "Confirmation failed, reversed: " + e.getMessage());
      payment = paymentRepository.save(payment);
      log.warn("Compensated payment {} via reversal {}", payment.getId(), reversalId);
    }
    return payment;
  }

  /**
   * Return/recall a settled payment: post a compensating reversal and mark it RETURNED
   * (FR-PAY-007).
   */
  public Payment returnPayment(UUID id, String reason) {
    Payment payment = get(id);
    if (!RETURNABLE.contains(payment.getStatus()) || payment.getLedgerEntryId() == null) {
      throw new BusinessException(
          ErrorCode.BUSINESS_RULE_VIOLATION,
          "Payment cannot be returned from status " + payment.getStatus());
    }
    UUID reversalId =
        ledgerPort.reverse(payment.getLedgerEntryId(), "ret-" + payment.getIdempotencyKey());
    payment.markReturned(reversalId, reason == null ? "Returned" : reason);
    payment = paymentRepository.save(payment);
    eventPublisher.publish(payment, "PaymentReturned");
    log.info("Returned payment {} via reversal {}", payment.getId(), reversalId);
    return payment;
  }

  public Payment get(UUID id) {
    return paymentRepository
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Payment not found: " + id));
  }

  /** Command to initiate a payment. {@code targetCurrencyCode} is used only for cross-border FX. */
  public record InitiatePaymentCommand(
      String idempotencyKey,
      PaymentType type,
      String debtorAccount,
      String creditorAccount,
      Money amount,
      String narrative,
      String targetCurrencyCode) {}
}
