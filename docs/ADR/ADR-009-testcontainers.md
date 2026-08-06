# ADR-009: Testcontainers for Integration Tests

- Status: **Accepted**
- Date: 2026-08-06
- Deciders: Shardar Rahman
- Scope: Platform (all services)

## Context

Integration tests need a database, a Kafka broker, and optionally a Redis cache to be running.
Three options are common:

| Approach | Fidelity | Speed | CI Complexity |
|----------|----------|-------|---------------|
| In-memory database (H2) | Low — dialect differences | Fastest | None |
| Shared test database (always-on) | High | Fast (no spin-up) | Managed separately; tests can interfere |
| Testcontainers (Docker per test run) | High — real engine | Moderate (container spin-up) | Docker available in CI |

Spring Boot 3.1 introduced first-class Testcontainers support with `@ServiceConnection` and
`spring.testcontainers.beans.*` auto-configuration, making the setup near-zero-boilerplate.

## Decision

Use **Testcontainers** with real infrastructure images for all integration tests:

```java
@SpringBootTest
@Testcontainers
class AccountServiceIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("postgres:16");

    @Container
    @ServiceConnection
    static KafkaContainer kafka =
        new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));

    // Spring Boot auto-wires DataSource and KafkaTemplate from the container ports
}
```

`@ServiceConnection` eliminates manual `@DynamicPropertySource` boilerplate; Spring Boot reads the
container's JDBC URL / bootstrap server automatically.

Flyway migrations run on the test-managed container, so every integration test starts with a
schema-correct, empty database — identical to the production migration state.

## Alternatives considered

1. **H2 in-memory database** — fast, no Docker required; however, H2's PostgreSQL compatibility
   mode does not support `JSONB`, `GENERATED ALWAYS AS IDENTITY`, or PostgreSQL-specific index
   types used in this platform. Silent test-passes that fail in production have been observed.
   Rejected.
2. **Shared test database (always-on Docker container or RDS)** — high fidelity but tests can
   interfere with each other if run in parallel, and the shared DB must be maintained separately
   from the codebase. Rejected: test isolation is non-negotiable.
3. **Mocks / WireMock for all downstream services** — appropriate for unit tests and contract
   tests; however, DB-layer integration tests must exercise the real schema, real Flyway migrations,
   and real SQL queries. Mocks do not provide this. Rejected for integration tests (kept for unit
   tests).
4. **`docker-compose up` before tests** — requires a running compose stack; tests cannot manage
   their own lifecycle, and CI pipelines must orchestrate the stack separately. Rejected in favour
   of Testcontainers self-managing containers within the test JVM.

## Consequences

- **Positive:** Tests run against real PostgreSQL 16 and real Kafka; schema migrations are
  validated on every CI run; no shared state between test runs (each test gets a fresh container or
  uses `@Transactional` rollback); `@ServiceConnection` reduces boilerplate to near zero.
- **Negative:** Container spin-up adds 5–15 s to integration test execution; Docker daemon must be
  available in CI (standard on GitHub Actions with `ubuntu-latest` runners); image pull on cold CI
  cache adds ~30 s.
- **Risks + mitigations:** Flaky tests due to container startup timing — mitigated by
  `waitingFor(Wait.forHealthcheck())` on each container definition. Docker-in-Docker issues on
  some CI providers — use Testcontainers Cloud or `ryuk` disable flag as documented.

## References

- ADR-006 — PostgreSQL 16 as the datastore (the container matches production)
- ADR-005 — Kafka KRaft mode (Testcontainers Kafka image uses KRaft in CI)
- [Testcontainers Spring Boot support](https://spring.io/blog/2023/06/23/improved-testcontainers-support-in-spring-boot-3-1)
- [Testcontainers for Java](https://java.testcontainers.org/)
