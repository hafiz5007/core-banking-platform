package com.bank.interestfee.adapter.in.web.dto;

import com.bank.interestfee.domain.DayCountBasis;
import com.bank.interestfee.domain.InterestPosition;
import java.time.LocalDate;
import java.util.UUID;

/** Response view of an interest position. */
public record PositionResponse(
        UUID id,
        String organizationId,
        String accountCode,
        String currencyCode,
        String annualRatePercent,
        String principal,
        String accruedInterest,
        DayCountBasis dayCount,
        LocalDate lastAccrualDate) {

    public static PositionResponse from(InterestPosition p) {
        return new PositionResponse(
                p.getId(), p.getOrganizationId(), p.getAccountCode(), p.getCurrencyCode(),
                p.getAnnualRatePercent().toPlainString(), p.getPrincipal().toPlainString(),
                p.getAccruedInterest().toPlainString(), p.getDayCount(), p.getLastAccrualDate());
    }
}
