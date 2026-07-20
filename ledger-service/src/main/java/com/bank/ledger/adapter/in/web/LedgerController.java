package com.bank.ledger.adapter.in.web;

import com.bank.ledger.adapter.in.web.dto.CreateLedgerAccountRequest;
import com.bank.ledger.adapter.in.web.dto.JournalEntryResponse;
import com.bank.ledger.adapter.in.web.dto.LedgerAccountResponse;
import com.bank.ledger.adapter.in.web.dto.PostEntryRequest;
import com.bank.ledger.adapter.in.web.dto.TrialBalanceResponse;
import com.bank.ledger.application.LedgerService;
import com.bank.ledger.application.LedgerService.LineCommand;
import com.bank.ledger.application.LedgerService.PostingCommand;
import com.bank.ledger.domain.JournalEntry;
import com.bank.ledger.domain.LedgerAccount;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.Currency;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ledger")
public class LedgerController {

    private final LedgerService ledgerService;

    public LedgerController(LedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

    @PostMapping("/accounts")
    public ResponseEntity<LedgerAccountResponse> createAccount(
            @Valid @RequestBody CreateLedgerAccountRequest request) {
        LedgerAccount account = ledgerService.createAccount(
                request.code(), request.name(), request.accountType(),
                Currency.getInstance(request.currencyCode()));
        return ResponseEntity
                .created(URI.create("/api/v1/ledger/accounts/" + account.getCode()))
                .body(LedgerAccountResponse.from(account));
    }

    @GetMapping("/accounts/{code}")
    public LedgerAccountResponse getAccount(@PathVariable String code) {
        return LedgerAccountResponse.from(ledgerService.getAccount(code));
    }

    @PostMapping("/entries")
    public ResponseEntity<JournalEntryResponse> post(@Valid @RequestBody PostEntryRequest request) {
        PostingCommand command = new PostingCommand(
                request.idempotencyKey(), request.narrative(), request.valueDate(),
                request.lines().stream()
                        .map(l -> new LineCommand(l.accountCode(), l.direction(), l.amount()))
                        .toList());
        JournalEntry entry = ledgerService.post(command);
        return ResponseEntity
                .created(URI.create("/api/v1/ledger/entries/" + entry.getId()))
                .body(JournalEntryResponse.from(entry));
    }

    @GetMapping("/entries/{id}")
    public JournalEntryResponse getEntry(@PathVariable UUID id) {
        return JournalEntryResponse.from(ledgerService.getEntry(id));
    }

    @PostMapping("/entries/{id}/reversal")
    public ResponseEntity<JournalEntryResponse> reverse(
            @PathVariable UUID id, @RequestBody(required = false) ReversalRequest request) {
        String key = request != null && request.idempotencyKey() != null
                ? request.idempotencyKey()
                : "rev-" + id;
        JournalEntry reversal = ledgerService.reverse(id, key);
        return ResponseEntity
                .created(URI.create("/api/v1/ledger/entries/" + reversal.getId()))
                .body(JournalEntryResponse.from(reversal));
    }

    @GetMapping("/trial-balance")
    public TrialBalanceResponse trialBalance() {
        return TrialBalanceResponse.from(ledgerService.trialBalance());
    }

    /** Optional body for a reversal request. */
    public record ReversalRequest(String idempotencyKey) {
    }
}
