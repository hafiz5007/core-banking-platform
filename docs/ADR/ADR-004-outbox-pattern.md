# ADR-004: Outbox Pattern for Effectively-Once Event Delivery

- Status: **Accepted**
- Date: 2026-08-06
- Deciders: Shardar Rahman
- Scope: Cross-cutting (payment-service reference implementation; applicable to all services)

## Context

Services need to publish domain events to Kafka (e.g. `PaymentInitiated`, `LedgerPosted`,
`AccountOpened`). The naive dual-write approach — update the DB and then publish to Kafka in the
same application method — is not atomic:

- If the DB commit succeeds but Kafka publish fails, the event is lost (underdelivery).
- If Kafka publish succeeds but the DB commit fails, downstream consumers act on a phantom event
  (overdelivery / phantom write).

Neither outcome is acceptable in a financial system.

## Decision

Use the **Transactional Outbox Pattern** across all services that need reliable event publishing:

1. **Write phase** — in the same DB transaction as the business state change, insert one row per
   event into an `outbox_event` table:
   ```
   outbox_event(id, aggregate_type, aggregate_id, event_type, payload JSONB,
                created_at, published_at, status)
   ```
   The business change and the outbox row commit atomically; Kafka is not touched.

2. **Relay phase** — a lightweight `OutboxRelayScheduler` (or Debezium CDC connector) polls for
   `status = PENDING` rows, publishes each to the correct Kafka topic, and marks the row
   `PUBLISHED`. The relay uses the outbox row `id` as the Kafka message key to preserve ordering
   per aggregate.

3. **Consumer idempotency** — because the relay may retry on transient failures, consumers must
   handle duplicate events. Each consumer checks a `processed_events` de-duplication table (or uses
   Kafka's idempotent producer + `enable.idempotence=true`) to skip already-applied events.

Together these give **effectively-once semantics** (at-least-once delivery + idempotent consumer).

## Alternatives considered

1. **Dual-write without outbox** — simplest code, but fails atomicity guarantee. Rejected for any
   financial event.
2. **Kafka transactions (exactly-once semantics)** — Kafka's `transactional.id` + `read_committed`
   gives exactly-once at the broker layer, but requires the DB write and Kafka publish to be
   coordinated via a two-phase commit or Saga. Without XA transactions this does not help with the
   DB-to-Kafka boundary. Rejected: adds broker configuration complexity without solving the root
   dual-write problem.
3. **Debezium CDC directly off the WAL** — captures DB changes without any application-level code;
   excellent for high-volume streams. Rejected for v1: requires an additional Kafka Connect
   deployment; the scheduled relay is simpler to operate and sufficient for the demo load target.
   Debezium is noted as the upgrade path when volume warrants it.
4. **Saga choreography only (no outbox)** — Sagas coordinate multi-service workflows via events but
   do not by themselves solve the dual-write problem within a single service step. Outbox and Saga
   are complementary, not alternatives.

## Consequences

- **Positive:** Atomic business-change + event publication; relay retries without data loss;
  consumer idempotency makes the whole pipeline resilient to network and broker failures.
- **Negative:** Extra `outbox_event` table per service; relay adds a small publication latency
  (poll interval, default 500 ms); developers must remember to write to the outbox in every
  transactional flow.
- **Risks + mitigations:** Outbox table growth — mitigated by a scheduled cleanup job that archives
  `PUBLISHED` rows older than the retention window. Relay downtime — mitigated by the relay being
  stateless and restartable; the outbox rows survive.

## References

- ADR-003 — Ledger immutable journal (posting events use the outbox)
- ADR-005 — Kafka KRaft mode
- [Transactional Outbox Pattern — microservices.io](https://microservices.io/patterns/data/transactional-outbox.html)
