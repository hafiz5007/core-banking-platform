package com.bank.account.adapter.out.ledger;

import com.bank.account.application.port.LedgerPostingPort;
import com.bank.common.error.BusinessException;
import com.bank.common.error.ErrorCode;
import com.bank.common.money.Money;
import com.bank.ledger.grpc.v1.Direction;
import com.bank.ledger.grpc.v1.JournalLine;
import com.bank.ledger.grpc.v1.LedgerPostingGrpc;
import com.bank.ledger.grpc.v1.PostJournalEntryRequest;
import com.bank.ledger.grpc.v1.PostJournalEntryResponse;
import io.grpc.StatusRuntimeException;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Posts customer-account debits to ledger-service over gRPC (ADR-007).
 *
 * <p>Writes the balanced pair: DEBIT the customer's account code, CREDIT the configured settlement
 * account ({@code ledger.settlement-account}). Enabled with {@code ledger.posting=grpc}.
 */
@Component
@ConditionalOnProperty(name = "ledger.posting", havingValue = "grpc")
public class GrpcLedgerPostingAdapter implements LedgerPostingPort {

  private static final Logger log = LoggerFactory.getLogger(GrpcLedgerPostingAdapter.class);

  private final LedgerPostingGrpc.LedgerPostingBlockingStub stub;
  private final String settlementAccount;
  private final long deadlineMs;

  public GrpcLedgerPostingAdapter(
      LedgerPostingGrpc.LedgerPostingBlockingStub stub,
      @Value("${ledger.settlement-account:SETTLEMENT}") String settlementAccount,
      @Value("${ledger.grpc.deadline-ms:5000}") long deadlineMs) {
    this.stub = stub;
    this.settlementAccount = settlementAccount;
    this.deadlineMs = deadlineMs;
  }

  @Override
  public String postDebit(
      String accountCode, Money amount, String narrative, String idempotencyKey) {
    PostJournalEntryRequest request =
        PostJournalEntryRequest.newBuilder()
            .setIdempotencyKey(idempotencyKey)
            .setNarrative(narrative)
            .addLines(line(accountCode, Direction.DEBIT, amount))
            .addLines(line(settlementAccount, Direction.CREDIT, amount))
            .build();
    try {
      PostJournalEntryResponse response =
          stub.withDeadlineAfter(deadlineMs, TimeUnit.MILLISECONDS).postJournalEntry(request);
      log.info(
          "Posted debit of {} on {} to ledger entry {}",
          amount,
          accountCode,
          response.getEntryId());
      return response.getEntryId();
    } catch (StatusRuntimeException e) {
      throw translate(e, accountCode);
    }
  }

  /**
   * Map the ledger's gRPC status back onto the platform error model, so a ledger rejection reaches
   * the caller as a business failure rather than an opaque 500.
   */
  private static RuntimeException translate(StatusRuntimeException e, String accountCode) {
    String detail =
        e.getStatus().getDescription() == null
            ? e.getStatus().getCode().name()
            : e.getStatus().getDescription();
    return switch (e.getStatus().getCode()) {
      case NOT_FOUND ->
          new BusinessException(
              ErrorCode.BUSINESS_RULE_VIOLATION,
              "Ledger account not found while posting " + accountCode + ": " + detail);
      case INVALID_ARGUMENT ->
          new BusinessException(
              ErrorCode.VALIDATION_FAILED, "Ledger rejected the posting: " + detail);
      case ALREADY_EXISTS -> new BusinessException(ErrorCode.DUPLICATE_REQUEST, detail);
      case FAILED_PRECONDITION -> new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, detail);
        // UNAVAILABLE / DEADLINE_EXCEEDED are infrastructure faults, not business outcomes:
        // surface them as 5xx so they are retried and alerted on rather than shown to a customer.
      default ->
          new LedgerUnavailableException(
              "Ledger posting failed (" + e.getStatus().getCode() + "): " + detail, e);
    };
  }

  /** Raised when the ledger could not be reached or did not answer in time. */
  public static class LedgerUnavailableException extends RuntimeException {
    public LedgerUnavailableException(String message, Throwable cause) {
      super(message, cause);
    }
  }

  private static JournalLine line(String accountCode, Direction direction, Money amount) {
    return JournalLine.newBuilder()
        .setAccountCode(accountCode)
        .setDirection(direction)
        // Plain decimal string — never a float. Matches the ledger's BigDecimal exactly.
        .setAmount(amount.amount().toPlainString())
        .build();
  }
}
