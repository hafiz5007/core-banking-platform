package com.bank.payment.application.rail;

import com.bank.payment.application.iso20022.Iso20022MessageFactory;
import com.bank.payment.application.iso20022.Pacs008Message;
import com.bank.payment.application.port.ClearingPort;
import com.bank.payment.application.port.ClearingPort.ClearingInstruction;
import com.bank.payment.application.port.ClearingPort.ClearingResult;
import com.bank.payment.application.port.LedgerPort;
import com.bank.payment.application.port.LedgerPort.TransferCommand;
import com.bank.payment.domain.Payment;
import com.bank.payment.domain.PaymentType;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Outbound domestic clearing for the instant, RTGS and ACH rails. The beneficiary is at another
 * bank, so the internal money movement debits the debtor's account and credits a clearing
 * settlement account; the real beneficiary travels in the ISO 20022 message sent to the scheme.
 *
 * <p>On a scheme rejection the ledger posting is reversed and a {@link PaymentRailException} is
 * raised so the orchestrator records a COMPENSATED outcome — funds are never left in suspense.
 */
@Component
public class ExternalClearingHandler implements PaymentRailHandler {

    private final LedgerPort ledgerPort;
    private final ClearingPort clearingPort;
    private final Iso20022MessageFactory messageFactory;
    private final String settlementAccount;

    public ExternalClearingHandler(LedgerPort ledgerPort,
                                   ClearingPort clearingPort,
                                   Iso20022MessageFactory messageFactory,
                                   @Value("${clearing.settlement-account:SETTLEMENT}") String settlementAccount) {
        this.ledgerPort = ledgerPort;
        this.clearingPort = clearingPort;
        this.messageFactory = messageFactory;
        this.settlementAccount = settlementAccount;
    }

    @Override
    public Set<PaymentType> supportedTypes() {
        return Set.of(PaymentType.DOMESTIC_INSTANT, PaymentType.RTGS, PaymentType.ACH);
    }

    @Override
    public void execute(Payment payment) {
        String key = payment.getIdempotencyKey();

        // 1. Money movement: debit the customer, credit the clearing settlement account.
        UUID entryId = ledgerPort.postTransfer(new TransferCommand(
                "pay-" + key, payment.getNarrative(),
                payment.getDebtorAccount(), settlementAccount, payment.money()));
        payment.markPosted(entryId);

        // 2. Build the ISO 20022 message and submit to the scheme.
        Pacs008Message message = messageFactory.pacs008(payment);
        ClearingResult result = clearingPort.submit(payment.getType(), new ClearingInstruction("clr-" + key, message));

        // 3. Compensate on rejection.
        if (!result.accepted()) {
            UUID reversalId = ledgerPort.reverse(entryId, "rev-" + key);
            throw new PaymentRailException(reversalId, "Scheme rejected: " + result.reason());
        }

        payment.markSubmitted(result.schemeReference());
        payment.markSettled();
    }
}
