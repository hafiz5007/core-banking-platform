package com.bank.payment.application;

import com.bank.common.error.ResourceNotFoundException;
import com.bank.common.money.Money;
import com.bank.payment.adapter.out.persistence.StandingOrderRepository;
import com.bank.payment.application.PaymentService.InitiatePaymentCommand;
import com.bank.payment.domain.PaymentType;
import com.bank.payment.domain.recurring.Frequency;
import com.bank.payment.domain.recurring.StandingOrder;
import com.bank.payment.domain.recurring.StandingOrderStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manages standing orders and executes the ones due, reusing the intrabank payment engine. Each run
 * uses a deterministic idempotency key ({@code so-<id>-<runDate>}) so re-running a date never
 * double-pays (FR-PAY-005/008).
 */
@Service
public class StandingOrderService {

    private static final Logger log = LoggerFactory.getLogger(StandingOrderService.class);

    private final StandingOrderRepository repository;
    private final PaymentService paymentService;

    public StandingOrderService(StandingOrderRepository repository, PaymentService paymentService) {
        this.repository = repository;
        this.paymentService = paymentService;
    }

    @Transactional
    public StandingOrder create(String debtorAccount, String creditorAccount, Money amount,
                                String narrative, Frequency frequency, LocalDate startDate, LocalDate endDate) {
        StandingOrder order = StandingOrder.create(debtorAccount, creditorAccount, amount,
                narrative, frequency, startDate, endDate);
        return repository.save(order);
    }

    @Transactional(readOnly = true)
    public StandingOrder get(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Standing order not found: " + id));
    }

    @Transactional
    public StandingOrder cancel(UUID id) {
        StandingOrder order = get(id);
        order.cancel();
        return repository.save(order);
    }

    /** Execute every standing order due on the given date. Returns the number executed. */
    @Transactional
    public int runDue(LocalDate on) {
        List<StandingOrder> due = repository.findByStatusAndNextRunDateLessThanEqual(
                StandingOrderStatus.ACTIVE, on);
        int executed = 0;
        for (StandingOrder order : due) {
            String key = "so-" + order.getId() + "-" + order.getNextRunDate();
            paymentService.initiate(new InitiatePaymentCommand(
                    key, PaymentType.INTRABANK, order.getDebtorAccount(), order.getCreditorAccount(),
                    order.money(), order.getNarrative(), null));
            order.advance();
            repository.save(order);
            executed++;
        }
        log.info("Standing orders executed on {}: {}", on, executed);
        return executed;
    }
}
