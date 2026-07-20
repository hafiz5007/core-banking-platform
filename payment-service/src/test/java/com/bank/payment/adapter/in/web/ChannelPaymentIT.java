package com.bank.payment.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.bank.payment.AbstractIntegrationTest;
import com.bank.payment.adapter.in.web.ChannelPaymentController.BillRequest;
import com.bank.payment.adapter.in.web.ChannelPaymentController.P2pRequest;
import com.bank.payment.adapter.in.web.ChannelPaymentController.RegisterAliasRequest;
import com.bank.payment.adapter.in.web.ChannelPaymentController.RegisterBillerRequest;
import com.bank.payment.adapter.in.web.dto.PaymentResponse;
import com.bank.payment.domain.PaymentStatus;
import com.bank.payment.domain.directory.AliasType;
import com.bank.payment.testsupport.FakeLedgerConfig;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** End-to-end tests of P2P-by-alias and bill payment, executing through the (faked) ledger. */
@Import(FakeLedgerConfig.class)
class ChannelPaymentIT extends AbstractIntegrationTest {

    @Autowired
    TestRestTemplate rest;

    @Test
    void p2pResolvesAliasAndPays() {
        String alias = "alice-" + UUID.randomUUID() + "@example.com";
        rest.postForEntity("/api/v1/aliases",
                new RegisterAliasRequest(alias, AliasType.EMAIL, "2000"), Object.class);

        ResponseEntity<PaymentResponse> p2p = rest.postForEntity("/api/v1/payments/p2p",
                new P2pRequest("p2p-" + UUID.randomUUID(), "1000", alias, new BigDecimal("25.00"), "USD"),
                PaymentResponse.class);

        assertThat(p2p.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(p2p.getBody().status()).isEqualTo(PaymentStatus.CONFIRMED);
        assertThat(p2p.getBody().creditorAccount()).isEqualTo("2000");
    }

    @Test
    void billPaymentResolvesBillerAndPays() {
        String code = "UTIL-" + UUID.randomUUID();
        rest.postForEntity("/api/v1/billers",
                new RegisterBillerRequest(code, "City Power", "9000"), Object.class);

        ResponseEntity<PaymentResponse> bill = rest.postForEntity("/api/v1/payments/bill",
                new BillRequest("bill-" + UUID.randomUUID(), "1000", code, "ACC-REF-42",
                        new BigDecimal("60.00"), "USD"),
                PaymentResponse.class);

        assertThat(bill.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(bill.getBody().status()).isEqualTo(PaymentStatus.CONFIRMED);
        assertThat(bill.getBody().creditorAccount()).isEqualTo("9000");
    }

    @Test
    void unknownAliasIsNotFound() {
        ResponseEntity<String> response = rest.postForEntity("/api/v1/payments/p2p",
                new P2pRequest("x-" + UUID.randomUUID(), "1000", "nobody@example.com",
                        new BigDecimal("10.00"), "USD"),
                String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
