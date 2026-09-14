
# Walkthrough: ledger-service

## Purpose (2 sentences)
Ledger-service owns the authoritative double-entry general ledger for the platform. It manages the chart of accounts, accepts balanced journal entries, supports reversals, and proves the books balance through a trial-balance query.

## Public API surface
- `POST /api/v1/ledger/accounts` create a ledger account
- `GET /api/v1/ledger/accounts/{code}` fetch a ledger account
- `POST /api/v1/ledger/entries` post a balanced journal entry
- `GET /api/v1/ledger/entries/{id}` fetch a journal entry
- `POST /api/v1/ledger/entries/{id}/reversal` write a compensating reversal entry
- `GET /api/v1/ledger/trial-balance` check that debits equal credits
- Events published: none
- Events consumed: none

## Data model (1 paragraph)
The service owns four core tables: `ledger_account`, `journal_entry`, `journal_line`, and `change_log`. `ledger_account` stores the chart of accounts with debit-positive running balances, account type, currency, and tenant scope; `journal_entry` stores immutable balanced postings with idempotency keys and optional reversal linkage; `journal_line` stores the individual debit or credit legs tied to a journal entry; and `change_log` stores append-only entity changes with organization and correlation id. These tables are not shared because the ledger is the system of record for accounting state, and its invariants must remain atomic inside one bounded context.

## Key design decisions (3-5)

- Decision: Enforce double-entry balance at construction time.
- Why: `JournalEntry.validateBalanced` rejects entries with fewer than two lines, mixed currencies, negative legs, or unequal debits and credits before persistence.
- Alternative considered: Allowing the database or downstream reconciliation to catch bad entries later.
- Trade-off accepted: The service does more validation up front, but invalid accounting state can never be stored.

- Decision: Make journal entries immutable and correct them with reversals.
- Why: Accounting history must remain auditable, so mistakes are corrected with a compensating entry instead of editing the original.
- Alternative considered: Updating or deleting posted journal rows.
- Trade-off accepted: More rows and a slightly more complex lifecycle, but a much cleaner audit trail.

- Decision: Use idempotency keys on posting and reversal.
- Why: Replays must not duplicate entries or distort balances if a client retries the same request.
- Alternative considered: Relying on transport-level retries alone.
- Trade-off accepted: Extra uniqueness checks and request metadata, but safe retry behavior.

- Decision: Store balances in debit-positive terms and derive natural balance views.
- Why: The internal posting math becomes simple and consistent across account types.
- Alternative considered: Storing separate debit and credit totals or only natural-side balances.
- Trade-off accepted: The model needs one helper to present account balances in natural sign form, but the posting logic stays straightforward.

- Decision: Keep chart-of-accounts operations in the same service as posting.
- Why: Account creation, posting, and trial balance all depend on the same accounting invariants and tenant scope.
- Alternative considered: Splitting account maintenance into a separate service.
- Trade-off accepted: The service is broader, but the accounting model remains coherent and transactional.

## Where the interesting code lives
- Domain logic: ledger-service/src/main/java/com/bank/ledger/domain/JournalEntry.java, ledger-service/src/main/java/com/bank/ledger/domain/LedgerAccount.java, ledger-service/src/main/java/com/bank/ledger/domain/JournalLine.java
- Adapters: ledger-service/src/main/java/com/bank/ledger/adapter/in/web/LedgerController.java, ledger-service/src/main/java/com/bank/ledger/adapter/out/persistence/*
- Configuration: ledger-service/src/main/java/com/bank/ledger/LedgerServiceApplication.java, ledger-service/src/main/java/com/bank/ledger/config/OpenApiConfig.java

## Design Q&A
- Q: "Why did you enforce balance in the domain?" → A: Because invalid journal entries must be impossible to persist; the accounting invariant belongs in the aggregate, not in a downstream check.
- Q: "How would you scale this to 10x?" → A: I would keep the same posting contract but scale reads and writes by tenant or account partition if contention grew, while preserving the single transactional balance invariant.
- Q: "What would you change with hindsight?" → A: I would likely add more explicit domain services around posting templates or batching if the set of supported accounting flows expanded a lot.

## Follow-ups
- Add more property-based tests around balance and reversal invariants.
- Introduce explicit posting templates if accounting operations become repetitive.
- Add reporting projections if trial-balance or account-balance queries become hot.
