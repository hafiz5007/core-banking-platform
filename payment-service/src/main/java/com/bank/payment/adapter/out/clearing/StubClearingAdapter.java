package com.bank.payment.adapter.out.clearing;

import com.bank.payment.application.port.ClearingPort;
import com.bank.payment.domain.PaymentType;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Stub clearing-scheme connectivity for local development and tests. Simulates submission to the
 * instant / RTGS / ACH rails: a creditor account containing "RETURN" (case insensitive) is rejected
 * by the scheme; everything else is accepted with a generated scheme reference.
 *
 * <p>Replace with real, rail-specific adapters (scheme gateways, ISO 20022 transport) in production.
 */
@Component
public class StubClearingAdapter implements ClearingPort {

    private static final Logger log = LoggerFactory.getLogger(StubClearingAdapter.class);

    @Override
    public ClearingResult submit(PaymentType rail, ClearingInstruction instruction) {
        String creditor = instruction.message().creditorAccount();
        if (creditor != null && creditor.toUpperCase().contains("RETURN")) {
            log.info("Clearing scheme {} rejected message {}", rail, instruction.message().messageId());
            return ClearingResult.rejected("beneficiary rejected by scheme");
        }
        String reference = rail + "-" + UUID.randomUUID();
        log.info("Clearing scheme {} accepted message {} ref={}",
                rail, instruction.message().messageId(), reference);
        return ClearingResult.accepted(reference);
    }
}
