# Service Wiring & Security Model

How the services call each other, and how the platform is secured. The integration points are
behind **ports** so each can be a no-op/stub for local dev and tests, and a real HTTP call in a
wired deployment — switched by configuration, never by code changes.

## 1. Inter-service call graph

```
                         ┌───────────────┐
   external clients ───► │  api-gateway  │  (OAuth2 JWT at the edge, rate limiting)
                         └──────┬────────┘
        routes /api/v1/** to each service by path
                                │
   ┌───────────┬───────────┬───┴────────┬─────────────┬───────────────┐
   ▼           ▼           ▼            ▼             ▼               ▼
customer    account     payment       card        interest        risk-aml
   │           │           │            │             │               ▲
   │           │ provision │ post       │ settle      │ post          │ screen
   │           ▼ GL acct   ▼ entry      ▼ entry       ▼ entry         │ (evaluate)
   │        ┌──────────────── ledger-service ───────────────┐         │
   │        └────────────────────────────────────────────────┘        │
   └──────────────────────── payment ───────────────────────────────►─┘
```

Concrete wirings implemented (config-switched):

| Caller | Port | Default (dev/test) | Wired (`docker compose`) | Target |
|--------|------|--------------------|--------------------------|--------|
| payment-service | `ScreeningPort` | `StubScreeningAdapter` (`screening.adapter=stub`) | `HttpRiskScreeningAdapter` (`screening.adapter=risk-service`) | `risk-aml-service` `/api/v1/risk/evaluate` |
| payment-service | `LedgerPort` | fake in tests | `HttpLedgerAdapter` | `ledger-service` `/api/v1/ledger/entries` |
| account-service | `LedgerProvisioningPort` | `NoOpLedgerProvisioningAdapter` (`ledger.provisioning=off`) | `HttpLedgerProvisioningAdapter` (`ledger.provisioning=http`) | `ledger-service` `/api/v1/ledger/accounts` |
| interest-fee-service | `LedgerPort` | fake in tests | `HttpLedgerAdapter` | `ledger-service` |
| card-service | `LedgerPort` | fake in tests | `HttpLedgerAdapter` | `ledger-service` |

Resilience: every outbound call uses timeouts and propagates the `X-Correlation-Id`. A screening
call that fails causes the payment to be **rejected**, never silently allowed. Ledger provisioning
treats a 409 (account already exists) as idempotent success.

## 2. Security model

### Edge (north-south) — enforced today
The **api-gateway** is an OAuth2 **resource server**. With `gateway.security.enabled=true` every
`/api/v1/**` request must carry a valid bearer **JWT** issued by the bank's identity provider
(`spring.security.oauth2.resourceserver.jwt.issuer-uri`). Actuator stays open for probes. Per-client
**rate limiting** is applied before routing. In dev the chain permits all so the platform runs
without an IdP.

### Service-to-service (east-west)
Two complementary controls:

1. **Network**: services are not exposed publicly — only the gateway is. In Kubernetes this is
   enforced with NetworkPolicies (only the gateway and peer services may reach a service's port) and
   **mTLS** via the service mesh (e.g. Istio/Linkerd) or Spring Boot SSL with a private CA.
2. **Token propagation (optional, per service)**: a service can additionally validate a JWT on
   inbound calls. To turn this on for a service:
   - add `spring-boot-starter-security` + `spring-boot-starter-oauth2-resource-server` to its POM;
   - add a `SecurityFilterChain` that permits `/actuator/**` and requires authentication elsewhere
     (identical to `api-gateway`'s `SecurityConfig`);
   - set `spring.security.oauth2.resourceserver.jwt.issuer-uri`.

   This is intentionally **opt-in** so the platform boots and tests run without an IdP. The gateway
   pattern in `api-gateway/.../SecurityConfig.java` is the copy-paste template.

### Secrets, transport, audit
- TLS 1.2+ externally; mTLS service-to-service (mesh/CA).
- Secrets from Vault/KMS at runtime — never in code, images or logs.
- Every privileged action is written to the immutable audit trail in **admin-service**; sensitive
  changes go through its **maker-checker** workflow first.

## 3. Enabling the full wiring locally

`docker compose up --build` already sets the wired profile:
- `account-service`: `LEDGER_PROVISIONING=http`
- `payment-service`: `SCREENING_ADAPTER=risk-service`
- all ledger-posting services point at `ledger-service`
- `api-gateway` routes to every service on the compose network

To enforce edge auth, set `GATEWAY_SECURITY_ENABLED=true` on the gateway and point it at your IdP.
