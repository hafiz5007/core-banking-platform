package com.bank.payment.application.port;

import com.bank.common.money.Money;

/**
 * Outbound port for sanctions/fraud screening of a payment (FR-PAY-009). Domestic payments screen
 * by threshold; cross-border payments (later sprints) always screen all parties.
 */
public interface ScreeningPort {

    ScreeningResult screen(String creditorAccount, Money amount);

    record ScreeningResult(boolean blocked, String reason) {
        public static ScreeningResult clear() {
            return new ScreeningResult(false, null);
        }
    }
}
