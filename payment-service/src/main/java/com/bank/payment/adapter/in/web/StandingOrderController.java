package com.bank.payment.adapter.in.web;

import com.bank.common.money.Money;
import com.bank.payment.application.StandingOrderService;
import com.bank.payment.domain.recurring.Frequency;
import com.bank.payment.domain.recurring.StandingOrder;
import com.bank.payment.domain.recurring.StandingOrderStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.util.Currency;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/standing-orders")
public class StandingOrderController {

  private final StandingOrderService standingOrderService;

  public StandingOrderController(StandingOrderService standingOrderService) {
    this.standingOrderService = standingOrderService;
  }

  @PostMapping
  public ResponseEntity<StandingOrderResponse> create(
      @Valid @RequestBody CreateStandingOrderRequest request) {
    Currency currency = Currency.getInstance(request.currencyCode());
    StandingOrder order =
        standingOrderService.create(
            request.debtorAccount(),
            request.creditorAccount(),
            Money.of(request.amount(), currency),
            request.narrative() == null ? "Standing order" : request.narrative(),
            request.frequency(),
            request.startDate(),
            request.endDate());
    return ResponseEntity.created(URI.create("/api/v1/standing-orders/" + order.getId()))
        .body(StandingOrderResponse.from(order));
  }

  @GetMapping("/{id}")
  public StandingOrderResponse get(@PathVariable UUID id) {
    return StandingOrderResponse.from(standingOrderService.get(id));
  }

  @PostMapping("/{id}/cancel")
  public StandingOrderResponse cancel(@PathVariable UUID id) {
    return StandingOrderResponse.from(standingOrderService.cancel(id));
  }

  /**
   * Operational trigger to execute all standing orders due on a date (also runnable on a schedule).
   */
  @PostMapping("/run")
  public RunResponse run(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate on) {
    int executed = standingOrderService.runDue(on == null ? LocalDate.now() : on);
    return new RunResponse(executed);
  }

  public record CreateStandingOrderRequest(
      @NotBlank @Size(max = 40) String debtorAccount,
      @NotBlank @Size(max = 40) String creditorAccount,
      @NotNull @Positive BigDecimal amount,
      @NotNull
          @Pattern(regexp = "^[A-Z]{3}$", message = "currencyCode must be a 3-letter ISO-4217 code")
          String currencyCode,
      @NotNull Frequency frequency,
      @NotNull LocalDate startDate,
      LocalDate endDate,
      @Size(max = 280) String narrative) {}

  public record StandingOrderResponse(
      UUID id,
      String debtorAccount,
      String creditorAccount,
      String amount,
      String currencyCode,
      Frequency frequency,
      LocalDate nextRunDate,
      LocalDate endDate,
      StandingOrderStatus status) {
    static StandingOrderResponse from(StandingOrder o) {
      return new StandingOrderResponse(
          o.getId(),
          o.getDebtorAccount(),
          o.getCreditorAccount(),
          o.getAmount().toPlainString(),
          o.getCurrencyCode(),
          o.getFrequency(),
          o.getNextRunDate(),
          o.getEndDate(),
          o.getStatus());
    }
  }

  public record RunResponse(int executed) {}
}
