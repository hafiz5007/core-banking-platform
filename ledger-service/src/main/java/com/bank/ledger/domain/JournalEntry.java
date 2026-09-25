package com.bank.ledger.domain;

import com.bank.common.error.BusinessException;
import com.bank.common.error.ErrorCode;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A journal entry: an immutable, balanced set of debit and credit lines posted together. The
 * balanced invariant (debits == credits, single currency, at least two legs) is enforced at
 * construction so an unbalanced entry can never be persisted.
 */
@Entity
@Table(name = "journal_entry")
public class JournalEntry {

  @Id
  @Column(nullable = false, updatable = false)
  private UUID id;

  @Column(name = "idempotency_key", nullable = false, unique = true, updatable = false, length = 80)
  private String idempotencyKey;

  @Column(nullable = false, updatable = false, length = 280)
  private String narrative;

  @Column(name = "value_date", nullable = false, updatable = false)
  private LocalDate valueDate;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private EntryStatus status;

  @Column(name = "reversal_of_id", updatable = false)
  private UUID reversalOfId;

  @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
  @JoinColumn(name = "journal_entry_id", nullable = false, updatable = false)
  private List<JournalLine> lines = new ArrayList<>();

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected JournalEntry() {
    // Required by JPA.
  }

  private JournalEntry(
      String idempotencyKey,
      String narrative,
      LocalDate valueDate,
      List<JournalLine> lines,
      UUID reversalOfId) {
    validateBalanced(lines);
    this.id = UUID.randomUUID();
    this.idempotencyKey = idempotencyKey;
    this.narrative = narrative;
    this.valueDate = valueDate;
    this.status = EntryStatus.POSTED;
    this.reversalOfId = reversalOfId;
    this.lines = new ArrayList<>(lines);
    this.createdAt = Instant.now();
  }

  public static JournalEntry post(
      String idempotencyKey, String narrative, LocalDate valueDate, List<JournalLine> lines) {
    return new JournalEntry(idempotencyKey, narrative, valueDate, lines, null);
  }

  /** Build a compensating entry that mirrors this one with every leg's direction flipped. */
  public JournalEntry reversal(String idempotencyKey, List<JournalLine> reversedLines) {
    return new JournalEntry(
        idempotencyKey, "Reversal of " + this.id, LocalDate.now(), reversedLines, this.id);
  }

  public void markReversed() {
    this.status = EntryStatus.REVERSED;
  }

  /**
   * Enforce the double-entry invariant. Public and static so it is trivially unit-testable without
   * a database.
   */
  public static void validateBalanced(List<JournalLine> lines) {
    if (lines == null || lines.size() < 2) {
      throw new BusinessException(
          ErrorCode.BUSINESS_RULE_VIOLATION, "A journal entry must have at least two lines");
    }
    String currency = lines.get(0).getCurrencyCode();
    BigDecimal debits = BigDecimal.ZERO;
    BigDecimal credits = BigDecimal.ZERO;
    for (JournalLine line : lines) {
      if (!line.getCurrencyCode().equals(currency)) {
        throw new BusinessException(
            ErrorCode.BUSINESS_RULE_VIOLATION,
            "All lines of a journal entry must share one currency");
      }
      if (line.getAmount().signum() <= 0) {
        throw new BusinessException(
            ErrorCode.BUSINESS_RULE_VIOLATION, "Line amounts must be positive");
      }
      if (line.getDirection() == Direction.DEBIT) {
        debits = debits.add(line.getAmount());
      } else {
        credits = credits.add(line.getAmount());
      }
    }
    if (debits.compareTo(credits) != 0) {
      throw new BusinessException(
          ErrorCode.BUSINESS_RULE_VIOLATION,
          "Unbalanced entry: debits %s != credits %s".formatted(debits, credits));
    }
  }

  public UUID getId() {
    return id;
  }

  public String getIdempotencyKey() {
    return idempotencyKey;
  }

  public String getNarrative() {
    return narrative;
  }

  public LocalDate getValueDate() {
    return valueDate;
  }

  public EntryStatus getStatus() {
    return status;
  }

  public UUID getReversalOfId() {
    return reversalOfId;
  }

  public List<JournalLine> getLines() {
    return List.copyOf(lines);
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
