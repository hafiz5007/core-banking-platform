# Go-Live Readiness & Hardening (Sprints 11–12)

Sprints 11 and 12 are not feature sprints — they certify that what was built in Sprints 0–10 is
safe, resilient, and compliant enough to carry real money. This document is the checklist and the
operating playbook. Nothing here ships new domain logic; it gates the release.

## 1. Release gate — must all be GREEN before go-live

| Gate | Evidence | Owner | Status |
|------|----------|-------|--------|
| All `Must` requirements implemented & demoed | Traceability matrix vs. PRS | Product / QA | ☐ |
| `mvn verify` green across all modules | CI run link | Eng | ☐ |
| Unit + integration + contract tests pass | CI reports | Eng | ☐ |
| Ledger reconciles to zero over a full simulated business cycle (incl. EOD) | Reconciliation report | Eng / Ops | ☐ |
| Payment certification: intrabank, instant, RTGS, ACH, SWIFT | Scheme cert sign-off | Payments | ☐ |
| Independent penetration test — no open critical/high | Pen-test report | Security | ☐ |
| Performance targets met (≥1,000 TPS, p95 < 800 ms) under load | Load-test report | Eng / SRE | ☐ |
| DR failover drill passed (RTO ≤ 1h, RPO 0) | Drill record | SRE | ☐ |
| Compliance sign-off: KYC/AML, PCI scope, data protection | Sign-off memos | Compliance | ☐ |
| Runbooks, on-call rota, dashboards in place | This doc + links | SRE | ☐ |

## 2. Hardening checklist (Sprint 11)

**Resilience**
- Timeouts, retries with backoff, and circuit breakers on every external call (ledger, schemes, FX, SWIFT, KYC, notifications).
- Dead-letter handling for poison messages; outbox relay retries until delivered.
- Graceful degradation: reads stay available if a downstream rail is impaired; nothing posts unsafely.
- Chaos/failover testing of each rail adapter; verify compensation paths under induced failure.

**Payments uniformity**
- Returns, recalls and reversals verified across every rail (intrabank, instant, RTGS, ACH, cross-border).
- Automated end-to-end reconciliation across domestic positions and nostro balances; aging alerts on breaks.
- Idempotency verified end-to-end (client key → payment → ledger posting key → scheme reference).

**Performance**
- Load test to NFR targets; tune connection pools, JVM, and DB indexes.
- Read/write separation: reporting served from a projection, never the transactional path.

**Optional regional rail**
- SEPA-style adapter behind the routing engine (feature-flagged) if the launch market requires it.

## 3. Security & compliance hardening

- TLS 1.2+ externally, mTLS service-to-service; secrets only from Vault/KMS, rotated, never in code or logs.
- PII/PAN masked in logs and non-production; synthetic data in lower environments.
- SAST, DAST, dependency/SBOM scan, image scan, and signed artifacts gate the pipeline; critical CVEs block release.
- RBAC least-privilege, segregation of duties, and maker–checker enforced for sensitive operations.
- Immutable, tamper-evident audit trail retained for the full regulatory period.
- PCI DSS 4.0.1 scope confirmed for the card environment (tokenization, segmentation, script integrity).

## 4. Observability & SLOs

- Golden signals (latency, traffic, errors, saturation) dashboarded per service.
- Distributed tracing across the full payment journey via the correlation id.
- SLOs with error budgets; alert on burn rate, not raw thresholds.
- Synthetic probes for the critical journeys (onboard, open account, intrabank payment, cross-border payment).

## 5. DR / BCP (Sprint 12 drill)

- Multi-AZ active deployment with automated failover.
- RTO ≤ 1 hour, RPO 0 for committed transactions.
- Quarterly (minimum annual) failover drill with a recorded result and post-mortem.
- Restore-from-backup test for PostgreSQL; verify Flyway history and ledger integrity after restore.

## 6. Go-live runbook (cutover)

1. Freeze changes; tag the release; confirm all gates GREEN.
2. Provision production via IaC; run Flyway migrations (each service owns its schema).
3. Seed reference data: chart of accounts (incl. `SETTLEMENT`, `NOSTRO`, `INTEREST-EXPENSE`, `FEE-INCOME`), products, limits.
4. Smoke test each service `/actuator/health`; run the critical-journey synthetic suite.
5. Enable scheme connectivity; run one penny-test per rail; reconcile.
6. Switch traffic (blue-green / canary); watch dashboards and error budgets.
7. Rollback plan: keep the previous version warm; DB changes are forward-only with compensating scripts prepared.

## 7. Post-go-live

- Hypercare window with heightened on-call and daily reconciliation review.
- Track KPIs from the PRS: STP ≥ 98%, day-end ledger variance = 0, availability ≥ 99.95%, payment p95 < 800 ms.
- Schedule the first KYC-refresh and EOD cycles; confirm regulatory report submission windows.
