# ADR-003: Ledger Design — Double-Entry with Immutable Journal Entries

- Status: **Accepted**
- Date: 2026-08-06
- Deciders: Shardar Rahman
- Scope: ledger-service

## Context

The general ledger is the financial heart of the platform. Every balance change across every account
must be traceable, auditable, and correctable without destroying history. The fundamental question
is: **should ledger records be mutable or immutable?**

A secondary question is whether to use single-entry (one row per balance change) or double-entry
(one debit row + one credit row whose amounts always net to zero).

## Decision

Implement **double-entry bookkeeping with an immutable append-only journal**:

1. **Journal entry** — every financial event creates a `journal_entry` header row (id, reference,
   description, event_time, posted_by, status).
2. **Journal lines** — each entry has exactly two or more `journal_line` rows: at least one DEBIT
   and at least one CREDIT; the sum of all line amounts for an entry must equal zero (`Σ debits =
   Σ credits`). This invariant is enforced in the domain layer before persistence.
3. **Immutability** — rows in `journal_entry` and `journal_line` are never UPDATEd or DELETEd.
   Corrections are made by posting a reversing entry followed by a new correct entry, preserving a
   complete audit trail.
4. **Balance computation** — account balances are derived by summing journal lines for that account
   (or from a maintained `account_balance` snapshot updated atomically in the same transaction as
   the journal lines to avoid full-table aggregation on every read).

```
journal_entry
  id, reference, description, event_time, posted_by, status, organization_id

journal_line
  id, journal_entry_id, account_id, side (DEBIT|CREDIT), amount, currency, organization_id
```

## Alternatives considered

1. **Single-entry ledger (one row per balance delta)** — simpler schema and easier to query, but
   provides no self-validating algebraic constraint; errors are harder to detect and the model does
   not meet accounting standards. Rejected.
2. **Mutable balance rows with history table (via triggers)** — common in legacy RDBMS systems;
   history is available but update/delete paths remain open, making it easier to accidentally corrupt
   balances. Rejected: immutability is the safer default.
3. **Event-sourced ledger (events only, no aggregate row)** — pure event sourcing gives a complete
   audit trail but requires full replay to compute balances; practical only with an event store that
   supports snapshotting. Rejected for v1: adds infra complexity (event store); the balance-snapshot
   approach achieves the same read performance without a second storage system.

## Consequences

- **Positive:** Self-validating (zero-sum constraint catches bugs at write time); full audit history
  without triggers; corrections are first-class domain operations; directly maps to Chart of
  Accounts used in real banking.
- **Negative:** More rows per transaction than single-entry; balance reads require either
  aggregation or a maintained snapshot; developers unfamiliar with bookkeeping need a brief primer.
- **Risks + mitigations:** Snapshot divergence (balance row out of sync with journal) — mitigated
  by updating the snapshot inside the same DB transaction as the journal lines, and by a nightly
  reconciliation job that recomputes balances from the journal and alerts on discrepancies.

## References

- ADR-004 — Outbox pattern (journal posting events are also published via outbox)
- ADR-007 — gRPC between account-service and ledger-service
- [Double-entry bookkeeping — Wikipedia](https://en.wikipedia.org/wiki/Double-entry_bookkeeping)
