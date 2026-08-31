# Service dependencies: tracing, debugging, and test data

How to work with one service when it depends on another. Worked through in full for
**account-service** first, then the same method applied to the rest.

Companion documents: `docs/DEBUG_TESTING.md` (test suites and traps),
`docs/DEPLOYMENT_SANDBOX.md` (releasing to a server).

---

## 1. The dependency map

Only five service-to-service calls exist in this platform. Everything else is independent.

| Caller | Target | Transport | Scope required | Switched by |
| --- | --- | --- | --- | --- |
| account-service | ledger-service | **gRPC** :9090 | `ledger:post` | `CBP_LEDGER_POSTING=grpc` |
| account-service | ledger-service | REST :8083 | `ledger:write` | `CBP_LEDGER_PROVISIONING=http` |
| card-service | ledger-service | REST :8083 | `ledger:post` | always on |
| interest-fee-service | ledger-service | REST :8083 | `ledger:post` | always on |
| payment-service | ledger-service | REST :8083 | `ledger:post` | always on |
| payment-service | risk-aml-service | REST :8088 | `risk:evaluate` | `CBP_SCREENING_ADAPTER=risk-service` |

Plus one asynchronous hop, which is **not** a dependency — the producer does not know the consumer
exists:

| Producer | Topic | Consumer |
| --- | --- | --- |
| payment-service | `payment.events` | notification-service |

**Everything depends on ledger-service.** It is the one service to start first and the one whose
outage is felt everywhere. `customer-service`, `admin-service`, `reporting-service` and
`notification-service` call nobody.

### Start order

```
postgres  ->  ledger-service  ->  risk-aml-service  ->  everything else  ->  api-gateway
```

Compose declares `depends_on` for postgres only, so a service can start before the service it calls.
That is fine — the calls are made per request, not at startup — but it means a call made in the
first few seconds can fail while a dependency is still booting.

---

## 2. Worked example: account-service

account-service is the best one to learn on because it uses **both** transports to the same
dependency: gRPC for posting a debit, REST for provisioning a ledger account.

### 2.1 What it needs before it can do anything

| Needs | Why | How to get it |
| --- | --- | --- |
| PostgreSQL | its own `account` schema | `docker compose up -d postgres` |
| ledger-service on :9090 | the debit posts a journal entry over gRPC | `docker compose up -d ledger-service` |
| Ledger accounts `DEP-001`, `SETTLEMENT` | a debit posts DEBIT customer / CREDIT settlement; both codes must exist | `scripts\Seed-TestData.ps1` |
| A product with an overdraft | a new account has a zero balance, so nothing is debitable without one | `scripts\Seed-TestData.ps1` creates `CUR-STD` with 500 |
| A service token | ledger-service refuses unauthenticated calls | minted automatically when `CBP_SERVICE_AUTH_ENABLED=true` |

### 2.2 Step by step, verifying each hop

Work outwards. Each step proves one link, so when something fails you know which link broke rather
than guessing.

**Step 1 — the database is up and migrated.**

```powershell
docker compose up -d postgres ledger-service account-service
docker compose logs account-service | Select-String -Pattern "Flyway|Started AccountServiceApplication"
```

Expect `Successfully applied N migrations` and a `Started ... in Xs` line. If Flyway fails here,
nothing downstream matters.

**Step 2 — each service answers on its own port.**

```powershell
curl.exe -s http://localhost:8081/actuator/health    # account-service
curl.exe -s http://localhost:8083/actuator/health    # ledger-service
```

Health is deliberately open even when service auth is on, so a failure here is the service itself,
never authentication.

**Step 3 — is the dependency reachable *from inside* the caller's container?**

This is the step people skip, and it is the one that catches Docker networking problems. `localhost`
inside a container is the container, not your machine.

```powershell
docker compose exec account-service sh -c "curl -s -o /dev/null -w '%{http_code}' http://ledger-service:8083/actuator/health"
```

`200` means the network link is fine. Connection refused means either ledger-service is not up or
you are on the wrong hostname — inside compose it is the **service name**, not `localhost`.

**Step 4 — is the gRPC port open?** gRPC does not answer plain HTTP, so test the socket, not the
protocol:

```powershell
docker compose exec account-service bash -c 'timeout 3 bash -c "</dev/tcp/ledger-service/9090" 2>/dev/null && echo open || echo closed'
```

`nc` is **not** in the runtime image (`eclipse-temurin:21-jre`), so `nc -z` reports
`closed` for an open port - a false negative. `bash` is present, so `/dev/tcp` is the reliable
check.

If it is closed, ledger-service is running without the gRPC server: it only starts when
`CBP_GRPC_SERVER_ENABLED=true`. Confirm with:

```powershell
docker compose logs ledger-service | Select-String "gRPC server listening"
```

**Step 5 — seed the data both services need.**

```powershell
.\scripts\Seed-TestData.ps1 -Direct
```

**Step 6 — exercise the dependency end to end.**

```powershell
$acct = (Get-Content scripts\.seed-state.json | ConvertFrom-Json).accountId
$body = @{ idempotencyKey = "dbg-$(New-Guid)"; amount = '25.00'; currencyCode = 'USD'
           narrative = 'Dependency check' } | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8081/api/v1/accounts/$acct/debit" -Method POST `
  -Body $body -ContentType 'application/json' `
  -Headers @{ 'X-Correlation-Id' = 'dbg-001' }
```

A negative balance in the response means the whole chain worked: account-service applied the debit,
called ledger-service over gRPC, and the ledger accepted the entry.

**Step 7 — confirm the other side actually saw it.**

Do not trust the caller's success alone. Check what the ledger actually stored:

```powershell
docker compose exec postgres psql -U banking -d banking -c `
  "select narrative, created_at from ledger.journal_entry order by created_at desc limit 3;"
```

**The correlation id does not reach ledger-service on this path.** `JwtClientInterceptor` puts only
the `authorization` key into the gRPC metadata; nothing carries `X-Correlation-Id` across, and the
gRPC server has no correlation filter. So this returns nothing even on a successful debit:

```powershell
docker compose logs ledger-service | Select-String "dbg-001"   # empty - expected, not a failure
```

Match on the narrative or the entry id instead. The same break exists at the Kafka hop for the same
reason: the outbox payload carries no correlation header. Correlation tracing is reliable **only
across REST hops** today.

### 2.3 When step 6 fails, read the status

The client translates the ledger's gRPC status deliberately, so the HTTP code tells you which link
broke:

| You get | Means | Look at |
| --- | --- | --- |
| **400** | your request was rejected before any call | the validation message; `@Size` limits on `DebitAccountRequest` |
| **422** `BUSINESS_RULE_VIOLATION` | the domain refused — insufficient funds, account not ACTIVE, or the ledger rejected the entry | account balance and overdraft; whether `DEP-001`/`SETTLEMENT` exist |
| **404** | the account id does not exist | re-run the seed script; the volume may have been wiped |
| **500** | ledger-service was unreachable or timed out | step 3 and step 4 above |
| **401** *in the ledger log* | the token was refused | `CBP_SERVICE_AUTH_SECRET` differs between the two containers |

The single most common cause of a mysterious 500 is that ledger-service has no `SETTLEMENT` account,
because a debit is a *balanced pair* and the credit side has nowhere to go. `Seed-TestData.ps1`
creates it.

---

## 3. Debugging: with Docker or without?

Both, and the choice matters. There are three modes.

| Mode | What runs where | Use when |
| --- | --- | --- |
| **A. All in Docker** | everything in compose | verifying a release, reproducing a sandbox problem, checking wiring and networking |
| **B. Hybrid** *(recommended)* | dependencies in Docker, the service you are working on from your IDE | you are changing one service and want breakpoints and hot restart |
| **C. All on host** | run each service with `mvn spring-boot:run` | rarely worth it - you hand-manage ports, schemas and start order |

### Mode A — everything in Docker

You cannot set a breakpoint without opening a debug port. Add an override:

```yaml
# docker-compose.override.yml  (gitignored, local only)
services:
  account-service:
    environment:
      JAVA_TOOL_OPTIONS: "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005"
    ports:
      - "5005:5005"
```

Then `docker compose up -d account-service` and attach a remote debugger to `localhost:5005`. Use
`suspend=y` to break before startup finishes — the container will not report healthy until you
attach, which is expected, not a hang.

**The cost:** every code change needs `docker compose build account-service` and a restart. That is
the reason mode B exists.

### Mode B — hybrid: dependencies in Docker, your service on the host

This is the one to use day to day. Infrastructure and the services you are *not* changing stay in
Docker; the one you are changing runs from your IDE with full debugging and restart.

```powershell
# 1. Dependencies only - note account-service is NOT in this list
docker compose up -d postgres ledger-service risk-aml-service

# 2. Export the same configuration the container would get
.\scripts\Setup-Environment.ps1 -SetSessionVariables

# 3. Point the service at the containers as seen from the host
$env:CBP_DB_URL           = 'jdbc:postgresql://localhost:5432/banking'
$env:CBP_LEDGER_BASE_URL  = 'http://localhost:8083'
$env:CBP_LEDGER_GRPC_TARGET = 'localhost:9090'

# 4. Run it from the host
mvn -pl account-service spring-boot:run
```

**The one thing that trips everyone up:** inside compose the hostname is the service name
(`ledger-service:9090`); from the host it is `localhost:9090`. `Setup-Environment.ps1` writes the
compose form, because that is what the containers need — so **you must override the three variables
in step 3** when running on the host. A `Connection refused` to `ledger-service` in a host-run
service is always this.

Ports are published in `docker-compose.yml`, so `localhost:5432`, `localhost:8083` and
`localhost:9090` all work from the host without extra configuration.

### Mode C — everything on the host

Only worth it if Docker is unavailable. You need a local PostgreSQL with the schemas created, and
you must start services in dependency order by hand. The integration tests still need Docker
regardless, because they use Testcontainers.

---

## 4. Test data that does not need recreating

`scripts\Seed-TestData.ps1` creates everything the flows need and is safe to re-run.

```powershell
.\scripts\Seed-TestData.ps1 -Direct     # services on their own ports
.\scripts\Seed-TestData.ps1             # through the gateway on 8080
.\scripts\Seed-TestData.ps1 -Verify     # report only, writes nothing
```

It creates six ledger accounts (`DEP-001`, `SETTLEMENT`, `NOSTRO`, `CARD-SETTLEMENT`, `FEE-INCOME`,
`INTEREST-EXPENSE`), two products, a customer, an account with a 500 overdraft, a P2P alias and a
biller. When service auth is on it mints its own tokens, so it works against guarded services.

### Why it does not duplicate

Two different mechanisms, because the API offers two different guarantees:

- **Things with a natural key** — ledger accounts, products, aliases, billers — are keyed by code.
  Creating an existing code returns **409**, which the script counts as "already there".
- **Things without one** — the customer and the account — have no lookup-by-attribute endpoint, so
  the script remembers their ids in `scripts/.seed-state.json` (gitignored) and re-verifies them
  with a `GET`. If the record still resolves it is reused; if it has gone, it is recreated.

A second run reports `created: 0, already present: 12` and the **same** `accountId` and
`customerId`. That stability is what makes run-to-run reporting possible.

### What survives what

| Command | Data | Seed state file |
| --- | --- | --- |
| `docker compose restart` | kept | kept |
| `docker compose down` then `up` | **kept** | kept |
| `docker compose down -v` | **destroyed** | now stale - the script detects this and recreates |

The data lives in the named volume `pgdata`. Only `-v` deletes it. If you do wipe the volume, just
re-run the seed script: it notices the remembered ids no longer resolve, reports `[gone]`, and
creates fresh ones.

### Preparing data for a report

Reference the **stable handles** the script prints, not values copied from a one-off run:

```powershell
$state = Get-Content scripts\.seed-state.json | ConvertFrom-Json
$state.accountId      # same across runs
$state.customerId
```

Two cautions if you are building reports on this:

- The seed creates **reference data**, not volume. Transactions you generate on top of it accumulate
  in the database, so a report over "all payments" grows every run. Filter by correlation id or
  time window, or reset the volume between report runs.
- `outbox_event` and `processed_notifications` also grow without bound. There is no retention job
  yet (open in `docs/PENDING_TASKS.md`).

---

## 5. The same method for the other services

The pattern is identical: bring up the dependency, prove the hop from inside the caller's container,
seed what both sides need, then exercise it and check the far side.

### payment-service — the most connected

Depends on **ledger-service** (REST) and **risk-aml-service** (REST), and produces to Kafka.

```powershell
docker compose up -d postgres ledger-service risk-aml-service payment-service kafka
docker compose up kafka-init          # exits 0; creates the topics
.\scripts\Seed-TestData.ps1 -Direct

docker compose exec payment-service sh -c "curl -s -o /dev/null -w '%{http_code}' http://ledger-service:8083/actuator/health"
docker compose exec payment-service sh -c "curl -s -o /dev/null -w '%{http_code}' http://risk-aml-service:8088/actuator/health"
```

Then initiate a payment (Postman collection, *Initiate payment (intrabank)*) and follow it:

```powershell
docker compose logs payment-service  | Select-String "<correlationId>"   # initiate, screen, post
docker compose logs risk-aml-service | Select-String "<correlationId>"   # the screening decision
docker compose logs ledger-service   | Select-String "<correlationId>"   # the journal entry
docker compose logs payment-service  | Select-String "Relayed"           # outbox -> Kafka
```

Specific to this service:

- **Screening is not bypassable.** If risk-aml-service is down the payment fails rather than being
  allowed. A 500 on `/payments` with risk-aml stopped is correct behaviour, not a bug.
- **A `HOLD` decision rejects the payment with 422.** Amounts at or above 10000 trigger
  `LARGE_AMOUNT`; use a smaller amount for a happy-path test.
- **Events are asynchronous.** Nothing appears on `payment.events` until the relay polls, up to
  `outbox.relay.delay-ms` (5s). A `PENDING` row immediately after a payment is normal.

### card-service and interest-fee-service — one dependency each

Both call **ledger-service** over REST with the `ledger:post` scope. Same steps as account-service,
minus gRPC:

```powershell
docker compose up -d postgres ledger-service card-service
docker compose exec card-service sh -c "curl -s -o /dev/null -w '%{http_code}' http://ledger-service:8083/actuator/health"
```

card-service settlement needs `CARD-SETTLEMENT` in the ledger; interest-fee needs `FEE-INCOME` and
`INTEREST-EXPENSE`. The seed script creates all three.

### notification-service — a consumer, not a caller

It calls nobody. It **consumes** `payment.events`, so it cannot be tested by calling it — you have
to make payment-service publish. There is no REST entry point for that path.

```powershell
docker compose up -d postgres kafka notification-service
docker compose up kafka-init

# Make a payment in payment-service, then:
docker compose logs notification-service | Select-String "Alerted"
docker compose exec postgres psql -U banking -d banking -c `
  "select topic, partition_id, record_offset, event_type from notification.processed_notifications order by processed_at desc limit 5;"
```

If nothing arrives, check consumer lag before suspecting the consumer:

```powershell
docker compose exec kafka /opt/kafka/bin/kafka-consumer-groups.sh `
  --bootstrap-server localhost:9092 --describe --group notification-service
```

### customer-service, admin-service, reporting-service — no dependencies

Each needs only PostgreSQL. Start it, seed if relevant, call it. Nothing to trace.

```powershell
docker compose up -d postgres customer-service
```

Note these three are **not** guarded by service auth: they receive only gateway traffic, and guarding
them needs gateway-side token issuance first (open in `docs/PENDING_TASKS.md`). Calls to them succeed
without a token even when auth is on elsewhere — that is current behaviour, not a misconfiguration.

---

## 6. Quick reference

```powershell
# Full platform
docker compose up -d ; docker compose up kafka-init

# Just what one service needs
docker compose up -d postgres ledger-service            # for account / card / interest-fee
docker compose up -d postgres ledger-service risk-aml-service kafka   # for payment

# Seed, idempotent
.\scripts\Seed-TestData.ps1 -Direct

# Is the dependency reachable from inside the caller?
docker compose exec <caller> sh -c "curl -s -o /dev/null -w '%{http_code}' http://<target>:<port>/actuator/health"

# Is the gRPC socket open?
docker compose exec account-service bash -c 'timeout 3 bash -c "</dev/tcp/ledger-service/9090" 2>/dev/null && echo open || echo closed'

# Follow one request everywhere
docker compose logs | Select-String "<correlationId>"

# What did the far side actually record?
docker compose exec postgres psql -U banking -d banking
```

| Service | Port | gRPC | Depends on | Guarded by service auth |
| --- | --- | --- | --- | --- |
| api-gateway | 8080 | - | routes to all | no |
| account-service | 8081 | client | ledger | no |
| customer-service | 8082 | - | none | no |
| ledger-service | 8083 | **server 9090** | none | **yes** |
| payment-service | 8084 | - | ledger, risk-aml | no |
| interest-fee-service | 8085 | - | ledger | no |
| card-service | 8086 | - | ledger | no |
| notification-service | 8087 | - | none (consumes Kafka) | no |
| risk-aml-service | 8088 | - | none | **yes** |
| admin-service | 8089 | - | none | no |
| reporting-service | 8090 | - | none | no |
