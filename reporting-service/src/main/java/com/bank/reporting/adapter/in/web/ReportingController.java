package com.bank.reporting.adapter.in.web;

import com.bank.reporting.application.ReportingService;
import com.bank.reporting.application.ReportingService.DailyReport;
import com.bank.reporting.domain.ReportMetric;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reports")
public class ReportingController {

    private final ReportingService reportingService;

    public ReportingController(ReportingService reportingService) {
        this.reportingService = reportingService;
    }

    @PostMapping("/metrics")
    public ResponseEntity<MetricResponse> record(@Valid @RequestBody RecordMetricRequest request) {
        ReportMetric metric = reportingService.record(
                request.businessDate(), request.metricKey(), request.value(), request.source());
        return ResponseEntity.status(201)
                .body(new MetricResponse(metric.getId(), metric.getMetricKey(), metric.getMetricValue()));
    }

    @GetMapping("/daily")
    public DailyReport daily(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return reportingService.dailyReport(date);
    }

    public record RecordMetricRequest(
            @NotNull LocalDate businessDate,
            @NotBlank String metricKey,
            @NotNull BigDecimal value,
            @NotBlank String source) {
    }

    public record MetricResponse(UUID id, String metricKey, BigDecimal value) {
    }
}
