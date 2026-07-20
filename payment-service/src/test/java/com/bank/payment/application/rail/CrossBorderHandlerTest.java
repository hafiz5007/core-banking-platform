package com.bank.payment.application.rail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bank.common.money.Money;
import com.bank.payment.application.iso20022.Iso20022MessageFactory;
import com.bank.payment.application.port.FxPort;
import com.bank.payment.application.port.FxPort.FxQuote;
import com.bank.payment.application.port.LedgerPort;
import com.bank.payment.application.port.SwiftPort;
import com.bank.payment.application.port.SwiftPort.SwiftResult;
import com.bank.payment.domain.Payment;
import com.bank.payment.domain.PaymentStatus;
import com.bank.payment.domain.PaymentType;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/** Unit tests for the cross-border rail handler with the ledger, FX and SWIFT ports mocked. */
class CrossBorderHandlerTest {

    private LedgerPort ledgerPort;
    private FxPort fxPort;
    private SwiftPort swiftPort;
    private CrossBorderHandler handler;

    @BeforeEach
    void setUp() {
        ledgerPort = Mockito.mock(LedgerPort.class);
        fxPort = Mockito.mock(FxPort.class);
        swiftPort = Mockito.mock(SwiftPort.class);
        handler = new CrossBorderHandler(
                ledgerPort, fxPort, swiftPort, new Iso20022MessageFactory(), "NOSTRO");
        // 100.00 USD at an effective rate of 0.90 -> 90.00 EUR.
        when(fxPort.quote("USD", "EUR"))
                .thenReturn(new FxQuote("USD", "EUR", new BigDecimal("0.90"), BigDecimal.ZERO));
        when(ledgerPort.postTransfer(any())).thenReturn(UUID.randomUUID());
    }

    private Payment payment() {
        return Payment.received("xb-1", PaymentType.CROSS_BORDER, "1000",
                "DE89370400440532013000", Money.of("100.00", "USD"), "Overseas invoice", "EUR");
    }

    @Test
    void convertsFxAndSettlesOnSwiftAcceptance() {
        when(swiftPort.submit(any())).thenReturn(SwiftResult.accepted("gpi-123"));

        Payment payment = payment();
        handler.execute(payment);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SETTLED);
        assertThat(payment.getTargetCurrencyCode()).isEqualTo("EUR");
        assertThat(payment.getTargetAmount()).isEqualByComparingTo("90.00");
        assertThat(payment.getFxRate()).isEqualByComparingTo("0.90");
        assertThat(payment.getSchemeReference()).isEqualTo("gpi-123");
    }

    @Test
    void reversesAndThrowsOnSwiftRejection() {
        UUID entryId = UUID.randomUUID();
        UUID reversalId = UUID.randomUUID();
        when(ledgerPort.postTransfer(any())).thenReturn(entryId);
        when(swiftPort.submit(any())).thenReturn(SwiftResult.rejected("correspondent rejected"));
        when(ledgerPort.reverse(eq(entryId), any())).thenReturn(reversalId);

        Payment payment = payment();
        assertThatThrownBy(() -> handler.execute(payment))
                .isInstanceOf(PaymentRailException.class)
                .hasMessageContaining("SWIFT rejected");

        verify(ledgerPort).reverse(eq(entryId), any());
    }
}
