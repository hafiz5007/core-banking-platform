package com.bank.payment.adapter.in.web;

import com.bank.common.money.Money;
import com.bank.payment.adapter.in.web.dto.PaymentResponse;
import com.bank.payment.application.ChannelPaymentService;
import com.bank.payment.domain.directory.AliasType;
import com.bank.payment.domain.directory.Biller;
import com.bank.payment.domain.directory.PaymentAlias;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.Currency;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Channel conveniences: register aliases/billers and make P2P / bill payments. */
@RestController
@RequestMapping("/api/v1")
public class ChannelPaymentController {

    private final ChannelPaymentService channelPaymentService;

    public ChannelPaymentController(ChannelPaymentService channelPaymentService) {
        this.channelPaymentService = channelPaymentService;
    }

    @PostMapping("/aliases")
    public ResponseEntity<AliasResponse> registerAlias(@Valid @RequestBody RegisterAliasRequest request) {
        PaymentAlias alias = channelPaymentService.registerAlias(
                request.alias(), request.aliasType(), request.accountCode());
        return ResponseEntity.status(201)
                .body(new AliasResponse(alias.getAlias(), alias.getAliasType(), alias.getAccountCode()));
    }

    @PostMapping("/billers")
    public ResponseEntity<BillerResponse> registerBiller(@Valid @RequestBody RegisterBillerRequest request) {
        Biller biller = channelPaymentService.registerBiller(
                request.billerCode(), request.name(), request.settlementAccount());
        return ResponseEntity.status(201)
                .body(new BillerResponse(biller.getBillerCode(), biller.getName(), biller.getSettlementAccount()));
    }

    @PostMapping("/payments/p2p")
    public ResponseEntity<PaymentResponse> payToAlias(@Valid @RequestBody P2pRequest request) {
        Currency currency = Currency.getInstance(request.currencyCode());
        var payment = channelPaymentService.payToAlias(
                request.fromAccount(), request.toAlias(),
                Money.of(request.amount(), currency), request.idempotencyKey());
        return ResponseEntity.status(201).body(PaymentResponse.from(payment));
    }

    @PostMapping("/payments/bill")
    public ResponseEntity<PaymentResponse> payBill(@Valid @RequestBody BillRequest request) {
        Currency currency = Currency.getInstance(request.currencyCode());
        var payment = channelPaymentService.payBill(
                request.fromAccount(), request.billerCode(), request.customerReference(),
                Money.of(request.amount(), currency), request.idempotencyKey());
        return ResponseEntity.status(201).body(PaymentResponse.from(payment));
    }

    public record RegisterAliasRequest(
            @NotBlank @Size(max = 120) String alias,
            @NotNull AliasType aliasType,
            @NotBlank @Size(max = 40) String accountCode) {
    }

    public record AliasResponse(String alias, AliasType aliasType, String accountCode) {
    }

    public record RegisterBillerRequest(
            @NotBlank @Size(max = 40) String billerCode,
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Size(max = 40) String settlementAccount) {
    }

    public record BillerResponse(String billerCode, String name, String settlementAccount) {
    }

    public record P2pRequest(
            @NotBlank @Size(max = 80) String idempotencyKey,
            @NotBlank @Size(max = 40) String fromAccount,
            @NotBlank @Size(max = 120) String toAlias,
            @NotNull @Positive BigDecimal amount,
            @NotNull @Pattern(regexp = "^[A-Z]{3}$", message = "currencyCode must be a 3-letter ISO-4217 code")
            String currencyCode) {
    }

    public record BillRequest(
            @NotBlank @Size(max = 80) String idempotencyKey,
            @NotBlank @Size(max = 40) String fromAccount,
            @NotBlank @Size(max = 40) String billerCode,
            // Bounded so the composed narrative ("Bill payment <name> ref <ref>") fits VARCHAR(280).
            @NotBlank @Size(max = 140) String customerReference,
            @NotNull @Positive BigDecimal amount,
            @NotNull @Pattern(regexp = "^[A-Z]{3}$", message = "currencyCode must be a 3-letter ISO-4217 code")
            String currencyCode) {
    }
}
