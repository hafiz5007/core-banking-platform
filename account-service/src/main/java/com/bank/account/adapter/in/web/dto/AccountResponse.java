package com.bank.account.adapter.in.web.dto;

import com.bank.account.domain.Account;
import com.bank.account.domain.AccountStatus;
import com.bank.account.domain.AccountType;
import com.bank.account.domain.MandateType;
import java.time.Instant;
import java.util.UUID;

/**
 * Response view of an account. Monetary balance is rendered as a string + currency, never a float.
 */
public record AccountResponse(
    UUID id,
    String accountNumber,
    String organizationId,
    UUID customerId,
    AccountType accountType,
    AccountStatus status,
    String balance,
    String currencyCode,
    String productCode,
    MandateType mandateType,
    String minBalance,
    String dailyLimit,
    String overdraftLimit,
    String availableToSpend,
    Instant createdAt) {

  public static AccountResponse from(Account account) {
    return new AccountResponse(
        account.getId(),
        account.getAccountNumber(),
        account.getOrganizationId(),
        account.getCustomerId(),
        account.getAccountType(),
        account.getStatus(),
        account.balance().amount().toPlainString(),
        account.getCurrencyCode(),
        account.getProductCode(),
        account.getMandateType(),
        account.getMinBalance().toPlainString(),
        account.getDailyLimit().toPlainString(),
        account.getOverdraftLimit().toPlainString(),
        account.availableToSpend().amount().toPlainString(),
        account.getCreatedAt());
  }
}
