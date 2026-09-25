package com.bank.common.error;

/** Raised when a requested resource does not exist; maps to HTTP 404. */
public class ResourceNotFoundException extends BusinessException {

  public ResourceNotFoundException(String message) {
    super(ErrorCode.RESOURCE_NOT_FOUND, message);
  }
}
