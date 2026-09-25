package com.bank.ledger.adapter.in.web.dto;

import com.bank.ledger.domain.Direction;
import com.bank.ledger.domain.EntryStatus;
import com.bank.ledger.domain.JournalEntry;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Response view of a journal entry and its lines. */
public record JournalEntryResponse(
    UUID id,
    String idempotencyKey,
    String narrative,
    LocalDate valueDate,
    EntryStatus status,
    UUID reversalOfId,
    List<LineView> lines,
    Instant createdAt) {

  public record LineView(
      String accountCode, Direction direction, String amount, String currencyCode) {}

  public static JournalEntryResponse from(JournalEntry e) {
    List<LineView> lines =
        e.getLines().stream()
            .map(
                l ->
                    new LineView(
                        l.getAccountCode(), l.getDirection(),
                        l.getAmount().toPlainString(), l.getCurrencyCode()))
            .toList();
    return new JournalEntryResponse(
        e.getId(),
        e.getIdempotencyKey(),
        e.getNarrative(),
        e.getValueDate(),
        e.getStatus(),
        e.getReversalOfId(),
        lines,
        e.getCreatedAt());
  }
}
