package com.bank.payment.adapter.out.outbox;

import com.bank.payment.application.port.EventBrokerPort;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Polls the outbox and forwards pending events to the event backbone (ADR-004, ADR-005).
 *
 * <p>A row is marked PUBLISHED only after the broker has acknowledged it. If a publish fails the row
 * stays PENDING and is retried on the next pass, so an event is never lost — at the cost of possibly
 * being delivered twice, which is why consumers must be idempotent (ADR-014).
 *
 * <p>Publishing happens one row at a time and a failure stops the batch: events for an aggregate are
 * ordered, and skipping past a failure would publish a later event before an earlier one.
 */
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxEventRepository repository;
    private final EventBrokerPort broker;
    private final String topic;

    public OutboxRelay(OutboxEventRepository repository,
                       EventBrokerPort broker,
                       @Value("${kafka.topics.payment-events:payment.events}") String topic) {
        this.repository = repository;
        this.broker = broker;
        this.topic = topic;
    }

    @Scheduled(fixedDelayString = "${outbox.relay.delay-ms:5000}")
    @Transactional
    public void flush() {
        List<OutboxEvent> pending = repository.findTop100ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING);
        if (pending.isEmpty()) {
            return;
        }
        int published = 0;
        for (OutboxEvent event : pending) {
            try {
                // Key by aggregate id so all events for one payment land on the same partition and
                // therefore keep their order.
                broker.publish(topic, event.getAggregateId().toString(), event.getPayload());
            } catch (RuntimeException e) {
                log.warn("Outbox publish failed for event {} ({}); leaving it PENDING to retry: {}",
                        event.getId(), event.getEventType(), e.getMessage());
                break;
            }
            event.markPublished();
            published++;
        }
        repository.saveAll(pending.subList(0, published));
        if (published > 0) {
            log.info("Relayed {} of {} pending outbox event(s) to {}", published, pending.size(), topic);
        }
    }
}
