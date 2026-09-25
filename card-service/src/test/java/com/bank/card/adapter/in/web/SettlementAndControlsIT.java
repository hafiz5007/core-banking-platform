package com.bank.card.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.bank.card.AbstractIntegrationTest;
import com.bank.card.adapter.in.web.dto.AuthorizationResponse;
import com.bank.card.adapter.in.web.dto.AuthorizeRequest;
import com.bank.card.adapter.in.web.dto.CardResponse;
import com.bank.card.adapter.in.web.dto.IssueCardRequest;
import com.bank.card.adapter.in.web.dto.UpdateControlsRequest;
import com.bank.card.application.SettlementService.ClearingEntry;
import com.bank.card.application.SettlementService.ReconciliationSummary;
import com.bank.card.domain.AuthorizationStatus;
import com.bank.card.domain.CardChannel;
import com.bank.card.testsupport.FakeLedgerConfig;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** End-to-end tests of customer card controls and clearing/settlement reconciliation. */
@Import(FakeLedgerConfig.class)
class SettlementAndControlsIT extends AbstractIntegrationTest {

  @Autowired TestRestTemplate rest;

  private CardResponse issue() {
    return rest.postForEntity(
            "/api/v1/cards",
            new IssueCardRequest("ACC-1", "USD", new BigDecimal("500.00")),
            CardResponse.class)
        .getBody();
  }

  private AuthorizationResponse authorize(
      UUID cardId, String amount, CardChannel channel, boolean intl) {
    return rest.postForEntity(
            "/api/v1/cards/" + cardId + "/authorizations",
            new AuthorizeRequest(new BigDecimal(amount), "USD", "Merchant", channel, intl),
            AuthorizationResponse.class)
        .getBody();
  }

  @Test
  void internationalControlGatesAuthorization() {
    CardResponse card = issue();
    // International is off by default -> declined.
    assertThat(authorize(card.id(), "20.00", CardChannel.ONLINE, true).status())
        .isEqualTo(AuthorizationStatus.DECLINED);

    // Enable international (no per-transaction limit) -> approved.
    rest.postForEntity(
        "/api/v1/cards/" + card.id() + "/controls",
        new UpdateControlsRequest(true, true, true, true, new BigDecimal("0.00"), "USD"),
        CardResponse.class);
    assertThat(authorize(card.id(), "20.00", CardChannel.ONLINE, true).status())
        .isEqualTo(AuthorizationStatus.APPROVED);
  }

  @Test
  void reconcilesClearingFileAgainstAuthorizations() {
    CardResponse card = issue();
    AuthorizationResponse auth = authorize(card.id(), "30.00", CardChannel.POS, false);
    assertThat(auth.status()).isEqualTo(AuthorizationStatus.APPROVED);

    List<ClearingEntry> file =
        List.of(
            new ClearingEntry(auth.id(), "NET-1"),
            new ClearingEntry(UUID.randomUUID(), "NET-2")); // no matching authorization

    ResponseEntity<ReconciliationSummary> summary =
        rest.postForEntity("/api/v1/cards/settlement/reconcile", file, ReconciliationSummary.class);

    assertThat(summary.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(summary.getBody().total()).isEqualTo(2);
    assertThat(summary.getBody().matched()).isEqualTo(1);
    assertThat(summary.getBody().unmatched()).isEqualTo(1);
  }
}
