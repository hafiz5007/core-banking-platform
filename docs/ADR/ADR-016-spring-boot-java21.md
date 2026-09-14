# ADR-016: Spring Boot 3.4 + Java 21 as the JVM Application Framework

- Status: **Accepted**
- Date: 2026-08-06
- Deciders: Shardar Rahman
- Scope: Platform (all JVM services)

## Context

Every JVM service needs a web/application framework, a dependency-injection container, an ORM, and
health/metrics/tracing hooks. The three realistic candidates in 2025/2026 are Spring Boot 3.x,
Micronaut 4.x, and Quarkus 3.x.

The choice also fixes the Java version: Spring Boot 3.4 requires Java 17+ and actively targets Java
21 virtual threads (Project Loom).

## Decision

Use **Spring Boot 3.4 on Java 21** for all JVM services.

Key factors:

- **Virtual threads (Loom)** — `spring.threads.virtual.enabled=true` turns every Tomcat/Jetty
  request thread into a cheap virtual thread; removes the need to add reactive programming
  (`WebFlux`) for throughput, keeping the codebase imperative and readable.
- **Spring Data JPA + Flyway** — battle-tested, IDE-supported, direct mapping to the PostgreSQL
  schemas already designed.
- **Spring Cloud Gateway** — reactive API gateway with OAuth 2.0 resource-server and rate-limiting
  built in; replaces a custom gateway with ~10 lines of YAML.
- **Actuator + Micrometer + OTLP** — one dependency away from OpenTelemetry traces, Prometheus
  metrics, and structured health checks.
- **Legibility** — Spring Boot is the stack most JVM engineers in this domain already know, so a
  new contributor spends their attention on the banking logic rather than on the framework.

## Alternatives considered

1. **Micronaut 4.x** — compile-time DI and faster cold-start are genuine advantages; however, IDE
   support is weaker, the AOP story is more limited, and Spring Data JPA is not available
   (Micronaut Data has a different query API). Rejected: marginal gain, higher onboarding cost.
2. **Quarkus 3.x** — native compilation with GraalVM is compelling for serverless/edge; however,
   native builds add 5–10 min to CI, JVM mode gains are similar to Loom-enabled Spring Boot, and
   Quarkus is uncommon in this domain. Rejected: disproportionate CI cost for no throughput gain.
3. **Spring Boot 3.x + WebFlux (reactive)** — solves the same throughput problem as Loom but at
   the cost of reactive programming model throughout (Mono/Flux everywhere, no blocking drivers).
   Rejected: imperative code with virtual threads is simpler, easier to reason about, and achieves
   comparable throughput for the load this platform targets.

## Consequences

- **Positive:** Fastest path from blank service to production-ready HTTP + DB + Kafka + tracing;
  virtual threads mean blocking JDBC is safe under high concurrency; wide knowledge base for every
  dependency.
- **Negative:** Spring Boot startup time (~1–2 s on JVM) is slower than Quarkus native; the
  dependency tree is large (~150 MB fat jar per service).
- **Risks + mitigations:** Spring Boot upgrades can break auto-configuration — mitigated by pinning
  versions in the parent `pom.xml` and running the full Testcontainers suite on each upgrade.

## References

- ADR-015 — Polyglot language strategy (this ADR covers the JVM subset in detail)
- ADR-009 — Testcontainers for integration tests
- [Spring Boot 3.4 Release Notes](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-3.4-Release-Notes)
- [JEP 444 — Virtual Threads](https://openjdk.org/jeps/444)
