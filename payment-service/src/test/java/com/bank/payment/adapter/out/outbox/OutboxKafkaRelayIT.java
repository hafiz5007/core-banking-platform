package com.bank.payment.adapter.out.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.bank.payment.AbstractIntegrationTest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.kafka.KafkaContainer;

/**
 * The producer half of the event backbone (ADR-004/005): the outbox relay must put pending rows on
 * a real Kafka topic and mark them PUBLISHED only once the broker has taken them.
 */
class OutboxKafkaRelayIT extends AbstractIntegrationTest {

  private static final String TOPIC = "payment.events";

  private static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:3.7.2");

  static {
    // Started once per JVM alongside the shared PostgreSQL, for the same context-caching reason.
    KAFKA.start();
  }

  @DynamicPropertySource
  static void kafkaProperties(DynamicPropertyRegistry registry) {
    registry.add("kafka.enabled", () -> "true");
    registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    // The relay is driven explicitly in these tests; keep the scheduler out of the way.
    registry.add("outbox.relay.delay-ms", () -> "3600000");
  }

  @Autowired OutboxRelay relay;

  @Autowired OutboxEventRepository outbox;

  private KafkaConsumer<String, String> consumer;

  @BeforeEach
  void subscribe() {
    consumer =
        new KafkaConsumer<>(
            Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG,
                "test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class));
    consumer.subscribe(List.of(TOPIC));
    consumer.poll(Duration.ofSeconds(1));
  }

  @AfterEach
  void unsubscribe() {
    consumer.close();
  }

  private OutboxEvent pendingEvent(String paymentId) {
    String payload =
        """
                {"paymentId":"%s","idempotencyKey":"k-%s","type":"INTRABANK","debtor":"1000",\
                "creditor":"2000","amount":"42.00","currency":"USD","status":"CONFIRMED"}"""
            .formatted(paymentId, paymentId);
    return outbox.save(
        new OutboxEvent("Payment", UUID.fromString(paymentId), "PaymentPosted", payload));
  }

  private List<ConsumerRecord<String, String>> drain(int expected) {
    List<ConsumerRecord<String, String>> collected = new ArrayList<>();
    await()
        .atMost(Duration.ofSeconds(20))
        .until(
            () -> {
              consumer.poll(Duration.ofMillis(500)).forEach(collected::add);
              return collected.size() >= expected;
            });
    return collected;
  }

  @Test
  @Timeout(60)
  void aPendingEventIsPublishedAndMarkedPublished() {
    String paymentId = UUID.randomUUID().toString();
    OutboxEvent event = pendingEvent(paymentId);

    relay.flush();

    List<ConsumerRecord<String, String>> records = drain(1);
    assertThat(records)
        .anySatisfy(
            record -> {
              assertThat(record.value()).contains(paymentId).contains("\"status\":\"CONFIRMED\"");
              // Keyed by aggregate id, so a payment's events keep their order on one partition.
              assertThat(record.key()).isEqualTo(paymentId);
            });
    assertThat(outbox.findById(event.getId()).orElseThrow().getStatus())
        .isEqualTo(OutboxStatus.PUBLISHED);
  }

  @Test
  @Timeout(60)
  void everyPendingEventIsDrainedInOnePass() {
    List<OutboxEvent> events =
        List.of(
            pendingEvent(UUID.randomUUID().toString()),
            pendingEvent(UUID.randomUUID().toString()),
            pendingEvent(UUID.randomUUID().toString()));

    relay.flush();

    drain(3);
    assertThat(events)
        .allSatisfy(
            event ->
                assertThat(outbox.findById(event.getId()).orElseThrow().getStatus())
                    .isEqualTo(OutboxStatus.PUBLISHED));
  }

  @Test
  @Timeout(60)
  void anAlreadyPublishedEventIsNotSentAgain() {
    pendingEvent(UUID.randomUUID().toString());
    relay.flush();
    drain(1);

    // Nothing is pending now, so a second pass must publish nothing.
    relay.flush();

    assertThat(outbox.findTop100ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING)).isEmpty();
  }
}
