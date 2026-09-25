package com.bank.payment.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.bank.payment.AbstractIntegrationTest;
import com.bank.payment.adapter.in.web.dto.InitiatePaymentRequest;
import com.bank.payment.adapter.in.web.dto.PaymentResponse;
import com.bank.payment.domain.PaymentStatus;
import com.bank.payment.domain.PaymentType;
import com.bank.payment.testsupport.FakeLedgerConfig;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * End-to-end tests of the payment flows (intrabank + domestic clearing) with an in-memory ledger.
 */
@Import(FakeLedgerConfig.class)
class PaymentControllerIT extends AbstractIntegrationTest {

  @Autowired TestRestTemplate rest;

  private InitiatePaymentRequest request(
      String key, PaymentType type, String debtor, String creditor) {
    return new InitiatePaymentRequest(
        key, type, debtor, creditor, new BigDecimal("100.00"), "USD", "Test transfer", null);
  }

  @Test
  void initiatesAndConfirmsIntrabankPayment() {
    ResponseEntity<PaymentResponse> created =
        rest.postForEntity(
            "/api/v1/payments",
            request("p-" + UUID.randomUUID(), PaymentType.INTRABANK, "1000", "2000"),
            PaymentResponse.class);

    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    PaymentResponse body = created.getBody();
    assertThat(body).isNotNull();
    assertThat(body.status()).isEqualTo(PaymentStatus.CONFIRMED);
    assertThat(body.ledgerEntryId()).isNotNull();
  }

  @Test
  void settlesDomesticRtgsViaScheme() {
    ResponseEntity<PaymentResponse> created =
        rest.postForEntity(
            "/api/v1/payments",
            request(
                "rtgs-" + UUID.randomUUID(), PaymentType.RTGS, "1000", "GB29NWBK60161331926819"),
            PaymentResponse.class);

    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    PaymentResponse body = created.getBody();
    assertThat(body).isNotNull();
    assertThat(body.status()).isEqualTo(PaymentStatus.CONFIRMED);
    assertThat(body.schemeReference()).isNotBlank();
    assertThat(body.ledgerEntryId()).isNotNull();
  }

  @Test
  void schemeRejectionCompensatesPayment() {
    ResponseEntity<PaymentResponse> created =
        rest.postForEntity(
            "/api/v1/payments",
            request("rej-" + UUID.randomUUID(), PaymentType.DOMESTIC_INSTANT, "1000", "ACC-RETURN"),
            PaymentResponse.class);

    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    PaymentResponse body = created.getBody();
    assertThat(body).isNotNull();
    assertThat(body.status()).isEqualTo(PaymentStatus.COMPENSATED);
    assertThat(body.reversalEntryId()).isNotNull();
  }

  @Test
  void replayWithSameKeyReturnsSamePayment() {
    String key = "idem-" + UUID.randomUUID();
    PaymentResponse first =
        rest.postForEntity(
                "/api/v1/payments",
                request(key, PaymentType.INTRABANK, "1000", "2000"),
                PaymentResponse.class)
            .getBody();
    PaymentResponse second =
        rest.postForEntity(
                "/api/v1/payments",
                request(key, PaymentType.INTRABANK, "1000", "2000"),
                PaymentResponse.class)
            .getBody();

    assertThat(first).isNotNull();
    assertThat(second).isNotNull();
    assertThat(second.id()).isEqualTo(first.id());
  }

  @Test
  void returnsASettledPayment() {
    PaymentResponse confirmed =
        rest.postForEntity(
                "/api/v1/payments",
                request("ret-" + UUID.randomUUID(), PaymentType.INTRABANK, "1000", "2000"),
                PaymentResponse.class)
            .getBody();
    assertThat(confirmed).isNotNull();

    ResponseEntity<PaymentResponse> returned =
        rest.postForEntity(
            "/api/v1/payments/" + confirmed.id() + "/return", null, PaymentResponse.class);

    assertThat(returned.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(returned.getBody()).isNotNull();
    assertThat(returned.getBody().status()).isEqualTo(PaymentStatus.RETURNED);
    assertThat(returned.getBody().reversalEntryId()).isNotNull();
  }

  @Test
  void settlesCrossBorderWithFxViaSwift() {
    var crossBorder =
        new InitiatePaymentRequest(
            "xb-" + UUID.randomUUID(),
            PaymentType.CROSS_BORDER,
            "1000",
            "DE89370400440532013000",
            new BigDecimal("100.00"),
            "USD",
            "Overseas invoice",
            "EUR");

    ResponseEntity<PaymentResponse> created =
        rest.postForEntity("/api/v1/payments", crossBorder, PaymentResponse.class);

    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    PaymentResponse body = created.getBody();
    assertThat(body).isNotNull();
    assertThat(body.status()).isEqualTo(PaymentStatus.CONFIRMED);
    assertThat(body.schemeReference()).startsWith("gpi-");
    assertThat(body.targetCurrencyCode()).isEqualTo("EUR");
    assertThat(body.targetAmount()).isNotBlank();
    assertThat(body.fxRate()).isNotBlank();
  }

  @Test
  void blankDebtorIsRejected() {
    var bad =
        new InitiatePaymentRequest(
            "bad-" + UUID.randomUUID(),
            PaymentType.INTRABANK,
            "",
            "2000",
            new BigDecimal("100.00"),
            "USD",
            "x",
            null);
    ResponseEntity<String> response = rest.postForEntity("/api/v1/payments", bad, String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }
}
