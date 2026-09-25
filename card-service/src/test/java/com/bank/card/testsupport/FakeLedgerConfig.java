package com.bank.card.testsupport;

import com.bank.card.application.port.LedgerPort;
import java.util.UUID;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** In-memory {@link LedgerPort} so settlement tests run without a live ledger service. */
@TestConfiguration
public class FakeLedgerConfig {

  @Bean
  @Primary
  public LedgerPort fakeLedgerPort() {
    return command -> UUID.randomUUID();
  }
}
