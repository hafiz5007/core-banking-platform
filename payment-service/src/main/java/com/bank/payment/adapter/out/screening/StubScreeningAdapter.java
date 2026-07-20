package com.bank.payment.adapter.out.screening;

import com.bank.common.money.Money;
import com.bank.payment.application.port.ScreeningPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Stub payment screening for local development and tests (the default). A creditor account
 * containing "BLOCKED" (case insensitive) produces a hold; everything else clears. Set
 * {@code screening.adapter=risk-service} to call the real risk-aml-service instead.
 */
@Component
@ConditionalOnProperty(name = "screening.adapter", havingValue = "stub", matchIfMissing = true)
public class StubScreeningAdapter implements ScreeningPort {

    @Override
    public ScreeningResult screen(String creditorAccount, Money amount) {
        if (creditorAccount != null && creditorAccount.toUpperCase().contains("BLOCKED")) {
            return new ScreeningResult(true, "creditor on watchlist");
        }
        return ScreeningResult.clear();
    }
}
