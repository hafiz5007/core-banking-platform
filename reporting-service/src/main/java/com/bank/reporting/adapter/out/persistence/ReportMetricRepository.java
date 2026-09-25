package com.bank.reporting.adapter.out.persistence;

import com.bank.reporting.domain.ReportMetric;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportMetricRepository extends JpaRepository<ReportMetric, UUID> {
  List<ReportMetric> findByBusinessDateOrderByMetricKeyAsc(LocalDate businessDate);
}
