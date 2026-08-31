
# Walkthrough: Common Library

## Purpose (2 sentences)
Common-lib is the shared foundation module for platform-wide concerns, not a standalone business capability. It provides exact money handling, standardized API error types, correlation-id tracing, and tenant context propagation so every service follows the same infrastructure rules.



## Public API surface
None. Common-lib is a plain Java library, so it does not expose REST endpoints or Kafka topics.

## Data model (1 paragraph)
No direct Postgres tables live in common-lib. It only defines reusable value objects and request-scoped helpers; services such as account-service own the actual database schema, which keeps persistence close to the owning bounded context and avoids a brittle shared schema.

## Key design decisions (3-5)

- Decision: Use an immutable `Money` value object backed by `BigDecimal` and ISO-4217 currency.
- Why: It prevents floating-point rounding errors and forbids cross-currency arithmetic by design.
- Alternative considered: Using primitive numeric types or raw `BigDecimal` values everywhere.
- Trade-off accepted: Slightly more object creation and stricter arithmetic rules in exchange for correctness.

- Decision: Standardize API failures with `ApiError`, `ErrorCode`, and `BusinessException`.
- Why: Services can return stable client-safe errors without leaking stack traces or internal identifiers.
- Alternative considered: Letting each service format its own ad hoc error payloads.
- Trade-off accepted: A shared error model is less flexible per service, but much easier to consume consistently.

- Decision: Propagate request correlation IDs through `CorrelationIdFilter` and SLF4J MDC.
- Why: It makes end-to-end tracing and log correlation consistent across services.
- Alternative considered: Generating trace identifiers separately inside each service or relying only on external tracing.
- Trade-off accepted: Each service still needs to register the filter, but the logging contract stays uniform.

- Decision: Store tenant context in `TenantContext` and populate it from `X-Organization-Id`.
- Why: It gives request-scoped organization isolation while keeping single-tenant deployments working via the `DEFAULT` fallback.
- Alternative considered: Passing organization IDs manually through every service method.
- Trade-off accepted: ThreadLocal state must be cleared carefully, but the request plumbing becomes much simpler.

- Decision: Keep common-lib as a non-runnable library and skip Spring Boot repackaging.
- Why: It is meant to be reused by other modules, not deployed on its own.
- Alternative considered: Turning it into a small service or application.
- Trade-off accepted: It cannot run independently, but its reuse model is cleaner.

## Where the interesting code lives
- Domain logic: common-lib/src/main/java/com/bank/common/money/Money.java, common-lib/src/main/java/com/bank/common/error/*, common-lib/src/main/java/com/bank/common/tenant/TenantContext.java
- Adapters: common-lib/src/main/java/com/bank/common/web/CorrelationIdFilter.java, common-lib/src/main/java/com/bank/common/tenant/TenantContextFilter.java
- Configuration: common-lib/pom.xml

## Interview flashcards
- Q: "Why did you use X here?" → A: We use `Money` so every service gets exact decimal math, currency awareness, and a single place to enforce rounding rules.
- Q: "How would you scale this to 10x?" → A: The library itself is already stateless; scaling comes from keeping the shared contracts stable while each service independently adopts them.
- Q: "What would you change with hindsight?" → A: I would centralize more of the web exception mapping and filter registration conventions so every service needs less boilerplate.

## Follow-ups
- Add a shared exception handler and response mapping convention if more services start duplicating it.
- Add more tests around tenant-context cleanup and filter ordering.
- Expand the library only for truly cross-cutting concerns; keep business logic in the owning services.
