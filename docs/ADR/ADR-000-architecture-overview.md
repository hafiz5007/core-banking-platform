# ADR-000 — Architecture Overview

- **Status:** Accepted
- **Date:** 2026-09-14 (supersedes the original 2026-07-19 draft, which described a polyglot
  design that was evaluated and not built — see *Alternatives considered*)
- **Scope:** the whole platform

---

## Context

A retail core banking platform has to do several things that are individually well understood but
awkward together:

- Move money between accounts without ever losing or duplicating a posting, including when a
  downstream rail rejects the transfer after the books have already moved.
- Keep an authoritative set of books that can be proven correct at any moment.
- Screen customers and transactions against sanctions and AML rules, and fail *closed* when the
  screening system is unavailable.
- Talk to external rails — domestic clearing, SWIFT, card networks, KYC providers — none of which
  are available during development.
- Stay auditable end to end, because a regulator can ask who changed what and when.

This ADR records the overall shape chosen to satisfy those constraints, and the boundaries that
were deliberately drawn. Each significant decision has its own ADR; this one is the map.

## Decision

Build the platform as **thirteen Maven modules in a single reactor**: eleven runnable Spring Boot
services (ten business services plus an API gateway) and two shared libraries, all on **Java 21 and
Spring Boot 3.4**.

**Service boundaries follow the banking domain**, not technical layers:

| Service | Owns |
|---------|------|
| `customer-service` | Onboarding, KYC, sanctions/PEP screening, consent, Open Banking TPP consents |
| `account-service` | Accounts, product catalogue, holders and mandates, holds and limits |
| `ledger-service` | The authoritative double-entry general ledger |
| `payment-service` | The payment engine and every rail strategy |
| `card-service` | Issuance, authorization, settlement, clearing reconciliation |
| `interest-fee-service` | Accrual, capitalization, fees, end-of-day batch |
| `risk-aml-service` | Transaction monitoring, alerts, cases, SAR filing |
| `notification-service` | Alerts, OTP, statement dispatch |
| `admin-service` | RBAC, maker-checker, immutable audit trail |
| `reporting-service` | Operational and regulatory reporting |
| `api-gateway` | The external front door |

Plus `common-lib` (the `Money` type, RFC 9457 error model, correlation-id and tenant propagation,
service-auth interceptors) and `ledger-grpc-api` (the gRPC contract, owned by neither caller nor
callee).

Five decisions hold the design together:

1. **The ledger is the single source of truth for money** and is append-only. Entries are immutable
   and balanced at write time; a mistake is corrected by a reversing entry, never by an update or
   delete. A trial-balance endpoint proves the invariant on demand. → [ADR-003](ADR-003-ledger-double-entry.md)

2. **Anything that moves money is idempotent and compensating.** Every mutating call carries an
   idempotency key, so a retry returns the original result rather than posting twice. Payments run
   as a saga: if a rail rejects after the ledger has been posted, the saga reverses the posting and
   the payment ends `COMPENSATED` rather than silently inconsistent. → [ADR-012](ADR-012-iso20022-payments.md)

3. **Events are published through a transactional outbox**, written in the same database
   transaction as the state change and relayed to Kafka afterwards. This removes the dual-write
   failure where the database commits and the broker does not. → [ADR-004](ADR-004-outbox-pattern.md),
   [ADR-005](ADR-005-kafka-kraft.md)

4. **Every external integration sits behind a port** with a working stub adapter, selected by
   configuration rather than by code. The platform boots, and its entire test suite runs, with no
   external dependency whatsoever — and the same binary talks to a real provider when configured to.

5. **Synchronous where correctness needs an answer now, asynchronous where it does not.** A payment
   must know the ledger accepted the posting before it confirms, so that call is synchronous — over
   gRPC on the hot path between account and ledger ([ADR-007](ADR-007-grpc-account-ledger.md)),
   REST elsewhere. A customer alert does not block a payment, so notification is driven off Kafka
   events ([ADR-014](ADR-014-notification-async-kafka.md)).

**Internal structure.** Every service uses the same hexagonal layout: `domain/` holds aggregates and
value objects and imports no framework; `application/` holds use-cases and declares what it needs as
outbound `port/` interfaces; `adapter/in|out/` implements those ports. The dependency direction is
always inward. This is enforced by review, and it is what makes the stub/real adapter swap a
configuration change.

**Data.** One PostgreSQL instance, a private schema per service, forward-only Flyway migrations, and
Hibernate `ddl-auto: validate` so a service refuses to start if its entities and its migrated schema
have drifted. A schema boundary rather than a database boundary is a deliberate trade: it preserves
per-service data ownership and migration independence at a fraction of the local operational cost.
→ [ADR-006](ADR-006-postgresql-datastore.md)

**Security.** OAuth2 at the edge on the gateway; short-lived JWTs on east-west calls over both REST
and gRPC, off by default so the platform runs without an identity provider
([ADR-008](ADR-008-jwt-service-auth.md)). Rate limiting before routing
([ADR-013](ADR-013-rate-limiting-gateway.md)).

**Correctness of money itself.** A dedicated `Money` value object over `BigDecimal` plus an ISO-4217
currency, because `BigDecimal` alone cannot tell `GBP 100` from `USD 100` and silent currency mixing
is a critical bug class in financial software. → [ADR-010](ADR-010-money-type.md)

**Testing.** Integration tests run against a real PostgreSQL 16 in Testcontainers rather than an
in-memory substitute, so migrations, SQL and JPA mappings are exercised as they will actually run.
→ [ADR-009](ADR-009-testcontainers.md)

## In scope / out of scope

| Capability | In scope | Out of scope |
|------------|----------|--------------|
| Customer management | Lifecycle, KYC hooks, screening, consent, risk rating | Document capture, biometrics, sanctions escalation workflow |
| Accounts | Open/freeze/close, product catalogue, mandates, holds, limits | Overdraft management, complex interest edge cases |
| General ledger | Double-entry postings, reversal, trial balance, multi-currency | Hedge accounting, off-balance-sheet items |
| Payments | Intrabank, instant/RTGS/ACH, cross-border, FX, standing orders, direct debit, P2P, bill pay | Real rail connectivity |
| Cards | Issuance, authorization, holds, settlement, clearing reconciliation | Real card-network authorisation |
| Risk & AML | Monitoring rules, alerts, cases, SAR | Production-grade rule engine, ML models |
| Observability | Correlation ids, metrics, health, OpenAPI | SLO burn-rate alerting, cost dashboards |

## Alternatives considered

**A modular monolith.** Faster to build and far simpler to operate. Rejected because the problems
worth solving here — idempotency across a service boundary, compensation when a remote call fails,
effectively-once event delivery, contract ownership — only exist once the boundaries are real. In a
monolith they collapse into method calls and the design demonstrates nothing.

**A polyglot estate (Java + Kotlin + .NET) with per-service databases, Debezium CDC and Redis.**
This was the original plan and is what the superseded draft of this ADR described. Rejected during
implementation: three build ecosystems and three sets of idioms multiplied the maintenance cost
without changing a single architectural property, and CDC via Debezium added a piece of
infrastructure to operate for an outbox relay that a scheduled poller handles correctly at this
scale. The estate is now uniformly Java 21, and the outbox is relayed by a scheduled publisher.

**Full event sourcing as the universal persistence model.** Rejected as over-engineering. Event
sourcing earns its complexity for the ledger, where the journal genuinely *is* the state — and the
ledger is modelled that way. Applying it to customer records and product catalogues would have cost
weeks and bought nothing.

**Kubernetes-first with no Docker Compose.** Rejected: it turns local development into an ops
project. Compose is the primary local target; a Kubernetes deployment is a later concern.

## Consequences

**What this buys.** The failure modes that matter in payments are addressed explicitly rather than
implicitly: no double posting, no orphaned posting after a rail rejection, no committed state whose
event was lost, no cross-currency arithmetic. Boundaries are enforced by the module graph, so a
violation is a compile error rather than a code-review argument. Every external dependency can be
stubbed, so the test suite is hermetic and fast.

**What it costs.** Eleven services to run locally, and a reactor build that takes minutes rather
than seconds. Cross-service changes require a contract change first. Debugging a payment means
following a correlation id across four services rather than reading one stack trace.

**What is deliberately unfinished.** Every external integration is a stub. Service-to-service auth
uses a shared HMAC secret, which is adequate for local and CI use but means every holder can mint as
well as verify — production needs RS256 + JWKS. Edge OAuth2 is off by default. These are tracked,
with priorities, in [`../PENDING_TASKS.md`](../PENDING_TASKS.md).

## References

- [`../SERVICE_WIRING.md`](../SERVICE_WIRING.md) — call graph and security model
- [`../development-guidance.md`](../development-guidance.md) — build, run, debug, test, publish
- [`../GO_LIVE_READINESS.md`](../GO_LIVE_READINESS.md) — release gate and hardening checklist
- [`../walkthroughs/`](../walkthroughs/) — one narrative per service
