package com.bank.payment.adapter.out.kafka;

import com.bank.payment.application.port.EventBrokerPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Default broker adapter for deployments without Kafka: logs the event and returns normally, so the
 * relay drains the outbox as before. Set {@code kafka.enabled=true} to publish for real.
 */
@Component
@ConditionalOnProperty(name = "kafka.enabled", havingValue = "false", matchIfMissing = true)
public class LoggingEventBrokerAdapter implements EventBrokerPort {

  private static final Logger log = LoggerFactory.getLogger(LoggingEventBrokerAdapter.class);

  @Override
  public void publish(String topic, String key, String payload) {
    log.info("Kafka disabled; would publish to {} key={} payload={}", topic, key, payload);
  }
}
