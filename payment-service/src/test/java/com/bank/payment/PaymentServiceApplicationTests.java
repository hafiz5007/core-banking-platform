package com.bank.payment;

import com.bank.payment.testsupport.FakeLedgerConfig;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

/** Smoke test: the Spring context loads against a real database with a fake ledger. */
@Import(FakeLedgerConfig.class)
class PaymentServiceApplicationTests extends AbstractIntegrationTest {

    @Test
    void contextLoads() {
        // Fails if the application context cannot start.
    }
}
