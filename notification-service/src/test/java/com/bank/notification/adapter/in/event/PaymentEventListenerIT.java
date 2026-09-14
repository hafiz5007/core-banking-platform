package com.bank.notification.adapter.in.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.bank.notification.AbstractIntegrationTest;
import com.bank.notification.adapter.out.persistence.NotificationRepository;
import com.bank.notification.adapter.out.persistence.ProcessedNotificationRepository;
import com.bank.notification.domain.Notification;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.kafka.KafkaContainer;

/**
 * The consumer half of the event backbone (ADR-014): a payment event on the topic becomes a customer
 * notification, exactly once even though Kafka delivers at least once.
 */
class PaymentEventListenerIT extends AbstractIntegrationTest {

    private static final String TOPIC = "payment.events";

    private static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:3.7.2");

    static {
        KAFKA.start();
    }

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("kafka.enabled", () -> "true");
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @Autowired
    PaymentEventListener listener;

    @Autowired
    NotificationRepository notifications;

    @Autowired
    ProcessedNotificationRepository processedNotifications;

    private static String eventFor(String debtor) {
        return """
                {"paymentId":"%s","idempotencyKey":"k-1","type":"INTRABANK","debtor":"%s",\
                "creditor":"9000","amount":"42.00","currency":"USD","status":"CONFIRMED"}"""
                .formatted(UUID.randomUUID(), debtor);
    }

    private void publish(String payload) {
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class))) {
            producer.send(new ProducerRecord<>(TOPIC, UUID.randomUUID().toString(), payload));
            producer.flush();
        }
    }

    private long alertsFor(String recipient) {
        return notifications.findAll().stream()
                .filter(n -> recipient.equals(n.getRecipient()))
                .count();
    }

    @Test
    @Timeout(120)
    void aPaymentEventOnTheTopicBecomesACustomerAlert() {
        String debtor = "acct-" + UUID.randomUUID().toString().substring(0, 8);

        publish(eventFor(debtor));

        await().atMost(Duration.ofSeconds(60)).until(() -> alertsFor(debtor) == 1);
        Notification alert = notifications.findAll().stream()
                .filter(n -> debtor.equals(n.getRecipient()))
                .findFirst().orElseThrow();
        assertThat(alert.getMessage()).contains("42.00").contains("USD").contains("confirmed");
    }

    @Test
    @Timeout(120)
    void redeliveryOfTheSameRecordDoesNotAlertTwice() {
        String debtor = "acct-" + UUID.randomUUID().toString().substring(0, 8);
        String payload = eventFor(debtor);
        // Kafka redelivers the same coordinate after a failed commit; simulate that directly so the
        // idempotency check is what is under test, not the broker.
        listener.onPaymentEvent(payload, TOPIC, 0, 4242L);

        listener.onPaymentEvent(payload, TOPIC, 0, 4242L);

        assertThat(alertsFor(debtor)).isEqualTo(1);
        assertThat(processedNotifications.existsByTopicAndPartitionIdAndRecordOffset(TOPIC, 0, 4242L)).isTrue();
    }

    @Test
    @Timeout(120)
    void adifferentOffsetIsTreatedAsANewEvent() {
        String debtor = "acct-" + UUID.randomUUID().toString().substring(0, 8);

        listener.onPaymentEvent(eventFor(debtor), TOPIC, 0, 5001L);
        listener.onPaymentEvent(eventFor(debtor), TOPIC, 0, 5002L);

        assertThat(alertsFor(debtor)).isEqualTo(2);
    }

    @Test
    @Timeout(120)
    void anUnreadableRecordIsRecordedAndSkippedRatherThanBlockingThePartition() {
        listener.onPaymentEvent("{\"not\":\"a payment\"}", TOPIC, 0, 6001L);

        // Marked handled, so redelivery will not retry it forever.
        assertThat(processedNotifications.existsByTopicAndPartitionIdAndRecordOffset(TOPIC, 0, 6001L)).isTrue();
    }
}
