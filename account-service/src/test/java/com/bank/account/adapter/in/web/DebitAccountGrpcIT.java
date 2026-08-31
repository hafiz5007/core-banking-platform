package com.bank.account.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.bank.account.AbstractIntegrationTest;
import com.bank.account.adapter.in.web.dto.AccountResponse;
import com.bank.account.adapter.in.web.dto.CreateProductRequest;
import com.bank.account.adapter.in.web.dto.DebitAccountRequest;
import com.bank.account.adapter.in.web.dto.OpenFromProductRequest;
import com.bank.account.adapter.in.web.dto.ProductResponse;
import com.bank.account.adapter.out.ledger.FakeLedgerPostingServer;
import com.bank.account.domain.AccountType;
import com.bank.account.domain.MandateType;
import com.bank.ledger.grpc.v1.Direction;
import com.bank.ledger.grpc.v1.JournalLine;
import com.bank.ledger.grpc.v1.PostJournalEntryRequest;
import io.grpc.Status;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The client half of the account→ledger gRPC bridge (ADR-007): debiting an account over REST posts
 * the matching balanced entry to ledger-service over a real gRPC connection.
 */
class DebitAccountGrpcIT extends AbstractIntegrationTest {

    private static final FakeLedgerPostingServer LEDGER = new FakeLedgerPostingServer();

    @DynamicPropertySource
    static void pointAtTheFakeLedger(DynamicPropertyRegistry registry) {
        registry.add("ledger.posting", () -> "grpc");
        registry.add("ledger.grpc.target", LEDGER::target);
        registry.add("ledger.settlement-account", () -> "SETTLEMENT");
    }

    @AfterAll
    static void stopLedger() {
        LEDGER.close();
    }

    @Autowired
    TestRestTemplate rest;

    @BeforeEach
    void resetLedger() {
        LEDGER.reset();
    }

    /** Opens an account on a product carrying a 500.00 overdraft, so it has funds to debit. */
    private AccountResponse openAccountWithOverdraft() {
        String code = "CUR-" + UUID.randomUUID().toString().substring(0, 8);
        rest.postForEntity("/api/v1/products",
                new CreateProductRequest(code, "Current with overdraft", AccountType.CURRENT, "USD",
                        new BigDecimal("0.00"), new BigDecimal("0.00"), new BigDecimal("0.00"),
                        new BigDecimal("10000.00"), new BigDecimal("500.00")),
                ProductResponse.class);

        return rest.postForEntity("/api/v1/accounts/from-product",
                new OpenFromProductRequest(code, UUID.randomUUID(), List.of(), MandateType.SINGLE),
                AccountResponse.class).getBody();
    }

    private ResponseEntity<AccountResponse> debit(UUID accountId, String amount, String key) {
        return rest.postForEntity("/api/v1/accounts/" + accountId + "/debit",
                new DebitAccountRequest(key, new BigDecimal(amount), "USD", "ATM withdrawal"),
                AccountResponse.class);
    }

    @Test
    void debitingAnAccountPostsABalancedEntryToTheLedgerOverGrpc() {
        AccountResponse account = openAccountWithOverdraft();
        String key = "debit-" + UUID.randomUUID();

        ResponseEntity<AccountResponse> response = debit(account.id(), "125.00", key);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(new BigDecimal(response.getBody().balance())).isEqualByComparingTo("-125.00");

        // The ledger really was called, over the wire, with a balanced pair.
        assertThat(LEDGER.received()).hasSize(1);
        PostJournalEntryRequest posted = LEDGER.lastRequest();
        assertThat(posted.getIdempotencyKey()).isEqualTo(key);
        assertThat(posted.getNarrative()).isEqualTo("ATM withdrawal");
        assertThat(posted.getLinesList()).hasSize(2);
        assertThat(posted.getLinesList())
                .extracting(JournalLine::getAccountCode, JournalLine::getDirection, JournalLine::getAmount)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                account.accountNumber(), Direction.DEBIT, "125.00"),
                        org.assertj.core.groups.Tuple.tuple("SETTLEMENT", Direction.CREDIT, "125.00"));
    }

    @Test
    void theDebitAndTheCreditAreEqualAndOpposite() {
        AccountResponse account = openAccountWithOverdraft();

        debit(account.id(), "12.34", "debit-" + UUID.randomUUID());

        List<JournalLine> lines = LEDGER.lastRequest().getLinesList();
        assertThat(lines).extracting(JournalLine::getAmount).containsOnly("12.34");
        assertThat(lines).extracting(JournalLine::getDirection)
                .containsExactlyInAnyOrder(Direction.DEBIT, Direction.CREDIT);
    }

    @Test
    void aDebitBeyondAvailableFundsIsRejectedAndNeverReachesTheLedger() {
        AccountResponse account = openAccountWithOverdraft();

        ResponseEntity<String> response = rest.postForEntity("/api/v1/accounts/" + account.id() + "/debit",
                new DebitAccountRequest("debit-" + UUID.randomUUID(), new BigDecimal("500.01"), "USD", "Too much"),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(LEDGER.received()).isEmpty();
    }

    @Test
    void aLedgerRejectionRollsBackTheBalanceChange() {
        AccountResponse account = openAccountWithOverdraft();
        LEDGER.failWith(Status.FAILED_PRECONDITION);

        ResponseEntity<String> response = rest.postForEntity("/api/v1/accounts/" + account.id() + "/debit",
                new DebitAccountRequest("debit-" + UUID.randomUUID(), new BigDecimal("50.00"), "USD", "Rejected"),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

        // The account and the ledger must not disagree: the balance change is rolled back.
        LEDGER.reset();
        AccountResponse after = rest.getForObject("/api/v1/accounts/" + account.id(), AccountResponse.class);
        assertThat(new BigDecimal(after.balance())).isEqualByComparingTo("0.00");
    }

    @Test
    void anUnreachableLedgerFailsTheDebitRatherThanLosingIt() {
        AccountResponse account = openAccountWithOverdraft();
        LEDGER.failWith(Status.UNAVAILABLE);

        ResponseEntity<String> response = rest.postForEntity("/api/v1/accounts/" + account.id() + "/debit",
                new DebitAccountRequest("debit-" + UUID.randomUUID(), new BigDecimal("50.00"), "USD", "Outage"),
                String.class);

        // Infrastructure faults are 5xx, not a business rejection shown to the customer.
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);

        LEDGER.reset();
        AccountResponse after = rest.getForObject("/api/v1/accounts/" + account.id(), AccountResponse.class);
        assertThat(new BigDecimal(after.balance())).isEqualByComparingTo("0.00");
    }

    @Test
    void anOverLongIdempotencyKeyIsRejectedBeforeAnyLedgerCall() {
        AccountResponse account = openAccountWithOverdraft();

        ResponseEntity<String> response = rest.postForEntity("/api/v1/accounts/" + account.id() + "/debit",
                new DebitAccountRequest("k".repeat(81), new BigDecimal("10.00"), "USD", "Too long"),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(LEDGER.received()).isEmpty();
    }
}
