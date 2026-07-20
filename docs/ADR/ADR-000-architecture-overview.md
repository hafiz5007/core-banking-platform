# ADR-000 — Architecture Overview

**Status:** Proposed
**Date:** 2026-07-19
**Deciders:** Shardar Rahman
**Consulted:** (none — solo build)
**Informed:** future interviewers

---

## Context

I'm building a portfolio-flagship Core Banking Platform to demonstrate multi-service system-design competency at Senior / Lead / Architect level. The audience is UK bank + fintech hiring managers who read READMEs during interview loops.

Every UK banking interview eventually asks: *"How would you design a core banking system?"* Portfolio projects that show a single microservice (auth, ledger, or KYC in isolation) partially answer this. A portfolio project that shows **seven services collaborating end-to-end**, uses **real ISO 20022 message formats**, and treats **reconciliation as a first-class concern** does the whole job.

Non-goal for this decision record: rebuild a real regulator-ready bank. That takes years of compliance work and a legal team. Everything below is scoped for a demo that answers senior-level design interviews in 45 minutes with real code to point at.

## Decision

Build a polyglot event-driven microservices platform, 7 services, 3 languages, deployed via Docker Compose locally and Helm on Kubernetes optionally. Kafka as the event backbone. Postgres per service. OpenTelemetry across the estate. Ships in three phases across Sprint Weeks 2-4 of the 6-month plan.

## System scope

| Capability | In scope | Out of scope |
| --- | --- | --- |
| Customer management | Basic lifecycle, PII-safe reads, KYC-lite hook | Full document capture, biometrics, sanctions escalation |
| Account lifecycle | Open, freeze, close, product catalogue (2-3 types) | Overdraft management, interest calculation edge cases |
| General ledger | Double-entry postings, multi-currency, balance projection | FX booking flows, hedge accounting, off-balance-sheet items |
| Payment routing | ISO 20022 `pain.001` → internal / Faster Payments / SWIFT stubs | Real rail connectivity, card-network authorisation |
| Reconciliation | T+1 batch, mock counterparty CSV, break management | Real Nostro reconciliation, corporate action processing |
| Statement generation | End-of-day balance snapshot, ISO 20022 `camt.053`, PDF | Regulatory statement variants, MT940 legacy support |
| API gateway | OAuth 2.0, per-endpoint rate limiting, distributed tracing | Bot detection, DDoS mitigation, WAF rules |
| Observability | Traces + metrics + logs, one distributed-trace demo | SLO burn-rate alerts, cost dashboards |

## Services (7 total)

```
core-banking-platform/
├── libs/
│   ├── shared-types/            # Money, AccountId, TransactionId — value objects
│   ├── shared-events/           # Avro / Protobuf schemas — Kafka contract
│   └── shared-observability/    # OpenTelemetry setup for cross-language sameness
├── services/
│   ├── customer-service/        # Java 21 + Spring Boot 3
│   ├── account-service/         # Java 21 + Spring Boot 3
│   ├── ledger-service/          # Java 21 + Spring Boot 3
│   ├── payment-router/          # Kotlin + Spring Boot 3
│   ├── reconciliation-service/  # .NET 10 Worker Service
│   ├── statement-service/       # Java 21 + Spring Batch
│   └── api-gateway/             # .NET 10 (reuses auth-dotnet patterns)
├── deploy/
│   ├── compose/                 # docker-compose for local
│   └── helm/                    # Helm charts for K8s (optional)
├── docs/
│   ├── ADR/                     # decision records
│   ├── architecture.md          # C4 diagrams + interaction flows
│   ├── banking-domain.md        # chart of accounts, ISO 20022 primer
│   └── runbook.md               # SRE-style operational guide
└── README.md
```

## Cross-cutting infrastructure

- **Kafka (KRaft mode)** — event backbone. Topics: `accounts.*`, `payments.*`, `settlements.*`, `statements.*`. Retention: 7 days for compacted balance snapshots, 30 days for events.
- **Postgres 16** — one instance per service (true microservice data ownership). Local dev shares one Postgres container with separate databases.
- **Redis 7** — rate limiter state (api-gateway) + hot balance cache (ledger-service).
- **Debezium** — CDC off outbox tables for effectively-once event publication.
- **OpenTelemetry Collector** → Prometheus (metrics) + Jaeger (traces) + Loki (logs) via Grafana.

## Key architectural decisions (deep-dive in separate ADRs)

1. **Polyglot by design** — three languages. Rationale: showcases cross-ecosystem competency; each language plays to strengths (Java for domain-heavy services, Kotlin for payment routing's expression power, .NET for the gateway + batch worker leveraging existing auth-dotnet patterns). Detailed in ADR-001.

2. **Per-service Postgres schema** — no shared database. Rationale: true bounded-context isolation; makes contract-driven communication mandatory; forces API discipline. Cost: 7 databases to manage locally (mitigated by shared container + logical databases).

3. **Kafka + Outbox pattern for effectively-once** — dual-write safety. Rationale: proven pattern from payments-ledger-demo repo; eliminates the classic "DB commit succeeded, Kafka publish failed" bug. Detailed in ADR-004.

4. **ISO 20022 as first-class citizen** — `pain.001`, `pacs.008`, `camt.053` handled natively. Rationale: SWIFT MT-to-ISO 20022 migration is complete (November 2025 deadline); every UK bank is now on 20022 or in coexistence mode. Detailed in ADR-002.

5. **T+1 batch reconciliation, not real-time** — nightly job matches ledger against mock counterparty CSV. Rationale: matches real-world banking cadence (Nostro reconciliation is T+1 industry-standard); real-time reconciliation is possible but not the norm and adds complexity for demo value zero. Detailed in ADR-003.

6. **OAuth 2.0 gateway reuses auth-dotnet** — the existing portfolio auth-dotnet repo becomes a git submodule / library reference from api-gateway. Rationale: don't rebuild what already exists; demonstrates linking portfolio pieces together (an interview talking point in itself).

## Non-goals (strict — say no repeatedly)

- **Regulatory reporting** — Basel III capital adequacy, IFRS 9 credit loss, PSD2 SCA exemption reporting. Each is a career project on its own. Not in scope.
- **Real SWIFT / Faster Payments / card-network connectivity** — mock the interfaces. The interesting design work is in the mock boundary, not the real rail integration.
- **Full customer UI** — an OpenAPI + Postman collection is enough. Every hour on a UI is an hour not writing an ADR.
- **Multi-region active-active** — single region. HA gimmicks are interview poison ("did you actually test this?") unless proven under real load.
- **ML features** — no fraud model, no credit scoring, no anomaly detection. Interesting but off-topic.
- **Real customer PII** — the customer-service handles PII-safe pattern (encryption-at-rest hooks, PII-masked logs, GDPR-adjacent) but stores only mock data.
- **Kubernetes operators / CRDs** — Helm charts are enough demonstration. Custom operators are scope creep.

## Alternatives considered

1. **Monolith with modular design.**
   - Pro: faster to build, lower ops complexity
   - Con: doesn't showcase distributed-systems thinking, which is the entire point
   - Rejected.

2. **Single language (Java for everything).**
   - Pro: simpler, one build system
   - Con: I already have single-language portfolio projects (`payments-ledger-demo` is pure Java, `auth-dotnet` is pure .NET). Repeating that adds nothing.
   - Rejected.

3. **Full event sourcing (event stream = source of truth, no relational DB).**
   - Pro: pure architectural elegance
   - Con: real banks use event sourcing selectively (for the ledger, not for customer records); doing it universally is over-engineering that costs weeks
   - Rejected — event sourcing is used only for ledger state (implicit via postings), not universally.

4. **Kubernetes-first, no Docker Compose.**
   - Pro: cleaner production story
   - Con: makes local development an ops project; a Sprint-scale portfolio can't afford this
   - Rejected — Docker Compose primary, Helm charts as bonus.

## Consequences

**Positive:**
- Strong Lead-signal for interviews — moves you from "Senior IC" to "systems architect" evidence
- Real ISO 20022 handling in a portfolio project (rare)
- Polyglot demonstrates architectural neutrality (rare)
- Reconciliation as a first-class service (very rare in portfolios)
- 10-15 ADRs = 10-15 talking points at every interview

**Negative:**
- 6-8 weeks of focused work — meaningful opportunity cost
- Complex local dev environment (7 services + Kafka + Postgres + Redis) — mitigated by Docker Compose
- Polyglot means maintaining 3 build ecosystems (Gradle, Maven, dotnet CLI) — accepted cost

**Risks + mitigations:**
- **Risk:** scope creep, especially adding UI / ML / regulatory features under time pressure
  **Mitigation:** the "Non-goals" section above is the veto rule; re-read weekly during Sunday review
- **Risk:** flagship slips beyond August, blocking September interviews from citing it
  **Mitigation:** Sprint Week 3 checkpoint — if fewer than 2 services fully shipped, cut Reconciliation from scope
- **Risk:** interview time on flagship walkthroughs is scarce; can't cover 7 services in 10 minutes
  **Mitigation:** README's "5-minute tour" section is the anchor; deeper questions land on ADRs

## What comes next

- **ADR-001** — Polyglot service-tech justification (which language for which service, and why)
- **ADR-002** — ISO 20022 accept-both (XML pain.001 + internal JSON API) — why both
- **ADR-003** — T+1 batch reconciliation vs. real-time — why batch
- **ADR-004** — Outbox pattern + Debezium CDC — why not direct Kafka publish
- **ADR-005** — Per-service Postgres vs. shared DB — the always-argued question
- **ADR-006** — Money type: BigDecimal + ISO 4217 currency code — precision and locale rules
- **ADR-007** — Chart of accounts design — how the 5 top-level accounts map to service data

Draft one ADR per week during the Sprint. Each ADR is an interview talking point.

## Open questions (revisit before ADR-001)

- Do we support **corporate accounts** or just retail? (Suggest: retail only for scope. Corporate opens Nostro/Vostro complexity.)
- Do we support **multiple currencies at the account level** or one currency per account? (Suggest: one currency per account, multiple accounts per customer. Simpler; matches most retail banks.)
- Do we ship **API contracts as OpenAPI** or **gRPC + proto**? (Suggest: OpenAPI 3.1 for external-facing / gateway; internal service-to-service can be OpenAPI too. gRPC is nice but adds tooling burden.)
- **Deployment target for portfolio show-off** — Docker Compose only, or one-click K8s (e.g. k3d + Helm)? (Suggest: Docker Compose primary; Helm charts written but K8s deployment is stretch scope.)

Answer these before starting Sprint Week 2.
