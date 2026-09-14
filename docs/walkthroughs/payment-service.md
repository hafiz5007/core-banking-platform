# Walkthrough: payment-service

## Purpose (2 sentences)
Payment-service owns payment initiation and its lifecycle across every rail the platform supports: intrabank transfers, external clearing (instant / RTGS / ACH), and cross-border with FX. It also owns the recurring and channel conveniences built on top of payments — standing orders, direct-debit mandates, P2P by alias, and bill payments — and it is the platform's first event producer.

At 64 main classes it is the largest service in the repository, and the one where most of the interesting distributed-systems behaviour lives: idempotency, a compensating saga, the transactional outbox, and two authenticated outbound integrations.

## Public API surface
- `POST /api/v1/payments` initiate a payment (rail chosen by `type`, defaults to intrabank)
- `GET /api/v1/payments/{id}` fetch a payment
- `POST /api/v1/payments/{id}/return` return a settled payment (writes a compensating ledger entry)
- `GET /api/v1/payments/{id}/change-log` append-only change history for the payment
- `POST /api/v1/payments/p2p` pay a person by registered alias
- `POST /api/v1/payments/bill` pay a registered biller
- `POST /api/v1/aliases` register a P2P alias (phone / email → account code)
- `POST /api/v1/billers` register a biller
- `POST /api/v1/standing-orders` create a standing order
- `GET /api/v1/standing-orders/{id}` fetch a standing order
- `POST /api/v1/standing-orders/{id}/cancel` cancel a standing order
- `POST /api/v1/standing-orders/run` run due standing orders (scheduled batch, also callable)
- `POST /api/v1/direct-debits/mandates` create a mandate
- `GET /api/v1/direct-debits/mandates/{reference}` fetch a mandate
- `POST /api/v1/direct-debits/mandates/{reference}/cancel` cancel a mandate
- `POST /api/v1/direct-debits/mandates/{reference}/collect` collect against a mandate
- **Events published:** `payment.events` — `PaymentPosted` (a payment reached the ledger) and `PaymentReturned` (a settled payment was reversed). Keyed by payment id.
- **Events consumed:** none. Payment-service is a producer only.

## Data model (1 paragraph)
The service owns seven tables in its private `payment` schema: `payment`, `outbox_event`, `standing_order`, `direct_debit_mandate`, `payment_alias`, `biller`, and `change_log`. `payment` is the aggregate root — idempotency key (unique), rail type, debtor and creditor account codes, amount and currency, narrative, status, optimistic-locking version, and the ledger entry ids for both the original posting and any reversal, which is what makes a return traceable back to the accounting record. `outbox_event` holds domain events written in the same transaction as the state change they describe, with a `PENDING`/`PUBLISHED` status the relay advances. `standing_order` and `direct_debit_mandate` carry the recurring instructions and their schedules; `payment_alias` and `biller` are small directories that resolve a human-facing identifier to an account code before a payment is created. Nothing is shared: the payment lifecycle has invariants (idempotency, status transitions, version) that must be atomic in one bounded context, and the ledger's own state lives behind an API in ledger-service rather than in these tables.

## Key design decisions (3-5)

- Decision: Idempotency on a caller-supplied key, checked first.
- Why: `initiate` returns the original payment for a replayed key before doing anything else, so a client retry after a timeout cannot double-spend. The key is unique in the database, so the guarantee survives concurrent retries rather than relying on the check alone.
- Alternative considered: Transport-level retries with dedup at the gateway.
- Trade-off accepted: Callers must supply and manage a key, but the guarantee is enforced by the system of record instead of by infrastructure that can be bypassed.

- Decision: Fulfil payments as a compensating saga, not a distributed transaction.
- Why: A payment spans this service and ledger-service, which have separate databases. `PaymentRailHandler.execute` posts to the ledger; if the rail then fails, the handler reverses its own posting and the payment moves to `COMPENSATED` carrying the reversal entry id. The books are always balanced, and the failure is a recorded state rather than a lost write.
- Alternative considered: A two-phase commit / XA transaction across both databases.
- Trade-off accepted: There is a window in which a payment is posted but not yet confirmed, and the model needs an explicit `COMPENSATED` status. In exchange the services stay independently deployable and neither blocks the other's locks.

- Decision: Publish domain events through a transactional outbox rather than calling the broker inline.
- Why: Writing the event row in the same transaction as the payment removes the dual-write problem entirely — there is no state in which the payment is committed and the event is lost, or the event is published for a payment that rolled back. `OutboxRelay` polls and forwards afterwards.
- Alternative considered: Publishing to Kafka directly from the use case, inside or just after the transaction.
- Trade-off accepted: Delivery is asynchronous and at-least-once, so consumers must be idempotent (ADR-014), and there is a polling delay. Both are cheaper than losing events.

- Decision: The relay marks a row `PUBLISHED` only after the broker acknowledges it, and a failed publish stops the batch.
- Why: The original stub marked rows published unconditionally. Against a real broker that silently drops every event produced during an outage. Stopping the batch rather than skipping the failed row matters because events for one payment are ordered — skipping would deliver a later event before an earlier one.
- Alternative considered: Fire-and-forget sends, or continuing past a failed row and retrying it later.
- Trade-off accepted: The relay is slower (it awaits each ack) and one poison row can hold up its partition's backlog until it drains or is investigated. The alternative is silent, undetectable event loss.

- Decision: Every outbound call carries a service identity, and screening cannot be bypassed.
- Why: Payment-service calls ledger-service (`ledger:post`) and risk-aml-service (`risk:evaluate`), both over REST with a short-lived JWT attached by `JwtPropagationInterceptor` (ADR-008). Screening runs before any rail is touched, and `HttpRiskScreeningAdapter` catches nothing — an unreachable risk service propagates the failure out of `initiate` rather than defaulting to "allow".
- Alternative considered: Trusting the internal network, and treating a screening outage as "allow".
- Trade-off accepted: A risk-aml outage stops payments. For a financial platform that is the correct failure direction — an unscreened payment is worse than a refused one.

## Where the interesting code lives
- Domain logic: `payment-service/src/main/java/com/bank/payment/domain/` — `Payment` (status machine, ledger/reversal linkage), `domain/recurring/` (`StandingOrder`, `DirectDebitMandate`), `domain/directory/` (`PaymentAlias`, `Biller`)
- Use cases: `application/PaymentService.java` (the initiate saga), `application/ChannelPaymentService.java`, `application/DirectDebitService.java`, `application/StandingOrderService.java`
- Rails: `application/rail/` — `IntrabankPaymentHandler`, `ExternalClearingHandler`, `CrossBorderHandler`; `application/RoutingEngine.java` picks one from the payment type
- ISO 20022: `application/iso20022/` — `Iso20022MessageFactory`, `Pacs008Message`
- Ports (the hexagonal boundary): `application/port/` — `LedgerPort`, `ScreeningPort`, `ClearingPort`, `SwiftPort`, `FxPort`, `EventBrokerPort`, `PaymentEventPublisher`
- Adapters: `adapter/out/ledger/HttpLedgerAdapter`, `adapter/out/screening/HttpRiskScreeningAdapter`, `adapter/out/kafka/KafkaEventBrokerAdapter`, `adapter/out/outbox/` (`OutboxEvent`, `OutboxEventPublisher`, `OutboxRelay`), plus stub adapters for clearing / SWIFT / FX
- Inbound: `adapter/in/web/` (four controllers), `adapter/in/scheduler/` (standing-order run)
- Configuration: `src/main/resources/application.yml` — `kafka.*`, `screening.adapter`, `service-auth.*`, `outbox.relay.delay-ms`

## Design Q&A

- Q: "Why an outbox instead of just publishing to Kafka?" → A: Because the alternative is a dual write. Publishing inline means either committing the payment and then failing to publish (event lost), or publishing and then rolling back (event describes something that never happened). The outbox row is written in the payment's own transaction, so the two cannot disagree; the relay turns that into at-least-once delivery afterwards. The cost is asynchrony and duplicate delivery, which is why the consumer keys on `(topic, partition, offset)`.

- Q: "What happens if the ledger accepts the posting but the rail then fails?" → A: The rail handler reverses its own posting and throws `PaymentRailException` carrying the reversal entry id. The payment moves to `COMPENSATED` with that id recorded, so the books balance and the reversal is traceable from the payment. That is the saga's compensating action — there is no distributed transaction.

- Q: "How would you scale this to 10x?" → A: Three things, in order. First the outbox relay is a single scheduled poller with a `findTop100` batch — at 10x it becomes the bottleneck and needs either partitioned claim-based polling (`SELECT … FOR UPDATE SKIP LOCKED`) or a CDC reader (Debezium) as ADR-000 anticipated. Second, `payment.events` has three partitions; ordering is per payment id, so partitions can grow freely and consumers scale with them. Third, the synchronous screening call is on the hot path of every payment, so it needs a circuit breaker and a latency budget before it becomes the limiting factor — today an unreachable risk service fails payments outright, which is correct but not resilient.

- Q: "Why is the payment→ledger call REST when account→ledger is gRPC?" → A: History rather than design. ADR-007 chose gRPC for the hot internal path and it was implemented first for account-service's debit flow. Payment-service still uses `HttpLedgerAdapter`. Both now carry the same JWT service identity, so the security posture matches; the transport does not. Converting it is a contained change because the call already sits behind `LedgerPort`.

- Q: "What would you change with hindsight?" → A: The event payload is hand-built JSON in `OutboxEventPublisher`. That is fine for one topic and one consumer, but it makes the schema implicit and unversioned — any change silently breaks consumers at runtime. A schema registry with Avro or Protobuf, as ADR-000 assumed, should come before a second consumer exists. I would also move the outbox relay's topic decision out of `application.yml` and into the event itself, so one relay can serve several aggregate types.

## Follow-ups
- Only `payment.events` has a producer and a consumer. ADR-014 also names `account.events`, `card.events` and `customer.events`, which have neither.
- No schema registry; the event payload is hand-built JSON (see the flashcard above).
- `ClearingPort`, `SwiftPort` and `FxPort` are still stub adapters — real scheme connectivity, gpi/UETR tracking, and a live rate feed with quote TTL are all open P0 items in `docs/PENDING_TASKS.md`.
- The outbox has no housekeeping: `outbox_event` grows forever once rows are `PUBLISHED`. It needs a retention job.
- The relay polls on a fixed delay with no backoff; a broker outage produces a warning every cycle.
- Convert `HttpLedgerAdapter` to the gRPC bridge for consistency with account-service (ADR-007).
