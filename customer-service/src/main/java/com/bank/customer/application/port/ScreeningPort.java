package com.bank.customer.application.port;

/**
 * Outbound port for sanctions / PEP / adverse-media screening (FR-CUS-003). A positive match must
 * block customer activation and open a case for investigation.
 */
public interface ScreeningPort {

    ScreeningResult screen(ScreeningRequest request);

    record ScreeningRequest(String firstName, String lastName, String nationality) {
    }

    /**
     * @param match   true if the party matched a watchlist
     * @param caseRef reference to the opened investigation case (null when no match)
     */
    record ScreeningResult(boolean match, String caseRef) {
    }
}
