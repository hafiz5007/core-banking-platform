package com.bank.common.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class ApiErrorTest {

  @Test
  void ofPopulatesEveryFieldAndStampsTheTime() {
    Instant before = Instant.now();

    ApiError error =
        ApiError.of(ErrorCode.VALIDATION_FAILED, "amount must be positive", 400, "corr-1");

    assertThat(error.code()).isEqualTo(ErrorCode.VALIDATION_FAILED);
    assertThat(error.message()).isEqualTo("amount must be positive");
    assertThat(error.status()).isEqualTo(400);
    assertThat(error.correlationId()).isEqualTo("corr-1");
    assertThat(error.timestamp()).isBetween(before, Instant.now());
  }

  @Test
  void businessExceptionCarriesItsCode() {
    BusinessException exception =
        new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "mandate does not permit this");

    assertThat(exception.getCode()).isEqualTo(ErrorCode.BUSINESS_RULE_VIOLATION);
    assertThat(exception).hasMessage("mandate does not permit this");
  }

  @Test
  void resourceNotFoundIsABusinessExceptionWithTheNotFoundCode() {
    ResourceNotFoundException exception = new ResourceNotFoundException("Biller not found: UTIL-1");

    assertThat(exception).isInstanceOf(BusinessException.class);
    assertThat(exception.getCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    assertThat(exception).hasMessageContaining("UTIL-1");
  }

  @Test
  void errorCodesAreStable() {
    assertThat(ErrorCode.values())
        .containsExactly(
            ErrorCode.VALIDATION_FAILED,
            ErrorCode.RESOURCE_NOT_FOUND,
            ErrorCode.BUSINESS_RULE_VIOLATION,
            ErrorCode.DUPLICATE_REQUEST,
            ErrorCode.UNAUTHENTICATED,
            ErrorCode.INTERNAL_ERROR);
  }
}
