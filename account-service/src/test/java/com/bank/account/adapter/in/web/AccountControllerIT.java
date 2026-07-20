package com.bank.account.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.bank.account.AbstractIntegrationTest;
import com.bank.account.adapter.in.web.dto.AccountResponse;
import com.bank.account.adapter.in.web.dto.OpenAccountRequest;
import com.bank.account.domain.AccountStatus;
import com.bank.account.domain.AccountType;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** End-to-end test of the walking skeleton: HTTP -> service -> PostgreSQL -> HTTP. */
class AccountControllerIT extends AbstractIntegrationTest {

    @Autowired
    TestRestTemplate rest;

    @Test
    void opensAndReadsAnAccount() {
        OpenAccountRequest request =
                new OpenAccountRequest(UUID.randomUUID(), AccountType.SAVINGS, "USD");

        ResponseEntity<AccountResponse> created =
                rest.postForEntity("/api/v1/accounts", request, AccountResponse.class);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        AccountResponse body = created.getBody();
        assertThat(body).isNotNull();
        assertThat(body.id()).isNotNull();
        assertThat(body.accountNumber()).isNotBlank();
        assertThat(body.status()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(body.balance()).isEqualTo("0.00");
        assertThat(body.currencyCode()).isEqualTo("USD");

        ResponseEntity<AccountResponse> fetched =
                rest.getForEntity("/api/v1/accounts/" + body.id(), AccountResponse.class);

        assertThat(fetched.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(fetched.getBody()).isNotNull();
        assertThat(fetched.getBody().id()).isEqualTo(body.id());
    }

    @Test
    void returns404ForUnknownAccount() {
        ResponseEntity<String> response =
                rest.getForEntity("/api/v1/accounts/" + UUID.randomUUID(), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void rejectsInvalidCurrency() {
        var bad = new OpenAccountRequest(UUID.randomUUID(), AccountType.CURRENT, "usd");
        ResponseEntity<String> response =
                rest.postForEntity("/api/v1/accounts", bad, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
