package com.bank.account.application.port;

import java.util.Currency;

/**
 * Outbound port to provision a general-ledger account when a customer account is opened, so that
 * payments can post against it. Implementations are config-switched (a no-op by default, an HTTP
 * call to ledger-service in a wired deployment).
 */
public interface LedgerProvisioningPort {

  void provisionAccount(String code, String name, Currency currency);
}
