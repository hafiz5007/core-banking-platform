# Core Banking & Payment System

A core banking platform with integrated domestic and overseas payments, built as Java / Spring Boot
microservices. This repository currently contains the **Sprint 0 foundations and walking skeleton**.

The full requirements, engineering guideline, and sprint-by-sprint delivery plan are in the PDF
documents at the root of this folder:

- `01_Core_Banking_Project_Requirements.pdf`
- `02_Developer_Guideline_and_Sprint_Plan.pdf`
- `03_Detailed_Developer_Delivery_Plan.pdf`

## What's implemented so far

| Module | Sprint | Purpose |
|--------|--------|---------|
| `common-lib` | 0 | Cross-cutting building blocks: the `Money` value object, problem+json error model, and the correlation-id filter. |
| `account-service` | 0 | Walking-skeleton service proving the full path: HTTP → service → PostgreSQL → traced/logged response. Opens and reads deposit accounts. Port 8081. |
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

**Sprints 11–12 (hardening, DR, certification, go-live)** are delivery activities, not new code — see `docs/GO_LIVE_READINESS.md` for the release gate, hardening checklist, DR/BCP drill, and cutover runbook.

**Remaining engineering work** — the prioritized developer backlog (build/verify, stub→real
adapters, security rollout, NFR/observability, go-live tasks) is in `docs/PENDING_TASKS.md`. Service
call graph and the security model are in `docs/SERVICE_WIRING.md`. Key decisions are recorded as
ADRs in `docs/adr/` — **ADR-001** (auditing & change history) and **ADR-002** (organization /
multi-tenancy). A reference implementation of both (tenant context + `organization_id` +
`change_log`) is in `common-lib` and `account-service`.

`customer-service` also hosts **Open Banking TPP consent** (scoped, time-bound, revocable, SCA-authorised) and `payment-service` adds **P2P-by-alias** and **bill pay** on top of the engine.

Each service has its own PostgreSQL schema (`account`, `customer`, `ledger`, `payment`, `interest`, `card`, `notification`, `risk`, `admin`, `reporting`) so migrations never collide. The `api-gateway` is stateless.

The walking skeleton demonstrates the patterns every later service must follow: exact-decimal money
(never floating point), versioned Flyway migrations, RFC 9457 error responses, correlation-id
tracing, actuator health/metrics, OpenAPI docs, and integration tests against a real database via
Testcontainers.

## Prerequisites

- **JDK 21** (LTS)
- **Maven 3.9+**
- **Docker** (for `docker compose` and for Testcontainers-based integration tests)

## Build & test

```bash
# Compile everything, run unit + integration tests (starts a throwaway PostgreSQL via Testcontainers)
mvn verify

# Compile only, skip tests
mvn -DskipTests package
```

> Note: the integration tests require Docker to be running.

## Run locally

All configuration and every secret live in a single `.env` file at the repo root. Every
variable is prefixed `CBP_` so this project cannot collide with another on the same machine. Copy the template
and fill it in — `.env` is gitignored and is never committed:

```bash
cp .env.example .env
```

On Windows, a script generates it for you and creates the secrets:

```powershell
.\scripts\Setup-Environment.ps1              # everything off, fastest start
.\scripts\Setup-Environment.ps1 -Profile Sandbox   # service auth + Kafka on, secrets generated
```

`.env.example` documents every variable, what it does, and its safe default. Nothing has a weak
default: an unset variable turns a feature *off* rather than enabling it insecurely.

```bash
# Brings up PostgreSQL and the services
docker compose up --build
```

### Turning on service-to-service authentication

Internal calls (account/card/interest-fee/payment → ledger, payment → risk-aml) can carry a
short-lived JWT (ADR-008). It is off by default. To enable it, generate a secret and set two values
in `.env`:

```bash
echo "CBP_SERVICE_AUTH_SECRET=$(openssl rand -base64 48)" >> .env
echo "CBP_SERVICE_AUTH_ENABLED=true" >> .env
```

With this on, ledger-service and risk-aml-service refuse any inbound call that does not present a
valid token, on both REST and gRPC. Health and metrics endpoints stay open so probes keep working.
Enable callers before receivers when rolling this out across environments — see
`service-auth.require-inbound` in `.env.example`.

Then (account-service on 8081, customer-service on 8082):

- Swagger UI: `http://localhost:8081/swagger-ui.html` and `http://localhost:8082/swagger-ui.html`
- Health: `http://localhost:8081/actuator/health`, `http://localhost:8082/actuator/health`
- Metrics (Prometheus): `.../actuator/prometheus`

### Try it

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
├── common-lib                 # shared library (Money, errors, web filters)
│   └── src/main/java/com/bank/common/{money,error,web}
└── account-service            # walking-skeleton microservice (hexagonal layout)
    └── src/main/java/com/bank/account/
        ├── domain/            # Account aggregate, enums (no framework)
        ├── application/       # AccountService use-cases
        ├── adapter/in/web/    # REST controllers, DTOs, error handler
        ├── adapter/out/persistence/  # JPA repository
        └── config/            # OpenAPI config
```

## Coding rules (enforced from day one)

- **Money is never a float.** Use `com.bank.common.money.Money` (BigDecimal + ISO-4217 currency).
- **The domain layer imports no framework** (no Spring/JPA in `domain/`).
- **Every schema change is a Flyway migration** — forward-only, reviewed.
- **No PII/PAN/secrets in logs**; every request carries a correlation id.
- See `02_Developer_Guideline_and_Sprint_Plan.pdf` for the full standards.

## Sprint status

Sprints 0–10 are implemented as code; Sprints 11–12 are captured as the go-live readiness package
(`docs/GO_LIVE_READINESS.md`). All services share the hexagonal structure and reuse `common-lib`.

| Sprint | Deliverable |
|--------|-------------|
| 0 | Foundations, common-lib, walking skeleton (account-service) |
| 1 | customer-service (onboarding, KYC, screening, consent) |
| 2 | account/product lifecycle, holds, limits |
| 3 | ledger-service (double-entry, postings, reversal, trial balance) |
| 4 | payment-service core + intrabank + saga/compensation |
| 5 | domestic clearing (instant / RTGS / ACH, ISO 20022) |
| 6 | cross-border (SWIFT, FX, nostro) |
| 7 | interest-fee-service (accrual, fees, EOD) |
| 8 | card-service (issuance + authorization) |
| 9 | notification-service (alerts, OTP, statements) |
| 10 | risk-aml-service (monitoring, cases, SAR) |
| 11–12 | hardening, DR, certification, go-live — see `docs/GO_LIVE_READINESS.md` |

Sprint 2 depth (product catalogue, mandates/joint accounts, limits) is implemented in
`account-service`, and standing orders + direct debit are implemented in `payment-service`
(`/api/v1/standing-orders`, `/api/v1/direct-debits`), both reusing the intrabank payment engine.
