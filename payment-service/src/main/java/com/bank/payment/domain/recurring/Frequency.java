package com.bank.payment.domain.recurring;

import java.time.LocalDate;

/** Recurrence frequency for a standing order. */
public enum Frequency {
    DAILY,
    WEEKLY,
    MONTHLY;

    /** The next run date after {@code from} for this frequency. */
    public LocalDate next(LocalDate from) {
        return switch (this) {
            case DAILY -> from.plusDays(1);
            case WEEKLY -> from.plusWeeks(1);
            case MONTHLY -> from.plusMonths(1);
        };
    }
}
