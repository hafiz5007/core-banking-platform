-- ADR-002 (tenancy) rollout for reporting-service. Metrics are already append-only records, so no
-- separate change log is needed here — only organization scoping.

ALTER TABLE report_metric ADD COLUMN organization_id VARCHAR(60) NOT NULL DEFAULT 'DEFAULT';
CREATE INDEX idx_report_metric_organization ON report_metric (organization_id, business_date);
