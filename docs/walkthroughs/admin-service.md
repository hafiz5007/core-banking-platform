
# Walkthrough: admin-service

## Purpose (2 sentences)
Admin-service owns back-office governance for the platform. It manages RBAC roles and permissions, maker-checker approvals for sensitive actions, and an immutable audit trail of privileged activity.

## Public API surface
- `POST /api/v1/admin/roles` create an RBAC role
- `GET /api/v1/admin/roles` list roles
- `GET /api/v1/admin/roles/{name}/permissions/{permission}` check whether a role has a permission
- `POST /api/v1/admin/approvals` submit a maker-checker approval request
- `POST /api/v1/admin/approvals/{id}/approve` approve a request
- `POST /api/v1/admin/approvals/{id}/reject` reject a request
- `GET /api/v1/admin/audit?actor={actor}` fetch audit events by actor
- Events published: none
- Events consumed: none

## Data model (1 paragraph)
The service owns three tables: `role`, `approval_request`, and `audit_event`. `role` stores a role name plus a comma-separated permission set and is scoped by `organization_id`; `approval_request` stores sensitive actions awaiting four-eyes review with maker/checker state, decision timestamps, and optimistic locking; and `audit_event` stores an append-only, organization-scoped record of privileged actions with actor, target, details, and correlation id. These tables are not shared because authorization, approvals, and audit history are privileged back-office concerns that must evolve independently and remain transactionally consistent inside this bounded context.

## Key design decisions (3-5)

- Decision: Model permissions as an RBAC role catalogue.
- Why: The service can grant least-privilege access by assigning narrow roles instead of scattering permission checks across callers.
- Alternative considered: Hard-coding privileges or using only ad hoc checks at the gateway.
- Trade-off accepted: Managing a role catalogue adds a small amount of admin data, but makes permissions easier to reason about.

- Decision: Enforce maker-checker approvals in the domain model.
- Why: `ApprovalRequest` prevents the same user from acting as both maker and checker, which makes the four-eyes rule explicit and testable.
- Alternative considered: Validating this only in the controller or UI.
- Trade-off accepted: A slightly richer domain object, but much stronger business-rule enforcement.

- Decision: Keep the audit trail append-only and write it through `AuditService`.
- Why: Privileged actions need a tamper-evident record that can be queried later by actor.
- Alternative considered: Reusing the same table for mutable logs or shipping audit events to another service first.
- Trade-off accepted: More storage over time, but better regulatory traceability.

- Decision: Scope every back-office record by `organization_id`.
- Why: Roles, approvals, and audits must remain isolated per organization while reusing the same service.
- Alternative considered: A global shared admin namespace.
- Trade-off accepted: Some extra tenant plumbing, but much better isolation and future flexibility.

- Decision: Use optimistic locking on approval requests.
- Why: Concurrent approve/reject actions should fail cleanly instead of overwriting each other.
- Alternative considered: Pessimistic locking or last-write-wins behavior.
- Trade-off accepted: Occasional retry handling, but safer state transitions.

## Where the interesting code lives
- Domain logic: admin-service/src/main/java/com/bank/admin/domain/Role.java, admin-service/src/main/java/com/bank/admin/domain/ApprovalRequest.java, admin-service/src/main/java/com/bank/admin/domain/AuditEvent.java
- Adapters: admin-service/src/main/java/com/bank/admin/adapter/in/web/AdminController.java, admin-service/src/main/java/com/bank/admin/adapter/out/persistence/*
- Configuration: admin-service/src/main/java/com/bank/admin/AdminServiceApplication.java, admin-service/src/main/java/com/bank/admin/config/OpenApiConfig.java

## Design Q&A
- Q: "Why did you use RBAC here?" → A: It gives a clear, least-privilege model for back-office access and keeps permission checks centralized.
- Q: "How would you scale this to 10x?" → A: The service is already read-light and write-light; I would keep the same transactional model and scale horizontally, then add indexes or projections only if audit or role lookups became hot.
- Q: "What would you change with hindsight?" → A: I would externalize permission names and privileged action types into a more explicit policy model if the catalogue grew much larger.

## Follow-ups
- Add tests for cross-organization isolation on roles, approvals, and audit queries.
- Replace comma-separated permissions with a join table if permission metadata needs to grow.
- Add stronger actor identity validation if requests stop being system-assisted and become fully user-driven.
