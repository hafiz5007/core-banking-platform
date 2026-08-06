
# Walkthrough: risk-aml-service

## Purpose (2 sentences)
What business capability does it own?
Risk-aml-service owns real-time transaction monitoring, alert generation, AML investigation cases, and optional SAR filing on case closure. It evaluates transactions against deterministic rules, opens cases when a rule fires, and keeps an append-only audit trail of what was opened and why.

## Public API surface
- `POST /api/v1/risk/evaluate` evaluate a transaction against AML rules
- `GET /api/v1/risk/cases/{id}` fetch an AML case
- `POST /api/v1/risk/cases/{id}/close` close a case and optionally file a SAR
- Events published: none
- Events consumed: none

## Data model (1 paragraph)
The service owns four main tables: `aml_case`, `aml_alert`, `change_log`, and the repository-backed monitoring engine does not persist rule state. `aml_case` stores the investigation lifecycle, tenant, account reference, fired rule, closure resolution, and SAR flag; `aml_alert` stores each triggered transaction alert and links it to the case that was opened; and `change_log` stores append-only audit rows scoped by organization and correlation id. These tables are not shared because AML monitoring, case handling, and audit history must remain transactional and isolated inside this bounded context.

## Key design decisions (3-5)
For each:
- Decision: Keep monitoring rules deterministic and explicit.
- Why: The `MonitoringEngine` makes AML decisions from a small set of testable rules like high-risk country, large amount, and possible structuring.
- Alternative considered: Using a black-box model with no obvious rule trace.
- Trade-off accepted: The engine is simpler than a full model-driven system, but easier to reason about and validate.

- Decision: Open a case only when a rule actually fires.
- Why: Alerts and cases stay tied to a concrete rule code and transaction reference, which makes investigations traceable.
- Alternative considered: Creating cases for every transaction or every alert type.
- Trade-off accepted: The service does less by default, but the investigative workload stays focused.

- Decision: Keep SAR filing as a case-closure outcome.
- Why: The case can be resolved with a human-readable resolution and an optional SAR flag in the same workflow.
- Alternative considered: Splitting SAR filing into a separate service step.
- Trade-off accepted: The closure API carries one more flag, but the workflow stays compact.

- Decision: Record local change-log rows for AML case creation.
- Why: AML events need a tenant-scoped, correlation-aware audit trail that can be queried later.
- Alternative considered: Relying only on application logs or the case table itself.
- Trade-off accepted: More storage, but much better auditability and supportability.

- Decision: Scope cases and alerts by organization.
- Why: Monitoring and investigations must remain partitioned by tenant and traceable to the current organization context.
- Alternative considered: A single global AML queue.
- Trade-off accepted: More tenant plumbing, but safer multi-organization operation.

## Where the interesting code lives
- Domain logic: risk-aml-service/src/main/java/com/bank/riskaml/domain/AmlCase.java, risk-aml-service/src/main/java/com/bank/riskaml/domain/Alert.java, risk-aml-service/src/main/java/com/bank/riskaml/domain/ChangeLog.java
- Adapters: risk-aml-service/src/main/java/com/bank/riskaml/adapter/in/web/RiskController.java, risk-aml-service/src/main/java/com/bank/riskaml/adapter/out/persistence/*
- Configuration: risk-aml-service/src/main/java/com/bank/riskaml/RiskAmlServiceApplication.java, risk-aml-service/src/main/java/com/bank/riskaml/config/OpenApiConfig.java

## Interview flashcards
- Q: "Why did you keep the rules deterministic?" → A: It makes AML decisions explainable, testable, and easy to audit, which matters more than model complexity at this stage.
- Q: "How would you scale this to 10x?" → A: I would keep the rule engine small and stateless, then partition alert/case persistence and add richer asynchronous review workflows if transaction volume grew sharply.
- Q: "What would you change with hindsight?" → A: I would probably split case management and monitoring rules into separate components once the rule set and investigation workflow become much larger.

## Follow-ups
- Add richer rule coverage and regression tests for borderline structuring scenarios.
- Add analyst assignment and case notes if the investigation workflow grows.
- Replace the simple rule engine with configurable rule sets or model-assisted scoring if the business needs it later.
```