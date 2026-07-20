package com.bank.interestfee.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.bank.interestfee.AbstractIntegrationTest;
import com.bank.interestfee.adapter.in.web.dto.OpenPositionRequest;
import com.bank.interestfee.adapter.in.web.dto.PositionResponse;
import com.bank.interestfee.application.InterestService.EodSummary;
import com.bank.interestfee.domain.DayCountBasis;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** End-to-end test of interest accrual, capitalization and the EOD batch. */
@Import(AbstractIntegrationTest.FakeLedgerConfig.class)
class InterestControllerIT extends AbstractIntegrationTest {

    @Autowired
    TestRestTemplate rest;

    @Test
    void opensAccruesAndCapitalizes() {
        String account = "ACC-" + System.nanoTime();
        OpenPositionRequest open = new OpenPositionRequest(
                account, "USD", new BigDecimal("3.65"), new BigDecimal("10000.00"), DayCountBasis.ACT_365);

        ResponseEntity<PositionResponse> created =
                rest.postForEntity("/api/v1/interest/positions", open, PositionResponse.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Accrue forward 10 days from today.
        java.time.LocalDate upTo = java.time.LocalDate.now().plusDays(10);
        ResponseEntity<PositionResponse> accrued = rest.postForEntity(
                "/api/v1/interest/positions/" + account + "/accrue?upTo=" + upTo, null, PositionResponse.class);
        assertThat(accrued.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(new BigDecimal(accrued.getBody().accruedInterest())).isGreaterThan(BigDecimal.ZERO);

        // Capitalize — accrued resets to zero, principal grows.
        ResponseEntity<PositionResponse> capitalized = rest.postForEntity(
                "/api/v1/interest/positions/" + account + "/capitalize", null, PositionResponse.class);
        assertThat(capitalized.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(new BigDecimal(capitalized.getBody().accruedInterest())).isEqualByComparingTo("0");
        assertThat(new BigDecimal(capitalized.getBody().principal())).isGreaterThan(new BigDecimal("10000.00"));
    }

    @Test
    void runsEndOfDayBatch() {
        String account = "EOD-" + System.nanoTime();
        rest.postForEntity("/api/v1/interest/positions",
                new OpenPositionRequest(account, "USD", new BigDecimal("5.00"),
                        new BigDecimal("5000.00"), DayCountBasis.ACT_365), PositionResponse.class);

        java.time.LocalDate businessDate = java.time.LocalDate.now().plusDays(30);
        ResponseEntity<EodSummary> eod = rest.postForEntity(
                "/api/v1/interest/eod/run?businessDate=" + businessDate, null, EodSummary.class);

        assertThat(eod.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(eod.getBody()).isNotNull();
        assertThat(eod.getBody().positions()).isGreaterThanOrEqualTo(1);
    }
}
