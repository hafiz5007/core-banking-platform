# Debugging and testing

How to run the tests, find out why something failed, and trace a request through the platform.
Written from the failures this codebase actually produces — the traps section is not hypothetical.

---

## 1. Running the tests

```bash
mvn verify                  # everything: unit + integration, ~5 min, needs Docker
mvn test                    # unit tests only, no Docker, ~1 min
mvn verify -pl payment-service          # one module
mvn verify -pl payment-service -am      # ...and what it depends on
```

Integration tests (`*IT`) run under **failsafe**, unit tests (`*Test`) under **surefire**. That
matters when you narrow a run:

```bash
mvn test    -pl ledger-service -Dtest=JournalEntryBalanceTest        # a unit test
mvn verify  -pl ledger-service -Dit.test=LedgerPostingGrpcIT \
            -DfailIfNoSpecifiedTests=false                            # one integration test
mvn verify  -pl ledger-service -Dit.test='LedgerPostingGrpcIT#postsABalancedEntryOverGrpcAndPersistsIt'
```

Every `*IT` starts a real PostgreSQL (and, for two of them, a real Kafka) via Testcontainers, so
**Docker must be running**. There is no in-memory substitute — that is deliberate: `ddl-auto:
validate` means an H2 dialect difference would hide real schema bugs.

### The quality gate

```bash
mvn verify -Pquality                                    # Spotless + JaCoCo
mvn verify -Pquality -Dspotless.check.skip=true         # coverage only
```

**`-Pquality` currently fails on formatting**, on pre-existing code — Spotless is configured for
google-java-format and the repo is not written that way. Until that is settled (see
`docs/PENDING_TASKS.md`), skip Spotless to check coverage. The JaCoCo floor is 40% per module and
every module passes.

### Where the reports are

```
<module>/target/surefire-reports/<Class>.txt      # unit failures, with stack traces
<module>/target/failsafe-reports/<Class>.txt      # integration failures
<module>/target/site/jacoco/index.html            # coverage, after -Pquality
```

Read the `.txt` report, not the Maven console output. Maven truncates the useful part; the report
has the full `Caused by:` chain, which is nearly always where the answer is.

---

## 2. Traps this codebase actually sets

Each of these has bitten. If a symptom matches, start here.

### "Could not find a valid Docker environment" — but Docker is running

Docker Engine 29 dropped support for API versions below 1.40, and docker-java negotiates an older
one. The handshake fails with an unhelpful HTTP 400 that Testcontainers reports as "no valid Docker
environment".

The build already pins it (`docker.api.version`, default `1.41`, passed to surefire/failsafe as the
**`api.version` system property** — there is no env-var equivalent). If you hit this outside Maven,
set it yourself. Confirm the daemon's floor with:

```bash
docker version --format '{{.Server.APIVersion}} (min {{.Server.MinAPIVersion}})'
DOCKER_API_VERSION=1.32 docker info    # reproduces the same 400
```

### An integration test passes alone but fails in a suite

Spring caches the application context **across test classes**. A container tied to one class's
lifecycle (`@Testcontainers` + `@Container`) is stopped when that class finishes, while a later class
still points its DataSource at it — you get `Connection refused` to a dead port.

Every `AbstractIntegrationTest` here therefore uses the **singleton container** pattern: started once
in a `static` block, never stopped, reaped by Ryuk at JVM exit. Copy that pattern; do not add
`@Testcontainers`.

### `LazyInitializationException` from a GET endpoint

`spring.jpa.open-in-view` is `false` in every service — correct, but it means mapping a lazy
collection to a response **after** the transaction closes throws. This is what made
`GET /api/v1/ledger/entries/{id}` return 500 for every entry until it was fixed with a fetch-join.

If a read endpoint 500s with "could not initialize proxy", the repository needs a
`@Query(... left join fetch ...)` finder, not an `@Transactional` on the controller.

### A `@Bean` silently does not exist

`@ConditionalOnMissingBean` matches on the **raw** type. Services already register
`FilterRegistrationBean`s for the correlation-id and tenant filters, so a new
`FilterRegistrationBean<SomethingElse>` carrying that annotation is skipped — and the service starts
looking configured while the filter is absent. This is exactly how the service-auth filter was
silently disabled during development.

Confirm what actually got wired:

```bash
# temporarily, in the service's application.yml
logging.level.org.springframework.boot.autoconfigure: DEBUG
```

The condition evaluation report prints why each bean was or was not created.

### Events are never delivered

The outbox relay marks a row `PUBLISHED` **only** after the broker acknowledges it. A row stuck at
`PENDING` means the publish failed — that is the design working, not a bug:

```sql
select status, count(*) from payment.outbox_event group by status;
select id, event_type, created_at from payment.outbox_event where status = 'PENDING' order by created_at limit 10;
```

Then check whether the topic exists at all — auto-creation is deliberately **off**:

```bash
docker compose exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list
docker compose logs kafka-init          # expect: topics ready
```

And whether the producer is real or the logging stub:

```bash
docker compose logs payment-service | grep "Kafka disabled"   # any output = CBP_KAFKA_ENABLED is false
```

### A consumer processed nothing, or processed twice

notification-service records `(topic, partition, offset)` in `processed_notifications` before acting.

```sql
select topic, partition_id, record_offset, event_type, processed_at
from notification.processed_notifications order by processed_at desc limit 10;
```

An `event_type` of `UNPARSEABLE` means the record could not be read; it is recorded as handled so it
cannot block its partition, and logged at ERROR. There is no dead-letter topic — the payload is only
in the log.

### 401 from a service that used to answer

`service-auth.require-inbound=true` on **ledger-service** and **risk-aml-service**. They refuse any
call without a valid token. Health and metrics stay open. The refusal reason is in the response and
the log:

```bash
docker compose logs ledger-service | grep "Rejected"
```

Reasons are deliberately specific — *token is not addressed to this service*, *token lacks the
required scope*, *token has expired* — and never echo the token.

---

## 3. Tracing one request end to end

Every request carries a correlation id. Send your own and it appears on every log line the request
touches, in every service:

```bash
curl -H 'X-Correlation-Id: debug-me-123' http://localhost:8080/api/v1/payments/...
docker compose logs | grep debug-me-123
```

The log pattern is `%5p [<service>,correlationId=%X{correlationId:-n/a}]`, so `correlationId=n/a`
means the request never went through `CorrelationIdFilter` — usually a direct call to a port that
bypassed the gateway, or a background job (the scheduler has no inbound request).

The Postman collections set `X-Correlation-Id: postman-{{$guid}}` on every request for this reason.

Follow one payment across the estate:

```bash
docker compose logs payment-service      | grep <correlationId>   # initiate, screen, post
docker compose logs risk-aml-service     | grep <correlationId>   # the screening decision
docker compose logs ledger-service       | grep <correlationId>   # the journal entry
docker compose logs payment-service      | grep "Relayed"          # outbox -> Kafka
docker compose logs notification-service | grep "Alerted"          # consumer -> alert
```

The last two links are asynchronous, so the correlation id does **not** propagate across them — the
outbox payload carries no header. That is a genuine gap: to follow a payment past the broker you have
to match on the payment id in the event body.

---

## 4. Attaching a debugger

To a service running in compose, add to that service in an override file:

```yaml
services:
  payment-service:
    environment:
      JAVA_TOOL_OPTIONS: "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005"
    ports:
      - "5005:5005"
```

Then attach a remote JVM debugger to `localhost:5005`. Use `suspend=y` if you need to break before
startup completes — the container will not become healthy until you connect.

To an integration test, run Maven with the debugger listening:

```bash
mvn verify -pl payment-service -Dit.test=OutboxKafkaRelayIT -Dmaven.failsafe.debug
```

Failsafe suspends on port 5005 and waits for you to attach. `-Dmaven.surefire.debug` does the same
for unit tests.

---

## 5. Inspecting state directly

**Database** — one instance, one database, a schema per service:

```bash
docker compose exec postgres psql -U banking -d banking
\dn                                  -- schemas: account, customer, ledger, payment, ...
\dt payment.*                        -- tables in one service
select * from payment.payment order by created_at desc limit 5;
select * from ledger.journal_entry order by created_at desc limit 5;
```

**Kafka** — read the topic without disturbing the consumer group:

```bash
docker compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 --topic payment.events --from-beginning

# how far behind is notification-service?
docker compose exec kafka /opt/kafka/bin/kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 --describe --group notification-service
```

A growing `LAG` means the consumer is failing or stopped; check its logs before assuming the producer
is at fault.

**gRPC** — ledger-service listens on 9090. Postman and curl cannot speak it; use `grpcurl`:

```bash
grpcurl -plaintext -d '{
  "idempotency_key":"dbg-1","narrative":"debug",
  "lines":[{"account_code":"DEP-001","direction":"DEBIT","amount":"1.00"},
           {"account_code":"SETTLEMENT","direction":"CREDIT","amount":"1.00"}]
}' localhost:9090 bank.ledger.v1.LedgerPosting/PostJournalEntry
```

Server reflection is **not** enabled, so pass the proto explicitly:
`-import-path ledger-grpc-api/src/main/proto -proto ledger_posting.proto`.

With service auth on you must also pass a token:
`-H "authorization: Bearer <jwt>"` — otherwise the call is refused `UNAUTHENTICATED` before it
reaches any handler.

**Actuator** — exposed endpoints are `health`, `info`, `prometheus`, `metrics`:

```bash
curl -s localhost:8084/actuator/health | jq .
curl -s localhost:8084/actuator/metrics | jq -r '.names[]' | grep -i kafka
```

The `loggers` endpoint is **not** exposed. To change a log level at runtime you have to add it to
`management.endpoints.web.exposure.include` and restart — at which point setting
`logging.level.*` directly is simpler.

---

## 6. Things that are not broken

Time saved by knowing these:

- **`kafka-init` shows `Exited (0)`.** That is success. It creates the topic and stops.
- **A `PENDING` outbox row right after a payment** is normal for up to the relay interval
  (`outbox.relay.delay-ms`, default 5000).
- **The OTP code is never in the API response.** Only its hash is stored. Take the code from the
  notification-service log; that is the design, not a missing field.
- **`mvn verify` takes ~5 minutes.** Most of it is containers starting. Use `-pl <module>` while
  iterating.
- **A single `payment.events` partition may hold events for several payments.** Ordering is
  guaranteed per payment id (the partition key), not globally.
