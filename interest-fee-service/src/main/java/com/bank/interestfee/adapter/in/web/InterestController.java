package com.bank.interestfee.adapter.in.web;

import com.bank.common.money.Money;
import com.bank.interestfee.adapter.in.web.dto.ApplyFeeRequest;
import com.bank.interestfee.adapter.in.web.dto.OpenPositionRequest;
import com.bank.interestfee.adapter.in.web.dto.PositionResponse;
import com.bank.interestfee.application.InterestService;
import com.bank.interestfee.application.InterestService.EodSummary;
import com.bank.interestfee.domain.FeeCharge;
import com.bank.interestfee.domain.InterestPosition;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.Currency;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/interest")
public class InterestController {

    private final InterestService interestService;

    public InterestController(InterestService interestService) {
        this.interestService = interestService;
    }

    @PostMapping("/positions")
    public ResponseEntity<PositionResponse> open(@Valid @RequestBody OpenPositionRequest request) {
        Currency currency = Currency.getInstance(request.currencyCode());
        InterestPosition position = interestService.openPosition(
                request.accountCode(), currency, request.annualRatePercent(),
                Money.of(request.principal(), currency), request.dayCountOrDefault());
        return ResponseEntity
                .created(URI.create("/api/v1/interest/positions/" + position.getAccountCode()))
                .body(PositionResponse.from(position));
    }

    @GetMapping("/positions/{accountCode}")
    public PositionResponse get(@PathVariable String accountCode) {
        return PositionResponse.from(interestService.getPosition(accountCode));
    }

    @PostMapping("/positions/{accountCode}/accrue")
    public PositionResponse accrue(
            @PathVariable String accountCode,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate upTo) {
        return PositionResponse.from(interestService.accrue(accountCode, upTo == null ? LocalDate.now() : upTo));
    }

    @PostMapping("/positions/{accountCode}/capitalize")
    public PositionResponse capitalize(@PathVariable String accountCode) {
        return PositionResponse.from(interestService.capitalize(accountCode));
    }

    @PostMapping("/fees")
    public ResponseEntity<FeeChargeView> applyFee(@Valid @RequestBody ApplyFeeRequest request) {
        Currency currency = Currency.getInstance(request.currencyCode());
        FeeCharge charge = interestService.applyFee(
                request.accountCode(), request.feeType(), Money.of(request.amount(), currency));
        return ResponseEntity.status(201).body(FeeChargeView.from(charge));
    }

    @PostMapping("/eod/run")
    public EodSummary runEod(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate businessDate) {
        return interestService.runEndOfDay(businessDate == null ? LocalDate.now() : businessDate);
    }

    /** Response view of an applied fee. */
    public record FeeChargeView(UUID id, String accountCode, String feeType, String amount,
                                String currencyCode, UUID ledgerEntryId) {
        static FeeChargeView from(FeeCharge c) {
            return new FeeChargeView(c.getId(), c.getAccountCode(), c.getFeeType().name(),
                    c.getAmount().toPlainString(), c.getCurrencyCode(), c.getLedgerEntryId());
        }
    }
}
