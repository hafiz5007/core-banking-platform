package com.bank.account.adapter.in.web.dto;

import com.bank.account.domain.AccountHolder;
import com.bank.account.domain.HolderRole;
import java.util.UUID;

/** Response view of an account holder. */
public record AccountHolderResponse(UUID customerId, HolderRole role) {

  public static AccountHolderResponse from(AccountHolder h) {
    return new AccountHolderResponse(h.getCustomerId(), h.getRole());
  }
}
