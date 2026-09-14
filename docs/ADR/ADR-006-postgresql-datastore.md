# ADR-006: PostgreSQL 16 as the Primary Datastore

- Status: **Accepted**
- Date: 2026-08-06
- Deciders: Shardar Rahman
- Scope: Platform (all services)

## Context

Each microservice needs a relational datastore. The decision has two layers:

1. **Which database engine?** Candidates: PostgreSQL, MySQL / MariaDB, CockroachDB, Amazon Aurora.
2. **Shared vs. per-service instance?** True microservice isolation dictates one DB per service
   (separate schemas/databases), but that means running 9+ separate instances locally.

## Decision

Use **PostgreSQL 16** as the database engine, with **one logical database per service** (separate
`POSTGRES_DB` or schema within a single PostgreSQL container for local development, separate
containers/instances in production).

Rationale for PostgreSQL:

- **JSONB columns** — used for outbox payloads, audit JSON, and flexible metadata without a
  separate document store.
- **Row-level security (RLS)** — native enforcement point for multi-tenancy (see ADR-002); RLS
  policies can enforce `organization_id` at the DB layer as a defence-in-depth backstop.
- **LISTEN / NOTIFY** — lightweight mechanism for the outbox relay to be notified of new rows
  without constant polling (enhancement path over the scheduler in ADR-004).
- **Full ACID transactions** — non-negotiable for a double-entry ledger and payment processing.
- **Prevalence in fintech** — PostgreSQL is the default choice across the sector, so its
  operational behaviour under financial workloads is well documented and widely understood.
- **Flyway** — Spring Boot + Flyway migrates PostgreSQL schemas on startup; no separate migration
  runner required.

Per-service isolation:

```
docker-compose:
  postgres:
    image: postgres:16
  # each service connects to its own DB:
  #   customer_db, account_db, ledger_db, payment_db, …
  # created by init SQL or Flyway migrations
```

In production, each service would connect to an independent PostgreSQL instance (or Aurora cluster)
to prevent a single DB outage from affecting all services.

## Alternatives considered

1. **MySQL 8 / MariaDB** — widely supported but lacks JSONB, has weaker RLS support, and is less
   common in UK fintech new builds. Rejected.
2. **CockroachDB** — distributed SQL with strong consistency and horizontal scaling; however, its
   PostgreSQL wire compatibility is imperfect (some Hibernate DDL statements require tweaks) and
   the operational model differs significantly. Rejected: complexity not warranted for demo scale.
3. **H2 in-memory (test only)** — fast but dialect differences from PostgreSQL have caused silent
   test-passes that fail in production (e.g. `JSONB` type, different index behaviour). Rejected in
   favour of Testcontainers with real PostgreSQL (see ADR-009).
4. **Shared single database for all services** — simplifies local setup but violates service
   isolation, makes independent schema evolution impossible, and creates a single point of failure.
   Rejected.

## Consequences

- **Positive:** Full ACID, JSONB, RLS, and LISTEN/NOTIFY without additional services; aligns with
  UK fintech norms; single container in Docker Compose with multiple databases inside.
- **Negative:** PostgreSQL 16 is not the absolute latest release; upgrade path exists (16 → 17) via
  `pg_upgrade` or dump/restore.
- **Risks + mitigations:** Cross-service joins become impossible with per-service DBs — this is
  intentional; use Kafka events and API composition instead. Schema migrations must be
  backward-compatible during rolling deploys — enforce with a no-destructive-migration policy in CI.

## References

- ADR-002 — Multi-tenancy (organization_id column; RLS mention)
- ADR-003 — Ledger design (relies on ACID for journal line + balance snapshot atomicity)
- ADR-004 — Outbox pattern (outbox_event table in PostgreSQL)
- ADR-009 — Testcontainers (real PostgreSQL in tests)
