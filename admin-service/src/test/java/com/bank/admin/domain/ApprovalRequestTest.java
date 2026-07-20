package com.bank.admin.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bank.common.error.BusinessException;
import org.junit.jupiter.api.Test;

/** Unit tests for the four-eyes (maker-checker) rule. */
class ApprovalRequestTest {

    private ApprovalRequest request() {
        return new ApprovalRequest("LIMIT_CHANGE", "{\"limit\":5000}", "alice");
    }

    @Test
    void checkerMustDifferFromMaker() {
        ApprovalRequest r = request();
        assertThatThrownBy(() -> r.approve("alice"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Four-eyes");
    }

    @Test
    void differentCheckerCanApprove() {
        ApprovalRequest r = request();
        r.approve("bob");
        assertThat(r.getStatus()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(r.getChecker()).isEqualTo("bob");
    }

    @Test
    void cannotDecideTwice() {
        ApprovalRequest r = request();
        r.approve("bob");
        assertThatThrownBy(() -> r.reject("carol", "late"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already decided");
    }
}
