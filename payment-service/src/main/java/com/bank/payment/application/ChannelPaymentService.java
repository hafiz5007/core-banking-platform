package com.bank.payment.application;

import com.bank.common.error.BusinessException;
import com.bank.common.error.ErrorCode;
import com.bank.common.error.ResourceNotFoundException;
import com.bank.common.money.Money;
import com.bank.payment.adapter.out.persistence.BillerRepository;
import com.bank.payment.adapter.out.persistence.PaymentAliasRepository;
import com.bank.payment.application.PaymentService.InitiatePaymentCommand;
import com.bank.payment.domain.Payment;
import com.bank.payment.domain.PaymentType;
import com.bank.payment.domain.directory.AliasType;
import com.bank.payment.domain.directory.Biller;
import com.bank.payment.domain.directory.PaymentAlias;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Channel conveniences: a P2P alias directory and a biller directory, both resolving to an account
 * and then driving an ordinary intrabank payment through {@link PaymentService} (FR-PAY-010).
 */
@Service
public class ChannelPaymentService {

  private static final Logger log = LoggerFactory.getLogger(ChannelPaymentService.class);

  private final PaymentAliasRepository aliasRepository;
  private final BillerRepository billerRepository;
  private final PaymentService paymentService;

  public ChannelPaymentService(
      PaymentAliasRepository aliasRepository,
      BillerRepository billerRepository,
      PaymentService paymentService) {
    this.aliasRepository = aliasRepository;
    this.billerRepository = billerRepository;
    this.paymentService = paymentService;
  }

  @Transactional
  public PaymentAlias registerAlias(String alias, AliasType type, String accountCode) {
    if (aliasRepository.existsByAlias(alias)) {
      throw new BusinessException(
          ErrorCode.DUPLICATE_REQUEST, "Alias already registered: " + alias);
    }
    return aliasRepository.save(new PaymentAlias(alias, type, accountCode));
  }

  @Transactional
  public Biller registerBiller(String billerCode, String name, String settlementAccount) {
    if (billerRepository.existsByBillerCode(billerCode)) {
      throw new BusinessException(
          ErrorCode.DUPLICATE_REQUEST, "Biller already registered: " + billerCode);
    }
    return billerRepository.save(new Biller(billerCode, name, settlementAccount));
  }

  /** Send money to a payee addressed by alias. */
  @Transactional
  public Payment payToAlias(
      String fromAccount, String toAlias, Money amount, String idempotencyKey) {
    PaymentAlias alias =
        aliasRepository
            .findByAlias(toAlias)
            .orElseThrow(() -> new ResourceNotFoundException("Alias not found: " + toAlias));
    Payment payment =
        paymentService.initiate(
            new InitiatePaymentCommand(
                idempotencyKey,
                PaymentType.INTRABANK,
                fromAccount,
                alias.getAccountCode(),
                amount,
                "P2P to " + toAlias,
                null));
    log.info("P2P {} from {} to alias {}", amount, fromAccount, toAlias);
    return payment;
  }

  /** Pay a registered biller, tagging the customer reference in the narrative. */
  @Transactional
  public Payment payBill(
      String fromAccount,
      String billerCode,
      String customerReference,
      Money amount,
      String idempotencyKey) {
    Biller biller =
        billerRepository
            .findByBillerCode(billerCode)
            .orElseThrow(() -> new ResourceNotFoundException("Biller not found: " + billerCode));
    Payment payment =
        paymentService.initiate(
            new InitiatePaymentCommand(
                idempotencyKey,
                PaymentType.INTRABANK,
                fromAccount,
                biller.getSettlementAccount(),
                amount,
                "Bill payment " + biller.getName() + " ref " + customerReference,
                null));
    log.info("Bill payment {} from {} to biller {}", amount, fromAccount, billerCode);
    return payment;
  }
}
