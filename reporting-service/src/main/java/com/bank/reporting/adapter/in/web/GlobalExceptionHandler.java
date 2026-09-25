package com.bank.reporting.adapter.in.web;

import com.bank.common.error.ApiError;
import com.bank.common.error.BusinessException;
import com.bank.common.error.ErrorCode;
import com.bank.common.web.CorrelationIdFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps exceptions to RFC 9457 problem+json responses. Never leaks stack traces to clients. */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
    String message =
        ex.getBindingResult().getFieldErrors().stream()
            .findFirst()
            .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
            .orElse("Validation failed");
    return build(ErrorCode.VALIDATION_FAILED, message, HttpStatus.BAD_REQUEST);
  }

  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<ApiError> handleBusiness(BusinessException ex) {
    HttpStatus status =
        switch (ex.getCode()) {
          case RESOURCE_NOT_FOUND -> HttpStatus.NOT_FOUND;
          case DUPLICATE_REQUEST -> HttpStatus.CONFLICT;
          default -> HttpStatus.UNPROCESSABLE_ENTITY;
        };
    return build(ex.getCode(), ex.getMessage(), status);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiError> handleUnexpected(Exception ex) {
    log.error("Unhandled exception [correlationId={}]", CorrelationIdFilter.current(), ex);
    return build(
        ErrorCode.INTERNAL_ERROR, "An unexpected error occurred", HttpStatus.INTERNAL_SERVER_ERROR);
  }

  private ResponseEntity<ApiError> build(ErrorCode code, String message, HttpStatus status) {
    ApiError body = ApiError.of(code, message, status.value(), CorrelationIdFilter.current());
    return ResponseEntity.status(status).body(body);
  }
}
