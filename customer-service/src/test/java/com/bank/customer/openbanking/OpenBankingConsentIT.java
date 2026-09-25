package com.bank.customer.openbanking;

import static org.assertj.core.api.Assertions.assertThat;

import com.bank.customer.AbstractIntegrationTest;
import com.bank.customer.openbanking.OpenBankingConsentController.AuthoriseRequest;
import com.bank.customer.openbanking.OpenBankingConsentController.ConsentResponse;
import com.bank.customer.openbanking.OpenBankingConsentController.RequestConsentRequest;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** End-to-end test of the Open Banking consent flow. */
class OpenBankingConsentIT extends AbstractIntegrationTest {

  @Autowired TestRestTemplate rest;

  @Test
  void requestAuthoriseAndRevokeConsent() {
    String ref = "CR-" + UUID.randomUUID();
    var request =
        new RequestConsentRequest(
            ref,
            UUID.randomUUID(),
            "FinTechCo",
            Set.of(ConsentScope.ACCOUNT_INFO, ConsentScope.BALANCES),
            30);

    ResponseEntity<ConsentResponse> created =
        rest.postForEntity("/api/v1/open-banking/consents", request, ConsentResponse.class);
    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(created.getBody().status()).isEqualTo(ConsentStatus.AWAITING_AUTHORISATION);

    ResponseEntity<ConsentResponse> authorised =
        rest.postForEntity(
            "/api/v1/open-banking/consents/" + ref + "/authorise",
            new AuthoriseRequest("sca-" + UUID.randomUUID()),
            ConsentResponse.class);
    assertThat(authorised.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(authorised.getBody().status()).isEqualTo(ConsentStatus.ACTIVE);

    ResponseEntity<ConsentResponse> revoked =
        rest.postForEntity(
            "/api/v1/open-banking/consents/" + ref + "/revoke", null, ConsentResponse.class);
    assertThat(revoked.getBody().status()).isEqualTo(ConsentStatus.REVOKED);
  }

  @Test
  void authoriseWithoutScaIsRejected() {
    String ref = "CR-" + UUID.randomUUID();
    rest.postForEntity(
        "/api/v1/open-banking/consents",
        new RequestConsentRequest(
            ref, UUID.randomUUID(), "FinTechCo", Set.of(ConsentScope.PAYMENT_INITIATION), 30),
        ConsentResponse.class);

    ResponseEntity<String> response =
        rest.postForEntity(
            "/api/v1/open-banking/consents/" + ref + "/authorise",
            new AuthoriseRequest("   "),
            String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }
}
