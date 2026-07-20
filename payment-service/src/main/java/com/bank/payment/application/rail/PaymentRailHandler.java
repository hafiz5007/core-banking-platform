package com.bank.payment.application.rail;

import com.bank.payment.domain.Payment;
import com.bank.payment.domain.PaymentType;
import java.util.Set;

/**
 * Strategy for fulfilling a payment on a particular rail. Each handler owns the rail-specific money
 * movement (ledger posting) and any external scheme submission, mutating the {@link Payment} through
 * its lifecycle. If fulfilment fails after the ledger has posted, the handler reverses the posting
 * and throws {@link PaymentRailException}.
 */
public interface PaymentRailHandler {

    Set<PaymentType> supportedTypes();

    void execute(Payment payment);
}
