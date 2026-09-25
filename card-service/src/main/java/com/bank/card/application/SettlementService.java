package com.bank.card.application;

import com.bank.common.error.BusinessException;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Reconciles a clearing/settlement file from the card scheme against our authorizations
 * (FR-CRD-003). Each entry references an authorization; a matched, approved authorization is
 * settled (posting to the ledger), while anything that cannot be matched is reported as an
 * exception for investigation.
 */
@Service
public class SettlementService {

  private static final Logger log = LoggerFactory.getLogger(SettlementService.class);

  private final CardService cardService;

  public SettlementService(CardService cardService) {
    this.cardService = cardService;
  }

  public ReconciliationSummary reconcile(List<ClearingEntry> entries) {
    int matched = 0;
    int unmatched = 0;
    for (ClearingEntry entry : entries) {
      try {
        cardService.settle(entry.authorizationId());
        matched++;
      } catch (BusinessException e) {
        unmatched++;
        log.warn(
            "Unmatched clearing entry {} (auth {}): {}",
            entry.networkRef(),
            entry.authorizationId(),
            e.getMessage());
      }
    }
    ReconciliationSummary summary = new ReconciliationSummary(entries.size(), matched, unmatched);
    log.info("Settlement reconciliation: {}", summary);
    return summary;
  }

  /** One line of a scheme clearing file. */
  public record ClearingEntry(UUID authorizationId, String networkRef) {}

  public record ReconciliationSummary(int total, int matched, int unmatched) {}
}
