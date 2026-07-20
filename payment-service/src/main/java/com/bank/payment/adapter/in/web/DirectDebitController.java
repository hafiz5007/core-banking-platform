package com.bank.payment.adapter.in.web;

import com.bank.common.money.Money;
import com.bank.payment.adapter.in.web.dto.PaymentResponse;
import com.bank.payment.application.DirectDebitService;
import com.bank.payment.domain.recurring.DirectDebitMandate;
import com.bank.payment.domain.recurring.MandateStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.net.URI;
import java.util.Currency;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/direct-debits")
public class DirectDebitController {

    private final DirectDebitService directDebitService;

    public DirectDebitController(DirectDebitService directDebitService) {
        this.directDebitService = directDebitService;
    }

    @PostMapping("/mandates")
    public ResponseEntity<MandateResponse> createMandate(@Valid @RequestBody CreateMandateRequest request) {
        Currency currency = Currency.getInstance(request.currencyCode());
        DirectDebitMandate mandate = directDebitService.createMandate(
                request.mandateReference(), request.payerAccount(), request.payeeAccount(),
                Money.of(request.maxAmount(), currency));
        return ResponseEntity.created(URI.create("/api/v1/direct-debits/mandates/" + mandate.getMandateReference()))
                .body(MandateResponse.from(mandate));
    }

    @GetMapping("/mandates/{reference}")
    public MandateResponse getMandate(@PathVariable String reference) {
        return MandateResponse.from(directDebitService.getMandate(reference));
    }

    @PostMapping("/mandates/{reference}/cancel")
    public MandateResponse cancelMandate(@PathVariable String reference) {
        return MandateResponse.from(directDebitService.cancelMandate(reference));
    }

    @PostMapping("/mandates/{reference}/collect")
    public ResponseEntity<PaymentResponse> collect(
            @PathVariable String reference, @Valid @RequestBody CollectRequest request) {
        Currency currency = Currency.getInstance(request.currencyCode());
        var payment = directDebitService.collect(
                reference, Money.of(request.amount(), currency),
                request.collectionReference(), request.narrative());
        return ResponseEntity.status(201).body(PaymentResponse.from(payment));
    }

    public record CreateMandateRequest(
            @NotBlank String mandateReference,
            @NotBlank String payerAccount,
            @NotBlank String payeeAccount,
            @NotNull @Positive BigDecimal maxAmount,
            @NotNull @Pattern(regexp = "^[A-Z]{3}$", message = "currencyCode must be a 3-letter ISO-4217 code")
            String currencyCode) {
    }

    public record CollectRequest(
            @NotNull @Positive BigDecimal amount,
            @NotNull @Pattern(regexp = "^[A-Z]{3}$", message = "currencyCode must be a 3-letter ISO-4217 code")
            String currencyCode,
            @NotBlank String collectionReference,
            String narrative) {
    }

    public record MandateResponse(String mandateReference, String payerAccount, String payeeAccount,
                                  String maxAmount, String currencyCode, MandateStatus status) {
        static MandateResponse from(DirectDebitMandate m) {
            return new MandateResponse(m.getMandateReference(), m.getPayerAccount(), m.getPayeeAccount(),
                    m.getMaxAmount().toPlainString(), m.getCurrencyCode(), m.getStatus());
        }
    }
}
