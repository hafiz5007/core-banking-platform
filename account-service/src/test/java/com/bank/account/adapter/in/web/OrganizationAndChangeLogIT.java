package com.bank.account.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.bank.account.AbstractIntegrationTest;
import com.bank.account.adapter.in.web.dto.AccountResponse;
import com.bank.account.adapter.in.web.dto.ChangeLogResponse;
import com.bank.account.adapter.in.web.dto.OpenAccountRequest;
import com.bank.account.domain.AccountType;
import com.bank.account.domain.ChangeType;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** Verifies organization capture from the tenant header and the entity change log (ADR-001/002). */
class OrganizationAndChangeLogIT extends AbstractIntegrationTest {

  @Autowired TestRestTemplate rest;

  @Test
  void capturesOrganizationFromHeaderAndLogsCreation() {
    HttpHeaders headers = new HttpHeaders();
    headers.set("X-Organization-Id", "ORG-ACME");
    var request = new OpenAccountRequest(UUID.randomUUID(), AccountType.SAVINGS, "USD");

    ResponseEntity<AccountResponse> created =
        rest.postForEntity(
            "/api/v1/accounts", new HttpEntity<>(request, headers), AccountResponse.class);

    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(created.getBody().organizationId()).isEqualTo("ORG-ACME");

    ResponseEntity<List<ChangeLogResponse>> log =
        rest.exchange(
            "/api/v1/accounts/" + created.getBody().id() + "/change-log",
            HttpMethod.GET,
            null,
            new ParameterizedTypeReference<>() {});

    assertThat(log.getBody()).isNotEmpty();
    ChangeLogResponse entry = log.getBody().get(0);
    assertThat(entry.changeType()).isEqualTo(ChangeType.CREATE);
    assertThat(entry.organizationId()).isEqualTo("ORG-ACME");
    assertThat(entry.entityType()).isEqualTo("Account");
  }

  @Test
  void defaultsOrganizationWhenHeaderAbsent() {
    var request = new OpenAccountRequest(UUID.randomUUID(), AccountType.CURRENT, "USD");
    ResponseEntity<AccountResponse> created =
        rest.postForEntity("/api/v1/accounts", request, AccountResponse.class);
    assertThat(created.getBody().organizationId()).isEqualTo("DEFAULT");
  }
}
