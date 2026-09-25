package com.bank.payment.domain;

/**
 * Canonical payment lifecycle shared by every rail:
 *
 * <pre>
 * RECEIVED -> VALIDATED -> SCREENED -> POSTED -> CONFIRMED          (intrabank)
 *                                   -> POSTED -> SUBMITTED -> SETTLED -> CONFIRMED  (external clearing)
 *          \-> REJECTED      (failed validation/screening/posting; no funds moved)
 *              POSTED..      -> COMPENSATED  (a later step failed; the posting was reversed)
 *              CONFIRMED/SETTLED -> RETURNED (a settled payment was returned/recalled and reversed)
 * </pre>
 */
public enum PaymentStatus {
  RECEIVED,
  VALIDATED,
  SCREENED,
  POSTED,
  SUBMITTED,
  SETTLED,
  CONFIRMED,
  REJECTED,
  COMPENSATED,
  RETURNED
}
