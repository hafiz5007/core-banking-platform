package com.bank.payment.application.iso20022;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Minimal representation of an ISO 20022 {@code pacs.008} (FI-to-FI customer credit transfer) used
 * for domestic and cross-border credit transfers. This captures the fields the platform needs and
 * can render a representative XML payload; a production build would marshal against the official
 * XSD.
 *
 * @param messageId unique message identifier (MsgId)
 * @param uetr unique end-to-end transaction reference (UUID)
 * @param creationDateTime message creation timestamp (CreDtTm)
 * @param amount instructed amount
 * @param currency ISO-4217 currency
 * @param debtorAccount ordering party's account
 * @param creditorAccount beneficiary account/IBAN
 * @param remittanceInfo free-text remittance information
 */
public record Pacs008Message(
    String messageId,
    String uetr,
    OffsetDateTime creationDateTime,
    BigDecimal amount,
    String currency,
    String debtorAccount,
    String creditorAccount,
    String remittanceInfo) {

  /** Render a representative pacs.008 XML document. */
  public String toXml() {
    return """
                <?xml version="1.0" encoding="UTF-8"?>
                <Document xmlns="urn:iso:std:iso:20022:tech:xsd:pacs.008.001.08">
                  <FIToFICstmrCdtTrf>
                    <GrpHdr>
                      <MsgId>%s</MsgId>
                      <CreDtTm>%s</CreDtTm>
                      <NbOfTxs>1</NbOfTxs>
                    </GrpHdr>
                    <CdtTrfTxInf>
                      <PmtId><UETR>%s</UETR></PmtId>
                      <IntrBkSttlmAmt Ccy="%s">%s</IntrBkSttlmAmt>
                      <DbtrAcct><Id><Othr><Id>%s</Id></Othr></Id></DbtrAcct>
                      <CdtrAcct><Id><Othr><Id>%s</Id></Othr></Id></CdtrAcct>
                      <RmtInf><Ustrd>%s</Ustrd></RmtInf>
                    </CdtTrfTxInf>
                  </FIToFICstmrCdtTrf>
                </Document>"""
        .formatted(
            messageId,
            creationDateTime,
            uetr,
            currency,
            amount.toPlainString(),
            debtorAccount,
            creditorAccount,
            remittanceInfo == null ? "" : remittanceInfo);
  }
}
