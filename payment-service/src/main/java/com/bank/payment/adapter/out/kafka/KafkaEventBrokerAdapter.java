package com.bank.payment.adapter.out.kafka;

import com.bank.payment.application.port.EventBrokerPort;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

/**
 * Publishes outbox events to Kafka (ADR-005). Enabled with {@code kafka.enabled=true}.
 *
 * <p>The send is awaited rather than fired and forgotten: the relay must not mark a row PUBLISHED
 * until the broker has actually acknowledged it, or an event would be silently dropped on a broker
 * outage. Producer acks are set to {@code all} in configuration for the same reason.
 */
@Component
@ConditionalOnProperty(name = "kafka.enabled", havingValue = "true")
public class KafkaEventBrokerAdapter implements EventBrokerPort {

  private static final Logger log = LoggerFactory.getLogger(KafkaEventBrokerAdapter.class);

  private final KafkaTemplate<String, String> kafkaTemplate;
  private final Duration sendTimeout;

  public KafkaEventBrokerAdapter(
      KafkaTemplate<String, String> kafkaTemplate,
      @Value("${kafka.send-timeout-ms:10000}") long sendTimeoutMs) {
    this.kafkaTemplate = kafkaTemplate;
    this.sendTimeout = Duration.ofMillis(sendTimeoutMs);
  }

  @Override
  public void publish(String topic, String key, String payload) {
    try {
      SendResult<String, String> result =
          kafkaTemplate
              .send(topic, key, payload)
              .get(sendTimeout.toMillis(), TimeUnit.MILLISECONDS);
      log.debug(
          "Published to {}-{}@{} key={}",
          topic,
          result.getRecordMetadata().partition(),
          result.getRecordMetadata().offset(),
          key);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new EventPublishException("Interrupted publishing to " + topic, e);
    } catch (ExecutionException | TimeoutException e) {
      throw new EventPublishException("Broker did not accept the event for " + topic, e);
    }
  }

  /** Raised when the broker did not acknowledge an event; the outbox row stays PENDING. */
  public static class EventPublishException extends RuntimeException {
    public EventPublishException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
