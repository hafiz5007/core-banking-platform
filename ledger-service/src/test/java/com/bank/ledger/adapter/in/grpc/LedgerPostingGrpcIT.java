package com.bank.ledger.adapter.in.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bank.ledger.AbstractIntegrationTest;
import com.bank.ledger.adapter.in.web.dto.JournalEntryResponse;
import com.bank.ledger.application.LedgerService;
import com.bank.ledger.config.GrpcServerConfig;
import com.bank.ledger.domain.EntryStatus;
import com.bank.ledger.domain.LedgerAccountType;
import com.bank.ledger.grpc.v1.Direction;
import com.bank.ledger.grpc.v1.JournalLine;
import com.bank.ledger.grpc.v1.LedgerPostingGrpc;
import com.bank.ledger.grpc.v1.PostJournalEntryRequest;
import com.bank.ledger.grpc.v1.PostJournalEntryResponse;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.math.BigDecimal;
import java.util.Currency;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.test.context.TestPropertySource;

/**
 * Drives the gRPC posting endpoint over a real Netty transport against a real PostgreSQL — the
 * server half of the account→ledger bridge (ADR-007).
 */
@TestPropertySource(properties = {"grpc.server.enabled=true", "grpc.server.port=0"})
class LedgerPostingGrpcIT extends AbstractIntegrationTest {

    private static final Currency USD = Currency.getInstance("USD");

    @Autowired
    LedgerService ledger;

    @Autowired
    GrpcServerConfig grpcServer;

    @Autowired
    TestRestTemplate rest;

    private ManagedChannel channel;
    private LedgerPostingGrpc.LedgerPostingBlockingStub stub;
    private String deposits;
    private String settlement;

    @BeforeEach
    void connect() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        deposits = "DEP-" + suffix;
        settlement = "SETL-" + suffix;
        ledger.createAccount(deposits, "Customer Deposits", LedgerAccountType.LIABILITY, USD);
        ledger.createAccount(settlement, "Settlement", LedgerAccountType.ASSET, USD);

        channel = ManagedChannelBuilder.forAddress("localhost", grpcServer.port()).usePlaintext().build();
        stub = LedgerPostingGrpc.newBlockingStub(channel);
    }

    @AfterEach
    void disconnect() throws InterruptedException {
        channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
    }

    private PostJournalEntryRequest debitOf(String amount, String key) {
        return PostJournalEntryRequest.newBuilder()
                .setIdempotencyKey(key)
                .setNarrative("Account debit")
                .addLines(JournalLine.newBuilder()
                        .setAccountCode(deposits).setDirection(Direction.DEBIT).setAmount(amount))
                .addLines(JournalLine.newBuilder()
                        .setAccountCode(settlement).setDirection(Direction.CREDIT).setAmount(amount))
                .build();
    }

    @Test
    void postsABalancedEntryOverGrpcAndPersistsIt() {
        PostJournalEntryResponse response = stub.postJournalEntry(debitOf("125.00", "grpc-" + UUID.randomUUID()));

        assertThat(response.getEntryId()).isNotBlank();
        assertThat(response.getStatus()).isEqualTo(EntryStatus.POSTED.name());

        // Read it back through the REST API: an entry posted over gRPC is the same entry either way.
        JournalEntryResponse persisted = readEntry(response.getEntryId());
        assertThat(persisted.lines()).hasSize(2);
        assertThat(persisted.lines()).extracting(l -> new BigDecimal(l.amount()))
                .allSatisfy(a -> assertThat(a).isEqualByComparingTo("125.00"));
        assertThat(persisted.lines())
                .extracting(l -> l.accountCode() + ":" + l.direction())
                .containsExactlyInAnyOrder(deposits + ":DEBIT", settlement + ":CREDIT");
    }

    private JournalEntryResponse readEntry(String entryId) {
        return rest.getForObject("/api/v1/ledger/entries/" + entryId, JournalEntryResponse.class);
    }

    @Test
    void theDecimalAmountSurvivesTheWireExactly() {
        PostJournalEntryResponse response = stub.postJournalEntry(debitOf("0.07", "grpc-" + UUID.randomUUID()));

        assertThat(readEntry(response.getEntryId()).lines()).extracting(l -> new BigDecimal(l.amount()))
                .allSatisfy(a -> assertThat(a).isEqualByComparingTo("0.07"));
    }

    @Test
    void replayingAnIdempotencyKeyReturnsTheOriginalEntry() {
        String key = "grpc-" + UUID.randomUUID();

        String first = stub.postJournalEntry(debitOf("40.00", key)).getEntryId();
        String second = stub.postJournalEntry(debitOf("40.00", key)).getEntryId();

        assertThat(second).isEqualTo(first);
    }

    @Test
    void anUnbalancedEntryIsRejected() {
        PostJournalEntryRequest unbalanced = PostJournalEntryRequest.newBuilder()
                .setIdempotencyKey("grpc-" + UUID.randomUUID())
                .setNarrative("Unbalanced")
                .addLines(JournalLine.newBuilder()
                        .setAccountCode(deposits).setDirection(Direction.DEBIT).setAmount("100.00"))
                .addLines(JournalLine.newBuilder()
                        .setAccountCode(settlement).setDirection(Direction.CREDIT).setAmount("99.00"))
                .build();

        assertThatThrownBy(() -> stub.postJournalEntry(unbalanced))
                .isInstanceOf(StatusRuntimeException.class)
                .satisfies(e -> assertThat(((StatusRuntimeException) e).getStatus().getCode())
                        .isEqualTo(Status.Code.FAILED_PRECONDITION));
    }

    @Test
    void anUnknownAccountIsNotFound() {
        PostJournalEntryRequest unknown = PostJournalEntryRequest.newBuilder()
                .setIdempotencyKey("grpc-" + UUID.randomUUID())
                .setNarrative("Unknown account")
                .addLines(JournalLine.newBuilder()
                        .setAccountCode("NO-SUCH-ACCOUNT").setDirection(Direction.DEBIT).setAmount("10.00"))
                .addLines(JournalLine.newBuilder()
                        .setAccountCode(settlement).setDirection(Direction.CREDIT).setAmount("10.00"))
                .build();

        assertThatThrownBy(() -> stub.postJournalEntry(unknown))
                .isInstanceOf(StatusRuntimeException.class)
                .satisfies(e -> assertThat(((StatusRuntimeException) e).getStatus().getCode())
                        .isEqualTo(Status.Code.NOT_FOUND));
    }

    @Test
    void aMissingIdempotencyKeyIsAnInvalidArgument() {
        PostJournalEntryRequest noKey = debitOf("10.00", "").toBuilder().setIdempotencyKey("").build();

        assertThatThrownBy(() -> stub.postJournalEntry(noKey))
                .isInstanceOf(StatusRuntimeException.class)
                .satisfies(e -> assertThat(((StatusRuntimeException) e).getStatus().getCode())
                        .isEqualTo(Status.Code.INVALID_ARGUMENT));
    }

    @Test
    void aNonDecimalAmountIsAnInvalidArgument() {
        PostJournalEntryRequest bad = debitOf("not-a-number", "grpc-" + UUID.randomUUID());

        assertThatThrownBy(() -> stub.postJournalEntry(bad))
                .isInstanceOf(StatusRuntimeException.class)
                .satisfies(e -> assertThat(((StatusRuntimeException) e).getStatus().getCode())
                        .isEqualTo(Status.Code.INVALID_ARGUMENT));
    }
}
