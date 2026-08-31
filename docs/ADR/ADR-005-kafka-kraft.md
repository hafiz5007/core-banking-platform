# ADR-005: Kafka KRaft Mode (No ZooKeeper)

- Status: **Accepted**
- Date: 2026-08-06
- Deciders: Shardar Rahman
- Scope: Infrastructure

## Context

Kafka historically required a separate ZooKeeper ensemble for cluster coordination (leader election,
topic metadata, consumer group state). This doubled the operational footprint: two distributed
systems to configure, monitor, and upgrade in lockstep.

From Kafka 2.8 (preview) and **Kafka 3.3 (production-ready)**, the KRaft consensus protocol
replaces ZooKeeper entirely. Confluent and Apache declared ZooKeeper deprecated in Kafka 3.5 with
removal planned for Kafka 4.0.

## Decision

Run Kafka in **KRaft mode** (no ZooKeeper) using the `apache/kafka:3.7` Docker image.

Docker Compose configuration:
```yaml
kafka:
  image: apache/kafka:3.7
  environment:
    KAFKA_NODE_ID: 1
    KAFKA_PROCESS_ROLES: broker,controller
    KAFKA_LISTENERS: PLAINTEXT://:9092,CONTROLLER://:9093
    KAFKA_CONTROLLER_QUORUM_VOTERS: "1@kafka:9093"
    KAFKA_CONTROLLER_LISTENER_NAMES: CONTROLLER
    KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
    KAFKA_AUTO_CREATE_TOPICS_ENABLE: "false"
    CLUSTER_ID: "Mv7sTAzST0OX5LxfPE2Apg"
```

Single-node KRaft is sufficient for local development and the demo environment. The same
configuration scales to a multi-node KRaft cluster in production without introducing ZooKeeper.

## Alternatives considered

1. **Kafka + ZooKeeper (classic)** — proven in production for years, widely documented, but
   requires two separate services with separate health checks, credentials, and JVM tuning.
   Rejected: extra operational burden for no benefit on a project starting fresh in 2026.
2. **Redpanda** — ZooKeeper-free, Kafka-compatible, C++ implementation with lower latency at small
   scale; however, it is not Apache Kafka and minor protocol differences have caused compatibility
   issues with some Spring Kafka versions. Rejected: risk of subtle compatibility issues outweighs
   the performance benefit at demo scale.
3. **Confluent Platform (Kraft)** — Confluent's managed distribution is production-grade but
   requires license for some features and is overkill for a self-hosted portfolio project. Rejected.

## Consequences

- **Positive:** Single Kafka container in Docker Compose (no ZooKeeper sidecar); KRaft is the
  forward-compatible path as ZooKeeper is removed in Kafka 4.0; simpler networking (no ZK port
  2181 to expose).
- **Negative:** Some legacy monitoring tooling (older Kafka Manager, Kafdrop <3.x) still
  interrogates ZooKeeper; use Kafka-native tooling (`kafka-topics.sh`, `kafka-consumer-groups.sh`)
  instead.
- **Risks + mitigations:** KRaft quorum loss in a single-node setup is a total broker outage —
  acceptable for dev/demo; document that production deployments require ≥3 controller nodes.

## References

- ADR-004 — Outbox pattern (depends on Kafka)
- ADR-014 — Notification service async via Kafka
- [KIP-500: Replace ZooKeeper with a Self-Managed Metadata Quorum](https://cwiki.apache.org/confluence/display/KAFKA/KIP-500%3A+Replace+ZooKeeper+with+a+Self-Managed+Metadata+Quorum)
- [Kafka KRaft documentation](https://kafka.apache.org/documentation/#kraft)

---

## Implementation status

**Implemented end-to-end** for the payment → notification flow. Off by default
(`CBP_KAFKA_ENABLED=false`), so the platform still runs with no broker.

| Piece | Where |
| --- | --- |
| Broker | `docker-compose.yml` `kafka` — `apache/kafka:3.7.2`, KRaft, single node, `KAFKA_AUTO_CREATE_TOPICS_ENABLE=false` |
| Topic creation | `kafka-init` one-shot creates all four ADR-014 topics — `payment.events`, `account.events`, `card.events`, `customer.events` (3 partitions each) — and exits |
| Producer | `payment-service` `EventBrokerPort` → `KafkaEventBrokerAdapter` (`kafka.enabled=true`) or `LoggingEventBrokerAdapter` (default) |
| Relay | `OutboxRelay` publishes each pending row and marks it PUBLISHED **only** on broker ack |
| Consumer | `notification-service` `PaymentEventListener` on `payment.events`, group `notification-service` |
| Idempotency | `processed_notifications` table keyed `(topic, partition, offset)` — created by V3, renamed by V4 to the name ADR-014 specifies |

Decisions taken while building it:

- **A row is marked PUBLISHED only after the broker acknowledges.** The previous stub marked rows
  published unconditionally; against a real broker that would silently drop events during an outage.
  A failed publish leaves the row PENDING for the next pass — this is what makes the outbox
  at-least-once rather than at-most-once.
- **A failed publish stops the batch rather than skipping the row.** Events for one aggregate are
  ordered, and continuing past a failure would deliver a later event before an earlier one.
- **Records are keyed by aggregate id**, so all events for one payment land on the same partition
  and keep their order.
- **Producer is `acks=all` with idempotence on and one in-flight request.** The outbox guarantees the
  event is not lost before the broker sees it; these settings stop the broker losing or reordering
  what it accepted.
- **An unreadable record is recorded as handled and skipped.** A poison message that can never be
  parsed would otherwise block its partition forever. It is logged at ERROR for investigation.

**Partly implemented:** all four ADR-014 topics are declared, but only `payment.events` has a producer
and a consumer. The other three exist so the estate topic set is explicit and reviewable rather than
appearing by accident on first publish; they carry no traffic yet. No schema registry: the
payload is hand-built JSON, as ADR-004 anticipated.
