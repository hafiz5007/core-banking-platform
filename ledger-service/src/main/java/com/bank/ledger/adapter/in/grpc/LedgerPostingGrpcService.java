package com.bank.ledger.adapter.in.grpc;

import com.bank.common.error.BusinessException;
import com.bank.ledger.application.LedgerService;
import com.bank.ledger.application.LedgerService.LineCommand;
import com.bank.ledger.application.LedgerService.PostingCommand;
import com.bank.ledger.domain.Direction;
import com.bank.ledger.domain.JournalEntry;
import com.bank.ledger.grpc.v1.LedgerPostingGrpc;
import com.bank.ledger.grpc.v1.PostJournalEntryRequest;
import com.bank.ledger.grpc.v1.PostJournalEntryResponse;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * gRPC inbound adapter for ledger posting (ADR-007). A second transport in front of the same
 * {@link LedgerService#post} use case the REST controller calls — no business logic lives here, only
 * protobuf ↔ domain mapping and error translation.
 */
@Component
public class LedgerPostingGrpcService extends LedgerPostingGrpc.LedgerPostingImplBase {

    private static final Logger log = LoggerFactory.getLogger(LedgerPostingGrpcService.class);

    private final LedgerService ledgerService;

    public LedgerPostingGrpcService(LedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

    @Override
    public void postJournalEntry(PostJournalEntryRequest request,
                                 StreamObserver<PostJournalEntryResponse> responseObserver) {
        try {
            JournalEntry entry = ledgerService.post(toCommand(request));
            responseObserver.onNext(PostJournalEntryResponse.newBuilder()
                    .setEntryId(entry.getId().toString())
                    .setStatus(entry.getStatus().name())
                    .build());
            responseObserver.onCompleted();
        } catch (StatusRuntimeException e) {
            // Already carries a gRPC status (mapping failures below).
            responseObserver.onError(e);
        } catch (BusinessException e) {
            log.info("gRPC posting rejected: {}", e.getMessage());
            responseObserver.onError(toStatus(e).withDescription(e.getMessage()).asRuntimeException());
        } catch (IllegalArgumentException e) {
            log.info("gRPC posting rejected as invalid: {}", e.getMessage());
            responseObserver.onError(
                    Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
        } catch (RuntimeException e) {
            // Never leak internals over the wire; the correlation id in the logs is the support handle.
            log.error("gRPC posting failed", e);
            responseObserver.onError(
                    Status.INTERNAL.withDescription("Posting failed").asRuntimeException());
        }
    }

    /** Mirrors the REST GlobalExceptionHandler's mapping, in gRPC's status vocabulary. */
    private static Status toStatus(BusinessException e) {
        return switch (e.getCode()) {
            case RESOURCE_NOT_FOUND -> Status.NOT_FOUND;
            case DUPLICATE_REQUEST -> Status.ALREADY_EXISTS;
            case VALIDATION_FAILED -> Status.INVALID_ARGUMENT;
            default -> Status.FAILED_PRECONDITION;
        };
    }

    private static PostingCommand toCommand(PostJournalEntryRequest request) {
        if (request.getIdempotencyKey().isBlank()) {
            throw Status.INVALID_ARGUMENT.withDescription("idempotency_key is required").asRuntimeException();
        }
        if (request.getLinesCount() == 0) {
            throw Status.INVALID_ARGUMENT.withDescription("at least one line is required").asRuntimeException();
        }
        List<LineCommand> lines = request.getLinesList().stream()
                .map(LedgerPostingGrpcService::toLine)
                .toList();
        return new PostingCommand(
                request.getIdempotencyKey(), request.getNarrative(), toValueDate(request.getValueDate()), lines);
    }

    private static LineCommand toLine(com.bank.ledger.grpc.v1.JournalLine line) {
        if (line.getAccountCode().isBlank()) {
            throw Status.INVALID_ARGUMENT.withDescription("account_code is required").asRuntimeException();
        }
        Direction direction = switch (line.getDirection()) {
            case DEBIT -> Direction.DEBIT;
            case CREDIT -> Direction.CREDIT;
            default -> throw Status.INVALID_ARGUMENT
                    .withDescription("direction must be DEBIT or CREDIT").asRuntimeException();
        };
        return new LineCommand(line.getAccountCode(), direction, toAmount(line.getAmount()));
    }

    /** Amounts cross the wire as decimal strings; anything else would lose precision. */
    private static BigDecimal toAmount(String raw) {
        try {
            return new BigDecimal(raw);
        } catch (NumberFormatException e) {
            throw Status.INVALID_ARGUMENT
                    .withDescription("amount is not a decimal: " + raw).asRuntimeException();
        }
    }

    private static LocalDate toValueDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(raw);
        } catch (DateTimeParseException e) {
            throw Status.INVALID_ARGUMENT
                    .withDescription("value_date must be ISO-8601 (yyyy-MM-dd): " + raw).asRuntimeException();
        }
    }
}
