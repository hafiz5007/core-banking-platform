package com.bank.payment.application.rail;

import com.bank.payment.application.port.LedgerPort;
import com.bank.payment.application.port.LedgerPort.TransferCommand;
import com.bank.payment.domain.Payment;
import com.bank.payment.domain.PaymentType;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Intrabank (book) transfer: both parties are on this bank, so the money movement is a single
 * balanced ledger posting — debit the debtor account, credit the creditor account. No external
 * scheme is involved, so the payment is complete once posted.
 */
@Component
public class IntrabankPaymentHandler implements PaymentRailHandler {

    private final LedgerPort ledgerPort;

    public IntrabankPaymentHandler(LedgerPort ledgerPort) {
        this.ledgerPort = ledgerPort;
    }

    @Override
    public Set<PaymentType> supportedTypes() {
        return Set.of(PaymentType.INTRABANK);
    }

    @Override
    public void execute(Payment payment) {
        UUID entryId = ledgerPort.postTransfer(new TransferCommand(
                "pay-" + payment.getIdempotencyKey(), payment.getNarrative(),
                payment.getDebtorAccount(), payment.getCreditorAccount(), payment.money()));
        payment.markPosted(entryId);
    }
}
