package com.bank.payment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bank.common.error.BusinessException;
import com.bank.common.money.Money;
import com.bank.payment.adapter.out.persistence.PaymentRepository;
import com.bank.payment.application.PaymentService.InitiatePaymentCommand;
import com.bank.payment.application.port.LedgerPort;
import com.bank.payment.application.port.PaymentEventPublisher;
import com.bank.payment.application.port.ScreeningPort;
import com.bank.payment.application.port.ScreeningPort.ScreeningResult;
import com.bank.payment.application.rail.PaymentRailException;
import com.bank.payment.application.rail.PaymentRailHandler;
import com.bank.payment.domain.Payment;
import com.bank.payment.domain.PaymentStatus;
import com.bank.payment.domain.PaymentType;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/** Unit tests for the orchestrator with the rail handler, ledger, screening and publisher mocked. */
class PaymentServiceTest {

    private PaymentRepository repository;
    private LedgerPort ledgerPort;
    private ScreeningPort screeningPort;
    private PaymentEventPublisher publisher;
    private RoutingEngine routingEngine;
    private PaymentRailHandler handler;
    private PaymentService service;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(PaymentRepository.class);
        ledgerPort = Mockito.mock(LedgerPort.class);
        screeningPort = Mockito.mock(ScreeningPort.class);
        publisher = Mockito.mock(PaymentEventPublisher.class);
        routingEngine = Mockito.mock(RoutingEngine.class);
        handler = Mockito.mock(PaymentRailHandler.class);
        ChangeLogRecorder changeLog = Mockito.mock(ChangeLogRecorder.class);
        service = new PaymentService(repository, ledgerPort, screeningPort, publisher, routingEngine, changeLog);

        when(repository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(repository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(screeningPort.screen(any(), any())).thenReturn(ScreeningResult.clear());
        when(routingEngine.handlerFor(any())).thenReturn(handler);
        // Default handler behaviour: post to the ledger (records an entry id on the payment).
        doAnswer(inv -> {
            inv.getArgument(0, Payment.class).markPosted(UUID.randomUUID());
            return null;
        }).when(handler).execute(any());
    }

    private InitiatePaymentCommand cmd() {
        return new InitiatePaymentCommand("idem-1", PaymentType.INTRABANK,
                "1000", "2000", Money.of("100.00", "USD"), "Test transfer", null);
    }

    @Test
    void happyPathConfirms() {
        Payment result = service.initiate(cmd());

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.CONFIRMED);
        assertThat(result.getLedgerEntryId()).isNotNull();
        verify(handler).execute(any());
        verify(publisher).publish(any(), eq("PaymentPosted"));
        verify(ledgerPort, never()).reverse(any(), any());
    }

    @Test
    void idempotentReplayReturnsExisting() {
        Payment original = Payment.received("idem-1", PaymentType.INTRABANK, "1000", "2000",
                Money.of("100.00", "USD"), "Test transfer");
        when(repository.findByIdempotencyKey("idem-1")).thenReturn(Optional.of(original));

        Payment result = service.initiate(cmd());

        assertThat(result).isSameAs(original);
        verify(handler, never()).execute(any());
    }

    @Test
    void sameDebtorAndCreditorRejected() {
        InitiatePaymentCommand bad = new InitiatePaymentCommand("idem-2", PaymentType.INTRABANK,
                "1000", "1000", Money.of("100.00", "USD"), "Self", null);
        assertThatThrownBy(() -> service.initiate(bad)).isInstanceOf(BusinessException.class);
        verify(handler, never()).execute(any());
    }

    @Test
    void screeningHoldRejectsBeforeFulfilment() {
        when(screeningPort.screen(any(), any())).thenReturn(new ScreeningResult(true, "watchlist"));
        assertThatThrownBy(() -> service.initiate(cmd())).isInstanceOf(BusinessException.class);
        verify(handler, never()).execute(any());
    }

    @Test
    void railRejectionLeavesPaymentCompensated() {
        UUID reversalId = UUID.randomUUID();
        doThrow(new PaymentRailException(reversalId, "Scheme rejected: bad beneficiary"))
                .when(handler).execute(any());

        Payment result = service.initiate(cmd());

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.COMPENSATED);
        assertThat(result.getReversalEntryId()).isEqualTo(reversalId);
        // The handler owns its own reversal in this path; the service does not call reverse again.
        verify(ledgerPort, never()).reverse(any(), any());
    }

    @Test
    void publishFailureAfterPostingTriggersCompensatingReversal() {
        UUID reversalId = UUID.randomUUID();
        doThrow(new RuntimeException("broker down")).when(publisher).publish(any(), any());
        when(ledgerPort.reverse(any(), any())).thenReturn(reversalId);

        Payment result = service.initiate(cmd());

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.COMPENSATED);
        assertThat(result.getReversalEntryId()).isEqualTo(reversalId);
        verify(ledgerPort).reverse(any(), any());
    }
}
