package com.bank.payment.application.port;

import com.bank.payment.domain.Payment;

/**
 * Outbound port for emitting payment domain events. The default adapter uses the transactional
 * outbox pattern so an event is never lost and never published without its state change committing.
 */
public interface PaymentEventPublisher {

  void publish(Payment payment, String eventType);
}
