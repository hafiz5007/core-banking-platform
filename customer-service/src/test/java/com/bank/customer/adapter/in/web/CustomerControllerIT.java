package com.bank.customer.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.bank.customer.AbstractIntegrationTest;
import com.bank.customer.adapter.in.web.dto.CustomerResponse;
import com.bank.customer.adapter.in.web.dto.OnboardCustomerRequest;
import com.bank.customer.domain.CustomerStatus;
import com.bank.customer.domain.KycStatus;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** End-to-end test of onboarding: HTTP -> service -> stub KYC/screening -> PostgreSQL -> HTTP. */
class CustomerControllerIT extends AbstractIntegrationTest {

  @Autowired TestRestTemplate rest;

  private OnboardCustomerRequest request(String lastName) {
    return new OnboardCustomerRequest(
        "Ada",
        lastName,
        LocalDate.of(1990, 1, 1),
        "GB",
        "ada@example.com",
        "+441234567890",
        "TAX123",
        true,
        "WEB");
  }

  @Test
  void onboardsAndActivatesACleanCustomer() {
    ResponseEntity<CustomerResponse> created =
        rest.postForEntity("/api/v1/customers", request("Lovelace"), CustomerResponse.class);

    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    CustomerResponse body = created.getBody();
    assertThat(body).isNotNull();
    assertThat(body.cif()).isNotBlank();
    assertThat(body.status()).isEqualTo(CustomerStatus.ACTIVE);
    assertThat(body.kycStatus()).isEqualTo(KycStatus.VERIFIED);
    assertThat(body.riskRating()).isNotNull();

    ResponseEntity<CustomerResponse> fetched =
        rest.getForEntity("/api/v1/customers/" + body.id(), CustomerResponse.class);
    assertThat(fetched.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(fetched.getBody()).isNotNull();
    assertThat(fetched.getBody().id()).isEqualTo(body.id());
  }

  @Test
  void sanctionedSurnameIsBlocked() {
    ResponseEntity<CustomerResponse> created =
        rest.postForEntity("/api/v1/customers", request("Sanctioned"), CustomerResponse.class);

    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(created.getBody()).isNotNull();
    assertThat(created.getBody().status()).isEqualTo(CustomerStatus.BLOCKED);
    assertThat(created.getBody().screeningMatch()).isTrue();
  }

  @Test
  void invalidNationalityIsRejected() {
    var bad =
        new OnboardCustomerRequest(
            "Ada",
            "Lovelace",
            LocalDate.of(1990, 1, 1),
            "GBR",
            "ada@example.com",
            null,
            null,
            true,
            "WEB");
    ResponseEntity<String> response = rest.postForEntity("/api/v1/customers", bad, String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void unknownCustomerReturns404() {
    ResponseEntity<String> response =
        rest.getForEntity("/api/v1/customers/" + UUID.randomUUID(), String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }
}
