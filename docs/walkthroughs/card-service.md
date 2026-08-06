
# Walkthrough: card-service

## Purpose (2 sentences)
What business capability does it own?
Card-service owns debit card issuance, usage controls, real-time authorization, and settlement reconciliation. It keeps PCI scope small by storing only a card token and last four digits, while settlement posts the financial movement to the ledger.

## Public API surface
- `POST /api/v1/cards` issue a new card
- `GET /api/v1/cards/{id}` fetch a card
- `POST /api/v1/cards/{id}/block` block a card
- `POST /api/v1/cards/{id}/unblock` unblock a card
- `POST /api/v1/cards/{id}/controls` update channel and transaction controls
- `POST /api/v1/cards/{id}/authorizations` authorize a transaction and place a hold
- `POST /api/v1/cards/settlement/reconcile` reconcile a scheme clearing file
- `POST /api/v1/cards/authorizations/{authId}/settle` settle an authorization
- `POST /api/v1/cards/authorizations/{authId}/reversal` reverse an authorization
- Events published: none
- Events consumed: none

## Data model (1 paragraph)
The service owns four tables: `card`, `card_authorization`, `change_log`, and the repository-backed ledger adapter does not persist card data itself. `card` stores the tokenized card identity, organization, account code, available balance, status, usage controls, and per-transaction limit; `card_authorization` stores each authorization request and its outcome; and `change_log` is an append-only audit record for card lifecycle changes. These tables are not shared because card usage rules, holds, settlement state, and audit history must stay consistent inside one transactional bounded context.

## Key design decisions (3-5)
For each:
- Decision: Never store the full PAN.
- Why: The card aggregate stores only a token and last four digits, which keeps PCI scope smaller.
- Alternative considered: Persisting the full card number for convenience.
- Trade-off accepted: Tokenization adds a little operational complexity, but greatly reduces sensitive-data exposure.

- Decision: Model authorizations as a separate aggregate.
- Why: An authorization has its own lifecycle and status history independent of the card.
- Alternative considered: Embedding authorization state directly into the card row.
- Trade-off accepted: More tables and repository calls, but clearer lifecycle boundaries and better auditability.

- Decision: Enforce usage controls in the domain model.
- Why: Channel restrictions, international flags, and per-transaction limits are business rules that must apply before a hold is placed.
- Alternative considered: Validating controls only in the controller or gateway.
- Trade-off accepted: The card aggregate is stricter, but it guarantees the rules are always applied.

- Decision: Keep settlement behind a ledger port.
- Why: Settlement must post a balanced movement to the ledger without coupling the card service to a specific transport.
- Alternative considered: Calling the ledger implementation directly from the service.
- Trade-off accepted: One extra abstraction, but the settlement integration stays swappable and testable.

- Decision: Reconcile scheme clearing files through a dedicated service.
- Why: Settlement reconciliation is a batch-style concern that should translate file entries into authorization settlement attempts and a summary result.
- Alternative considered: Folding reconciliation into the authorization endpoint flow.
- Trade-off accepted: Another service class, but much cleaner separation between online authorization and batch reconciliation.

## Where the interesting code lives
- Domain logic: card-service/src/main/java/com/bank/card/domain/Card.java, card-service/src/main/java/com/bank/card/domain/Authorization.java, card-service/src/main/java/com/bank/card/domain/ChangeLog.java
- Adapters: card-service/src/main/java/com/bank/card/adapter/in/web/CardController.java, card-service/src/main/java/com/bank/card/adapter/out/persistence/*, card-service/src/main/java/com/bank/card/adapter/out/ledger/HttpLedgerAdapter.java
- Configuration: card-service/src/main/java/com/bank/card/CardServiceApplication.java, card-service/src/main/java/com/bank/card/config/OpenApiConfig.java

## Interview flashcards
- Q: "Why did you tokenize the card?" → A: It keeps the stored data minimal and avoids persisting the full PAN, which reduces sensitive-data exposure.
- Q: "How would you scale this to 10x?" → A: I would keep the online authorization path small and stateless, then scale it horizontally and separate any batch reconciliation or ledger posting pressure from the synchronous card decision path.
- Q: "What would you change with hindsight?" → A: I would move more of the controls and settlement rules into explicit domain methods or policy objects if the card product matrix became more complex.

## Follow-ups
- Add dedicated tests for each control combination and decline reason.
- Replace the demo token/PAN handling with a real tokenization integration if this were production-bound.
- Expand reconciliation reporting if scheme processing needs richer exception handling.
```