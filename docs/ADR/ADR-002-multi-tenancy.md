# ADR-002: Organization / Multi-Tenancy Model

- Status: **Accepted (default; revisit if strict isolation is required)**
- Date: 2026
- Context: the platform had no `organization_id`/`tenant_id`. We must decide how "organization" is
  represented before it spreads across every table and query.

## Options

| Model | Isolation | Ops cost | Fit |
|-------|-----------|----------|-----|
| Shared schema + `organization_id` column | Query-scoped (discipline required) | Lowest | Single bank with branches/units; early multi-tenant |
| Schema-per-tenant | Strong | Medium | Multi-tenant with moderate isolation |
| Database-per-tenant | Strongest (data residency) | Highest | Regulated per-jurisdiction / BaaS |

## Decision

Adopt **shared schema with an `organization_id` column on every table** as the default, plus a
**request-scoped tenant context**:

- `common-lib` `TenantContext` holds the current organization for the request; `TenantContextFilter`
  reads it from the `X-Organization-Id` header (in production, from the JWT `org` claim mapped at the
  api-gateway) and puts it in the MDC. Absent a value it defaults to `DEFAULT` (single-tenant).
- Every table gets `organization_id NOT NULL` (defence in depth); every create sets it from the
  context; every query filters by it. Reference implementation: `account-service` (`organization_id`
  on `account`, set from `TenantContext`).

This is reversible/upgradeable: if strict isolation or data residency is later required, move to
**schema-per-tenant** (Hibernate multitenancy `SCHEMA`) or **database-per-tenant** — the
`organization_id` column and the tenant context stay, only the connection routing changes.

## Rationale

- Works immediately whether "organization" means internal branches/units of one bank or early
  multi-tenant, with the least disruption to the current codebase.
- Keeping `organization_id` even under future schema/DB isolation gives defence in depth and a
  smooth migration path.

## Consequences

- Roll `organization_id` out to every table via a migration per service (reference:
  `account-service` V3), and set/filter it in each service.
- The api-gateway must map the authenticated org (JWT claim) to `X-Organization-Id` before routing;
  services must reject requests whose context org does not match the resource's `organization_id`.
- Cross-org access is a security violation and must be covered by tests.
