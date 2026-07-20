package com.bank.riskaml.adapter.in.web;

import com.bank.common.money.Money;
import com.bank.riskaml.application.RiskService;
import com.bank.riskaml.application.RiskService.EvaluationResult;
import com.bank.riskaml.domain.AmlCase;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.Currency;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/risk")
public class RiskController {

    private final RiskService riskService;

    public RiskController(RiskService riskService) {
        this.riskService = riskService;
    }

    @PostMapping("/evaluate")
    public EvaluationResult evaluate(@Valid @RequestBody EvaluateRequest request) {
        Currency currency = Currency.getInstance(request.currencyCode());
        return riskService.evaluate(request.transactionRef(), request.accountRef(),
                Money.of(request.amount(), currency), request.counterpartyCountry());
    }

    @GetMapping("/cases/{id}")
    public CaseResponse getCase(@PathVariable UUID id) {
        return CaseResponse.from(riskService.getCase(id));
    }

    @PostMapping("/cases/{id}/close")
    public CaseResponse closeCase(@PathVariable UUID id, @RequestBody CloseCaseRequest request) {
        boolean fileSar = request != null && request.fileSar();
        String resolution = request != null ? request.resolution() : null;
        return CaseResponse.from(riskService.closeCase(id, resolution, fileSar));
    }

    public record EvaluateRequest(
            @NotBlank String transactionRef,
            @NotBlank String accountRef,
            @NotNull @Positive BigDecimal amount,
            @NotNull @Pattern(regexp = "^[A-Z]{3}$", message = "currencyCode must be a 3-letter ISO-4217 code")
            String currencyCode,
            String counterpartyCountry) {
    }

    public record CloseCaseRequest(String resolution, boolean fileSar) {
    }

    public record CaseResponse(UUID id, String organizationId, String accountRef, String ruleCode,
                               String status, boolean sarFiled, String resolution) {
        static CaseResponse from(AmlCase c) {
            return new CaseResponse(c.getId(), c.getOrganizationId(), c.getAccountRef(), c.getRuleCode(),
                    c.getStatus().name(), c.isSarFiled(), c.getResolution());
        }
    }
}
