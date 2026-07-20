-- Sprint 10: reporting projection.

CREATE TABLE report_metric (
    id            UUID         NOT NULL,
    business_date DATE         NOT NULL,
    metric_key    VARCHAR(80)  NOT NULL,
    metric_value  NUMERIC(23, 4) NOT NULL,
    source        VARCHAR(60)  NOT NULL,
    recorded_at   TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_report_metric PRIMARY KEY (id)
);

CREATE INDEX idx_report_metric_date ON report_metric (business_date, metric_key);
