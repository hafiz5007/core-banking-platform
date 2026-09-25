package com.bank.interestfee.adapter.in.scheduler;

import com.bank.interestfee.application.InterestService;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Triggers the end-of-day accrual batch on a schedule. Disabled by default (run on demand via the
 * API); enable with {@code eod.scheduler.enabled=true} and a cron expression in production.
 */
@Component
@ConditionalOnProperty(value = "eod.scheduler.enabled", havingValue = "true")
public class EodScheduler {

  private static final Logger log = LoggerFactory.getLogger(EodScheduler.class);

  private final InterestService interestService;

  public EodScheduler(InterestService interestService) {
    this.interestService = interestService;
  }

  @Scheduled(cron = "${eod.scheduler.cron:0 0 23 * * *}")
  public void runEndOfDay() {
    var summary = interestService.runEndOfDay(LocalDate.now());
    log.info("Scheduled EOD complete: {}", summary);
  }
}
