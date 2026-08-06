
# Walkthrough: account-service

## Purpose (2 sentences)
What business capability does it own?
Account-service owns retail deposit account onboarding and product-driven account setup. It creates accounts, attaches holders, provisions the ledger, and keeps an append-only change log for auditability.

## Public API surface
- `POST /api/v1/products` create a product catalogue item
- `GET /api/v1/products` list all products
- `GET /api/v1/products/{code}` fetch one product
- `POST /api/v1/accounts` open a basic account
- `POST /api/v1/accounts/from-product` open an account from a product template
- `GET /api/v1/accounts/{id}` fetch an account
- `GET /api/v1/accounts/{id}/holders` list account holders
- `GET /api/v1/accounts/{id}/change-log` list the account change history
- Events published: none
- Events consumed: none

## Data model (1 paragraph)
The service owns four main tables: `product`, `account`, `account_holder`, and `change_log`. `product` stores catalogue terms such as currency, fees, balances, and limits; `account` stores the live account aggregate with `organization_id`, balance, status, and product reference; `account_holder` links one or more customers to an account for joint ownership; and `change_log` is an append-only audit trail keyed by entity type/id, organization, and correlation id. These tables are not shared because account lifecycle rules, audit history, and holder relationships belong to this bounded context and must be committed transactionally inside the service.

## Key design decisions (3-5)
For each:
- Decision: Use a dedicated product catalogue to open accounts from reusable terms.
- Why: Authorized staff can define currency, fees, and limits without code changes.
- Alternative considered: Hard-coding all account terms in the service or UI.
- Trade-off accepted: More catalogue data to manage, but better flexibility and clearer business ownership.

- Decision: Store money as exact decimal amounts and expose balances through `Money`.
- Why: It avoids floating-point drift and keeps currency-aware arithmetic consistent with the rest of the platform.
- Alternative considered: Using `double` or raw numbers for balances and limits.
- Trade-off accepted: More ceremony in the domain model, but much safer financial calculations.

- Decision: Capture multi-tenancy with `organization_id` and `TenantContext`.
- Why: Every account, product, and audit row is scoped to the current organization and can be filtered consistently.
- Alternative considered: Passing tenant ids through every repository call or relying on one shared global tenant.
- Trade-off accepted: Thread-local context must be cleared correctly, but the request flow stays much simpler.

- Decision: Write audit rows through `ChangeLogRecorder` in the same transaction as the business change.
- Why: The change log commits atomically with the account update and remains append-only for traceability.
- Alternative considered: Sending audit events to a separate logger service.
- Trade-off accepted: The service owns more local persistence logic, but the audit guarantee is stronger.

- Decision: Keep ledger setup behind `LedgerProvisioningPort`.
- Why: The account lifecycle can provision the ledger without hard-coding the downstream integration strategy.
- Alternative considered: Calling the ledger directly from the controller or embedding the integration in the domain.
- Trade-off accepted: One more abstraction layer, but the integration stays swappable and testable.

## Where the interesting code lives
- Domain logic: account-service/src/main/java/com/bank/account/domain/Account.java, account-service/src/main/java/com/bank/account/domain/Product.java, account-service/src/main/java/com/bank/account/domain/ChangeLog.java
- Adapters: account-service/src/main/java/com/bank/account/adapter/in/web/AccountController.java, account-service/src/main/java/com/bank/account/adapter/in/web/ProductController.java, account-service/src/main/java/com/bank/account/adapter/out/persistence/*, account-service/src/main/java/com/bank/account/adapter/out/ledger/*
- Configuration: account-service/src/main/java/com/bank/account/AccountServiceApplication.java, account-service/src/main/java/com/bank/account/config/OpenApiConfig.java

## Interview flashcards
- Q: "Why did you use a product catalogue here?" → A: It lets the business define account terms once and open many accounts from those terms without code changes.
- Q: "How would you scale this to 10x?" → A: I would keep the bounded context and transactional boundaries the same, then scale the service horizontally and move more read-heavy queries to projections if needed.
- Q: "What would you change with hindsight?" → A: I would extract more of the account-opening rules into explicit domain services or factories to make the lifecycle rules easier to test in isolation.

## Follow-ups
- Add more domain tests around account opening, holder assignment, and product inheritance.
- Tighten validation around product status and holder count rules if the business expands.
- Replace the demo account-number generation scheme with a formal bank-numbering strategy.
```