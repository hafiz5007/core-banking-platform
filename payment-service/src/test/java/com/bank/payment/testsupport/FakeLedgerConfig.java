package com.bank.payment.testsupport;

import com.bank.payment.application.port.LedgerPort;
import java.util.UUID;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Provides an in-memory {@link LedgerPort} so integration tests exercise the payment flow without a
 * running ledger service. Marked {@code @Primary} so it overrides the real HTTP adapter.
 */
@TestConfiguration
public class FakeLedgerConfig {

  @Bean
  @Primary
  public LedgerPort fakeLedgerPort() {
    return new LedgerPort() {
      @Override
      public UUID postTransfer(TransferCommand command) {
        return UUID.randomUUID();
      }

      @Override
      public UUID reverse(UUID ledgerEntryId, String idempotencyKey) {
        return UUID.randomUUID();
      }
    };
  }
}
