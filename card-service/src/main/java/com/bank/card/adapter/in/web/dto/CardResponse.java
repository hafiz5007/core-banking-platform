package com.bank.card.adapter.in.web.dto;

import com.bank.card.domain.Card;
import com.bank.card.domain.CardStatus;
import java.util.UUID;

/** Response view of a card. The PAN is never exposed — only a token and the last four digits. */
public record CardResponse(
        UUID id,
        String organizationId,
        String cardToken,
        String maskedNumber,
        String accountCode,
        String currencyCode,
        CardStatus status,
        String availableBalance,
        boolean onlineEnabled,
        boolean contactlessEnabled,
        boolean atmEnabled,
        boolean internationalEnabled,
        String perTransactionLimit) {

    public static CardResponse from(Card c) {
        return new CardResponse(
                c.getId(), c.getOrganizationId(), c.getCardToken(), "**** **** **** " + c.getLastFour(),
                c.getAccountCode(), c.getCurrencyCode(), c.getStatus(),
                c.getAvailableBalance().toPlainString(),
                c.isOnlineEnabled(), c.isContactlessEnabled(), c.isAtmEnabled(),
                c.isInternationalEnabled(), c.getPerTransactionLimit().toPlainString());
    }
}
