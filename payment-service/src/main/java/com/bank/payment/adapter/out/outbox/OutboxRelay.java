package com.bank.payment.adapter.out.outbox;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Polls the outbox and forwards pending events to the event backbone. Here it logs and marks them
 * PUBLISHED; wiring an actual Kafka producer in is a drop-in change that does not touch callers.
 */
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxEventRepository repository;

    public OutboxRelay(OutboxEventRepository repository) {
        this.repository = repository;
    }

    @Scheduled(fixedDelayString = "${outbox.relay.delay-ms:5000}")
    @Transactional
    public void flush() {
        List<OutboxEvent> pending = repository.findTop100ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING);
        if (pending.isEmpty()) {
            return;
        }
        for (OutboxEvent event : pending) {
            // TODO: replace with a real broker publish (Kafka) — payload is broker-ready.
            log.info("Publishing outbox event {} type={} aggregate={}",
                    event.getId(), event.getEventType(), event.getAggregateId());
            event.markPublished();
        }
        repository.saveAll(pending);
    }
}
