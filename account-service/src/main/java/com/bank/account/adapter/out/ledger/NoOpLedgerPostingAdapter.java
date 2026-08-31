package com.bank.account.adapter.out.ledger;

import com.bank.account.application.port.LedgerPostingPort;
import com.bank.common.money.Money;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Default no-op ledger posting for local development and tests. Set {@code ledger.posting=grpc} to
 * post real double-entries to ledger-service over gRPC.
 */
@Component
@ConditionalOnProperty(name = "ledger.posting", havingValue = "off", matchIfMissing = true)
public class NoOpLedgerPostingAdapter implements LedgerPostingPort {

    private static final Logger log = LoggerFactory.getLogger(NoOpLedgerPostingAdapter.class);

    @Override
    public String postDebit(String accountCode, Money amount, String narrative, String idempotencyKey) {
        log.debug("Ledger posting disabled; not posting debit of {} on {}", amount, accountCode);
        return null;
    }
}
