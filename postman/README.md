# Postman collections

One collection per microservice, generated from the controllers and their request DTOs, so the
bodies match the real fields and their `@Size` / `@Pattern` constraints.

| Collection | Service | Default port | Requests |
| --- | --- | --- | --- |
| `account-service.postman_collection.json` | account-service | 8081 | 9 |
| `customer-service.postman_collection.json` | customer-service | 8082 | 9 |
| `ledger-service.postman_collection.json` | ledger-service | 8083 | 7 |
| `payment-service.postman_collection.json` | payment-service | 8084 | 17 |
| `interest-fee-service.postman_collection.json` | interest-fee-service | 8085 | 6 |
| `card-service.postman_collection.json` | card-service | 8086 | 9 |
| `notification-service.postman_collection.json` | notification-service | 8087 | 3 |
| `risk-aml-service.postman_collection.json` | risk-aml-service | 8088 | 6 |
| `admin-service.postman_collection.json` | admin-service | 8089 | 7 |
| `reporting-service.postman_collection.json` | reporting-service | 8090 | 2 |

Every REST endpoint in the platform is covered. `api-gateway` has no collection of its own — it
routes to these, so point a collection's `baseUrl` at the gateway to exercise the same request
through the edge.

## Environments

| File | Use it for |
| --- | --- |
| `local.postman_environment.json` | Services running directly on localhost ports |
| `gateway.postman_environment.json` | Everything through `api-gateway` on 8080 |
| `sandbox.postman_environment.json` | A deployed sandbox behind TLS and a real IdP |

Import the collections and one environment. Neither environment contains a secret — `accessToken`
and `clientSecret` are empty and marked secret, so nothing sensitive is committed.

## How they are wired

- **Chained requests.** Requests that create something capture the id into a collection variable
  (`accountId`, `paymentId`, `caseId`, `entryId`, …) in a test script, so the following requests
  work without copy-paste. Run a collection top to bottom with the Collection Runner.
- **`Authorization` is present but disabled** on every request. Enable it and set `accessToken` once
  `gateway.security.enabled=true`. Direct-to-service calls need it too when
  `service-auth.require-inbound=true` — that applies to **ledger-service** and **risk-aml-service**.
- **`X-Correlation-Id`** is set to `postman-{{$guid}}` on every request, so anything you send is
  greppable in the service logs. See `docs/DEBUG_TESTING.md`.
- **`X-Organization-Id`** carries the tenant. It defaults to `DEFAULT`; the sandbox environment uses
  `SANDBOX`.

## Order matters for some flows

A few requests need data that another service owns. The dependencies are real, not artefacts of the
collections:

1. **ledger-service** — run *Create ledger account (liability)* and *(settlement)* first. Payments,
   card settlement and interest postings all fail without ledger accounts to post against.
2. **account-service** — *Debit account* needs an account with funds or an overdraft, so run
   *Create product* then *Open account from product* (the product carries a 500 overdraft).
3. **payment-service** — *Pay by alias* needs *Register P2P alias* first; *Pay a bill* needs
   *Register biller*.
4. **notification-service** — *Verify OTP* needs the code, which is never returned by the API. Take
   it from the service log; only its hash is stored.

## Regenerating

The collections were generated from the controller signatures. If you add or change an endpoint,
update the matching collection by hand — there is no build step wiring them together, and a stale
collection is worse than none. Cross-check with:

```bash
# every @GetMapping/@PostMapping in the codebase
grep -rhoE '@(Get|Post|Put|Delete)Mapping' */src/main/java --include=*Controller.java | wc -l
```

Each service also serves live OpenAPI at `/v3/api-docs` and Swagger UI at `/swagger-ui.html`, which
is the authoritative surface if the two ever disagree.
