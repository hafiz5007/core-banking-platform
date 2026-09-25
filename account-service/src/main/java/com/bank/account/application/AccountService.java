package com.bank.account.application;

import com.bank.account.adapter.out.persistence.AccountHolderRepository;
import com.bank.account.adapter.out.persistence.AccountRepository;
import com.bank.account.application.port.LedgerPostingPort;
import com.bank.account.application.port.LedgerProvisioningPort;
import com.bank.account.domain.Account;
import com.bank.account.domain.AccountHolder;
import com.bank.account.domain.AccountType;
import com.bank.account.domain.ChangeType;
import com.bank.account.domain.HolderRole;
import com.bank.account.domain.MandateType;
import com.bank.account.domain.Product;
import com.bank.common.error.ResourceNotFoundException;
import com.bank.common.money.Money;
import com.bank.common.tenant.TenantContext;
import java.security.SecureRandom;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Use-case layer for account operations. Holds no framework concerns beyond transactions. */
@Service
public class AccountService {

  private static final Logger log = LoggerFactory.getLogger(AccountService.class);
  private static final SecureRandom RANDOM = new SecureRandom();

  private final AccountRepository repository;
  private final AccountHolderRepository holderRepository;
  private final ProductService productService;
  private final LedgerProvisioningPort ledgerProvisioning;
  private final LedgerPostingPort ledgerPosting;
  private final ChangeLogRecorder changeLog;

  public AccountService(
      AccountRepository repository,
      AccountHolderRepository holderRepository,
      ProductService productService,
      LedgerProvisioningPort ledgerProvisioning,
      LedgerPostingPort ledgerPosting,
      ChangeLogRecorder changeLog) {
    this.repository = repository;
    this.holderRepository = holderRepository;
    this.productService = productService;
    this.ledgerProvisioning = ledgerProvisioning;
    this.ledgerPosting = ledgerPosting;
    this.changeLog = changeLog;
  }

  /** Open a simple account (single mandate, no product). */
  @Transactional
  public Account openAccount(UUID customerId, AccountType type, Currency currency) {
    Account account =
        repository.save(
            Account.open(
                TenantContext.getOrDefault(),
                customerId,
                type,
                currency,
                generateUniqueAccountNumber()));
    holderRepository.save(new AccountHolder(account.getId(), customerId, HolderRole.PRIMARY));
    ledgerProvisioning.provisionAccount(
        account.getAccountNumber(), "Customer deposits " + account.getAccountNumber(), currency);
    changeLog.record(
        "Account",
        account.getId().toString(),
        ChangeType.CREATE,
        null,
        "Opened " + type + " account " + account.getAccountNumber());
    log.info(
        "Opened account {} ({}/{}) for customer {} org {}",
        account.getAccountNumber(),
        type,
        currency.getCurrencyCode(),
        customerId,
        account.getOrganizationId());
    return account;
  }

  /** Open an account against a catalogue product, with optional joint holders (FR-ACC-003/006). */
  @Transactional
  public Account openFromProduct(
      String productCode,
      UUID primaryCustomerId,
      List<UUID> additionalHolders,
      MandateType mandateType) {
    Product product = productService.getByCode(productCode);
    MandateType mandate = mandateType == null ? MandateType.SINGLE : mandateType;
    Account account =
        repository.save(
            Account.openFromProduct(
                TenantContext.getOrDefault(),
                primaryCustomerId,
                generateUniqueAccountNumber(),
                product,
                mandate));
    holderRepository.save(
        new AccountHolder(account.getId(), primaryCustomerId, HolderRole.PRIMARY));
    if (additionalHolders != null) {
      for (UUID holder : additionalHolders) {
        holderRepository.save(new AccountHolder(account.getId(), holder, HolderRole.JOINT));
      }
    }
    ledgerProvisioning.provisionAccount(
        account.getAccountNumber(),
        "Customer deposits " + account.getAccountNumber(),
        product.currency());
    changeLog.record(
        "Account",
        account.getId().toString(),
        ChangeType.CREATE,
        null,
        "Opened account " + account.getAccountNumber() + " from product " + productCode);
    log.info(
        "Opened account {} from product {} (mandate {}) for customer {}",
        account.getAccountNumber(),
        productCode,
        mandate,
        primaryCustomerId);
    return account;
  }

  /**
   * Debit an account and post the matching double-entry to the general ledger (ADR-007).
   *
   * <p>The domain applies the debit rules and the balance change; the ledger posting goes out
   * through {@link LedgerPostingPort} (gRPC in a wired deployment). The posting happens inside the
   * transaction on purpose: if the ledger rejects the entry the balance change is rolled back, so
   * the account and the ledger cannot disagree. The cost is that a ledger outage fails the debit —
   * the correct trade-off here, since a debit that never reaches the ledger is worse than one that
   * is refused. The ledger is idempotent on {@code idempotencyKey}, so a retry is safe.
   */
  @Transactional
  public Account debitAccount(
      UUID accountId, Money amount, String narrative, String idempotencyKey) {
    Account account = getAccount(accountId);
    account.debit(amount);
    repository.save(account);

    String entryId =
        ledgerPosting.postDebit(
            account.getAccountNumber(),
            amount,
            narrative == null || narrative.isBlank() ? "Account debit" : narrative,
            idempotencyKey);

    changeLog.record(
        "Account",
        account.getId().toString(),
        ChangeType.UPDATE,
        null,
        "Debited " + amount + (entryId == null ? "" : " (ledger entry " + entryId + ")"));
    log.info(
        "Debited {} from account {} -> balance {} ledgerEntry={}",
        amount,
        account.getAccountNumber(),
        account.balance(),
        entryId);
    return account;
  }

  @Transactional(readOnly = true)
  public Account getAccount(UUID id) {
    return repository
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + id));
  }

  @Transactional(readOnly = true)
  public List<AccountHolder> holdersOf(UUID accountId) {
    getAccount(accountId);
    return holderRepository.findByAccountId(accountId);
  }

  private String generateUniqueAccountNumber() {
    String candidate;
    do {
      // Demo scheme: 12-digit numeric. Production would derive a scheme-valid IBAN.
      candidate = String.format("%012d", Math.abs(RANDOM.nextLong()) % 1_000_000_000_000L);
    } while (repository.existsByAccountNumber(candidate));
    return candidate;
  }
}
