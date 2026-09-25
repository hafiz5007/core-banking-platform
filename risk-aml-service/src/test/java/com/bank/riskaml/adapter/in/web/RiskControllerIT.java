package com.bank.riskaml.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.bank.riskaml.AbstractIntegrationTest;
import com.bank.riskaml.adapter.in.web.RiskController.CaseResponse;
import com.bank.riskaml.adapter.in.web.RiskController.CloseCaseRequest;
import com.bank.riskaml.adapter.in.web.RiskController.EvaluateRequest;
import com.bank.riskaml.application.RiskService.EvaluationResult;
import com.bank.riskaml.domain.Decision;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** End-to-end tests of transaction monitoring, case creation and closure (FR-AML-001/002/003). */
class RiskControllerIT extends AbstractIntegrationTest {

  @Autowired TestRestTemplate rest;

  private static String ref(String prefix) {
    return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
  }

  private EvaluationResult evaluate(BigDecimal amount, String country) {
    return rest.postForEntity(
            "/api/v1/risk/evaluate",
            new EvaluateRequest(ref("TXN"), ref("ACC"), amount, "USD", country),
            EvaluationResult.class)
        .getBody();
  }

  @Test
  void aSmallDomesticPaymentRaisesNoAlert() {
    EvaluationResult result = evaluate(new BigDecimal("100.00"), "GB");

    assertThat(result.decision()).isEqualTo(Decision.ALLOW);
    assertThat(result.ruleCode()).isNull();
    assertThat(result.caseId()).isNull();
  }

  @Test
  void aLargeAmountIsHeldAndOpensACase() {
    EvaluationResult result = evaluate(new BigDecimal("25000.00"), "GB");

    assertThat(result.decision()).isEqualTo(Decision.HOLD);
    assertThat(result.ruleCode()).isEqualTo("LARGE_AMOUNT");
    assertThat(result.caseId()).isNotNull();
    assertThat(result.alertId()).isNotNull();
  }

  @Test
  void anAmountJustUnderTheThresholdIsFlaggedAsPossibleStructuring() {
    EvaluationResult result = evaluate(new BigDecimal("9500.00"), "GB");

    assertThat(result.decision()).isEqualTo(Decision.ALLOW);
    assertThat(result.ruleCode()).isEqualTo("POSSIBLE_STRUCTURING");
    assertThat(result.caseId()).isNotNull();
  }

  @Test
  void theOpenedCaseIsReadableAndCarriesTheRuleThatFired() {
    UUID caseId = evaluate(new BigDecimal("25000.00"), "GB").caseId();

    ResponseEntity<CaseResponse> found =
        rest.getForEntity("/api/v1/risk/cases/" + caseId, CaseResponse.class);

    assertThat(found.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(found.getBody().ruleCode()).isEqualTo("LARGE_AMOUNT");
    assertThat(found.getBody().status()).isEqualTo("OPEN");
    assertThat(found.getBody().sarFiled()).isFalse();
  }

  @Test
  void closingACaseRecordsTheResolutionAndSarFlag() {
    UUID caseId = evaluate(new BigDecimal("25000.00"), "GB").caseId();

    ResponseEntity<CaseResponse> closed =
        rest.postForEntity(
            "/api/v1/risk/cases/" + caseId + "/close",
            new CloseCaseRequest("Reviewed: legitimate property purchase", true),
            CaseResponse.class);

    assertThat(closed.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(closed.getBody().status()).isEqualTo("CLOSED");
    assertThat(closed.getBody().sarFiled()).isTrue();
    assertThat(closed.getBody().resolution()).contains("legitimate property purchase");
  }

  @Test
  void aCaseCannotBeClosedTwice() {
    UUID caseId = evaluate(new BigDecimal("25000.00"), "GB").caseId();
    rest.postForEntity(
        "/api/v1/risk/cases/" + caseId + "/close",
        new CloseCaseRequest("first", false),
        CaseResponse.class);

    ResponseEntity<String> second =
        rest.postForEntity(
            "/api/v1/risk/cases/" + caseId + "/close",
            new CloseCaseRequest("second", false),
            String.class);

    assertThat(second.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
  }

  @Test
  void unknownCaseIsNotFound() {
    ResponseEntity<String> response =
        rest.getForEntity("/api/v1/risk/cases/" + UUID.randomUUID(), String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void anOverLongTransactionReferenceIsRejectedAsABadRequest() {
    ResponseEntity<String> response =
        rest.postForEntity(
            "/api/v1/risk/evaluate",
            new EvaluateRequest("T".repeat(81), ref("ACC"), new BigDecimal("10.00"), "USD", "GB"),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }
}
