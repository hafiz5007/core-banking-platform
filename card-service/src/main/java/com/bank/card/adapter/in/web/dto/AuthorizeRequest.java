package com.bank.card.adapter.in.web.dto;

import com.bank.card.domain.CardChannel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

/** Request to authorize a card transaction. */
public record AuthorizeRequest(
        @NotNull @Positive BigDecimal amount,
        @NotNull @Pattern(regexp = "^[A-Z]{3}$", message = "currencyCode must be a 3-letter ISO-4217 code")
        String currencyCode,
        @NotBlank String merchant,
        CardChannel channel,
        boolean international) {

    public CardChannel channelOrDefault() {
        return channel == null ? CardChannel.POS : channel;
    }
}
