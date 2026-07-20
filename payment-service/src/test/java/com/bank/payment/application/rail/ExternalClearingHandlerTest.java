package com.bank.payment.application.rail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bank.common.money.Money;
import com.bank.payment.application.iso20022.Iso20022MessageFactory;
import com.bank.payment.application.port.ClearingPort;
import com.bank.payment.application.port.ClearingPort.ClearingResult;
import com.bank.payment.application.port.LedgerPort;
import com.bank.payment.domain.Payment;
import com.bank.payment.domain.PaymentStatus;
import com.bank.payment.domain.PaymentType;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/** Unit tests for the external-clearing rail handler with the ledger and clearing ports mocked. */
class ExternalClearingHandlerTest {

    private LedgerPort ledgerPort;
    private ClearingPort clearingPort;
    private ExternalClearingHandler handler;

    @BeforeEach
    void setUp() {
        ledgerPort = Mockito.mock(LedgerPort.class);
        clearingPort = Mockito.mock(ClearingPort.class);
        handler = new ExternalClearingHandler(
                ledgerPort, clearingPort, new Iso20022MessageFactory(), "SETTLEMENT");
    }

    private Payment payment() {
        return Payment.received("idem-3", PaymentType.RTGS, "1000",
                "GB29NWBK60161331926819", Money.of("100.00", "GBP"), "Invoice");
    }

    @Test
    void acceptedSchemeSettlesPayment() {
        UUID entryId = UUID.randomUUID();
        when(ledgerPort.postTransfer(any())).thenReturn(entryId);
        when(clearingPort.submit(eq(PaymentType.RTGS), any()))
                .thenReturn(ClearingResult.accepted("RTGS-REF-1"));

        Payment payment = payment();
        handler.execute(payment);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SETTLED);
        assertThat(payment.getLedgerEntryId()).isEqualTo(entryId);
        assertThat(payment.getSchemeReference()).isEqualTo("RTGS-REF-1");
    }

    @Test
    void rejectedSchemeReversesAndThrows() {
        UUID entryId = UUID.randomUUID();
        UUID reversalId = UUID.randomUUID();
        when(ledgerPort.postTransfer(any())).thenReturn(entryId);
        when(clearingPort.submit(any(), any())).thenReturn(ClearingResult.rejected("beneficiary rejected"));
        when(ledgerPort.reverse(eq(entryId), any())).thenReturn(reversalId);

        Payment payment = payment();
        assertThatThrownBy(() -> handler.execute(payment))
                .isInstanceOf(PaymentRailException.class)
                .hasMessageContaining("Scheme rejected");

        verify(ledgerPort).reverse(eq(entryId), any());
    }
}
