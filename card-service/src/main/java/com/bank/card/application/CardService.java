package com.bank.card.application;

import com.bank.common.error.BusinessException;
import com.bank.common.error.ErrorCode;
import com.bank.common.error.ResourceNotFoundException;
import com.bank.common.money.Money;
import com.bank.card.adapter.out.persistence.AuthorizationRepository;
import com.bank.card.adapter.out.persistence.CardRepository;
import com.bank.card.application.port.LedgerPort;
import com.bank.card.application.port.LedgerPort.TransferCommand;
import com.bank.card.domain.Authorization;
import com.bank.card.domain.AuthorizationStatus;
import com.bank.card.domain.Card;
import com.bank.card.domain.CardChannel;
import java.security.SecureRandom;
import java.util.Currency;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Card issuance, customer controls, and real-time authorization (FR-CRD-001/002/004). Authorizations
 * place a hold subject to usage controls; settlement converts the hold to a ledger posting (debit
 * the customer account, credit the card settlement account), reversal releases the hold (FR-CRD-003).
 */
@Service
public class CardService {

    private static final Logger log = LoggerFactory.getLogger(CardService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final CardRepository cardRepository;
    private final AuthorizationRepository authorizationRepository;
    private final LedgerPort ledgerPort;
    private final ChangeLogRecorder changeLog;
    private final String settlementAccount;

    public CardService(CardRepository cardRepository,
                       AuthorizationRepository authorizationRepository,
                       LedgerPort ledgerPort,
                       ChangeLogRecorder changeLog,
                       @Value("${card.settlement-account:CARD-SETTLEMENT}") String settlementAccount) {
        this.cardRepository = cardRepository;
        this.authorizationRepository = authorizationRepository;
        this.ledgerPort = ledgerPort;
        this.changeLog = changeLog;
        this.settlementAccount = settlementAccount;
    }

    @Transactional
    public Card issue(String accountCode, Money openingAvailable) {
        String lastFour = String.format("%04d", RANDOM.nextInt(10000));
        String token = "tok_" + UUID.randomUUID().toString().replace("-", "");
        Card card = Card.issue(token, lastFour, accountCode, openingAvailable);
        Card saved = cardRepository.save(card);
        changeLog.record("Card", saved.getId().toString(), com.bank.card.domain.ChangeType.CREATE,
                null, "Issued card ****" + lastFour + " for account " + accountCode);
        log.info("Issued card {} (****{}) for account {}", saved.getId(), lastFour, accountCode);
        return saved;
    }

    @Transactional(readOnly = true)
    public Card getCard(UUID id) {
        return cardRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Card not found: " + id));
    }

    @Transactional
    public Card block(UUID id) {
        Card card = getCard(id);
        card.block();
        return cardRepository.save(card);
    }

    @Transactional
    public Card unblock(UUID id) {
        Card card = getCard(id);
        card.unblock();
        return cardRepository.save(card);
    }

    @Transactional
    public Card updateControls(UUID id, boolean online, boolean contactless, boolean atm,
                               boolean international, Money perTransactionLimit) {
        Card card = getCard(id);
        card.updateControls(online, contactless, atm, international, perTransactionLimit);
        return cardRepository.save(card);
    }

    /** Real-time authorization: approve and place a hold, or decline if blocked/controlled/insufficient. */
    @Transactional
    public Authorization authorize(UUID cardId, Money amount, String merchant,
                                   CardChannel channel, boolean international) {
        Card card = getCard(cardId);
        if (!card.getCurrencyCode().equals(amount.currency().getCurrencyCode())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Authorization currency mismatch");
        }
        try {
            card.placeHold(amount, channel, international);
            cardRepository.save(card);
            Authorization auth = new Authorization(cardId, amount, merchant, AuthorizationStatus.APPROVED);
            log.info("Authorized {} on card {} via {} at {}", amount, cardId, channel, merchant);
            return authorizationRepository.save(auth);
        } catch (BusinessException e) {
            Authorization declined = new Authorization(cardId, amount, merchant, AuthorizationStatus.DECLINED);
            log.info("Declined {} on card {}: {}", amount, cardId, e.getMessage());
            return authorizationRepository.save(declined);
        }
    }

    /** Settle an approved authorization: mark it SETTLED and post the movement to the ledger. */
    @Transactional
    public Authorization settle(UUID authorizationId) {
        Authorization auth = getAuthorization(authorizationId);
        if (auth.getStatus() != AuthorizationStatus.APPROVED) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Only an approved authorization can be settled");
        }
        Card card = getCard(auth.getCardId());
        UUID entryId = ledgerPort.postTransfer(new TransferCommand(
                "card-" + auth.getId(), "Card settlement " + auth.getMerchant(),
                card.getAccountCode(), settlementAccount, auth.money()));
        auth.settle();
        log.info("Settled authorization {} (ledger entry {})", authorizationId, entryId);
        return authorizationRepository.save(auth);
    }

    @Transactional
    public Authorization reverse(UUID authorizationId) {
        Authorization auth = getAuthorization(authorizationId);
        if (auth.getStatus() != AuthorizationStatus.APPROVED) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Only an approved authorization can be reversed");
        }
        Card card = getCard(auth.getCardId());
        card.releaseHold(auth.money());
        cardRepository.save(card);
        auth.reverse();
        return authorizationRepository.save(auth);
    }

    @Transactional(readOnly = true)
    public Authorization getAuthorization(UUID id) {
        return authorizationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Authorization not found: " + id));
    }
}
