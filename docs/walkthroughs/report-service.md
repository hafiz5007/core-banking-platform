
# Walkthrough: reporting-service

## Purpose (2 sentences)
Reporting-service owns operational and regulatory reporting from a dedicated metrics projection. It collects business-date metrics from the rest of the platform, stores them outside the transactional path, and produces daily totals by metric key.

## Public API surface
- `POST /api/v1/reports/metrics` record a reporting metric
- `GET /api/v1/reports/daily?date=YYYY-MM-DD` generate a daily report
- Events published: none
- Events consumed: none

## Data model (1 paragraph)
The service owns a single core table, `report_metric`, which stores one business-date metric figure at a time. Each row captures the organization, business date, metric key, metric value, source, and timestamp; the daily report simply reads all rows for a date and aggregates them by key. It is not shared because reporting is intentionally a read-model/projection concern and should stay decoupled from the transactional schemas that produce the numbers.

## Key design decisions (3-5)

- Decision: Store reporting data as a projection, not as transactional source data.
- Why: Reports can be queried and summed without touching live business tables.
- Alternative considered: Building reports directly from each service’s operational database.
- Trade-off accepted: The projection can lag the source slightly, but reporting stays isolated and fast.

- Decision: Key every metric by business date and metric name.
- Why: The service needs simple daily rollups and clear totals by category.
- Alternative considered: Embedding richer dimensional analytics in the main table.
- Trade-off accepted: The model is intentionally simple, so more advanced slicing would need future expansion.

- Decision: Keep reporting outside the transactional path.
- Why: Recording a metric should not interfere with the core business flow that produced it.
- Alternative considered: Synchronous report generation in the source service.
- Trade-off accepted: The report model is derived, but the source systems stay decoupled and performant.

- Decision: Scope metrics by organization.
- Why: Regulatory and operational reports must remain tenant-aware.
- Alternative considered: A global shared metrics bucket.
- Trade-off accepted: Slightly more scoping logic, but much better separation for multi-organization use.

- Decision: Use a simple aggregate query in the service layer.
- Why: Summing by metric key is easy to understand and keeps the reporting logic explicit.
- Alternative considered: Pre-computing totals in additional tables or materialized views.
- Trade-off accepted: Slightly more work at query time, but less storage and maintenance overhead.

## Where the interesting code lives
- Domain logic: reporting-service/src/main/java/com/bank/reporting/domain/ReportMetric.java
- Adapters: reporting-service/src/main/java/com/bank/reporting/adapter/in/web/ReportingController.java, reporting-service/src/main/java/com/bank/reporting/adapter/out/persistence/ReportMetricRepository.java
- Configuration: reporting-service/src/main/java/com/bank/reporting/ReportingServiceApplication.java, reporting-service/src/main/java/com/bank/reporting/config/OpenApiConfig.java

## Design Q&A
- Q: "Why did you use a projection instead of querying live tables?" → A: Reporting needs to stay off the transactional path and should be queryable without coupling to operational schemas.
- Q: "How would you scale this to 10x?" → A: I would keep the same projection pattern but partition or pre-aggregate by date and organization if metric volume became large.
- Q: "What would you change with hindsight?" → A: I would likely add richer dimensions or a separate analytics store only if the reporting questions outgrew simple daily totals.

## Follow-ups
- Add more report types if the business needs weekly or monthly rollups.
- Add pre-aggregation or partitioning if the metrics table grows significantly.
- Add export formats for downstream regulatory or finance consumers.
