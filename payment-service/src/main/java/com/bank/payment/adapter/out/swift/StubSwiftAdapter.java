package com.bank.payment.adapter.out.swift;

import com.bank.payment.application.port.SwiftPort;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Stub SWIFT / cross-border network connectivity for local development and tests. A creditor
 * account containing "RETURN" (case insensitive) is rejected; everything else is accepted with a
 * gpi-style tracking reference built from the message UETR. Replace with a real SWIFT gateway.
 */
@Component
public class StubSwiftAdapter implements SwiftPort {

    private static final Logger log = LoggerFactory.getLogger(StubSwiftAdapter.class);

    @Override
    public SwiftResult submit(SwiftInstruction instruction) {
        String creditor = instruction.message().creditorAccount();
        if (creditor != null && creditor.toUpperCase().contains("RETURN")) {
            log.info("SWIFT rejected message {}", instruction.message().messageId());
            return SwiftResult.rejected("beneficiary rejected by correspondent");
        }
        String reference = "gpi-" + instruction.message().uetr();
        log.info("SWIFT accepted message {} settlement={} {} ref={}",
                instruction.message().messageId(), instruction.settlementAmount(),
                instruction.settlementCurrency(), reference);
        return SwiftResult.accepted(reference);
    }
}
