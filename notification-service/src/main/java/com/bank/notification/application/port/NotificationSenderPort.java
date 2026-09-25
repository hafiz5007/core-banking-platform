package com.bank.notification.application.port;

import com.bank.notification.domain.Notification;

/** Outbound port to the delivery gateways (SMS/email/push). */
public interface NotificationSenderPort {

  boolean send(Notification notification);
}
