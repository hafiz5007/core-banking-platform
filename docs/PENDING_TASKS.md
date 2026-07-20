# Pending Tasks — Developer Guidance

What is **built** vs. what is **left to do**, written as an actionable backlog for engineers picking
up the platform. Priorities: **P0** = required before production go-live · **P1** = important
hardening / completeness · **P2** = enhancement.

> Status legend: ☐ not started · ◑ partial (stub/skeleton in place) · ☑ done

## 0. Build & verification (do this first)

- [ ] **P0 — Run a real build.** This repo was authored in an environment without a JDK/Maven, so
  it has only been verified statically (POMs well-formed, packages match directories, call-site
  arities consistent). On a dev machine with **JDK 21 + Maven + Docker**, run `mvn verify` and fix
  any compile/test issues before relying on it.
  - Highest risk module: **`api-gateway`** (Spring Cloud Gateway MVC + OAuth2 resource server) — a
    newer stack not exercised here; confirm the `spring-cloud-starter-gateway-mvc` artifact id and
    route config against Spring Cloud `2024.0.0`.
- [x] **P0 — CI + quality gates done**: GitHub Actions runs `mvn verify`, `mvn verify -Pquality`
  (Spotless format + JaCoCo coverage floor), dependency review and Gitleaks. Confirm the runs pass
  once building on a real toolchain.

## 1. Replace stub adapters with real integrations (◑ — interfaces done, behind ports)

Each is already a port with a working stub; swap in the real client and add contract tests.

- [ ] **P0 — KYC / identity & sanctions screening** (`customer-service` `KycVerificationPort`,
  `ScreeningPort`): integrate the real provider (sync API + async webhook); store evidence by
  reference only.
- [ ] **P0 — Sanctions/AML on payments** (`risk-aml-service` `MonitoringEngine`): replace threshold
  rules with the real screening engine / rules platform; payment-service already calls it via
  `HttpRiskScreeningAdapter` when `screening.adapter=risk-service`.
- [ ] **P0 — Clearing rails** (`payment-service` `ClearingPort` → `StubClearingAdapter`): real
  instant / RTGS / ACH scheme connectivity (ISO 20022 transport, acks, returns, camt reconciliation).
- [ ] **P0 — SWIFT / cross-border** (`SwiftPort` → `StubSwiftAdapter`): real SWIFT gateway, gpi/UETR
  tracking, recalls.
- [ ] **P0 — FX rates** (`FxPort` → `StubFxAdapter`): live rate feed, quote TTL + lock, realized-vs-
  quoted reconciliation.
- [ ] **P1 — Card processor** (`card-service`): real issuer/processor for issuance + authorization +
  clearing files; today the card holds an internal `availableBalance` rather than checking the live
  account — wire it to `account-service`/`ledger-service`.
- [ ] **P1 — Notification gateways** (`notification-service` `NotificationSenderPort` →
  `LoggingSenderAdapter`): real SMS/email/push providers with delivery callbacks + retry.
- [ ] **P1 — Outbox relay → Kafka** (`payment-service` `OutboxRelay`): publish to the real broker
  (payload is already broker-ready) and consume events in downstream services.

## 2. Functional completeness

- [ ] **P1 — Statement generation** (`notification-service`): real account-statement build (PDF +
  data) sourced from `ledger-service` postings — currently only alerts/OTP are implemented.
- [ ] **P1 — Regulatory report formats** (`reporting-service`): produce the mandated statutory
  formats and submission packaging; today it aggregates a metrics projection only. Wire services to
  publish their metrics into it.
- [ ] **P1 — Multi-currency accounts** (`account-service`): FR-ACC-005 — per-account balances in
  multiple currencies (currently one currency per account; multi-currency is modelled via separate
  accounts).
- [ ] **P2 — Interest withholding tax & fee waivers** (`interest-fee-service`): FR-INT-003/004.
- [ ] **P2 — Account dormancy/closure transitions** and value-dated holds expiry jobs.

## 3. Security (◑ — edge done, east-west documented)

- [ ] **P0 — Edge auth**: enable `gateway.security.enabled=true` and point the gateway at the bank
  IdP (`issuer-uri`). Verify JWT validation + scopes per route.
- [ ] **P0 — Service-to-service**: NetworkPolicies so only the gateway/peers reach a service; mTLS
  via the mesh (Istio/Linkerd) or Spring SSL + private CA.
- [ ] **P1 — Per-service JWT** (opt-in): add the resource-server starter + a `SecurityFilterChain`
  to each service (copy `api-gateway/SecurityConfig`); see `docs/SERVICE_WIRING.md`.
- [ ] **P0 — Secrets**: move DB creds and keys to Vault/KMS; remove dev defaults from compose for
  any non-local environment.
- [ ] **P1 — PII/PAN handling review**: confirm masking in all logs; confirm PCI scope for
  `card-service` (tokenization is in place; validate segmentation + script integrity controls).

## 4. Cross-cutting / non-functional

- [ ] **P1 — Observability**: ship dashboards (Grafana) and SLOs/alerts; wire OpenTelemetry export
  to a collector (tracing bridge is on the classpath, export is not configured).
- [ ] **P1 — Contract tests** (Pact / Spring Cloud Contract) for every inter-service and rail
  interface (screening, ledger, clearing, SWIFT, FX).
- [ ] **P1 — Performance & soak tests** (Gatling/k6) to the NFR targets (≥1,000 TPS, p95 < 800 ms).
- [ ] **P2 — Idempotency store TTL/cleanup** and outbox table housekeeping.
- [ ] **P2 — Static analysis**: wire Spotless/Checkstyle/PMD/SpotBugs + SonarQube as build gates.

## 4b. Audit & multi-tenancy rollout (◑ — decided + reference built)

Decisions ratified in `docs/adr/ADR-001-audit-and-change-history.md` and
`docs/adr/ADR-002-multi-tenancy.md`. Reference implementation lives in `account-service`
(`organization_id` column + tenant context + `change_log` + `ChangeLogRecorder`) and `common-lib`
(`TenantContext`, `TenantContextFilter`).

- [x] **P1 — Rollout complete across all 10 services**: `organization_id` + `TenantContextFilter`
  are in every service. The 8 transactional services (account, customer, payment, ledger,
  interest-fee, card, notification, risk-aml) each have a `change_log` with CREATE recording;
  admin re-uses its `audit_event` (org-scoped); reporting scopes its append-only metrics by org.
  account/customer/payment also expose a `/{id}/change-log` endpoint with ITs.
- [ ] **P1 — Remaining polish**: add `/change-log` read endpoints to the other services for
  parity, and **filter every read query by `organization_id`** (columns + context are in place;
  enforce org-scoping in repositories/queries and reject cross-org access).
- [ ] **P0 (if multi-tenant) — Gateway maps the JWT `org` claim → `X-Organization-Id`**, and each
  service rejects cross-org access (resource `organization_id` must equal the context org).
- [ ] **P2 — Consider schema-/DB-per-tenant** if strict isolation or data residency is required
  (the `organization_id` column and tenant context carry over; only connection routing changes).

## 5. Sprints 11–12 — go-live execution (real-world, not code) — see `docs/GO_LIVE_READINESS.md`

- [ ] **P0 — Scheme certification**: instant, RTGS, ACH, SWIFT.
- [ ] **P0 — Independent penetration test**; remediate all critical/high.
- [ ] **P0 — Load test** to NFR targets; tune pools/JVM/indexes.
- [ ] **P0 — DR failover drill** (RTO ≤ 1h, RPO 0); restore-from-backup test.
- [ ] **P0 — Compliance sign-off**: KYC/AML, PCI scope, data protection.
- [ ] **P0 — Cutover**: seed reference data (chart of accounts incl. `SETTLEMENT`, `NOSTRO`,
  `INTEREST-EXPENSE`, `FEE-INCOME`, `CARD-SETTLEMENT`, `FEE-INCOME`), penny-test each rail,
  blue-green switch, hypercare.

## Quick reference — what is already built (☑)

Sprints 0–10 as code (12 modules): `common-lib`, `account-service` (+products, mandates, limits),
`customer-service` (+KYC, Open Banking consent), `ledger-service`, `payment-service` (intrabank,
instant/RTGS/ACH, cross-border+FX, returns, standing orders, direct debit, P2P, bill pay),
`interest-fee-service`, `card-service` (+controls, settlement reconciliation), `notification-service`
(alerts/OTP), `risk-aml-service`, `admin-service` (RBAC/maker-checker/audit), `reporting-service`,
`api-gateway`. Inter-service wiring (screening, ledger provisioning, ledger posting) is in place and
config-switched. Sprints 11–12 are documented as the go-live gate.
