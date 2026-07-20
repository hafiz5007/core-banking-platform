package com.bank.reporting.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.bank.reporting.AbstractIntegrationTest;
import com.bank.reporting.adapter.in.web.ReportingController.RecordMetricRequest;
import com.bank.reporting.application.ReportingService.DailyReport;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** End-to-end test of metric ingestion and the daily report aggregation. */
class ReportingControllerIT extends AbstractIntegrationTest {

    @Autowired
    TestRestTemplate rest;

    @Test
    void ingestsMetricsAndAggregatesDailyReport() {
        LocalDate date = LocalDate.now();
        rest.postForEntity("/api/v1/reports/metrics",
                new RecordMetricRequest(date, "postings.count", new BigDecimal("100"), "ledger-service"),
                Object.class);
        rest.postForEntity("/api/v1/reports/metrics",
                new RecordMetricRequest(date, "postings.count", new BigDecimal("50"), "ledger-service"),
                Object.class);
        rest.postForEntity("/api/v1/reports/metrics",
                new RecordMetricRequest(date, "settlement.total", new BigDecimal("1234.56"), "payment-service"),
                Object.class);

        ResponseEntity<DailyReport> report =
                rest.getForEntity("/api/v1/reports/daily?date=" + date, DailyReport.class);

        assertThat(report.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(report.getBody().metricCount()).isEqualTo(3);
        assertThat(report.getBody().totalsByKey().get("postings.count")).isEqualByComparingTo("150");
        assertThat(report.getBody().totalsByKey().get("settlement.total")).isEqualByComparingTo("1234.56");
    }
}
