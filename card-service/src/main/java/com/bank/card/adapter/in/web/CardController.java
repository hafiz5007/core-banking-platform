package com.bank.card.adapter.in.web;

import com.bank.card.adapter.in.web.dto.AuthorizationResponse;
import com.bank.card.adapter.in.web.dto.AuthorizeRequest;
import com.bank.card.adapter.in.web.dto.CardResponse;
import com.bank.card.adapter.in.web.dto.IssueCardRequest;
import com.bank.card.adapter.in.web.dto.UpdateControlsRequest;
import com.bank.card.application.CardService;
import com.bank.card.application.SettlementService;
import com.bank.card.application.SettlementService.ClearingEntry;
import com.bank.card.application.SettlementService.ReconciliationSummary;
import com.bank.card.domain.Authorization;
import com.bank.card.domain.Card;
import com.bank.common.money.Money;
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
@RequestMapping("/api/v1/cards")
public class CardController {

    private final CardService cardService;
    private final SettlementService settlementService;

    public CardController(CardService cardService, SettlementService settlementService) {
        this.cardService = cardService;
        this.settlementService = settlementService;
    }

    @PostMapping
    public ResponseEntity<CardResponse> issue(@Valid @RequestBody IssueCardRequest request) {
        Currency currency = Currency.getInstance(request.currencyCode());
        Card card = cardService.issue(request.accountCode(), Money.of(request.openingAvailable(), currency));
        return ResponseEntity.created(URI.create("/api/v1/cards/" + card.getId()))
                .body(CardResponse.from(card));
    }

    @GetMapping("/{id}")
    public CardResponse get(@PathVariable UUID id) {
        return CardResponse.from(cardService.getCard(id));
    }

    @PostMapping("/{id}/block")
    public CardResponse block(@PathVariable UUID id) {
        return CardResponse.from(cardService.block(id));
    }

    @PostMapping("/{id}/unblock")
    public CardResponse unblock(@PathVariable UUID id) {
        return CardResponse.from(cardService.unblock(id));
    }

    @PostMapping("/{id}/controls")
    public CardResponse updateControls(@PathVariable UUID id, @Valid @RequestBody UpdateControlsRequest request) {
        Currency currency = Currency.getInstance(request.currencyCode());
        Card card = cardService.updateControls(id, request.online(), request.contactless(),
                request.atm(), request.international(), Money.of(request.perTransactionLimit(), currency));
        return CardResponse.from(card);
    }

    @PostMapping("/{id}/authorizations")
    public ResponseEntity<AuthorizationResponse> authorize(
            @PathVariable UUID id, @Valid @RequestBody AuthorizeRequest request) {
        Currency currency = Currency.getInstance(request.currencyCode());
        Authorization auth = cardService.authorize(
                id, Money.of(request.amount(), currency), request.merchant(),
                request.channelOrDefault(), request.international());
        return ResponseEntity.status(201).body(AuthorizationResponse.from(auth));
    }

    @PostMapping("/settlement/reconcile")
    public ReconciliationSummary reconcile(@RequestBody List<ClearingEntry> entries) {
        return settlementService.reconcile(entries);
    }

    @PostMapping("/authorizations/{authId}/settle")
    public AuthorizationResponse settle(@PathVariable UUID authId) {
        return AuthorizationResponse.from(cardService.settle(authId));
    }

    @PostMapping("/authorizations/{authId}/reversal")
    public AuthorizationResponse reverse(@PathVariable UUID authId) {
        return AuthorizationResponse.from(cardService.reverse(authId));
    }
}
