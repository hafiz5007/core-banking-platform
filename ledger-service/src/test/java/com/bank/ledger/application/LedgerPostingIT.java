package com.bank.ledger.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bank.common.error.BusinessException;
import com.bank.ledger.AbstractIntegrationTest;
import com.bank.ledger.application.LedgerService.LineCommand;
import com.bank.ledger.application.LedgerService.PostingCommand;
import com.bank.ledger.domain.Direction;
import com.bank.ledger.domain.EntryStatus;
import com.bank.ledger.domain.JournalEntry;
import com.bank.ledger.domain.LedgerAccountType;
import java.math.BigDecimal;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Full-stack tests of the posting engine against a real PostgreSQL. */
class LedgerPostingIT extends AbstractIntegrationTest {

  private static final Currency USD = Currency.getInstance("USD");

  @Autowired LedgerService ledger;

  private String cash;
  private String deposits;

  @BeforeEach
  void setUpAccounts() {
    // Unique codes per test so the shared container stays clean across tests.
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    cash = "CASH-" + suffix;
    deposits = "DEP-" + suffix;
    ledger.createAccount(cash, "Cash", LedgerAccountType.ASSET, USD);
    ledger.createAccount(deposits, "Customer Deposits", LedgerAccountType.LIABILITY, USD);
  }

  private PostingCommand transfer(String key, String amount) {
    return new PostingCommand(
        key,
        "Customer deposit",
        null,
        List.of(
            new LineCommand(cash, Direction.DEBIT, new BigDecimal(amount)),
            new LineCommand(deposits, Direction.CREDIT, new BigDecimal(amount))));
  }

  @Test
  void postsBalancedEntryAndUpdatesBalances() {
    ledger.post(transfer("k-" + cash, "100.00"));

    assertThat(ledger.getAccount(cash).normalBalance().amount()).isEqualByComparingTo("100.00");
    assertThat(ledger.getAccount(deposits).normalBalance().amount()).isEqualByComparingTo("100.00");

    var tb = ledger.trialBalance();
    assertThat(tb.balanced()).isTrue();
    assertThat(tb.totalDebits()).isEqualByComparingTo(tb.totalCredits());
  }

  @Test
  void replayWithSameKeyDoesNotDoublePost() {
    String key = "idem-" + cash;
    JournalEntry first = ledger.post(transfer(key, "100.00"));
    JournalEntry second = ledger.post(transfer(key, "100.00"));

    assertThat(second.getId()).isEqualTo(first.getId());
    // Balance reflects a single posting, not two.
    assertThat(ledger.getAccount(cash).normalBalance().amount()).isEqualByComparingTo("100.00");
  }

  @Test
  void reversalRestoresBalances() {
    JournalEntry posted = ledger.post(transfer("rev-" + cash, "100.00"));
    ledger.reverse(posted.getId(), "reversal-" + cash);

    assertThat(ledger.getAccount(cash).normalBalance().amount()).isEqualByComparingTo("0.00");
    assertThat(ledger.getAccount(deposits).normalBalance().amount()).isEqualByComparingTo("0.00");
    assertThat(ledger.getEntry(posted.getId()).getStatus()).isEqualTo(EntryStatus.REVERSED);
    assertThat(ledger.trialBalance().balanced()).isTrue();
  }

  @Test
  void rejectsUnbalancedEntry() {
    PostingCommand bad =
        new PostingCommand(
            "bad-" + cash,
            "Bad",
            null,
            List.of(
                new LineCommand(cash, Direction.DEBIT, new BigDecimal("100.00")),
                new LineCommand(deposits, Direction.CREDIT, new BigDecimal("90.00"))));
    assertThatThrownBy(() -> ledger.post(bad)).isInstanceOf(BusinessException.class);
  }
}
