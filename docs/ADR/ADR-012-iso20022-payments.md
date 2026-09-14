# ADR-012: ISO 20022 Message Format for Payments (vs. Custom JSON)

- Status: **Accepted**
- Date: 2026-08-06
- Deciders: Shardar Rahman
- Scope: payment-service

## Context

Payment initiation, status, and statement messages need a defined schema. The options are:

1. **Custom JSON** — ad-hoc field names designed for this platform.
2. **ISO 20022** — the international standard for financial messaging, mandated by SWIFT, Faster
   Payments, SEPA, and increasingly by UK Open Banking and the Bank of England for CHAPS.

UK banks are actively migrating from MT (SWIFT legacy format) to ISO 20022 (`MX`) messages.
Candidates at senior/architect level are expected to be familiar with the standard.

## Decision

Use **ISO 20022 message types** for payment flows in `payment-service`:

| Flow | ISO 20022 message | Description |
|------|-------------------|-------------|
| Payment initiation | `pain.001.001.09` | Customer Credit Transfer Initiation |
| Payment status | `pain.002.001.11` | Customer Payment Status Report |
| Account statement | `camt.053.001.08` | Bank to Customer Statement |
| Intraday statement | `camt.052.001.08` | Bank to Customer Account Report |

**Implementation approach:**

- Use **JAXB-generated Java classes** from the official ISO 20022 XSD schemas (available from
  `iso20022.org`); classes are generated once and committed to `payment-service/src/generated`.
- Internal service calls and Kafka events use a **lean internal DTO** (plain JSON); ISO 20022 XML
  is serialised/deserialised at the **service boundary** (inbound REST/Kafka and outbound to rails)
  using a `PaymentMessageMapper`.
- The XSD schema version is pinned; upgrades are explicit. Do not auto-download schemas from the
  internet at build time.

```
payment-service/
  src/
    generated/          ← JAXB classes from pain.001, pain.002, camt.052, camt.053 XSD
    main/
      mapper/           ← PaymentMessageMapper: ISO 20022 ↔ internal DTO
      service/          ← domain logic on internal DTOs only
```

## Alternatives considered

1. **Custom JSON schema** — fastest to implement; however, it forces any integration with real
   payment rails (Faster Payments, SWIFT, SEPA) to add a translation layer later — and that
   translation is exactly where message-mapping defects appear. Rejected.
2. **FIX protocol** — capital markets messaging standard; not used for retail/corporate banking
   payments. Rejected (wrong domain).
3. **SWIFT MT messages (legacy)** — still in use at many correspondent banks but being phased out
   by November 2025 mandatory migration. Rejected: building on a deprecated standard.
4. **Apache Camel + ISO 20022 component** — provides routing and transformation but adds a large
   dependency for a feature the platform implements directly. Rejected: unnecessary complexity.

## Consequences

- **Positive:** Demonstrates ISO 20022 awareness — directly relevant to senior UK banking roles;
  `pain.001` → internal DTO → Kafka → `camt.053` is a walkable end-to-end demo; real-rail
  integration requires only replacing the stub rail adapter, not the message model.
- **Negative:** JAXB-generated classes are verbose; ISO 20022 XSD schemas are large and evolve
  slowly; developers must understand the namespace and message structure before contributing.
- **Risks + mitigations:** XSD version mismatch with counterparty — mitigated by pinning versions
  and treating version upgrades as explicit change events. JAXB classes in source control increase
  repo size — acceptable; alternatively generate at build time from a pinned XSD artifact.

## References

- ADR-004 — Outbox pattern (payment events published via outbox after ISO 20022 parse)
- ADR-003 — Ledger design (payment triggers a journal entry)
- [ISO 20022 Message Catalogue](https://www.iso20022.org/iso-20022-message-definitions)
- [Bank of England CHAPS ISO 20022 migration](https://www.bankofengland.co.uk/payment-and-settlement/rtgs-renewal-programme/iso-20022-migration)
