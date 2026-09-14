# ADR-013: Rate Limiting at the API Gateway (vs. Per-Service)

- Status: **Accepted**
- Date: 2026-08-06
- Deciders: Shardar Rahman
- Scope: api-gateway

## Context

The platform needs rate limiting to:
- Protect downstream services from traffic spikes and abusive clients.
- Enforce per-client quotas (e.g. 100 req/s per API key, 1000 req/min per OAuth client).
- Return `429 Too Many Requests` with `Retry-After` before a request reaches any business logic.

Rate limiting can be applied at the **api-gateway** (single enforcement point) or at each
**individual service** (distributed enforcement).

## Decision

Enforce rate limiting **at the api-gateway** using **Spring Cloud Gateway's built-in
`RequestRateLimiter` filter** backed by Redis:

```yaml
# application.yml (api-gateway)
spring:
  cloud:
    gateway:
      routes:
        - id: account-service
          uri: lb://account-service
          predicates:
            - Path=/api/accounts/**
          filters:
            - name: RequestRateLimiter
              args:
                redis-rate-limiter.replenishRate: 100    # tokens/s
                redis-rate-limiter.burstCapacity: 200    # max burst
                redis-rate-limiter.requestedTokens: 1
                key-resolver: "#{@clientIdKeyResolver}"  # per OAuth client_id
```

`clientIdKeyResolver` extracts the `client_id` claim from the validated JWT; unauthenticated
requests fall back to source IP.

Sensitive endpoints (payment initiation, account open) have **stricter limits** via separate route
definitions. Rate limit metadata is exposed as response headers (`X-RateLimit-Remaining`,
`X-RateLimit-Retry-After`).

## Alternatives considered

1. **Per-service rate limiting** — each service enforces its own limits independently; provides
   isolation if the gateway is bypassed. However, it duplicates configuration across 9+ services,
   requires each to take a Redis dependency, and means a single client can exhaust each service's
   limit independently without any aggregate cap. Rejected: maintenance burden outweighs the
   isolation benefit for this platform; internal service-to-service calls bypass the gateway and
   are protected by JWT auth instead.
2. **Nginx / HAProxy rate limiting** — well-proven; however, introduces another infrastructure
   component between the gateway and clients, and this platform already uses Spring Cloud Gateway
   as the edge. Rejected: unnecessary extra hop.
3. **API management platform (Kong, AWS API Gateway)** — production-grade with dashboards,
   analytics, plugin marketplace; however, adds significant operational overhead and licensing cost
   relative to the requirement. Rejected: Spring Cloud Gateway covers the needed feature set.
4. **Token bucket in-memory (per-instance)** — no Redis required; however, with multiple gateway
   instances (horizontal scaling) each instance has a separate counter, so a client could multiply
   their effective limit by the instance count. Rejected for correctness; Redis provides distributed
   atomic counters.

## Consequences

- **Positive:** Single configuration point; Redis atomic counter is accurate across multiple gateway
  replicas; Spring Cloud Gateway filter applies before any route handler so protected services never
  see over-limit traffic; `429` responses include standard headers for client retry logic.
- **Negative:** Redis becomes a dependency of the gateway (added to Docker Compose); Redis downtime
  can affect the gateway's ability to enforce limits — mitigated by a fallback policy
  (`deny-on-error: false` allows traffic through if Redis is unreachable, trading safety for
  availability; set `deny-on-error: true` for payment endpoints).
- **Risks + mitigations:** Rate limit bypass via direct internal service URLs — mitigated by
  internal services not being exposed outside Docker network; all external traffic must go through
  the gateway.

## References

- ADR-008 — JWT auth (key resolver reads client_id from JWT)
- [Spring Cloud Gateway RequestRateLimiter](https://docs.spring.io/spring-cloud-gateway/docs/current/reference/html/#the-requestratelimiter-gatewayfilter-factory)
- [RFC 6585 — 429 Too Many Requests](https://datatracker.ietf.org/doc/html/rfc6585)
