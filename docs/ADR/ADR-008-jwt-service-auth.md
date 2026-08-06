# ADR-008: JWT Bearer Tokens for Service-to-Service Authentication

- Status: **Accepted**
- Date: 2026-08-06
- Deciders: Shardar Rahman
- Scope: Cross-cutting (all inter-service calls)

## Context

Services call each other via REST (api-gateway → downstream) and gRPC (account-service →
ledger-service). Without authentication on these internal calls, any process that can reach the
internal network can impersonate any service — a serious threat in a financial platform.

Three common patterns for service identity in microservices:

| Approach | Pros | Cons |
|----------|------|------|
| Mutual TLS (mTLS) | Cryptographic identity; no shared secret | Certificate management overhead; harder to debug |
| API keys / shared secrets | Simple | Static secrets; no expiry; easy to leak |
| JWT bearer tokens | Stateless; expiry built-in; carries claims; standard Spring Security support | Requires an issuer (auth server or self-signed) |

## Decision

Use **JWT bearer tokens** for service-to-service authentication:

1. **Token issuance** — the api-gateway authenticates the incoming client request (OAuth 2.0 /
   OIDC) and either forwards the user's access token or mints a short-lived service token
   (`sub = <calling-service>`, `aud = <target-service>`, `exp = now + 60 s`) signed with an RSA
   private key held by the gateway.

2. **Token propagation** — each service forwards the JWT in the `Authorization: Bearer <token>`
   header on outbound REST calls and in the `authorization` gRPC metadata key on outbound gRPC
   calls. `common-lib` `JwtPropagationInterceptor` (HTTP) and `JwtClientInterceptor` (gRPC) handle
   this transparently.

3. **Token validation** — each Spring Boot service configures
   `spring.security.oauth2.resourceserver.jwt.jwk-set-uri` (or an inline public key for dev).
   Spring Security validates signature, `exp`, and `aud` automatically before the request reaches
   any controller or gRPC handler.

4. **Scopes / claims** — service tokens carry a `svc` claim (service name) used by receiver-side
   method-level security (`@PreAuthorize("hasAuthority('SCOPE_ledger:post')")`) to restrict which
   callers can invoke which operations.

## Alternatives considered

1. **Mutual TLS (mTLS)** — strongest cryptographic guarantee; every service has its own X.509
   certificate; service mesh (Istio/Linkerd) can automate this. Rejected for v1: managing a PKI
   and service mesh adds significant operational complexity; revisit if deploying to Kubernetes with
   Istio.
2. **API keys in headers** — quick to implement; however, keys are long-lived static secrets with
   no built-in expiry, no claim payload, and are high-value targets if leaked. Rejected.
3. **No auth on internal network (network perimeter trust)** — acceptable only if the internal
   network is fully trusted and no lateral movement is possible. Rejected: defence-in-depth
   requires authentication at every hop, especially for a financial platform.
4. **OAuth 2.0 client credentials flow (external auth server)** — ideal for production; however,
   requires Keycloak / Auth0 running locally in Docker Compose, adding ~512 MB memory overhead and
   a dependency before any service starts. Deferred: use self-signed JWTs in dev/demo, swap issuer
   URL for a real OIDC server in production.

## Consequences

- **Positive:** Stateless validation (no db lookup per request); expiry limits blast radius of a
  leaked token; Spring Security resource-server support is first-class; same pattern as external
  user tokens simplifies the security mental model.
- **Negative:** Short-lived tokens require clock synchronisation across services (`NTP` / Docker
  host clock); token refresh adds a round-trip if a long-running request outlasts the token.
- **Risks + mitigations:** Token replay within the expiry window — mitigated by short `exp` (60 s)
  and including `jti` (token ID) for revocation in high-security paths. Private key compromise —
  mitigated by storing the signing key in Docker secrets / Kubernetes Secrets, never in source
  control.

## References

- ADR-007 — gRPC transport (JWTs carried in gRPC metadata)
- ADR-013 — Rate limiting at api-gateway (gateway is the JWT issuance point)
- [RFC 7519 — JSON Web Token](https://datatracker.ietf.org/doc/html/rfc7519)
- [Spring Security OAuth2 Resource Server](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/index.html)
