# Security Policy

## Scope

This repository is an educational reference implementation. **It is not deployed anywhere and is
not production-ready as published** — see the P0 items in
[`docs/PENDING_TASKS.md`](docs/PENDING_TASKS.md) for what production use would require.

Known and deliberate limitations, documented rather than hidden:

- Service-to-service authentication uses a **shared HMAC secret**, so any holder can mint as well
  as verify tokens. Adequate for local and CI use; production needs RS256 + JWKS (see
  [ADR-008](docs/ADR/ADR-008-jwt-service-auth.md)).
- Edge OAuth2 at the gateway is **off by default** so the platform boots without an identity
  provider.
- Every external integration — KYC, sanctions screening, clearing, SWIFT, FX, card networks — is a
  **stub**. None of them talk to a real counterparty.
- `.env` is gitignored and no secret is committed. Every configuration value defaults to
  "feature off" rather than to a weak setting.

## Reporting a vulnerability

If you find a security issue in the code or the design that others could learn from:

- **Email:** smhrcse@gmail.com
- **Or:** open a private [GitHub Security Advisory](https://github.com/hafiz5007/core-banking-platform/security/advisories/new)

Please don't open a public issue for a security matter until we've had a chance to discuss it.
I'll acknowledge within a few days.

## Automated checks

Every push and pull request runs secret scanning (Gitleaks) and dependency vulnerability review in
[CI](.github/workflows/ci.yml).
