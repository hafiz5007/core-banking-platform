package com.bank.interestfee.application;

import com.bank.common.error.BusinessException;
import com.bank.common.error.ErrorCode;
import com.bank.common.error.ResourceNotFoundException;
import com.bank.common.money.Money;
import com.bank.interestfee.adapter.out.persistence.FeeChargeRepository;
import com.bank.interestfee.adapter.out.persistence.InterestPositionRepository;
import com.bank.interestfee.application.port.LedgerPort;
import com.bank.interestfee.application.port.LedgerPort.TransferCommand;
import com.bank.interestfee.domain.DayCountBasis;
import com.bank.interestfee.domain.FeeCharge;
import com.bank.interestfee.domain.FeeType;
import com.bank.interestfee.domain.InterestPosition;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Interest accrual, capitalization and fee application (FR-INT-001/002), plus the end-of-day batch
 * (FR-LED-006). Capitalized interest and fees are posted to the ledger through the {@link
 * LedgerPort}.
 */
@Service
public class InterestService {

  private static final Logger log = LoggerFactory.getLogger(InterestService.class);

  private final InterestPositionRepository positionRepository;
  private final FeeChargeRepository feeChargeRepository;
  private final LedgerPort ledgerPort;
  private final ChangeLogRecorder changeLog;
  private final String interestExpenseAccount;
  private final String feeIncomeAccount;

  public InterestService(
      InterestPositionRepository positionRepository,
      FeeChargeRepository feeChargeRepository,
      LedgerPort ledgerPort,
      ChangeLogRecorder changeLog,
      @Value("${interest.expense-account:INTEREST-EXPENSE}") String interestExpenseAccount,
      @Value("${fee.income-account:FEE-INCOME}") String feeIncomeAccount) {
    this.positionRepository = positionRepository;
    this.feeChargeRepository = feeChargeRepository;
    this.ledgerPort = ledgerPort;
    this.changeLog = changeLog;
    this.interestExpenseAccount = interestExpenseAccount;
    this.feeIncomeAccount = feeIncomeAccount;
  }

  @Transactional
  public InterestPosition openPosition(
      String accountCode,
      Currency currency,
      BigDecimal annualRatePercent,
      Money principal,
      DayCountBasis dayCount) {
    if (positionRepository.existsByAccountCode(accountCode)) {
      throw new BusinessException(
          ErrorCode.DUPLICATE_REQUEST, "Interest position already exists for " + accountCode);
    }
    InterestPosition position =
        InterestPosition.open(
            accountCode, currency, annualRatePercent, principal, dayCount, LocalDate.now());
    InterestPosition saved = positionRepository.save(position);
    changeLog.record(
        "InterestPosition",
        saved.getAccountCode(),
        com.bank.interestfee.domain.ChangeType.CREATE,
        null,
        "Opened interest position at " + annualRatePercent + "%");
    return saved;
  }

  @Transactional(readOnly = true)
  public InterestPosition getPosition(String accountCode) {
    return positionRepository
        .findByAccountCode(accountCode)
        .orElseThrow(
            () -> new ResourceNotFoundException("Interest position not found: " + accountCode));
  }

  @Transactional
  public InterestPosition accrue(String accountCode, LocalDate upTo) {
    InterestPosition position = getPosition(accountCode);
    position.accrueTo(upTo);
    return positionRepository.save(position);
  }

  /**
   * Capitalize accrued interest: credit the customer account and debit interest expense, then add
   * the interest to the principal. Returns the position after capitalization.
   */
  @Transactional
  public InterestPosition capitalize(String accountCode) {
    InterestPosition position = getPosition(accountCode);
    Money amount = position.accruedMoney();
    if (amount.isZero()) {
      return position;
    }
    Money capitalized = position.capitalize();
    UUID entryId =
        ledgerPort.postTransfer(
            new TransferCommand(
                "int-" + accountCode + "-" + LocalDate.now(),
                "Interest capitalization " + accountCode,
                interestExpenseAccount,
                accountCode,
                capitalized));
    log.info("Capitalized {} interest for {} (ledger entry {})", capitalized, accountCode, entryId);
    return positionRepository.save(position);
  }

  /** Apply a fee: debit the customer account, credit fee income, and record the charge. */
  @Transactional
  public FeeCharge applyFee(String accountCode, FeeType feeType, Money amount) {
    if (amount.isZero() || amount.isNegative()) {
      throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Fee amount must be positive");
    }
    UUID entryId =
        ledgerPort.postTransfer(
            new TransferCommand(
                "fee-" + UUID.randomUUID(),
                feeType + " fee " + accountCode,
                accountCode,
                feeIncomeAccount,
                amount));
    FeeCharge charge = new FeeCharge(accountCode, feeType, amount, entryId);
    return feeChargeRepository.save(charge);
  }

  /** End-of-day: accrue interest on every position up to the given business date. */
  @Transactional
  public EodSummary runEndOfDay(LocalDate businessDate) {
    List<InterestPosition> positions = positionRepository.findAll();
    int accrued = 0;
    for (InterestPosition position : positions) {
      BigDecimal before = position.getAccruedInterest();
      position.accrueTo(businessDate);
      if (position.getAccruedInterest().compareTo(before) != 0) {
        accrued++;
      }
    }
    positionRepository.saveAll(positions);
    log.info(
        "EOD {}: accrued interest on {}/{} positions", businessDate, accrued, positions.size());
    return new EodSummary(businessDate, positions.size(), accrued);
  }

  public record EodSummary(LocalDate businessDate, int positions, int accrued) {}
}
