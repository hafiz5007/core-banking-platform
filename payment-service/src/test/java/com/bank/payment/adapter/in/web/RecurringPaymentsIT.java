package com.bank.payment.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.bank.payment.AbstractIntegrationTest;
import com.bank.payment.adapter.in.web.DirectDebitController.CollectRequest;
import com.bank.payment.adapter.in.web.DirectDebitController.CreateMandateRequest;
import com.bank.payment.adapter.in.web.DirectDebitController.MandateResponse;
import com.bank.payment.adapter.in.web.StandingOrderController.CreateStandingOrderRequest;
import com.bank.payment.adapter.in.web.StandingOrderController.RunResponse;
import com.bank.payment.adapter.in.web.StandingOrderController.StandingOrderResponse;
import com.bank.payment.adapter.in.web.dto.PaymentResponse;
import com.bank.payment.domain.PaymentStatus;
import com.bank.payment.domain.recurring.Frequency;
import com.bank.payment.testsupport.FakeLedgerConfig;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** End-to-end tests of standing orders and direct debit, executing through the (faked) ledger. */
@Import(FakeLedgerConfig.class)
class RecurringPaymentsIT extends AbstractIntegrationTest {

  @Autowired TestRestTemplate rest;

  @Test
  void standingOrderExecutesAndAdvances() {
    var create =
        new CreateStandingOrderRequest(
            "1000",
            "2000",
            new BigDecimal("50.00"),
            "USD",
            Frequency.MONTHLY,
            LocalDate.now(),
            null,
            "Rent");
    StandingOrderResponse order =
        rest.postForEntity("/api/v1/standing-orders", create, StandingOrderResponse.class)
            .getBody();
    assertThat(order).isNotNull();
    LocalDate firstRun = order.nextRunDate();

    ResponseEntity<RunResponse> run =
        rest.postForEntity(
            "/api/v1/standing-orders/run?on=" + LocalDate.now(), null, RunResponse.class);
    assertThat(run.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(run.getBody().executed()).isGreaterThanOrEqualTo(1);

    StandingOrderResponse after =
        rest.getForEntity("/api/v1/standing-orders/" + order.id(), StandingOrderResponse.class)
            .getBody();
    assertThat(after.nextRunDate()).isAfter(firstRun);
  }

  @Test
  void directDebitCollectsWithinMandateAndRejectsOverLimit() {
    String ref = "MNDT-" + UUID.randomUUID();
    var createMandate =
        new CreateMandateRequest(ref, "1000", "2000", new BigDecimal("100.00"), "USD");
    ResponseEntity<MandateResponse> mandate =
        rest.postForEntity("/api/v1/direct-debits/mandates", createMandate, MandateResponse.class);
    assertThat(mandate.getStatusCode()).isEqualTo(HttpStatus.CREATED);

    // Within the ceiling -> succeeds.
    ResponseEntity<PaymentResponse> ok =
        rest.postForEntity(
            "/api/v1/direct-debits/mandates/" + ref + "/collect",
            new CollectRequest(
                new BigDecimal("40.00"), "USD", "COLL-" + UUID.randomUUID(), "Utility"),
            PaymentResponse.class);
    assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(ok.getBody().status()).isEqualTo(PaymentStatus.CONFIRMED);

    // Above the ceiling -> rejected by the mandate.
    ResponseEntity<String> over =
        rest.postForEntity(
            "/api/v1/direct-debits/mandates/" + ref + "/collect",
            new CollectRequest(
                new BigDecimal("150.00"), "USD", "COLL-" + UUID.randomUUID(), "Utility"),
            String.class);
    assertThat(over.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
  }
}
