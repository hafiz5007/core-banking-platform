# ADR-015: Polyglot Language Strategy — Java for Banking Core, Kotlin for KYC, .NET for Auth

- Status: **Accepted**
- Date: 2026-08-06
- Deciders: Shardar Rahman
- Scope: Platform

## Context

The platform spans multiple capability domains with different optimisation axes:

- **Banking core** (customer, account, ledger, payment, reporting) — transaction safety, mature
  Spring ecosystem integration, and the deepest pool of engineers who can maintain it.
- **KYC / risk** — complex nullable domain models (document states, AML scores), frequent data
  transformations, benefit from concise null-safe code.
- **Auth / identity** — enterprise auth patterns (OAuth 2.0, OIDC, token validation) where .NET /
  ASP.NET Core has a deep and battle-tested ecosystem.

Running a single language would force trade-offs on at least one of these domains.

## Decision

Adopt a **three-language polyglot** strategy:

| Language | Services | Rationale |
|----------|----------|-----------|
| **Java 21** | customer-service, account-service, ledger-service, payment-service, notification-service, interest-fee-service, reporting-service, api-gateway (Spring Cloud Gateway) | Spring Boot 3.4 + virtual threads (Loom); dominant in UK banking stacks; widest hiring pool for reviewer credibility |
| **Kotlin (JVM)** | risk-aml-service (KYC/AML scoring) | Null-safe type system eliminates NPEs in nullable AML score models; data classes reduce boilerplate; 100 % Java interop means shared JVM infra |
| **.NET 10 (C#)** | admin-service (privileged operations + maker-checker) | ASP.NET Core identity and policy-based authorisation are first-class; demonstrates polyglot competency for .NET-heavy UK enterprise shops |

All services share the same transport layer (Kafka, gRPC, REST over HTTP/2), the same observability
stack (OpenTelemetry), and the same `common-lib` value objects where applicable.

## Alternatives considered

1. **Java only** — simplest ops, but the platform loses its polyglot demonstration value and misses
   Kotlin's null-safety benefits for complex AML domain models.
2. **Kotlin only** — Kotlin is a superset of the Java idiom, but forces all services onto a single
   language and reduces the hiring-readiness signal for Java-only roles.
3. **Node.js / Python for KYC** — popular in data-science/ML pipelines but adds a non-JVM runtime
   with no shared type definitions and weaker compile-time guarantees.
4. **Go for the gateway** — excellent performance, but adds a fourth runtime with no shared
   library story and no reuse of the platform's existing types.

## Consequences

- **Positive:** Demonstrates multi-language design competency; each language is used where it
  provides genuine value; JVM services share library code.
- **Negative:** Three runtimes increase Docker image count and CI matrix; developer onboarding
  requires basic familiarity with all three languages.
- **Risks + mitigations:** Schema drift between polyglot services — mitigated by shared Protobuf /
  Avro schemas in `common-lib` and contract tests at service boundaries.

## References

- ADR-000 (Architecture Overview) — service inventory and polyglot rationale at a glance
- ADR-016 — Spring Boot 3.4 + Java 21 selection detail
