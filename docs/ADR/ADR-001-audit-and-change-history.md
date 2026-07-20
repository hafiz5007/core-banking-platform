# ADR-001: Auditing & Change History

- Status: **Accepted**
- Date: 2026
- Context: the platform must capture who created/updated/deleted each record, and retain an
  immutable trail for regulators. The question raised was whether to add a "logger microservice"
  that every service calls on each add/update/delete.

## Decision

**Do not build a synchronous logger microservice on the CRUD hot path.** Separate two concerns:

1. **Operational logs** — structured JSON to stdout with a correlation id (already implemented via
   `common-lib` `CorrelationIdFilter`), shipped by a collector (OpenTelemetry Collector / Fluent
   Bit) into an aggregator (Loki / ELK / Splunk). No custom service.

2. **Business change history (entity add/update/delete)** — captured **in-service and
   asynchronously**, never by blocking on a remote logger. Chosen approach, in order of preference:
   - **(a) Per-service change log** written in the same DB transaction as the change (this ADR's
     reference implementation — see `account-service` `change_log` + `ChangeLogRecorder`). Simple,
     transactional, queryable, no extra infra.
   - **(b) Outbox → Kafka → audit-consumer** for a consolidated, tamper-evident enterprise audit
     store (builds on the existing `payment-service` outbox). Use when a central immutable store is
     required.
   - **(c) Hibernate Envers** or **Debezium CDC** where automatic field-level history off the ORM /
     DB transaction log is preferred over hand-written change records.

`admin-service` remains the home of the **privileged-action** audit trail and the maker-checker
workflow; approach (b) can feed it from all services.

## Rationale

- A synchronous logger service adds latency to every write, becomes a runtime dependency (does a
  write fail if it is down?), and risks losing records — the opposite of an audit guarantee.
- Writing the change record in the same local transaction (approach a) guarantees the audit row
  commits atomically with the change, which a remote call cannot.

## Consequences

- Each service owns a `change_log` table and records CREATE/UPDATE/DELETE at the application layer
  (reference: `account-service`). Roll this out per service.
- For an enterprise-wide immutable store, add the outbox→Kafka→audit-consumer pipeline later without
  changing callers.
- Retention: change-log and audit rows are append-only and retained for the full regulatory period.
