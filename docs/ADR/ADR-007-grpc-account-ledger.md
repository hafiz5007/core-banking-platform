# ADR-007: gRPC between account-service and ledger-service (vs. REST)

- Status: **Accepted**
- Date: 2026-08-06
- Deciders: Shardar Rahman
- Scope: account-service, ledger-service

## Context

`account-service` calls `ledger-service` synchronously to:

1. Post journal entries when an account balance changes (debit/credit).
2. Query the current balance for an account before authorising a transaction.

These calls are on the **hot path** of every payment and transfer flow. The integration must be
low-latency, strongly typed, and resilient to schema drift between services.

Two primary options were evaluated: REST/JSON over HTTP and gRPC over HTTP/2.

## Decision

Use **gRPC with Protobuf schemas** for all synchronous calls between `account-service` and
`ledger-service`.

Design:

```protobuf
// ledger.proto
service LedgerService {
  rpc PostJournalEntry(PostJournalEntryRequest) returns (PostJournalEntryResponse);
  rpc GetAccountBalance(GetAccountBalanceRequest) returns (GetAccountBalanceResponse);
}

message Money {
  string currency = 1;          // ISO 4217
  int64  units = 2;             // whole units
  int32  nanos = 3;             // fractional part × 10⁻⁹
}
```

The `.proto` file lives in `common-lib` and is compiled by both services; schema mismatch is a
**compile-time error**, not a runtime JSON deserialization surprise.

Spring Boot integration: `grpc-spring-boot-starter` (LogNet) exposes `@GrpcService` beans and
injects `@GrpcClient` stubs with minimal configuration.

## Alternatives considered

1. **REST/JSON (OpenAPI)** — universal tooling, easy to test with `curl`; however, JSON
   serialization is slower than Protobuf binary, HTTP/1.1 multiplexing is weaker than HTTP/2, and
   schema is enforced only at runtime (unless OpenAPI codegen is added). Rejected for the hot path;
   REST remains the external-facing API for all services.
2. **Async via Kafka (event-driven only)** — decoupled and resilient to ledger-service downtime;
   however, posting a journal entry and immediately knowing the result (accepted / rejected) requires
   a request-reply pattern on Kafka, which adds latency and complexity. Rejected for synchronous
   balance queries; Kafka is used for async domain events (outbox pattern, ADR-004) alongside gRPC.
3. **GraphQL** — flexible querying but adds a schema layer on top of the transport; not a standard
   for internal service-to-service calls. Rejected.

## Consequences

- **Positive:** Binary protocol is ~5× smaller and faster than equivalent JSON; HTTP/2 multiplexing
  reduces connection overhead; Protobuf schema enforces the contract at compile time; bidirectional
  streaming is available if a future use case requires it (e.g. real-time balance streaming).
- **Negative:** gRPC is harder to test manually than REST (no `curl` without `grpcurl`); requires
  the `.proto` file to be kept in sync across services (solved by `common-lib`); adds
  `grpc-spring-boot-starter` dependency.
- **Risks + mitigations:** gRPC-Web is needed if a browser client must call ledger-service directly
  — not currently required; the api-gateway translates REST → gRPC if needed. Protobuf field
  renaming is a breaking change — mitigated by field-number-based compatibility rules and a
  no-field-removal policy.

## References

- ADR-003 — Ledger design (defines what PostJournalEntry does)
- ADR-004 — Outbox pattern (async side of ledger events)
- ADR-008 — JWT for service-to-service auth (gRPC calls carry the JWT in metadata)
- [Protocol Buffers Language Guide](https://protobuf.dev/programming-guides/proto3/)
- [grpc-spring-boot-starter](https://github.com/grpc-ecosystem/grpc-spring)
