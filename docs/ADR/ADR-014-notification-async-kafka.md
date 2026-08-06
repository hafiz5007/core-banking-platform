# ADR-014: Notification Service Async via Kafka (vs. Sync HTTP)

- Status: **Accepted**
- Date: 2026-08-06
- Deciders: Shardar Rahman
- Scope: notification-service

## Context

Multiple services need to trigger notifications when domain events occur:

- `account-service` → "Your account has been opened"
- `payment-service` → "Payment of £250.00 received"
- `card-service` → "Your card has been activated"
- `customer-service` → "KYC verification complete"

Two integration patterns:

1. **Synchronous HTTP** — the originating service calls `notification-service` directly via REST
   before returning to the client.
2. **Asynchronous Kafka** — the originating service publishes a domain event; `notification-service`
   consumes it independently and sends the notification.

## Decision

Use **asynchronous Kafka consumption** in `notification-service`:

**Producers (any service):** publish domain events to typed Kafka topics via the outbox pattern
(ADR-004). The notification concern is entirely absent from producer code — events are published for
domain reasons, not notification reasons.

```
Topics consumed by notification-service:
  account.events      → AccountOpened, AccountClosed, AccountFrozen
  payment.events      → PaymentReceived, PaymentSent, PaymentFailed
  card.events         → CardActivated, CardBlocked
  customer.events     → KycApproved, KycRejected
```

**Consumer (`notification-service`):**

```java
@KafkaListener(topics = "payment.events", groupId = "notification-service")
public void onPaymentEvent(PaymentEvent event) {
    NotificationRequest req = mapper.toNotification(event);
    notificationDispatcher.send(req);   // email / SMS / push via provider gateway
}
```

`notification-service` is the **single service** responsible for template rendering, channel
selection (email / SMS / push), provider integration (SendGrid, Twilio), and delivery retry. No
other service has an outbound notification concern.

**Idempotency:** each consumer records the Kafka `offset` + `partition` in a `processed_notifications`
table; duplicate events (Kafka at-least-once) produce duplicate DB checks but not duplicate
notifications.

## Alternatives considered

1. **Synchronous HTTP call from each service** — simplest for the caller; however:
   - Adds latency to the hot path (payment processing waits for email delivery).
   - `notification-service` downtime causes payment/account operations to fail or time out.
   - Each service must know `notification-service`'s API contract, creating tight coupling.
   - Retry logic must be duplicated in every calling service.
   All four concerns are eliminated by async Kafka. Rejected.

2. **Synchronous HTTP with circuit breaker (Resilience4j)** — reduces cascading failures; however,
   the fundamental coupling remains: if `notification-service` is degraded, the circuit stays open
   and notifications are silently dropped or the caller receives a partial success response.
   Rejected: silent notification loss in a degraded circuit is hard to observe and remediate.

3. **gRPC streaming from producer to notification-service** — provides backpressure and streaming;
   however, it is still a synchronous coupling for non-latency-sensitive notifications. Rejected:
   Kafka is a better fit for fan-out to multiple channels and replay capability.

4. **Polling model (notification-service polls other services' APIs)** — completely decoupled from
   the producer side; however, polling introduces latency, missed events between polls, and
   amplified read load on upstream services. Rejected.

## Consequences

- **Positive:** `notification-service` downtime does not affect account, payment, or card
  operations; Kafka consumer lag makes delivery delay observable; consumers can be replayed from an
  earlier offset if a notification bug is fixed and re-delivery is needed; no coupling between
  producer services and `notification-service`'s API.
- **Negative:** Notifications are eventually consistent — a payment may complete 200 ms before the
  notification arrives (acceptable; real banks behave the same way). Debugging requires correlating
  Kafka offsets with service logs.
- **Risks + mitigations:** Notification backlog during `notification-service` outage — Kafka retains
  events for the configured retention period (default 7 days); `notification-service` catches up
  automatically on restart. Provider delivery failures (email bounce, SMS error) — handled by an
  internal retry queue with exponential backoff and a dead-letter topic for manual inspection.

## References

- ADR-004 — Outbox pattern (events are published via outbox before Kafka)
- ADR-005 — Kafka KRaft (the broker notification-service consumes from)
- ADR-011 — Correlation-ID propagation (correlation ID is carried in Kafka message headers)
