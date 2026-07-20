package com.bank.ledger.application;

import com.bank.common.error.BusinessException;
import com.bank.common.error.ErrorCode;
import com.bank.common.error.ResourceNotFoundException;
import com.bank.common.money.Money;
import com.bank.ledger.adapter.out.persistence.JournalEntryRepository;
import com.bank.ledger.adapter.out.persistence.LedgerAccountRepository;
import com.bank.ledger.domain.Direction;
import com.bank.ledger.domain.JournalEntry;
import com.bank.ledger.domain.JournalLine;
import com.bank.ledger.domain.LedgerAccount;
import com.bank.ledger.domain.LedgerAccountType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Currency;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The posting engine. Guarantees that every journal entry is balanced, posted atomically across all
 * affected accounts, idempotent on replay, and immutable (corrections are made by reversal).
 */
@Service
public class LedgerService {

    private static final Logger log = LoggerFactory.getLogger(LedgerService.class);

    private final LedgerAccountRepository accountRepository;
    private final JournalEntryRepository entryRepository;
    private final ChangeLogRecorder changeLog;

    public LedgerService(LedgerAccountRepository accountRepository, JournalEntryRepository entryRepository,
                         ChangeLogRecorder changeLog) {
        this.accountRepository = accountRepository;
        this.entryRepository = entryRepository;
        this.changeLog = changeLog;
    }

    @Transactional
    public LedgerAccount createAccount(String code, String name, LedgerAccountType type, Currency currency) {
        if (accountRepository.existsByCode(code)) {
            throw new BusinessException(ErrorCode.DUPLICATE_REQUEST, "Account code already exists: " + code);
        }
        LedgerAccount saved = accountRepository.save(LedgerAccount.create(code, name, type, currency));
        changeLog.record("LedgerAccount", saved.getCode(), com.bank.ledger.domain.ChangeType.CREATE,
                null, "Created " + type + " account " + code);
        return saved;
    }

    @Transactional(readOnly = true)
    public LedgerAccount getAccount(String code) {
        return accountRepository.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("Ledger account not found: " + code));
    }

    @Transactional(readOnly = true)
    public JournalEntry getEntry(UUID id) {
        return entryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Journal entry not found: " + id));
    }

    /**
     * Post a balanced journal entry. If the idempotency key has already been used, the original
     * entry is returned unchanged and no balances are touched.
     */
    @Transactional
    public JournalEntry post(PostingCommand command) {
        var existing = entryRepository.findByIdempotencyKey(command.idempotencyKey());
        if (existing.isPresent()) {
            log.info("Idempotent replay of entry key={} -> {}", command.idempotencyKey(), existing.get().getId());
            return existing.get();
        }

        Map<String, LedgerAccount> accounts = loadAccounts(command.lines());
        List<JournalLine> lines = new ArrayList<>();
        for (LineCommand lc : command.lines()) {
            LedgerAccount account = accounts.get(lc.accountCode());
            Money amount = Money.of(lc.amount(), account.currency());
            lines.add(new JournalLine(account.getId(), account.getCode(), lc.direction(), amount));
        }

        // Construction enforces the balanced invariant.
        JournalEntry entry = JournalEntry.post(
                command.idempotencyKey(), command.narrative(),
                command.valueDate() == null ? LocalDate.now() : command.valueDate(), lines);

        applyToBalances(entry.getLines(), accounts);
        accountRepository.saveAll(accounts.values());
        JournalEntry saved = entryRepository.save(entry);
        changeLog.record("JournalEntry", saved.getId().toString(), com.bank.ledger.domain.ChangeType.CREATE,
                null, "Posted entry key " + command.idempotencyKey());
        log.info("Posted entry {} ({} lines) key={}", saved.getId(), lines.size(), command.idempotencyKey());
        return saved;
    }

    /** Reverse a posted entry by writing a compensating entry and restoring balances. */
    @Transactional
    public JournalEntry reverse(UUID entryId, String idempotencyKey) {
        JournalEntry original = getEntry(entryId);
        if (original.getStatus() != com.bank.ledger.domain.EntryStatus.POSTED) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Only a POSTED entry can be reversed: " + entryId);
        }

        List<JournalLine> reversedLines = new ArrayList<>();
        for (JournalLine line : original.getLines()) {
            Direction flipped = line.getDirection() == Direction.DEBIT ? Direction.CREDIT : Direction.DEBIT;
            reversedLines.add(new JournalLine(
                    line.getLedgerAccountId(), line.getAccountCode(), flipped, line.money()));
        }

        Map<String, LedgerAccount> accounts = loadAccountsByLines(reversedLines);
        JournalEntry reversal = original.reversal(idempotencyKey, reversedLines);
        applyToBalances(reversal.getLines(), accounts);
        original.markReversed();

        accountRepository.saveAll(accounts.values());
        entryRepository.save(original);
        JournalEntry saved = entryRepository.save(reversal);
        log.info("Reversed entry {} via {}", entryId, saved.getId());
        return saved;
    }

    /** Prove that the books balance: total debits must equal total credits across all lines. */
    @Transactional(readOnly = true)
    public TrialBalance trialBalance() {
        BigDecimal debits = entryRepository.sumByDirection(Direction.DEBIT);
        BigDecimal credits = entryRepository.sumByDirection(Direction.CREDIT);
        return new TrialBalance(debits, credits, debits.compareTo(credits) == 0);
    }

    private Map<String, LedgerAccount> loadAccounts(List<LineCommand> lines) {
        Map<String, LedgerAccount> accounts = new HashMap<>();
        for (LineCommand lc : lines) {
            accounts.computeIfAbsent(lc.accountCode(), this::getAccount);
        }
        return accounts;
    }

    private Map<String, LedgerAccount> loadAccountsByLines(List<JournalLine> lines) {
        Map<String, LedgerAccount> accounts = new HashMap<>();
        for (JournalLine line : lines) {
            accounts.computeIfAbsent(line.getAccountCode(), this::getAccount);
        }
        return accounts;
    }

    private void applyToBalances(List<JournalLine> lines, Map<String, LedgerAccount> accounts) {
        for (JournalLine line : lines) {
            accounts.get(line.getAccountCode()).apply(line.getDirection(), line.money());
        }
    }

    /** Command to post an entry. */
    public record PostingCommand(
            String idempotencyKey,
            String narrative,
            LocalDate valueDate,
            List<LineCommand> lines) {
    }

    public record LineCommand(String accountCode, Direction direction, BigDecimal amount) {
    }

    public record TrialBalance(BigDecimal totalDebits, BigDecimal totalCredits, boolean balanced) {
    }
}
