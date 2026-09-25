package com.bank.card.adapter.in.web.dto;

import com.bank.card.domain.Authorization;
import com.bank.card.domain.AuthorizationStatus;
import java.util.UUID;

/** Response view of a card authorization. */
public record AuthorizationResponse(
    UUID id,
    UUID cardId,
    String amount,
    String currencyCode,
    String merchant,
    AuthorizationStatus status) {

  public static AuthorizationResponse from(Authorization a) {
    return new AuthorizationResponse(
        a.getId(),
        a.getCardId(),
        a.getAmount().toPlainString(),
        a.getCurrencyCode(),
        a.getMerchant(),
        a.getStatus());
  }
}
