package com.bank.payment.application.iso20022;

import static org.assertj.core.api.Assertions.assertThat;

import com.bank.common.money.Money;
import com.bank.payment.domain.Payment;
import com.bank.payment.domain.PaymentType;
import org.junit.jupiter.api.Test;

class Iso20022MessageFactoryTest {

  private final Iso20022MessageFactory factory = new Iso20022MessageFactory();

  private Payment payment() {
    return Payment.received(
        "idem-9",
        PaymentType.RTGS,
        "1000",
        "GB29NWBK60161331926819",
        Money.of("250.00", "GBP"),
        "Invoice 42");
  }

  @Test
  void buildsPacs008WithPaymentFields() {
    Pacs008Message msg = factory.pacs008(payment());

    assertThat(msg.amount()).isEqualByComparingTo("250.00");
    assertThat(msg.currency()).isEqualTo("GBP");
    assertThat(msg.creditorAccount()).isEqualTo("GB29NWBK60161331926819");
    assertThat(msg.uetr()).isNotBlank();
    assertThat(msg.messageId()).startsWith("MSG-");
  }

  @Test
  void rendersIsoXml() {
    String xml = factory.pacs008(payment()).toXml();

    assertThat(xml).contains("pacs.008.001.08");
    assertThat(xml).contains("<IntrBkSttlmAmt Ccy=\"GBP\">250.00</IntrBkSttlmAmt>");
    assertThat(xml).contains("GB29NWBK60161331926819");
  }
}
