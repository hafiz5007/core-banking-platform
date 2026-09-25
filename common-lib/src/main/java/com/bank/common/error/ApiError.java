package com.bank.common.error;

import java.time.Instant;

/**
 * RFC 9457 (problem+json) style error payload. Never carries stack traces, PII, or internal
 * identifiers — only a stable code, a safe message, and the correlation id for support.
 *
 * @param code stable machine-readable error code
 * @param message human-readable, client-safe message
 * @param status HTTP status code
 * @param correlationId request correlation id for traceability
 * @param timestamp when the error was produced (UTC)
 */
public record ApiError(
    ErrorCode code, String message, int status, String correlationId, Instant timestamp) {

  public static ApiError of(ErrorCode code, String message, int status, String correlationId) {
    return new ApiError(code, message, status, correlationId, Instant.now());
  }
}
