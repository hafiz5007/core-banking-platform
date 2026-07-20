package com.bank.card.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.bank.card.AbstractIntegrationTest;
import com.bank.card.adapter.in.web.dto.AuthorizationResponse;
import com.bank.card.adapter.in.web.dto.AuthorizeRequest;
import com.bank.card.adapter.in.web.dto.CardResponse;
import com.bank.card.adapter.in.web.dto.IssueCardRequest;
import com.bank.card.domain.AuthorizationStatus;
import com.bank.card.domain.CardChannel;
import com.bank.card.domain.CardStatus;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** End-to-end test of issuance and real-time authorization. */
class CardControllerIT extends AbstractIntegrationTest {

    @Autowired
    TestRestTemplate rest;

    private CardResponse issue(String available) {
        return rest.postForEntity("/api/v1/cards",
                new IssueCardRequest("ACC-1", "USD", new BigDecimal(available)), CardResponse.class).getBody();
    }

    @Test
    void issuesAndAuthorizesWithHold() {
        CardResponse card = issue("100.00");
        assertThat(card).isNotNull();
        assertThat(card.status()).isEqualTo(CardStatus.ACTIVE);
        assertThat(card.maskedNumber()).startsWith("**** **** **** ");

        ResponseEntity<AuthorizationResponse> auth = rest.postForEntity(
                "/api/v1/cards/" + card.id() + "/authorizations",
                new AuthorizeRequest(new BigDecimal("30.00"), "USD", "Coffee Shop", CardChannel.POS, false),
                AuthorizationResponse.class);
        assertThat(auth.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(auth.getBody().status()).isEqualTo(AuthorizationStatus.APPROVED);

        CardResponse after = rest.getForEntity("/api/v1/cards/" + card.id(), CardResponse.class).getBody();
        assertThat(new BigDecimal(after.availableBalance())).isEqualByComparingTo("70.00");
    }

    @Test
    void declinesWhenInsufficientFunds() {
        CardResponse card = issue("10.00");
        ResponseEntity<AuthorizationResponse> auth = rest.postForEntity(
                "/api/v1/cards/" + card.id() + "/authorizations",
                new AuthorizeRequest(new BigDecimal("50.00"), "USD", "Electronics", CardChannel.POS, false),
                AuthorizationResponse.class);
        assertThat(auth.getBody().status()).isEqualTo(AuthorizationStatus.DECLINED);
    }

    @Test
    void reversalReleasesHold() {
        CardResponse card = issue("100.00");
        AuthorizationResponse auth = rest.postForEntity(
                "/api/v1/cards/" + card.id() + "/authorizations",
                new AuthorizeRequest(new BigDecimal("40.00"), "USD", "Hotel", CardChannel.POS, false),
                AuthorizationResponse.class).getBody();

        rest.postForEntity("/api/v1/cards/authorizations/" + auth.id() + "/reversal", null,
                AuthorizationResponse.class);

        CardResponse after = rest.getForEntity("/api/v1/cards/" + card.id(), CardResponse.class).getBody();
        assertThat(new BigDecimal(after.availableBalance())).isEqualByComparingTo("100.00");
    }
}
