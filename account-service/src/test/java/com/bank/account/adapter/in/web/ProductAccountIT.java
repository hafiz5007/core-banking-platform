package com.bank.account.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.bank.account.AbstractIntegrationTest;
import com.bank.account.adapter.in.web.dto.AccountHolderResponse;
import com.bank.account.adapter.in.web.dto.AccountResponse;
import com.bank.account.adapter.in.web.dto.CreateProductRequest;
import com.bank.account.adapter.in.web.dto.OpenFromProductRequest;
import com.bank.account.adapter.in.web.dto.ProductResponse;
import com.bank.account.domain.AccountType;
import com.bank.account.domain.MandateType;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** End-to-end test of the product catalogue and opening a joint account from a product. */
class ProductAccountIT extends AbstractIntegrationTest {

    @Autowired
    TestRestTemplate rest;

    @Test
    void createsProductAndOpensJointAccountFromIt() {
        String code = "SAV-" + System.nanoTime();
        CreateProductRequest product = new CreateProductRequest(code, "Premium Savings",
                AccountType.SAVINGS, "USD", new BigDecimal("3.50"), new BigDecimal("0.00"),
                new BigDecimal("100.00"), new BigDecimal("10000.00"), new BigDecimal("0.00"));
        ResponseEntity<ProductResponse> created =
                rest.postForEntity("/api/v1/products", product, ProductResponse.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        UUID primary = UUID.randomUUID();
        UUID joint = UUID.randomUUID();
        OpenFromProductRequest open = new OpenFromProductRequest(code, primary, List.of(joint), MandateType.JOINT);
        ResponseEntity<AccountResponse> account =
                rest.postForEntity("/api/v1/accounts/from-product", open, AccountResponse.class);

        assertThat(account.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        AccountResponse body = account.getBody();
        assertThat(body).isNotNull();
        assertThat(body.productCode()).isEqualTo(code);
        assertThat(body.accountType()).isEqualTo(AccountType.SAVINGS);
        assertThat(body.currencyCode()).isEqualTo("USD");
        assertThat(body.mandateType()).isEqualTo(MandateType.JOINT);
        assertThat(body.minBalance()).isEqualTo("100.00");

        ResponseEntity<List<AccountHolderResponse>> holders = rest.exchange(
                "/api/v1/accounts/" + body.id() + "/holders", HttpMethod.GET, null,
                new ParameterizedTypeReference<>() { });
        assertThat(holders.getBody()).hasSize(2);
    }
}
