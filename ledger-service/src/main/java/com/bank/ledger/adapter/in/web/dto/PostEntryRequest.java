package com.bank.ledger.adapter.in.web.dto;

import com.bank.ledger.domain.Direction;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Request to post a balanced journal entry. */
public record PostEntryRequest(
        @NotBlank String idempotencyKey,
        @NotBlank String narrative,
        LocalDate valueDate,
        @NotEmpty @Valid List<Line> lines) {

    public record Line(
            @NotBlank String accountCode,
            @NotNull Direction direction,
            @NotNull @Positive BigDecimal amount) {
    }
}
