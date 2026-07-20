package com.bank.account.adapter.in.web.dto;

import com.bank.account.domain.MandateType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

/**
 * Request to open an account from a product, optionally as a joint account.
 *
 * @param additionalHolders extra (joint) customer ids; the primary is always a holder
 * @param mandateType       authorization rule; defaults to SINGLE when omitted
 */
public record OpenFromProductRequest(
        @NotBlank String productCode,
        @NotNull UUID primaryCustomerId,
        List<UUID> additionalHolders,
        MandateType mandateType) {
}
