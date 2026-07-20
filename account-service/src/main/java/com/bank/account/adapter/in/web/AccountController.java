package com.bank.account.adapter.in.web;

import com.bank.account.adapter.in.web.dto.AccountHolderResponse;
import com.bank.account.adapter.in.web.dto.AccountResponse;
import com.bank.account.adapter.in.web.dto.ChangeLogResponse;
import com.bank.account.adapter.in.web.dto.OpenAccountRequest;
import com.bank.account.adapter.in.web.dto.OpenFromProductRequest;
import com.bank.account.application.AccountService;
import com.bank.account.application.ChangeLogRecorder;
import com.bank.account.domain.Account;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

    private final AccountService accountService;
    private final ChangeLogRecorder changeLog;

    public AccountController(AccountService accountService, ChangeLogRecorder changeLog) {
        this.accountService = accountService;
        this.changeLog = changeLog;
    }

    @PostMapping
    public ResponseEntity<AccountResponse> open(@Valid @RequestBody OpenAccountRequest request) {
        Account account = accountService.openAccount(
                request.customerId(),
                request.accountType(),
                Currency.getInstance(request.currencyCode()));
        return ResponseEntity
                .created(URI.create("/api/v1/accounts/" + account.getId()))
                .body(AccountResponse.from(account));
    }

    @PostMapping("/from-product")
    public ResponseEntity<AccountResponse> openFromProduct(@Valid @RequestBody OpenFromProductRequest request) {
        Account account = accountService.openFromProduct(
                request.productCode(), request.primaryCustomerId(),
                request.additionalHolders(), request.mandateType());
        return ResponseEntity
                .created(URI.create("/api/v1/accounts/" + account.getId()))
                .body(AccountResponse.from(account));
    }

    @GetMapping("/{id}")
    public AccountResponse get(@PathVariable UUID id) {
        return AccountResponse.from(accountService.getAccount(id));
    }

    @GetMapping("/{id}/holders")
    public List<AccountHolderResponse> holders(@PathVariable UUID id) {
        return accountService.holdersOf(id).stream().map(AccountHolderResponse::from).toList();
    }

    @GetMapping("/{id}/change-log")
    public List<ChangeLogResponse> changeLog(@PathVariable UUID id) {
        accountService.getAccount(id); // 404 if unknown
        return changeLog.history("Account", id.toString()).stream().map(ChangeLogResponse::from).toList();
    }
}

