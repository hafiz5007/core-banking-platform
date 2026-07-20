package com.bank.payment.application;

import com.bank.common.error.BusinessException;
import com.bank.common.error.ErrorCode;
import com.bank.payment.application.rail.PaymentRailHandler;
import com.bank.payment.domain.PaymentType;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Selects the {@link PaymentRailHandler} that fulfils a given {@link PaymentType}. Handlers register
 * themselves via the rails they support, so adding a new rail (e.g. cross-border in a later sprint)
 * is a matter of adding a handler bean — no change here.
 */
@Component
public class RoutingEngine {

    private final Map<PaymentType, PaymentRailHandler> handlers = new EnumMap<>(PaymentType.class);

    public RoutingEngine(List<PaymentRailHandler> railHandlers) {
        for (PaymentRailHandler handler : railHandlers) {
            for (PaymentType type : handler.supportedTypes()) {
                handlers.put(type, handler);
            }
        }
    }

    public PaymentRailHandler handlerFor(PaymentType type) {
        PaymentRailHandler handler = handlers.get(type);
        if (handler == null) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Payment rail not available: " + type);
        }
        return handler;
    }
}
