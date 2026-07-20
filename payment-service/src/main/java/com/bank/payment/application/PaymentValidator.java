package com.bank.payment.application;

import com.bank.common.error.BusinessException;
import com.bank.common.error.ErrorCode;
import com.bank.payment.application.PaymentService.InitiatePaymentCommand;

/**
 * Pure, side-effect-free validation of a payment instruction (FR-PAY-004). Kept separate and static
 * so it is trivially unit-testable and reusable across rails.
 */
public final class PaymentValidator {

    private PaymentValidator() {
    }

    public static void validate(InitiatePaymentCommand cmd) {
        if (cmd.debtorAccount() == null || cmd.debtorAccount().isBlank()
                || cmd.creditorAccount() == null || cmd.creditorAccount().isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Debtor and creditor accounts are required");
        }
        if (cmd.debtorAccount().equals(cmd.creditorAccount())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Debtor and creditor accounts must differ");
        }
        if (cmd.amount() == null || cmd.amount().amount().signum() <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Amount must be positive");
        }
    }
}
