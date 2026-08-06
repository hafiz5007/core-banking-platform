# ADR-011: Correlation-ID Propagation via Filter (vs. Explicit Passing)

- Status: **Accepted**
- Date: 2026-08-06
- Deciders: Shardar Rahman
- Scope: common-lib, all services

## Context

Distributed tracing requires a single request to carry a consistent identifier across every service
it touches, so that log lines from `api-gateway → account-service → ledger-service` can be
correlated in a log aggregator (Loki, Splunk, ELK).

Two implementation strategies:

1. **Explicit passing** — every method signature includes a `correlationId` parameter; callers
   thread it through manually.
2. **Implicit propagation via filter + MDC** — a servlet filter or interceptor reads the ID from
   the incoming request header, stores it in a thread-local context (SLF4J `MDC`), and all
   downstream calls read from that context.

## Decision

Use **implicit propagation via a `CorrelationIdFilter`** in `common-lib`:

```java
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String id = Optional.ofNullable(request.getHeader(HEADER))
                            .filter(h -> !h.isBlank())
                            .orElseGet(() -> UUID.randomUUID().toString());
        MDC.put(MDC_KEY, id);
        response.setHeader(HEADER, id);   // echo back for client-side tracing
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);          // prevent thread-local leak on virtual threads / pools
        }
    }
}
```

**Outbound propagation:** `CorrelationIdRestClientInterceptor` (for `RestClient` / `RestTemplate`)
and `CorrelationIdGrpcClientInterceptor` (for gRPC stubs) read `MDC.get(MDC_KEY)` and inject
`X-Correlation-Id` / `x-correlation-id` metadata into every outbound call automatically.

**Kafka propagation:** `CorrelationIdProducerInterceptor` writes the ID into Kafka message headers;
`CorrelationIdConsumerInterceptor` reads it back into MDC on the consumer side.

**Log format:** all services use a Logback pattern that includes `%X{correlationId}`:
```
%d{ISO8601} [%thread] %-5level %logger{36} [%X{correlationId}] - %msg%n
```

## Alternatives considered

1. **Explicit correlationId parameter on every method** — zero magic, easy to test; however, it
   pollutes every method signature in the codebase, is error-prone to thread through async
   callbacks, and must be added retroactively to every existing method. Rejected.
2. **OpenTelemetry trace context only (W3C TraceContext header)** — the platform uses OpenTelemetry
   for distributed tracing (`traceparent` header), but trace IDs are long hex strings formatted for
   OTLP, not human-readable in log grep. The correlation ID (`X-Correlation-Id`) is the
   shorter, business-meaningful identifier that operations teams use. Both coexist: OTEL for
   trace/span graphs, correlation ID for log search. Not an either/or.
3. **Spring Cloud Sleuth / Micrometer Tracing propagation** — Micrometer Tracing (successor to
   Sleuth) propagates `traceparent` automatically; however, it does not carry a custom
   `X-Correlation-Id` by default, and we need that header to be readable by non-tracing-aware
   systems (mobile apps, external partners). Rejected as the sole mechanism; used alongside.

## Consequences

- **Positive:** Zero pollution of domain method signatures; MDC is available in all SLF4J appenders
  (Logback, Log4j2) with no code change; filter runs once per request with `OncePerRequestFilter`;
  `finally` block prevents MDC leaks on thread pools and virtual threads.
- **Negative:** MDC is thread-local; care required in reactive code (WebFlux) or when spawning new
  threads without explicitly copying the MDC context. This service uses virtual threads (not
  reactive), so MDC is safe.
- **Risks + mitigations:** MDC not propagated across async boundaries (`@Async`, `CompletableFuture`
  with a new thread pool) — mitigated by the `CorrelationIdTaskDecorator` wrapper for
  `ThreadPoolTaskExecutor` in `common-lib`.

## References

- ADR-001 (audit change history) — CorrelationIdFilter already referenced
- ADR-008 — JWT propagation (JWT and correlation ID are propagated by the same interceptor layer)
- [MDC in SLF4J](https://logback.qos.ch/manual/mdc.html)
- [W3C Trace Context](https://www.w3.org/TR/trace-context/)
