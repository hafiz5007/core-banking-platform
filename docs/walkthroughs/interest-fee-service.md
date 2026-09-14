
# Walkthrough: interest-fee-service

## Purpose (2 sentences)
Interest-fee-service owns daily interest accrual, interest capitalization, fee application, and the end-of-day batch for deposit accounts. It posts the financial movements to the ledger while keeping a local record of positions, fee charges, and audit history.

## Public API surface
- `POST /api/v1/interest/positions` open an interest-bearing position
- `GET /api/v1/interest/positions/{accountCode}` fetch a position
- `POST /api/v1/interest/positions/{accountCode}/accrue` accrue interest up to a date
- `POST /api/v1/interest/positions/{accountCode}/capitalize` capitalize accrued interest
- `POST /api/v1/interest/fees` apply a fee charge
- `POST /api/v1/interest/eod/run` run the end-of-day batch
- Events published: none
- Events consumed: none

## Data model (1 paragraph)
The service owns four main tables: `interest_position`, `fee_charge`, `change_log`, and the repository-backed ledger adapter does not persist local accounting state. `interest_position` stores the account code, currency, annual rate, principal, accrued interest, day-count basis, and last accrual date; `fee_charge` stores each fee applied to an account together with the ledger entry id; and `change_log` stores append-only audit rows scoped by organization and correlation id. These tables are not shared because interest, fees, and their audit trail need to stay consistent with the service’s own business rules and batch cadence.

## Key design decisions (3-5)

- Decision: Keep interest as a high-precision accrued amount until capitalization.
- Why: Accruing to an 8-decimal scale avoids rounding every day and only rounds when the bank capitalizes.
- Alternative considered: Rounding interest on every accrual step.
- Trade-off accepted: Slightly more precision management, but better financial correctness.

- Decision: Separate accrual from capitalization.
- Why: The service can track earned interest independently from when it is posted to the ledger and added to principal.
- Alternative considered: Posting and capitalizing immediately on each accrual.
- Trade-off accepted: Two steps instead of one, but clearer accounting and batch control.

- Decision: Route postings through a ledger port.
- Why: Capitalized interest and fees become ledger movements without coupling the service to ledger transport details.
- Alternative considered: Calling the ledger implementation directly from the service.
- Trade-off accepted: One more abstraction, but the integration remains swappable.

- Decision: Keep fee charges as a separate local record.
- Why: The service needs an audit record of every fee, the fee type, and the ledger entry id used to post it.
- Alternative considered: Inferring fee history only from the ledger.
- Trade-off accepted: Duplicate record keeping, but much easier operational traceability.

- Decision: Make end-of-day processing a batch over all positions.
- Why: The batch can accrue every position up to the business date and report how many positions changed.
- Alternative considered: Only accruing on-demand per account.
- Trade-off accepted: A scheduled batch job to manage, but a cleaner daily lifecycle.

## Where the interesting code lives
- Domain logic: interest-fee-service/src/main/java/com/bank/interestfee/domain/InterestPosition.java, interest-fee-service/src/main/java/com/bank/interestfee/domain/FeeCharge.java, interest-fee-service/src/main/java/com/bank/interestfee/domain/ChangeLog.java
- Adapters: interest-fee-service/src/main/java/com/bank/interestfee/adapter/in/web/InterestController.java, interest-fee-service/src/main/java/com/bank/interestfee/adapter/in/scheduler/EodScheduler.java, interest-fee-service/src/main/java/com/bank/interestfee/adapter/out/persistence/*, interest-fee-service/src/main/java/com/bank/interestfee/adapter/out/ledger/HttpLedgerAdapter.java
- Configuration: interest-fee-service/src/main/java/com/bank/interestfee/InterestFeeServiceApplication.java, interest-fee-service/src/main/java/com/bank/interestfee/config/OpenApiConfig.java

## Interview flashcards
- Q: "Why did you keep accrued interest separate from principal?" → A: It lets the service track earned-but-not-yet-capitalized interest accurately and post it only when the bank decides to capitalize.
- Q: "How would you scale this to 10x?" → A: I would keep the batch semantics the same, then partition positions by tenant or business grouping and parallelize the end-of-day run if the number of accounts grew significantly.
- Q: "What would you change with hindsight?" → A: I would probably extract the accrual and fee posting policies into dedicated domain services if the product set expanded a lot.

## Follow-ups
- Add tests for different day-count bases and capitalization edge cases.
- Make the scheduled EOD job observable with metrics and progress reporting if it becomes operationally important.
- Expand fee policy handling if the bank adds more charge types or waiver rules.
