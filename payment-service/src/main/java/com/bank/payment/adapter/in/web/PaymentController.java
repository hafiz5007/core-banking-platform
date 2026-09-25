package com.bank.payment.adapter.in.web;

import com.bank.common.money.Money;
import com.bank.payment.adapter.in.web.dto.ChangeLogResponse;
import com.bank.payment.adapter.in.web.dto.InitiatePaymentRequest;
import com.bank.payment.adapter.in.web.dto.PaymentResponse;
import com.bank.payment.application.ChangeLogRecorder;
import com.bank.payment.application.PaymentService;
import com.bank.payment.application.PaymentService.InitiatePaymentCommand;
import com.bank.payment.domain.Payment;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

  private final PaymentService paymentService;
  private final ChangeLogRecorder changeLog;

  public PaymentController(PaymentService paymentService, ChangeLogRecorder changeLog) {
    this.paymentService = paymentService;
    this.changeLog = changeLog;
  }

  @PostMapping
  public ResponseEntity<PaymentResponse> initiate(
      @Valid @RequestBody InitiatePaymentRequest request) {
    Money amount = Money.of(request.amount(), Currency.getInstance(request.currencyCode()));
    Payment payment =
        paymentService.initiate(
            new InitiatePaymentCommand(
                request.idempotencyKey(),
                request.typeOrDefault(),
                request.debtorAccount(),
                request.creditorAccount(),
                amount,
                request.narrative(),
                request.targetCurrencyCode()));
    return ResponseEntity.created(URI.create("/api/v1/payments/" + payment.getId()))
        .body(PaymentResponse.from(payment));
  }

  @GetMapping("/{id}")
  public PaymentResponse get(@PathVariable UUID id) {
    return PaymentResponse.from(paymentService.get(id));
  }

  @GetMapping("/{id}/change-log")
  public List<ChangeLogResponse> changeLog(@PathVariable UUID id) {
    paymentService.get(id); // 404 if unknown
    return changeLog.history("Payment", id.toString()).stream()
        .map(ChangeLogResponse::from)
        .toList();
  }

  @PostMapping("/{id}/return")
  public PaymentResponse returnPayment(
      @PathVariable UUID id, @RequestBody(required = false) ReturnRequest request) {
    String reason = request != null ? request.reason() : null;
    return PaymentResponse.from(paymentService.returnPayment(id, reason));
  }

  /** Optional body for a return/recall request. */
  public record ReturnRequest(String reason) {}
}
