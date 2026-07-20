package com.bank.notification.adapter.out.sender;

import com.bank.notification.application.port.NotificationSenderPort;
import com.bank.notification.domain.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Stub delivery adapter that logs notifications. Replace with real SMS/email/push gateway clients
 * (with delivery-status callbacks and retry) in production.
 */
@Component
public class LoggingSenderAdapter implements NotificationSenderPort {

    private static final Logger log = LoggerFactory.getLogger(LoggingSenderAdapter.class);

    @Override
    public boolean send(Notification notification) {
        log.info("Dispatching {} via {} to {}: {}",
                notification.getType(), notification.getChannel(),
                maskRecipient(notification.getRecipient()), notification.getMessage());
        return true;
    }

    private String maskRecipient(String recipient) {
        if (recipient == null || recipient.length() < 4) {
            return "***";
        }
        return "***" + recipient.substring(recipient.length() - 4);
    }
}
