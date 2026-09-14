# Releasing to a test server in sandbox mode

How to put this platform on a server that is not your laptop, with every feature switched on, and
prove it works before anyone calls it an environment.

**Read this first:** "sandbox" here means *fully wired but with stub external integrations and
non-real money*. It is not a production posture. The gaps that stop it being one are listed in
[Before you call it production](#before-you-call-it-production) — they are real, and none of them is
a formality.

---

## 1. What "sandbox mode" actually means here

The platform has no Spring profiles. Every switch is an environment variable, and every one of them
defaults to **off**. A sandbox is therefore not a profile you activate — it is a `.env` in which the
switches are deliberately turned on:

| Switch | Local default | Sandbox | What turning it on does |
| --- | --- | --- | --- |
| `CBP_SERVICE_AUTH_ENABLED` | `false` | **`true`** | Services mint JWTs for internal calls |
| `CBP_SERVICE_AUTH_SECRET` | empty | **required** | Signing secret; under 32 bytes is refused at startup |
| `CBP_KAFKA_ENABLED` | `false` | **`true`** | Outbox relay publishes for real; notification-service consumes |
| `CBP_LEDGER_POSTING` | `grpc` | `grpc` | account-service posts debits over gRPC |
| `CBP_SCREENING_ADAPTER` | `risk-service` | `risk-service` | Payments are screened by risk-aml-service, not a stub |
| `CBP_GATEWAY_SECURITY_ENABLED` | `false` | **`true`** | The gateway requires a bearer JWT |
| `CBP_GATEWAY_JWT_ISSUER_URI` | empty | **required** | Your IdP |
| `CBP_POSTGRES_PASSWORD` | `banking` | **changed** | Stop using the demo credential |

With the first two unset the platform still starts and internal calls are unauthenticated — which is
exactly the trap. **Verify the switches took effect** (§5) rather than assuming.

---

## 2. Prerequisites on the server

- Docker Engine 24+ with Compose v2 (`docker compose version`).
- ~4 GB RAM free. Eleven JVMs, PostgreSQL and Kafka.
- Ports 8080–8090 (services), 5432 (Postgres), 9090 (ledger gRPC), 9092 (Kafka). Only **8080** should
  be reachable from outside the host — see §6.
- Outbound network for the first build (Maven Central, Docker Hub).

You do **not** need a JDK or Maven on the server: the Dockerfiles build inside a Maven image.

---

## 3. Prepare the release

```bash
git clone <repo> core-banking-platform && cd core-banking-platform
git checkout <release-tag>          # deploy a tag, never a moving branch
cp .env.example .env
```

On Windows, `scripts\Setup-Environment.ps1 -Profile Sandbox` does all of the below in one
step - it generates the secrets, writes the file, and restricts it to the current user with
`icacls`.

Generate the secrets **on the server** and keep them there:

```bash
{
  echo "CBP_SERVICE_AUTH_ENABLED=true"
  echo "CBP_SERVICE_AUTH_SECRET=$(openssl rand -base64 48)"
  echo "CBP_KAFKA_ENABLED=true"
  echo "CBP_POSTGRES_PASSWORD=$(openssl rand -base64 24)"
  echo "CBP_ORGANIZATION_DEFAULT=SANDBOX"
} >> .env
chmod 600 .env
```

`.env` is gitignored. It is still a plaintext secret on a disk — acceptable for a sandbox, not for
production (§8).

Edge auth needs an IdP. If you have one:

```bash
echo "CBP_GATEWAY_SECURITY_ENABLED=true" >> .env
echo "CBP_GATEWAY_JWT_ISSUER_URI=https://idp.example.internal/realms/bank" >> .env
```

If you do not have one yet, leave it `false` and treat the whole host as untrusted — the gateway will
permit every request.

---

## 4. Build and start

```bash
docker compose build          # ~5-10 min cold; each image builds the reactor
docker compose up -d
docker compose ps             # every service Up, kafka healthy, kafka-init Exited (0)
```

`kafka-init` **exiting 0 is success** — it is a one-shot that creates the four ADR-014 topics and stops. Topic auto-creation is deliberately disabled, so if it failed, publishing fails later with a
confusing "unknown topic" rather than at startup. Check it:

```bash
docker compose logs kafka-init          # expect: topics ready
docker compose exec kafka /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server localhost:9092 --list
```

Flyway migrates each schema on first start. Watch for it rather than assuming:

```bash
docker compose logs ledger-service | grep -i flyway
```

---

## 5. Prove the switches actually took effect

This is the part people skip. Each check fails loudly if the feature is off.

**Service auth is on** — an unauthenticated internal call must be refused:

```bash
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:8083/api/v1/ledger/trial-balance
# 401  → service auth is ON
# 200  → it is OFF. Your secret did not reach the container.
```

**Health stays open** (orchestrators must reach it without a token):

```bash
curl -s http://localhost:8083/actuator/health | jq .status     # UP
```

**Kafka is really publishing** — not the logging stub:

```bash
docker compose logs payment-service | grep -i "Kafka disabled"
# any output → CBP_KAFKA_ENABLED did not reach payment-service
docker compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 --topic payment.events --from-beginning --max-messages 5
```

**The gateway is enforcing auth**:

```bash
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:8080/api/v1/accounts/does-not-exist
# 401 → edge auth ON.  404/500 → OFF.
```

---

## 6. Lock the host down

The services trust each other on the network. `TenantContextFilter` reads `X-Organization-Id`
straight from the request, and only the gateway authenticates callers — so **anything that can reach
a service port directly can select a tenant and, with service auth off, act unauthenticated**.

Publish only the gateway. Everything else stays on the internal Docker network:

```bash
# firewall: allow 8080 only
sudo ufw allow 8080/tcp && sudo ufw deny 8081:8090/tcp
sudo ufw deny 5432/tcp && sudo ufw deny 9092/tcp && sudo ufw deny 9090/tcp
```

Better: delete the `ports:` block from every service except `api-gateway` in a compose override, so
they are never bound to the host at all.

```yaml
# docker-compose.override.yml
services:
  account-service:  { ports: [] }
  ledger-service:   { ports: [] }
  # ... and the rest
```

---

## 7. Smoke test the release

Run this end-to-end. It exercises the three things this release added — gRPC, service auth, Kafka —
and touches four services.

```bash
GW=http://localhost:8080
TOKEN=...                       # from your IdP if edge auth is on
AUTH=(-H "Authorization: Bearer $TOKEN")

# 1. Ledger accounts to post against
curl -s "${AUTH[@]}" -X POST $GW/api/v1/ledger/accounts -H 'Content-Type: application/json' \
  -d '{"code":"DEP-001","name":"Customer Deposits","accountType":"LIABILITY","currencyCode":"USD"}'
curl -s "${AUTH[@]}" -X POST $GW/api/v1/ledger/accounts -H 'Content-Type: application/json' \
  -d '{"code":"SETTLEMENT","name":"Settlement","accountType":"ASSET","currencyCode":"USD"}'

# 2. A product with an overdraft, and an account opened from it
curl -s "${AUTH[@]}" -X POST $GW/api/v1/products -H 'Content-Type: application/json' \
  -d '{"code":"CUR-STD","name":"Standard Current","accountType":"CURRENT","currencyCode":"USD",
       "interestRatePercent":"0.50","monthlyFee":"0.00","minBalance":"0.00",
       "dailyLimit":"5000.00","overdraftLimit":"500.00"}'
ACCT=$(curl -s "${AUTH[@]}" -X POST $GW/api/v1/accounts/from-product -H 'Content-Type: application/json' \
  -d '{"productCode":"CUR-STD","primaryCustomerId":"'$(uuidgen)'","additionalHolders":[],
       "mandateType":"SINGLE"}' | jq -r .id)

# 3. Debit it — this is account-service -> ledger-service over JWT-secured gRPC
curl -s "${AUTH[@]}" -X POST $GW/api/v1/accounts/$ACCT/debit -H 'Content-Type: application/json' \
  -d '{"idempotencyKey":"smoke-'$(uuidgen)'","amount":"25.00","currencyCode":"USD",
       "narrative":"Smoke test"}' | jq '{balance}'

# 4. A payment — screened by risk-aml, posted to the ledger, event onto Kafka
curl -s "${AUTH[@]}" -X POST $GW/api/v1/payments -H 'Content-Type: application/json' \
  -d '{"idempotencyKey":"smoke-'$(uuidgen)'","type":"INTRABANK","debtorAccount":"DEP-001",
       "creditorAccount":"SETTLEMENT","amount":"75.00","currencyCode":"USD",
       "narrative":"Smoke test"}' | jq '{id,status}'

# 5. The books must balance
curl -s "${AUTH[@]}" $GW/api/v1/ledger/trial-balance | jq .
```

**What must be true afterwards:**

- the debit returned a negative balance (`-25.00`) — gRPC posting worked;
- the payment reached `CONFIRMED` — screening and ledger posting worked;
- `balanced` is `true`;
- within ~5 s (the relay poll interval) a `payment.events` record exists **and**
  notification-service created an alert:

```bash
docker compose logs notification-service | grep "Alerted"
```

If the alert never appears, the outbox relay is the place to look — the row stays `PENDING` when the
broker does not acknowledge, by design:

```bash
docker compose exec postgres psql -U banking -d banking \
  -c "select status, count(*) from payment.outbox_event group by status;"
```

The Postman collections in `postman/` cover the same ground interactively — import
`sandbox.postman_environment.json` and set `gatewayUrl` and `accessToken`.

---

## 8. Before you call it production

Every item below is open in `docs/PENDING_TASKS.md`. None is cosmetic.

**Security**

- **Service tokens are HS256 with a shared secret.** Every holder can *mint* as well as verify, so a
  compromised receiver can impersonate a caller. ADR-008 specifies RS256 + JWKS; that swap is the
  single most important item here.
- **Only 6 of 11 services are wired for service auth.** customer, admin, notification, reporting and
  the gateway are not, because guarding them needs gateway-side token issuance first.
- **`X-Organization-Id` is trusted from the request** and falls back to `DEFAULT` when absent. The
  gateway must strip it and re-issue from the verified JWT `org` claim.
- **Secrets live in a plaintext `.env`.** Production needs Vault/KMS or Docker/Kubernetes secrets.
- **All internal transport is plaintext**, gRPC included, assuming a service mesh that does not exist
  yet.

**Correctness and operations**

- **Every external integration is a stub**: KYC, sanctions screening, clearing rails, SWIFT, FX, card
  processor, and the notification sender. Nothing leaves the platform.
- **Single-node everything.** One Postgres, one Kafka broker, replication factor 1. No backups, no
  failover, no DR drill.
- **`outbox_event` and `processed_notifications` grow without bound** — no retention job.
- **No dashboards or alerting.** Micrometer and the tracing bridge are on the classpath; nothing is
  exported to a collector.

**Process**

- Scheme certification, an independent penetration test, a load test to the NFR targets
  (≥1,000 TPS, p95 < 800 ms), a DR failover drill and compliance sign-off are all outstanding.

---

## 9. Upgrading a running sandbox

```bash
git fetch --tags && git checkout <new-tag>
docker compose build
docker compose up -d          # recreates only changed containers
```

Flyway applies new migrations at startup. Two cautions specific to this repo:

- **Migration checksums have been edited in place once** (the `CHAR` → `VARCHAR` fix). If your
  sandbox database predates that change, Flyway will refuse to start with a checksum mismatch. The
  fix for a disposable sandbox is to recreate the volume:
  `docker compose down -v && docker compose up -d`. **That destroys all data** — never do it on
  anything you care about.
- **Roll forward, not back.** There are no `undo` migrations. To go back a version, restore a
  database snapshot taken before the upgrade.
