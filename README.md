# Core Banking Platform

**A reference architecture for a compliant, scalable retail banking system on the JVM.**
Eleven Spring Boot services and two shared libraries — double-entry ledger, ISO 20022 payments,
KYC/AML screening, cards, and an API gateway — wired together and runnable with one command.

Java 21 · Spring Boot 3.4 · PostgreSQL 16 · Kafka (KRaft) · gRPC · JWT service-to-service auth · Docker · Testcontainers

[![CI](https://github.com/hafiz5007/core-banking-platform/actions/workflows/ci.yml/badge.svg)](https://github.com/hafiz5007/core-banking-platform/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Java 21](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/projects/jdk/21/)

## Why this exists

Most banking portfolio projects show one service in isolation. This one shows the parts that only
become interesting when services have to agree with each other:

- **A double-entry ledger that refuses to go out of balance** — immutable journal entries,
  idempotent replay, reversal instead of deletion, and a trial-balance endpoint that proves it.
- **Saga-based payments with compensation** — when a clearing scheme or SWIFT rejects a payment,
  the ledger posting is reversed rather than silently orphaned.
- **Transactional outbox → Kafka** for effectively-once event delivery, so a committed database
  change and its published event can never disagree.
- **gRPC on the hot path** between account and ledger, with **JWT service-to-service auth** over
  both REST and gRPC.
- **Every integration behind a port** with a working stub, switched by configuration — the platform
  boots and its tests run with no external dependency at all.

Every architectural decision is written up as an ADR. Every service has a walkthrough. The
remaining work is tracked in the open rather than hidden.

## Quickstart

```bash
git clone https://github.com/hafiz5007/core-banking-platform.git
cd core-banking-platform
cp .env.example .env
docker compose up --build
```

Then:

| What | Where |
|------|-------|
| API gateway (front door) | `http://localhost:8080/api/v1/...` |
| Swagger UI, per service | `http://localhost:<port>/swagger-ui.html` |
| Health | `http://localhost:<port>/actuator/health` |
| Prometheus metrics | `http://localhost:<port>/actuator/prometheus` |

Requires **JDK 21**, **Maven 3.9+** and **Docker**. Full build, run, debug, test and publish
commands are in **[`docs/development-guidance.md`](docs/development-guidance.md)**; the debugging
and testing playbook is in [`docs/DEBUG_TESTING.md`](docs/DEBUG_TESTING.md).

## What's built


| Module | Sprint | Purpose |
|--------|--------|---------|
| `common-lib` | 0 | Cross-cutting building blocks: the `Money` value object, problem+json error model, and the correlation-id filter. |
| `account-service` | 0, 2 | Accounts and product catalogue, holders and mandates (joint accounts), holds and limits, per-record change log. Provisions GL accounts and posts debits to the ledger over gRPC. Port 8081. |
| `customer-service` | 1 | Customer onboarding with KYC verification, sanctions/PEP screening, risk rating and consent capture. External KYC/screening behind ports with swappable stub adapters. Port 8082. |
| `ledger-service` | 3 | Authoritative double-entry general ledger: chart of accounts, balanced atomic postings, idempotent replay, immutable entries with reversal, and a trial-balance reconciliation endpoint. Port 8083. |
| `payment-service` | 4–6 | Canonical payment engine with a per-rail strategy behind a routing engine. **Intrabank** book transfers (S4), **domestic clearing** — instant / RTGS / ACH via ISO 20022 pacs.008 (S5), and **cross-border** over SWIFT with **FX conversion** and **nostro** settlement (S6). Validates, screens, posts to the ledger, settles, and runs a saga that **compensates** (reverses the posting) on failure. Idempotent initiation, returns/recalls, transactional outbox. Port 8084. |
| `interest-fee-service` | 7 | Daily interest accrual & capitalization, fee/charge application, and the end-of-day batch — posting to the ledger. Port 8085. |
| `card-service` | 8 | Debit card issuance (PAN tokenized, never stored), real-time authorization with holds and customer usage controls (channels, international, per-transaction limit), settlement that posts to the ledger, and clearing-file reconciliation. Port 8086. |
| `notification-service` | 9 | Customer alerts, one-time passcodes (hashed, single-use, rate-limited) and statement dispatch behind a sender port. Port 8087. |
| `risk-aml-service` | 10 | Real-time transaction monitoring, alerts, AML case management and SAR filing. Port 8088. |
| `admin-service` | 10 | Back-office: RBAC roles/permissions, maker-checker (four-eyes) approval workflow, immutable audit trail. Port 8089. |
| `reporting-service` | 10 | Operational & regulatory reporting from a metrics projection (kept off the transactional path). Port 8090. |
| `api-gateway` | 9 | External front door (Spring Cloud Gateway MVC): routes to all services, OAuth2 resource-server security (JWT), and per-client rate limiting. Port 8080. |
| `ledger-grpc-api` | 3 | The gRPC/protobuf contract for ledger posting, in its own module so neither side depends on the other's code — only on the contract. |

**Sprints 11–12 (hardening, DR, certification, go-live)** are delivery activities, not new code — see `docs/GO_LIVE_READINESS.md` for the release gate, hardening checklist, DR/BCP drill, and cutover runbook.


## Architecture at a glance

```
                        external clients
                               │  Bearer JWT (when edge security is on)
                               ▼
                     ┌───────────────────┐
                     │  api-gateway 8080 │  rate limit → route by path
                     └─────────┬─────────┘
                               │
  ┌──────────┬──────────┬──────┴─────┬───────────┬────────────┬──────────┐
  ▼          ▼          ▼            ▼           ▼            ▼          ▼
customer  account    payment      card      interest-fee  risk-aml   admin
 8082      8081       8084        8086         8085         8088      8089
             │  gRPC 9090  │ HTTP      │ HTTP      │ HTTP        ▲
             │  (posting)  │ (entries) │           │             │ screen
             └────────►  ledger-service 8083  ◄────┘─────────────┘
                        (authoritative double-entry GL)

  outbox → Kafka → notification-service 8087        reporting-service 8090
  all services ──► PostgreSQL 16 (one instance, a private schema each)
```

Every service uses the same **hexagonal layout** — `domain/` (no framework imports),
`application/` with outbound `port/` interfaces, and `adapter/in|out/` implementations chosen by
configuration. `common-lib` supplies the `Money` type, the RFC 9457 error model, correlation-id
propagation, tenant context and the service-auth interceptors.

## Design decisions (ADRs)

Seventeen decision records live in [`docs/ADR/`](docs/ADR/). The ones worth reading first:

- [ADR-000 — Architecture overview](docs/ADR/ADR-000-architecture-overview.md) — scope, non-goals, and what was deliberately left out
- [ADR-003 — Double-entry ledger](docs/ADR/ADR-003-ledger-double-entry.md) — immutable journal, the balancing invariant
- [ADR-004 — Transactional outbox](docs/ADR/ADR-004-outbox-pattern.md) — effectively-once event delivery
- [ADR-005 — Kafka in KRaft mode](docs/ADR/ADR-005-kafka-kraft.md) — event backbone, no ZooKeeper
- [ADR-007 — gRPC account ↔ ledger](docs/ADR/ADR-007-grpc-account-ledger.md) — why not REST on the hot path
- [ADR-008 — JWT service-to-service auth](docs/ADR/ADR-008-jwt-service-auth.md) — east-west security over REST and gRPC
- [ADR-010 — The Money type](docs/ADR/ADR-010-money-type.md) — why `BigDecimal` alone is not enough
- [ADR-012 — ISO 20022 payments](docs/ADR/ADR-012-iso20022-payments.md) — pacs.008 on domestic and cross-border rails

## Service walkthroughs

Each service has a walkthrough covering what it owns, its API surface, its data model, and the
design decisions behind it — see [`docs/walkthroughs/`](docs/walkthroughs/):

[common-lib](docs/walkthroughs/00-common-lib.md) ·
[account](docs/walkthroughs/account-service.md) ·
[customer](docs/walkthroughs/customer-service.md) ·
[ledger](docs/walkthroughs/ledger-service.md) ·
[payment](docs/walkthroughs/payment-service.md) ·
[card](docs/walkthroughs/card-service.md) ·
[interest-fee](docs/walkthroughs/interest-fee-service.md) ·
[notification](docs/walkthroughs/notification-service.md) ·
[risk-aml](docs/walkthroughs/risk-aml-service.md) ·
[admin](docs/walkthroughs/admin-service.md) ·
[reporting](docs/walkthroughs/report-service.md)

Service call graph and the security model: [`docs/SERVICE_WIRING.md`](docs/SERVICE_WIRING.md) and
[`docs/SERVICE_DEPENDENCIES.md`](docs/SERVICE_DEPENDENCIES.md).

## Try it

```bash
# Onboard a customer (customer-service)
curl -s -X POST http://localhost:8082/api/v1/customers \
  -H 'Content-Type: application/json' \
  -d '{"firstName":"Ada","lastName":"Lovelace","dateOfBirth":"1990-01-01","nationality":"GB","email":"ada@example.com","taxId":"TAX123","dataProcessingConsent":true,"channel":"WEB"}'
# A surname of "Sanctioned" returns status BLOCKED; "Refer" returns PENDING (stub screening/KYC rules).

# Open an account (account-service)
curl -s -X POST http://localhost:8081/api/v1/accounts \
  -H 'Content-Type: application/json' \
  -d '{"customerId":"11111111-1111-1111-1111-111111111111","accountType":"SAVINGS","currencyCode":"USD"}'

# Read either resource back (use the id returned above)
curl -s http://localhost:8081/api/v1/accounts/<id>
curl -s http://localhost:8082/api/v1/customers/<id>
```

### Ledger: post a balanced double-entry transaction (ledger-service, port 8083)

```bash
# Create two GL accounts
curl -s -X POST http://localhost:8083/api/v1/ledger/accounts -H 'Content-Type: application/json' \
  -d '{"code":"1000","name":"Cash","accountType":"ASSET","currencyCode":"USD"}'
curl -s -X POST http://localhost:8083/api/v1/ledger/accounts -H 'Content-Type: application/json' \
  -d '{"code":"2000","name":"Customer Deposits","accountType":"LIABILITY","currencyCode":"USD"}'

# Post a balanced entry (debit Cash 100 / credit Deposits 100). The idempotencyKey makes retries safe.
curl -s -X POST http://localhost:8083/api/v1/ledger/entries -H 'Content-Type: application/json' \
  -d '{"idempotencyKey":"dep-0001","narrative":"Customer deposit","lines":[
        {"accountCode":"1000","direction":"DEBIT","amount":"100.00"},
        {"accountCode":"2000","direction":"CREDIT","amount":"100.00"}]}'

# Prove the books balance
curl -s http://localhost:8083/api/v1/ledger/trial-balance
```

### Payments: intrabank transfer (payment-service, port 8084)

Create the two ledger accounts above first (codes `1000` and `2000`), then:

```bash
# Initiate an intrabank payment. It validates, screens, posts to the ledger, and confirms.
curl -s -X POST http://localhost:8084/api/v1/payments -H 'Content-Type: application/json' \
  -d '{"idempotencyKey":"pmt-0001","type":"INTRABANK","debtorAccount":"1000",
       "creditorAccount":"2000","amount":"25.00","currencyCode":"USD","narrative":"Rent"}'
# Re-running with the same idempotencyKey returns the same payment (no double-post).
# A creditorAccount containing "BLOCKED" is held by screening (422).

# Domestic clearing (Sprint 5): instant / RTGS / ACH. Builds an ISO 20022 pacs.008 and submits to
# the clearing scheme; debits the debtor and credits the settlement account, then settles.
curl -s -X POST http://localhost:8084/api/v1/payments -H 'Content-Type: application/json' \
  -d '{"idempotencyKey":"rtgs-0001","type":"RTGS","debtorAccount":"1000",
       "creditorAccount":"GB29NWBK60161331926819","amount":"500.00","currencyCode":"USD","narrative":"Supplier"}'
# A creditorAccount containing "RETURN" is rejected by the scheme -> the payment is COMPENSATED (reversed).

# Return/recall a settled payment
curl -s -X POST http://localhost:8084/api/v1/payments/<id>/return \
  -H 'Content-Type: application/json' -d '{"reason":"customer recall"}'

# Cross-border (Sprint 6): converts the source amount to the beneficiary currency (FX), debits the
# customer and credits the nostro account, then submits a SWIFT pacs.008 (gpi-tracked) and settles.
curl -s -X POST http://localhost:8084/api/v1/payments -H 'Content-Type: application/json' \
  -d '{"idempotencyKey":"xb-0001","type":"CROSS_BORDER","debtorAccount":"1000",
       "creditorAccount":"DE89370400440532013000","amount":"1000.00","currencyCode":"USD",
       "targetCurrencyCode":"EUR","narrative":"Overseas supplier"}'
# The response includes fxRate, targetAmount and targetCurrencyCode. A creditor containing "RETURN"
# is rejected by SWIFT -> the payment is COMPENSATED (reversed).
```


## Testing the API

One Postman collection per service in `postman/`, covering every REST endpoint, plus environments
for local, gateway and sandbox. See `postman/README.md`.

- **Releasing to a test server:** `docs/DEPLOYMENT_SANDBOX.md` — what sandbox mode means here, how
  to switch every feature on, how to prove the switches took effect, and what still stops it being
  production.
- **Service dependencies, step by step:** `docs/SERVICE_DEPENDENCIES.md` - which service calls
  which, how to verify each hop, debugging with or without Docker, and seeding test data that
  survives restarts.
- **Debugging and testing:** `docs/DEBUG_TESTING.md` — running the suites, reading the reports,
  tracing a request by correlation id, attaching a debugger, and the traps this codebase sets.


## Project layout

```
banking-platform-parent (pom)
├── common-lib              # Money, RFC 9457 errors, correlation id, tenant context, service auth
├── ledger-grpc-api         # the .proto contract shared by account-service and ledger-service
├── api-gateway             # Spring Cloud Gateway MVC — routing, edge auth, rate limiting
└── <ten business services> # each laid out the same way:
    └── src/main/java/com/bank/<service>/
        ├── domain/         # aggregates and value objects — no framework imports
        ├── application/    # use-cases
        │   └── port/       # outbound interfaces (LedgerPort, ScreeningPort, …)
        ├── adapter/
        │   ├── in/web/     # REST controllers, DTOs, error handler
        │   ├── in/scheduler/   # cron entry points (EOD, standing orders)
        │   ├── out/persistence/# Spring Data JPA
        │   └── out/<integration>/  # HTTP / gRPC / Kafka / stub adapters
        └── config/
    └── src/main/resources/db/migration/  # Flyway, forward-only
```

The dependency direction is always inward: `domain` knows nothing of Spring or JPA, `application`
depends on `domain` and its own ports, `adapter` depends on `application`. Never the reverse.

## Coding rules (enforced from day one)

- **Money is never a float.** Use `com.bank.common.money.Money` (BigDecimal + ISO-4217 currency).
- **The domain layer imports no framework** (no Spring/JPA in `domain/`).
- **Every schema change is a Flyway migration** — forward-only, reviewed.
- **No PII/PAN/secrets in logs**; every request carries a correlation id.
- **Integrations go behind a port** with a stub adapter, selected by configuration — never an `if` in business code.
- **Anything that moves money is idempotent.** Same key, same result, no double posting.
- The full engineering manual is [`docs/development-guidance.md`](docs/development-guidance.md).


## What's next (the honest backlog)

Nothing is hidden. This is a reference implementation, not a production bank, and the gap is
documented rather than glossed over:

- **Every external integration is a stub** — KYC, sanctions screening, clearing schemes, SWIFT, FX
  rates, card networks, SMS/email. Each already sits behind a port, so swapping in a real client is
  a configuration and adapter change, not a rewrite.
- **Service-to-service auth uses a shared HMAC secret.** Fine for local and CI; production needs
  RS256 + JWKS (ADR-008).
- **Edge OAuth2 is off by default** so the platform runs without an identity provider.

The prioritised backlog — **P0** (before any production use), **P1** (hardening), **P2**
(enhancement) — is in [`docs/PENDING_TASKS.md`](docs/PENDING_TASKS.md). The release gate, hardening
checklist, DR drill and cutover runbook are in
[`docs/GO_LIVE_READINESS.md`](docs/GO_LIVE_READINESS.md).

## Author

Built by **S. M. Hafizur Rahman** — 16 years in engineering, currently Senior Software Engineer &
Technical Lead at a UK FCA-regulated fintech, working on regulated financial systems, KYC/AML and
distributed architecture.

- GitHub: [@hafiz5007](https://github.com/hafiz5007)
- LinkedIn: [hafizrahmanuk](https://www.linkedin.com/in/hafizrahmanuk)
- Related work: [payments-ledger-demo](https://github.com/hafiz5007/payments-ledger-demo) ·
  [kyc-screening-service](https://github.com/hafiz5007/kyc-screening-service) ·
  [auth-dotnet](https://github.com/hafiz5007/auth-dotnet) ·
  [uk-address-lookup-service](https://github.com/hafiz5007/uk-address-lookup-service)

## Contributing

Bug reports, ADR challenges and documentation fixes are welcome — see
[CONTRIBUTING.md](CONTRIBUTING.md). Security matters: [SECURITY.md](SECURITY.md).

## License

[MIT](LICENSE) — use it, learn from it, break it, improve it.
