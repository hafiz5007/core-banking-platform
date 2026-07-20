package com.bank.payment.adapter.out.outbox;

import com.bank.payment.application.port.PaymentEventPublisher;
import com.bank.payment.domain.Payment;
import org.springframework.stereotype.Component;

/** Default publisher: writes a domain event to the transactional outbox table. */
@Component
public class OutboxEventPublisher implements PaymentEventPublisher {

    private final OutboxEventRepository repository;

    public OutboxEventPublisher(OutboxEventRepository repository) {
        this.repository = repository;
    }

    @Override
    public void publish(Payment payment, String eventType) {
        // A compact JSON payload; a schema-registry-backed serializer would replace this in production.
        String payload = """
                {"paymentId":"%s","idempotencyKey":"%s","type":"%s","debtor":"%s","creditor":"%s",\
                "amount":"%s","currency":"%s","status":"%s"}"""
                .formatted(payment.getId(), payment.getIdempotencyKey(), payment.getType(),
                        payment.getDebtorAccount(), payment.getCreditorAccount(),
                        payment.getAmount().toPlainString(), payment.getCurrencyCode(), payment.getStatus());
        repository.save(new OutboxEvent("Payment", payment.getId(), eventType, payload));
    }
}
