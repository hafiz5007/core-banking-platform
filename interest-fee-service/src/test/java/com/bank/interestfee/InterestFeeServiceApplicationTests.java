package com.bank.interestfee;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

/** Smoke test: the Spring context loads against a real database with a fake ledger. */
@Import(AbstractIntegrationTest.FakeLedgerConfig.class)
class InterestFeeServiceApplicationTests extends AbstractIntegrationTest {

    @Test
    void contextLoads() {
        // Fails if the application context cannot start.
    }
}
