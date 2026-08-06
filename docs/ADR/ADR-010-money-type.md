# ADR-010: Money Type in common-lib (vs. BigDecimal Everywhere)

- Status: **Accepted**
- Date: 2026-08-06
- Deciders: Shardar Rahman
- Scope: common-lib, all services

## Context

Monetary amounts appear in almost every service: account balances, journal lines, payment amounts,
fee calculations, interest accruals. The raw Java type for financial arithmetic is `BigDecimal`, but
`BigDecimal` alone carries no currency information. Without a dedicated type:

- A method accepting `BigDecimal amount` cannot tell `GBP 100` from `USD 100` — mixing currencies
  silently is a critical bug class in financial software.
- Scale and rounding rules (e.g. `RoundingMode.HALF_EVEN` for banking) must be specified at every
  call site, leading to inconsistency.
- Serialisation format (how many decimal places? exponent form?) is re-decided per developer.

## Decision

Define a **`Money` value object in `common-lib`** that is the canonical representation of all
monetary amounts across the platform:

```java
public record Money(BigDecimal amount, Currency currency) {

    // Validation on construction
    public Money {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
        if (amount.scale() > currency.getDefaultFractionDigits()) {
            throw new IllegalArgumentException(
                "Scale %d exceeds %s fraction digits %d"
                    .formatted(amount.scale(), currency, currency.getDefaultFractionDigits()));
        }
    }

    public Money add(Money other) {
        assertSameCurrency(other);
        return new Money(amount.add(other.amount), currency);
    }

    public Money subtract(Money other) {
        assertSameCurrency(other);
        return new Money(amount.subtract(other.amount), currency);
    }

    public boolean isPositive()  { return amount.compareTo(BigDecimal.ZERO) > 0; }
    public boolean isNegative()  { return amount.compareTo(BigDecimal.ZERO) < 0; }
    public boolean isZero()      { return amount.compareTo(BigDecimal.ZERO) == 0; }

    private void assertSameCurrency(Money other) {
        if (!currency.equals(other.currency))
            throw new CurrencyMismatchException(currency, other.currency);
    }
}
```

**Persistence:** stored as two columns — `amount NUMERIC(19,4)` and `currency CHAR(3)` — with a
custom Hibernate `@Embeddable` or `AttributeConverter`. Serialised as `{"amount": "100.00",
"currency": "GBP"}` in JSON (string amount to avoid IEEE 754 floating-point precision loss).

**Protobuf / gRPC:** mapped to the `Money` message in `ledger.proto` (units + nanos + currency_code
following the Google Money type convention).

## Alternatives considered

1. **`BigDecimal` everywhere with a currency convention** — simplest; no shared type needed;
   however, currency is carried as a separate parameter (or not at all), arithmetic helper methods
   are duplicated per service, and scale/rounding is inconsistently applied. Rejected: the first
   cross-currency bug will be silent and hard to trace.
2. **JSR 354 `javax.money` (Moneta)** — standard Java Money API with full currency, rounding, and
   exchange-rate support; however, `MonetaryAmount` is an interface hierarchy with less-obvious
   serialisation support and a heavier dependency. Acceptable alternative but adds complexity not
   needed for the platform's scope. Rejected in favour of a thin, purpose-built record.
3. **`long` minor units (pence, cents) + currency code** — used internally by Stripe and Wise;
   avoids floating-point entirely; however, it makes multi-currency arithmetic (where fraction
   digit counts differ, e.g. JPY vs. GBP) error-prone, and is less readable in domain code.
   Noted as an upgrade path for high-performance paths if needed.

## Consequences

- **Positive:** Currency mismatch is a compile-time type-check (you cannot pass `Money` where only
  the amount is expected); rounding rules are centralised; JSON/Protobuf serialisation is
  consistent across all services.
- **Negative:** Adds a `common-lib` dependency to every service (already required for other shared
  types); minor friction when bridging to external systems that use raw `BigDecimal`.
- **Risks + mitigations:** `common-lib` changes to `Money` break all services — mitigated by
  semantic versioning of `common-lib` and a no-breaking-change policy for the `Money` record's
  public API.

## References

- ADR-003 — Ledger design (journal lines use `Money`)
- ADR-007 — gRPC schema (`Money` Protobuf message)
- ADR-011 — Correlation-ID propagation (also in common-lib)
- [Google Money Protobuf type](https://github.com/googleapis/googleapis/blob/master/google/type/money.proto)
