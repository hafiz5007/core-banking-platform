package com.bank.notification.adapter.in.event;

import com.bank.notification.adapter.out.persistence.ProcessedNotificationRepository;
import com.bank.notification.application.NotificationService;
import com.bank.notification.domain.NotificationChannel;
import com.bank.notification.domain.NotificationType;
import com.bank.notification.domain.ProcessedNotification;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns payment events into customer notifications (ADR-014).
 *
 * <p>notification-service is the only service with an outbound notification concern;
 * payment-service publishes for domain reasons and knows nothing about alerts.
 *
 * <p>Kafka delivers at least once, so every record is checked against {@link ProcessedNotification}
 * before anything is sent. The check and the send share one transaction: either the alert is stored
 * and the record marked handled, or neither happens and redelivery retries cleanly.
 */
@Component
@ConditionalOnProperty(name = "kafka.enabled", havingValue = "true")
public class PaymentEventListener {

  private static final Logger log = LoggerFactory.getLogger(PaymentEventListener.class);

  private final NotificationService notificationService;
  private final ProcessedNotificationRepository processedNotifications;
  private final ObjectMapper objectMapper;

  public PaymentEventListener(
      NotificationService notificationService,
      ProcessedNotificationRepository processedNotifications,
      ObjectMapper objectMapper) {
    this.notificationService = notificationService;
    this.processedNotifications = processedNotifications;
    this.objectMapper = objectMapper;
  }

  @KafkaListener(
      topics = "${kafka.topics.payment-events:payment.events}",
      groupId = "${kafka.consumer-group:notification-service}")
  @Transactional
  public void onPaymentEvent(
      @Payload String payload,
      @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
      @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
      @Header(KafkaHeaders.OFFSET) long offset) {
    if (processedNotifications.existsByTopicAndPartitionIdAndRecordOffset(
        topic, partition, offset)) {
      log.debug("Skipping already-handled record {}-{}@{}", topic, partition, offset);
      return;
    }

    PaymentEvent event;
    try {
      event = parse(payload);
    } catch (RuntimeException e) {
      // A record we cannot read will never become readable: record it as handled so it does
      // not block the partition, and rely on the log for investigation.
      log.error(
          "Unreadable payment event at {}-{}@{}; skipping: {}",
          topic,
          partition,
          offset,
          e.getMessage());
      processedNotifications.save(
          new ProcessedNotification(topic, partition, offset, "UNPARSEABLE"));
      return;
    }

    notificationService.sendAlert(
        event.debtor(),
        NotificationType.TRANSACTION_ALERT,
        NotificationChannel.EMAIL,
        message(event));
    processedNotifications.save(
        new ProcessedNotification(topic, partition, offset, event.status()));
    log.info("Alerted {} about payment {} ({})", event.debtor(), event.paymentId(), event.status());
  }

  private static String message(PaymentEvent event) {
    return "Payment of %s %s to %s is %s."
        .formatted(
            event.amount().toPlainString(),
            event.currency(),
            event.creditor(),
            event.status().toLowerCase());
  }

  private PaymentEvent parse(String payload) {
    try {
      JsonNode node = objectMapper.readTree(payload);
      return new PaymentEvent(
          text(node, "paymentId"),
          text(node, "debtor"),
          text(node, "creditor"),
          new BigDecimal(text(node, "amount")),
          text(node, "currency"),
          text(node, "status"));
    } catch (Exception e) {
      throw new IllegalArgumentException("not a payment event: " + e.getMessage(), e);
    }
  }

  private static String text(JsonNode node, String field) {
    JsonNode value = node.get(field);
    if (value == null || value.isNull() || value.asText().isBlank()) {
      throw new IllegalArgumentException("missing field: " + field);
    }
    return value.asText();
  }

  /** The subset of the payment event this service needs; the producer's payload may carry more. */
  private record PaymentEvent(
      String paymentId,
      String debtor,
      String creditor,
      BigDecimal amount,
      String currency,
      String status) {}
}
