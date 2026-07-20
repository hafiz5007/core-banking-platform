package com.bank.payment.application.iso20022;

import com.bank.common.money.Money;
import com.bank.payment.domain.Payment;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Builds ISO 20022 messages from domain payments. */
@Component
public class Iso20022MessageFactory {

    /** Build a pacs.008 using the payment's own (source) instructed amount. */
    public Pacs008Message pacs008(Payment payment) {
        return pacs008(payment, payment.money());
    }

    /**
     * Build a pacs.008 with an explicit interbank settlement amount — used for cross-border
     * transfers where the amount sent to the correspondent is the FX-converted target amount.
     */
    public Pacs008Message pacs008(Payment payment, Money settlementAmount) {
        return new Pacs008Message(
                "MSG-" + payment.getId(),
                UUID.randomUUID().toString(),
                OffsetDateTime.now(ZoneOffset.UTC),
                settlementAmount.amount(),
                settlementAmount.currency().getCurrencyCode(),
                payment.getDebtorAccount(),
                payment.getCreditorAccount(),
                payment.getNarrative());
    }
}
