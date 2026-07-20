package com.bank.reporting.application;

import com.bank.reporting.adapter.out.persistence.ReportMetricRepository;
import com.bank.reporting.domain.ReportMetric;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Records metrics into the reporting projection and assembles operational/regulatory reports. */
@Service
public class ReportingService {

    private final ReportMetricRepository repository;

    public ReportingService(ReportMetricRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public ReportMetric record(LocalDate businessDate, String metricKey, BigDecimal value, String source) {
        return repository.save(new ReportMetric(businessDate, metricKey, value, source));
    }

    /** A daily operational report: every metric for the date, summed by key. */
    @Transactional(readOnly = true)
    public DailyReport dailyReport(LocalDate businessDate) {
        List<ReportMetric> metrics = repository.findByBusinessDateOrderByMetricKeyAsc(businessDate);
        Map<String, BigDecimal> totals = new LinkedHashMap<>();
        for (ReportMetric m : metrics) {
            totals.merge(m.getMetricKey(), m.getMetricValue(), BigDecimal::add);
        }
        return new DailyReport(businessDate, metrics.size(), totals);
    }

    public record DailyReport(LocalDate businessDate, int metricCount, Map<String, BigDecimal> totalsByKey) {
    }
}
