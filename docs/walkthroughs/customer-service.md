
# Walkthrough: customer-service

## Purpose (2 sentences)
Customer-service owns customer onboarding, KYC and sanctions/PEP screening, risk rating, and consent capture. It also manages Open Banking consents for third-party providers with strong customer authentication and bounded expiry.

## Public API surface
- `POST /api/v1/customers` onboard a customer
- `GET /api/v1/customers/{id}` fetch a customer
- `GET /api/v1/customers/{id}/change-log` fetch the customer audit trail
- `GET /api/v1/customers/{id}/consents` list recorded consents
- `POST /api/v1/customers/{id}/consents` record a consent grant or withdrawal
- `POST /api/v1/open-banking/consents` request an Open Banking consent
- `GET /api/v1/open-banking/consents/{reference}` fetch an Open Banking consent
- `POST /api/v1/open-banking/consents/{reference}/authorise` authorise a consent with SCA
- `POST /api/v1/open-banking/consents/{reference}/revoke` revoke an Open Banking consent
- Events published: none
- Events consumed: none

## Data model (1 paragraph)
The service owns four tables in its private `customer` schema: `customer`, `consent`, `open_banking_consent`, and `change_log`. The KYC and screening integrations are stubs behind ports and persist nothing of their own — only their outcome is kept, on the customer record as `kyc_status`, `kyc_evidence_ref` and `screening_case_ref`, so evidence is held by reference rather than copied into the bank's store. `customer` stores the party master record plus KYC, screening, and risk state; `consent` records append-only customer consent grants or withdrawals; `change_log` captures append-only entity changes with organization and correlation id; and `open_banking_consent` stores TPP access grants with scopes, expiry, and SCA reference. These tables are not shared because onboarding, consent, and regulated access-control rules belong to this bounded context and need transactional consistency and auditability.

## Key design decisions (3-5)

- Decision: Orchestrate onboarding as a single service use case.
- Why: The flow needs to create the customer, verify KYC, screen for sanctions/PEP, assign a risk rating, and settle the final status consistently.
- Alternative considered: Splitting onboarding into several synchronous services.
- Trade-off accepted: The service is a bit richer, but the business rule flow stays atomic and easier to reason about.

- Decision: Keep KYC and screening behind outbound ports with stub adapters.
- Why: Real providers can be swapped in later while local development and tests stay deterministic.
- Alternative considered: Calling external providers directly from the domain or hard-coding one provider.
- Trade-off accepted: More adapter code now, but much lower integration risk later.

- Decision: Model customer consents as append-only records.
- Why: Consent changes should preserve the history of what was granted or withdrawn, when, and through which channel.
- Alternative considered: Updating a single consent row in place.
- Trade-off accepted: More rows over time, but much better auditability.

- Decision: Separate Open Banking consent from internal customer consent.
- Why: TPP access has its own lifecycle, scopes, expiry, and SCA rules that do not match ordinary marketing or processing consent.
- Alternative considered: Folding TPP consent into the same generic consent table.
- Trade-off accepted: Two consent models to understand, but cleaner regulatory boundaries.

- Decision: Keep organization scoping on all regulated records.
- Why: Customer, consent, and audit data must be partitioned per organization and traced with the current tenant context.
- Alternative considered: One global customer namespace.
- Trade-off accepted: A little more tenant plumbing, but much stronger isolation and traceability.

## Where the interesting code lives
- Domain logic: customer-service/src/main/java/com/bank/customer/domain/Customer.java, customer-service/src/main/java/com/bank/customer/domain/Consent.java, customer-service/src/main/java/com/bank/customer/openbanking/OpenBankingConsent.java, customer-service/src/main/java/com/bank/customer/domain/ChangeLog.java
- Adapters: customer-service/src/main/java/com/bank/customer/adapter/in/web/CustomerController.java, customer-service/src/main/java/com/bank/customer/openbanking/OpenBankingConsentController.java, customer-service/src/main/java/com/bank/customer/adapter/out/kyc/StubKycVerificationAdapter.java, customer-service/src/main/java/com/bank/customer/adapter/out/screening/StubScreeningAdapter.java, customer-service/src/main/java/com/bank/customer/adapter/out/persistence/*
- Configuration: customer-service/src/main/java/com/bank/customer/CustomerServiceApplication.java, customer-service/src/main/java/com/bank/customer/config/OpenApiConfig.java

## Interview flashcards
- Q: "Why did you keep KYC and screening behind ports?" → A: It lets the service swap real providers in later while keeping local development and testing deterministic.
- Q: "How would you scale this to 10x?" → A: I would keep onboarding transactional, keep provider calls behind ports, and move any heavy screening or consent processing to async workflows only if those external integrations became the bottleneck.
- Q: "What would you change with hindsight?" → A: I would likely split the Open Banking consent lifecycle into its own service once its regulatory and integration surface grew much larger.

## Follow-ups
- Replace stub KYC and screening adapters with real provider integrations.
- Add richer consent revocation and expiry reporting.
- Consider extracting Open Banking consent into a separate service if the TPP scope expands.
- Not covered by service-to-service authentication (ADR-008). customer-service receives only gateway traffic, and guarding it requires the gateway to mint tokens first — see the JWT item in `docs/PENDING_TASKS.md`.
