# Pending Tasks — Developer Guidance

What is **built** vs. what is **left to do**, written as an actionable backlog for engineers picking
up the platform. Priorities: **P0** = required before production go-live · **P1** = important
hardening / completeness · **P2** = enhancement.

> Status legend: ☐ not started · ◑ partial (stub/skeleton in place) · ☑ done

## 0. Build & verification (do this first)

- [x] **P0 — Real build done and green.** The platform now compiles and passes its full test suite
    | 9 | `GET /api/v1/ledger/entries/{id}` returned **500 for every entry**: `open-in-view` is off (correct) but the controller mapped the lazy `lines` collection after the transaction closed. No test covered the endpoint. Found while building the gRPC bridge. | added a fetch-join finder (`findByIdWithLines`) and used it in `LedgerService.getEntry` — also removes an N+1 |
on a real toolchain (**JDK 21.0.11 / Maven 3.9.9 / Docker 29.5.3**): `mvn verify` → **BUILD
  SUCCESS**, 14 modules, **170 tests, 0 failures**, including **19 integration-test classes**
  running against real PostgreSQL 16 containers via Testcontainers.
  - `api-gateway`, previously flagged as the highest-risk module, builds and its context test
    passes; `spring-cloud-starter-gateway-mvc` and the route config are correct for Spring Cloud
    `2024.0.0`.
  - Eight defects were found and fixed getting there — all listed below, since several are the kind
    that recur:

  | # | Defect | Fix |
  |---|---|---|
  | 1 | `common-lib` used SLF4J `MDC` (`CorrelationIdFilter`, `TenantContextFilter`) with no logging dependency — blocked the whole reactor | added `org.slf4j:slf4j-api` to `common-lib/pom.xml` |
  | 2 | `AbstractIntegrationTest.FakeLedgerConfig` was package-private but imported cross-package | made it `public` |
  | 3 | Testcontainers could not reach Docker: Engine 29 requires API ≥ 1.40 and docker-java negotiates older, failing with an opaque HTTP 400 | pinned `api.version` via surefire/failsafe `systemPropertyVariables` (`docker.api.version`, default `1.41`) — it is a **JVM system property**, there is no env-var equivalent |
  | 4 | Hibernate `ddl-auto: validate` rejected the schema: migrations declared `CHAR(n)` (bpchar) while every entity maps a plain `String` (`varchar`) | 15 columns across 10 migrations changed to `VARCHAR(n)` (also avoids bpchar space-padding on currency codes) |
  | 5 | **15 `*IT` integration tests were compiled but never executed** — failsafe was declared only in `pluginManagement`, so nothing bound its goals; the build was green while skipping every IT | declared failsafe in `<build><plugins>` of the parent |
  | 6 | Once the ITs ran: the 2nd+ IT class per module failed with `Connection refused`. `@Testcontainers`/`@Container` stops the container after each test **class**, but Spring caches the application context **across** classes, so later classes pointed a `DataSource` at a dead container | singleton-container pattern in all 10 `AbstractIntegrationTest` classes: started once in a `static` block, never stopped (Ryuk reaps it) |
  | 7 | `ChannelPaymentIT` built a biller code of `"UTIL-" + UUID` = 41 chars against `biller_code VARCHAR(40)` | shortened the generated code (the constraint is correct; the test data was wrong) |
  | 8 | Request DTOs had `@NotBlank` but no `@Size`, so over-long input reached the database and surfaced as a **500** `DataIntegrityViolationException` instead of a **400** | `@Size` bounds mirroring each `@Column(length=…)` added across **17 files in 9 services** |

- [ ] **P1 — Flyway checksums.** Fix #4 edited **already-committed** migration files, which changes
  their checksums. Safe only where databases are rebuilt from scratch. If any environment has
  already applied them, revert those files and move the `CHAR`→`VARCHAR` changes into a new `V*`
  migration instead. **This is the one open decision from the build work.**

- [x] **P1 — JaCoCo coverage floor now met by every module.** With Spotless skipped the floor is
  reachable, and it was failing in **4 of 13** modules: `common-lib` 0.39, `api-gateway` 0.21,
  `risk-aml-service` 0.31, `admin-service` 0.26. Tests were added — filters and error model in
  `common-lib`, `RateLimitFilterTest` in `api-gateway`, plus the two IT suites below — and
  `mvn verify -Pquality -Dspotless.check.skip=true` is now **BUILD SUCCESS** across all 13 modules.
  - Note `common-lib` ships `slf4j-api` with **no binding**, so `MDC` is the NOP adapter under test
    and silently discards everything. `logback-classic` is now a test-scope dependency there;
    without it, any assertion on MDC content passes vacuously.

- [x] **P1 — The two services that had no integration tests now have them.** `AdminControllerIT`
  (RBAC, maker-checker including the four-eyes violation and the double-decision guard, audit trail)
  and `RiskControllerIT` (monitoring rules, case open/read/close, SAR flag, double-close guard).

- [x] **P1 — CI publishes integration-test results.** The upload artifact path is now
  `**/target/*-reports/*.xml`, covering both surefire and failsafe; previously only surefire XML was
  uploaded, so IT failures were invisible in the run summary.

- [ ] **P1 — The `quality` CI job is still red, on formatting only.** `mvn verify -Pquality` fails at
  Spotless: google-java-format rejects **10 files** (e.g. `common-lib/.../ApiError.java`) because the
  repo's actual style — 4-space continuation indents, aligned `@param` javadoc, one record component
  per line — is not google-java-format. This predates all the work above. Pick one:
  1. adopt the formatter: `mvn -Pquality spotless:apply` (mechanical, but touches nearly every file); or
  2. keep the house style and replace `<googleJavaFormat/>` with a matching config (e.g. `palantirJavaFormat` or an Eclipse formatter file).

  This is the **only** thing still blocking the `quality` job — the coverage half of that profile now
  passes.

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
- [x] **P1 — gRPC bridge account-service → ledger-service (ADR-007) — done.** `POST
  /api/v1/accounts/{id}/debit` applies the debit in the domain and posts the balanced entry to
  ledger-service over gRPC. New `ledger-grpc-api` module holds the contract; server in
  `ledger-service` (`LedgerPostingGrpcService` + `GrpcServerConfig`), client in `account-service`
  (`LedgerPostingPort` → `GrpcLedgerPostingAdapter`). Off by default (`ledger.posting=off`); wired
  in docker-compose. Covered by 13 tests over a real Netty transport on both sides. See ADR-007 for
  the decisions taken while building it.
  - Follow-on: the **balance-query RPC** in ADR-007 is still not implemented — reads go over REST.
  - Follow-on: transport is **plaintext**, assuming mesh mTLS (see the service-to-service item in
    section 3).
  - Note `Account` still has **no lifecycle transitions** (no close/freeze/block); `AccountStatus`
    has five values but nothing moves between them, so the "only ACTIVE may be debited" guard is
    provable only by setting the field reflectively in a test.

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
- [x] **P1 — Request validation bounded**: every `@NotBlank` string field across the 10 services now
  carries a matching `@Size` mirroring its `@Column(length=…)`, so over-long input is rejected as a
  400 at the edge instead of failing as a 500 in the database. When adding a DTO field, set `@Size`
  from the column length in the same commit.
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
  Note the current filter is **trust-on-header**: `TenantContextFilter` reads `X-Organization-Id`
  straight from the request and silently falls back to `TenantContext.DEFAULT` when it is absent.
  Since the downstream services carry no authentication of their own (only `api-gateway` does), any
  caller able to reach a service directly can select or spoof a tenant. The gateway must strip and
  re-issue this header from the verified JWT claim, and the network must make direct access
  impossible (see the service-to-service item in section 3).
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
