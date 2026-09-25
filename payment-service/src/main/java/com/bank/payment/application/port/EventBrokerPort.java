package com.bank.payment.application.port;

/**
 * Outbound port to the event backbone (ADR-005). The outbox relay forwards pending rows through
 * this; implementations are config-switched so a deployment without a broker still runs.
 */
public interface EventBrokerPort {

  /**
   * Publish one event, blocking until the broker has acknowledged it.
   *
   * <p>Must throw if the event was not accepted. The relay marks a row PUBLISHED only on a clean
   * return, so a failure here leaves the row PENDING to be retried — which is what makes the outbox
   * at-least-once rather than at-most-once.
   *
   * @param key partition key; the aggregate id, so events for one aggregate keep their order
   */
  void publish(String topic, String key, String payload);
}
