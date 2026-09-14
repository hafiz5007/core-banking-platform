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

---

## Implementation status — session 1 of 2

**Implemented** on the account-service → ledger-service path, both transports. Off by default
(`service-auth.enabled=false`), so unsecured deployments and existing tests are unchanged.

| Piece | Where |
| --- | --- |
| Mint / verify | `common-lib` `security/ServiceTokenIssuer`, `ServiceTokenVerifier`, `ServiceAuthProperties` |
| gRPC | `security/grpc/JwtClientInterceptor` (caller), `JwtServerInterceptor` (receiver) |
| REST | `security/web/JwtPropagationInterceptor` (caller), `ServiceTokenAuthFilter` (receiver) |
| Caller wiring | `account-service` `config/ServiceAuthConfig`, attached to the gRPC stub and the ledger `RestClient` |
| Receiver wiring | `ledger-service` `config/ServiceAuthConfig` — gRPC interceptor + REST filter; posting demands the `ledger:post` scope |

Claims are as decided: `sub`/`svc` = calling service, `aud` = target, short `exp`, and a `jti` per
token. Tokens are minted per call rather than cached — they live ~60s and minting is a local HMAC,
so a cache would buy nothing and risk sending a stale token.

### Deviation from the decision above: HS256, not RSA

The decision specifies RSA signing with the gateway holding the private key and services fetching a
JWK set. **This slice uses HS256 with a shared secret instead**, for one concrete reason: CI runs
Gitleaks, and there is no way to ship a working default RSA keypair without committing private key
material to the repository. A shared secret supplied from the environment keeps the repo clean.

The secret is never defaulted in source — `SERVICE_AUTH_SECRET` must be supplied, and a secret
shorter than 32 bytes is rejected at startup rather than silently weakening the signature.

What this costs, and why it is acceptable for now: a shared secret means any holder can *mint* as
well as *verify*, so a compromised receiver could impersonate a caller. That is strictly weaker than
the asymmetric design and is **not** the intended production posture. It is adequate while every
service is deployed together from one secret store, and it gets the token plumbing, claim checks,
scope enforcement, and both transport hooks in place.

**Remaining (session 2):**

1. **Move to RS256 + JWKS** — `ServiceTokenIssuer`/`ServiceTokenVerifier` are the only places that
   know the algorithm; swap the signer/verifier and read the key from the secret store or the
   gateway's JWK set. This restores the ADR as written.
2. **Roll out to the other eight services** — currently only account-service (caller) and
   ledger-service (receiver) are wired. The receivers that matter next are risk-aml-service
   (payment-service already calls it over HTTP) and the three services posting to the ledger
   (payment, card, interest-fee).
3. **Promote the per-service `ServiceAuthConfig` to a common-lib auto-configuration** so the
   remaining services opt in with configuration only. Two copies is tolerable; ten would not be.
4. **Gateway token issuance** — the gateway should mint or exchange the caller's token, per §1 of
   the decision. Today each service mints its own.
5. **Method-level scope checks** (`@PreAuthorize`) — the gRPC path enforces `ledger:post`, but the
   REST filter currently only authenticates. Per-endpoint scopes need Spring Security resource
   server per §3.

### Session 2 update — rollout complete across internal calls

All five service-to-service call paths now carry a token, and both receivers enforce it:

| Caller | Target | Transport | Scope |
| --- | --- | --- | --- |
| account-service | ledger-service | gRPC | `ledger:post` |
| account-service | ledger-service | REST | `ledger:write` |
| card-service | ledger-service | REST | `ledger:post` |
| interest-fee-service | ledger-service | REST | `ledger:post` |
| payment-service | ledger-service | REST | `ledger:post` |
| payment-service | risk-aml-service | REST | `risk:evaluate` |

The per-service `ServiceAuthConfig` classes were replaced by a common-lib
`ServiceAuthAutoConfiguration`, so a service opts in with configuration rather than code. Only
ledger-service still declares anything by hand — the gRPC interceptor, because the scope it demands
is specific to that service.

Two independent switches, deliberately:

- `service-auth.enabled` — this service mints tokens for outbound calls.
- `service-auth.require-inbound` — this service refuses unauthenticated inbound calls.

They are separate so a rollout can enable every caller first and flip receivers afterwards. Turning
both on at once in a running estate would refuse traffic from any caller not yet deployed.

One trap worth recording: the filter registration originally carried `@ConditionalOnMissingBean`.
Services already register `FilterRegistrationBean`s for the correlation-id and tenant filters, and
the condition matches on the raw type — so it silently skipped the auth filter and left the service
unguarded while appearing configured. A test that asserted an unauthenticated call is refused caught
it; a test that only asserted the happy path would not have.

**Secrets** now live in a single gitignored `.env` at the repo root, with `.env.example` as the
committed template. Database credentials moved there too — `docker-compose.yml` no longer contains
any hardcoded credential.

**Still not done:** RS256/JWKS (see the deviation above); gateway-side issuance; per-endpoint REST
scopes; and the four services that receive only gateway traffic (customer, admin, notification,
reporting), which cannot be guarded until the gateway mints tokens.
