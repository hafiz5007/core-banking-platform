package com.bank.payment.application.rail;

import com.bank.common.money.Money;
import com.bank.payment.application.iso20022.Iso20022MessageFactory;
import com.bank.payment.application.iso20022.Pacs008Message;
import com.bank.payment.application.port.FxPort;
import com.bank.payment.application.port.FxPort.FxQuote;
import com.bank.payment.application.port.LedgerPort;
import com.bank.payment.application.port.LedgerPort.TransferCommand;
import com.bank.payment.application.port.SwiftPort;
import com.bank.payment.application.port.SwiftPort.SwiftInstruction;
import com.bank.payment.application.port.SwiftPort.SwiftResult;
import com.bank.payment.domain.Payment;
import com.bank.payment.domain.PaymentType;
import java.math.BigDecimal;
import java.util.Currency;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Cross-border (overseas) credit transfer over SWIFT. The beneficiary is at a foreign bank and is
 * usually paid in a different currency, so this handler:
 *
 * <ol>
 *   <li>obtains an FX quote and converts the source amount to the beneficiary currency;</li>
 *   <li>posts the money movement to the ledger — debit the customer, credit the nostro account
 *       (our account at the correspondent), in the source currency;</li>
 *   <li>builds an ISO 20022 pacs.008 for the converted settlement amount and submits it to SWIFT
 *       (gpi-tracked);</li>
 *   <li>on a network rejection, reverses the ledger posting and raises {@link PaymentRailException}
 *       so the saga records a COMPENSATED outcome.</li>
 * </ol>
 *
 * <p>Sanctions/AML screening of all parties is mandatory for cross-border and is performed by the
 * orchestrator before fulfilment.
 */
@Component
public class CrossBorderHandler implements PaymentRailHandler {

    private final LedgerPort ledgerPort;
    private final FxPort fxPort;
    private final SwiftPort swiftPort;
    private final Iso20022MessageFactory messageFactory;
    private final String nostroAccount;

    public CrossBorderHandler(LedgerPort ledgerPort,
                              FxPort fxPort,
                              SwiftPort swiftPort,
                              Iso20022MessageFactory messageFactory,
                              @Value("${crossborder.nostro-account:NOSTRO}") String nostroAccount) {
        this.ledgerPort = ledgerPort;
        this.fxPort = fxPort;
        this.swiftPort = swiftPort;
        this.messageFactory = messageFactory;
        this.nostroAccount = nostroAccount;
    }

    @Override
    public Set<PaymentType> supportedTypes() {
        return Set.of(PaymentType.CROSS_BORDER);
    }

    @Override
    public void execute(Payment payment) {
        String key = payment.getIdempotencyKey();
        String sourceCurrency = payment.getCurrencyCode();
        String targetCurrency = payment.getTargetCurrencyCode() != null
                ? payment.getTargetCurrencyCode() : sourceCurrency;

        // 1. FX: convert the source amount to the beneficiary currency at the effective rate.
        FxQuote quote = fxPort.quote(sourceCurrency, targetCurrency);
        BigDecimal effectiveRate = quote.effectiveRate();
        BigDecimal converted = payment.getAmount().multiply(effectiveRate);
        Money settlementAmount = Money.of(converted, Currency.getInstance(targetCurrency));
        payment.applyFx(effectiveRate, settlementAmount);

        // 2. Money movement: debit the customer (source ccy), credit the nostro account.
        UUID entryId = ledgerPort.postTransfer(new TransferCommand(
                "pay-" + key, payment.getNarrative(),
                payment.getDebtorAccount(), nostroAccount, payment.money()));
        payment.markPosted(entryId);

        // 3. Build the ISO 20022 message for the settlement amount and submit to SWIFT.
        Pacs008Message message = messageFactory.pacs008(payment, settlementAmount);
        SwiftResult result = swiftPort.submit(new SwiftInstruction(
                "swift-" + key, message, settlementAmount.amount(), targetCurrency));

        // 4. Compensate on rejection.
        if (!result.accepted()) {
            UUID reversalId = ledgerPort.reverse(entryId, "rev-" + key);
            throw new PaymentRailException(reversalId, "SWIFT rejected: " + result.reason());
        }

        payment.markSubmitted(result.reference());
        payment.markSettled();
    }
}
