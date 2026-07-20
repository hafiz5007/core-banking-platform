package com.bank.payment.adapter.in.scheduler;

import com.bank.payment.application.StandingOrderService;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Executes due standing orders on a schedule. Disabled by default (run on demand via the API);
 * enable with {@code standing-orders.scheduler.enabled=true} in production.
 */
@Component
@ConditionalOnProperty(value = "standing-orders.scheduler.enabled", havingValue = "true")
public class StandingOrderScheduler {

    private static final Logger log = LoggerFactory.getLogger(StandingOrderScheduler.class);

    private final StandingOrderService standingOrderService;

    public StandingOrderScheduler(StandingOrderService standingOrderService) {
        this.standingOrderService = standingOrderService;
    }

    @Scheduled(cron = "${standing-orders.scheduler.cron:0 30 0 * * *}")
    public void run() {
        int executed = standingOrderService.runDue(LocalDate.now());
        log.info("Scheduled standing-order run executed {} orders", executed);
    }
}
