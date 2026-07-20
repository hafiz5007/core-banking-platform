package com.bank.customer.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.bank.customer.AbstractIntegrationTest;
import com.bank.customer.adapter.in.web.dto.ChangeLogResponse;
import com.bank.customer.adapter.in.web.dto.CustomerResponse;
import com.bank.customer.adapter.in.web.dto.OnboardCustomerRequest;
import com.bank.customer.domain.ChangeType;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** Verifies organization capture from the tenant header and the change log (ADR-001/002). */
class OrganizationAndChangeLogIT extends AbstractIntegrationTest {

    @Autowired
    TestRestTemplate rest;

    @Test
    void capturesOrganizationAndLogsOnboarding() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Organization-Id", "ORG-BANKX");
        var request = new OnboardCustomerRequest("Ada", "Lovelace", LocalDate.of(1990, 1, 1),
                "GB", "ada@example.com", "+441234567890", "TAX123", true, "WEB");

        ResponseEntity<CustomerResponse> created = rest.postForEntity(
                "/api/v1/customers", new HttpEntity<>(request, headers), CustomerResponse.class);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody().organizationId()).isEqualTo("ORG-BANKX");

        ResponseEntity<List<ChangeLogResponse>> log = rest.exchange(
                "/api/v1/customers/" + created.getBody().id() + "/change-log",
                HttpMethod.GET, null, new ParameterizedTypeReference<>() { });

        assertThat(log.getBody()).isNotEmpty();
        assertThat(log.getBody().get(0).changeType()).isEqualTo(ChangeType.CREATE);
        assertThat(log.getBody().get(0).organizationId()).isEqualTo("ORG-BANKX");
        assertThat(log.getBody().get(0).entityType()).isEqualTo("Customer");
    }
}
