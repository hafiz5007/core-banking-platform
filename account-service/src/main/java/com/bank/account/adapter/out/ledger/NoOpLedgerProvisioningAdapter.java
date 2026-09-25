package com.bank.account.adapter.out.ledger;

import com.bank.account.application.port.LedgerProvisioningPort;
import java.util.Currency;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Default no-op ledger provisioning for local development and tests. Set {@code
 * ledger.provisioning=http} to actually create GL accounts in ledger-service.
 */
@Component
@ConditionalOnProperty(name = "ledger.provisioning", havingValue = "off", matchIfMissing = true)
public class NoOpLedgerProvisioningAdapter implements LedgerProvisioningPort {

  private static final Logger log = LoggerFactory.getLogger(NoOpLedgerProvisioningAdapter.class);

  @Override
  public void provisionAccount(String code, String name, Currency currency) {
    log.debug("Ledger provisioning disabled; skipping GL account creation for {}", code);
  }
}
