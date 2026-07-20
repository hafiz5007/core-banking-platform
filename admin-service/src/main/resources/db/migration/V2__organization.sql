-- ADR-002 (tenancy) rollout for admin-service. The existing audit_event table is already the
-- append-only change trail (ADR-001), so it is org-scoped here rather than adding a second log.

ALTER TABLE role ADD COLUMN organization_id VARCHAR(60) NOT NULL DEFAULT 'DEFAULT';
ALTER TABLE approval_request ADD COLUMN organization_id VARCHAR(60) NOT NULL DEFAULT 'DEFAULT';
ALTER TABLE audit_event ADD COLUMN organization_id VARCHAR(60) NOT NULL DEFAULT 'DEFAULT';

CREATE INDEX idx_role_organization ON role (organization_id);
CREATE INDEX idx_approval_request_organization ON approval_request (organization_id);
CREATE INDEX idx_audit_event_organization ON audit_event (organization_id, occurred_at);
