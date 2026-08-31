# Developer Guidance — Customer Self-Service Verification (Email, Mobile, EID)

Status: **Plan / design guidance** (no code yet). Target: `customer-service`, reusing
`notification-service` OTP. Follows the existing hexagonal style (ports + adapters, config-switched
stubs).

## 1. Problem

When a customer self-registers (`POST /api/v1/customers`), we must prove they actually control the
email and mobile they entered, and that they are a real, identified person via their **EID**
(Emirates ID / national e-ID) — **before** the account becomes usable.

### Current state (verified against the code)
- `Customer` stores `email` and `phone` but has **no `email_verified` / `phone_verified` flags** and
  **no EID fields**.
- Onboarding runs KYC (stub) + screening (stub) + risk rating, then `resolveOnboarding()` sets
  `ACTIVE` / `PENDING` / `BLOCKED`. **No channel verification is required to become ACTIVE.**
- `notification-service` **already has OTP**: `POST /api/v1/notifications/otp` → `{challengeId}` and
  `POST /api/v1/notifications/otp/verify {challengeId, code}` → `{verified}` (hashed, single-use,
  expiring, attempt-limited). We reuse this — do **not** build a second OTP engine.
- There is **no EID verification** anywhere.

## 2. Goal

A self-service registration flow where the customer must:
1. **Verify email** (OTP or magic link),
2. **Verify mobile** (OTP — reuse notification-service),
3. **Verify EID** (Emirates ID / national e-ID) — real identity,

and only then does onboarding proceed to KYC/screening and activation. Each step is auditable and
idempotent.

## 3. Design principle

Keep `customer-service` orchestrating; put every external call behind a **port** so it is
stub-first and swappable (exactly like `KycVerificationPort` / `ScreeningPort` today):

```
customer-service (orchestrator + registration state machine)
  ├─ OtpVerificationPort ─────► notification-service (existing OTP API)      # email + mobile
  └─ EidVerificationPort ─────► EID provider adapter (stub → UAE PASS / ICP) # identity
```

## 4. Recommended flow — a short-lived "registration session"

A one-shot `POST /customers` cannot hold multi-step verification. Introduce a **registration
session** the client drives step by step. The full `Customer` row is only created once all three
verifications pass.

```
1. POST /api/v1/registrations
     body: firstName,lastName,dob,nationality,email,phone,taxId,dataProcessingConsent
     → creates RegistrationSession (status=STARTED), returns registrationId
     → triggers email OTP + mobile OTP (via notification-service)

2. POST /api/v1/registrations/{id}/email/verify   { code }      → emailVerified=true
3. POST /api/v1/registrations/{id}/mobile/verify  { code }      → mobileVerified=true
   (POST .../email/resend and .../mobile/resend for re-send, rate-limited)

4. POST /api/v1/registrations/{id}/eid/verify
     body: eidNumber + (provider token / OCR data / UAE PASS auth code)
     → EidVerificationPort → eidVerified=true, eidReference stored (NO raw doc images)

5. POST /api/v1/registrations/{id}/complete
     → guard: emailVerified && mobileVerified && eidVerified   (else 409/422)
     → runs existing onboard(): KYC + screening + risk rating + resolveOnboarding()
     → creates the Customer, links eidReference, marks channels verified
     → returns the customer id
```

Sessions **expire** (e.g. 30 min) and are one-time. This keeps `POST /customers` (server-to-server /
admin path) working as-is, while adding the self-service path alongside it.

> Simpler alternative if you don't want a session: keep `POST /customers` creating a `PENDING`
> customer, then require the three verify calls on that customer id before it can go `ACTIVE`. The
> session approach is cleaner because no half-formed customer exists until verified.

## 5. Domain / data changes (`customer-service`)

**New entity `RegistrationSession`** (table `customer.registration_session`):
`id, organization_id, first_name, last_name, dob, nationality, email, phone, tax_id,
data_processing_consent, email_verified, mobile_verified, eid_verified, eid_reference,
email_challenge_id, mobile_challenge_id, status(STARTED|VERIFIED|COMPLETED|EXPIRED),
created_at, expires_at`.

**Extend `Customer`** (migration `V4__customer_verification.sql`):
`email_verified BOOLEAN NOT NULL DEFAULT FALSE`,
`phone_verified BOOLEAN NOT NULL DEFAULT FALSE`,
`eid_number VARCHAR(30)` (or store only a hash/reference — see §8),
`eid_verified BOOLEAN NOT NULL DEFAULT FALSE`,
`eid_reference VARCHAR(100)`.

Add an `EidStatus` if you want a lifecycle (`NOT_STARTED|VERIFIED|FAILED`).

## 6. Ports & adapters

**`OtpVerificationPort`** (new, in `application/port`)
```
UUID  requestOtp(String recipient, Channel channel);   // EMAIL or SMS
boolean verifyOtp(UUID challengeId, String code);
```
- `HttpOtpAdapter` → calls notification-service (`/otp`, `/otp/verify`). Stub adapter for tests.
- Config-switched: `otp.adapter=http|stub`, `notification.base-url=...` (mirror the existing
  ledger/screening wiring in this repo).

**`EidVerificationPort`** (new)
```
EidResult verify(EidRequest request);   // eidNumber + provider token/OCR/auth-code
record EidResult(boolean verified, String reference, String fullNameOnEid, LocalDate dobOnEid) {}
```
- **`StubEidVerificationAdapter`** first (deterministic: e.g. EID ending in `0000` → fail, else pass)
  so the flow is testable end-to-end — same pattern as `StubKycVerificationAdapter`.
- Real adapter later (`eid.adapter=uaepass|icp|stub`):
  - **UAE PASS** — federated OAuth2/OIDC login; the customer authenticates with UAE PASS and you
    receive verified identity claims (recommended for self-service; no card reader needed).
  - **ICP / Emirates ID validation gateway** — validate the EID number + card data (OCR/NFC read on
    the client) against the government service.
- **Match check**: after EID verify, compare `fullNameOnEid` / `dobOnEid` against what the customer
  entered; mismatch ⇒ fail (fraud signal).

## 7. Reusing notification-service (email + mobile) — concrete

- **Mobile**: `requestOtp(phone, SMS)` → notification-service `POST /otp` returns `challengeId`;
  store it on the session; `verifyOtp(challengeId, code)`.
- **Email**: two options —
  1. **OTP by email** — same `POST /otp` with `channel=EMAIL` (already supported). Simplest; reuse
     as-is.
  2. **Magic link** — notification-service sends a signed, expiring URL; clicking it hits a
     `customer-service` callback that marks `emailVerified`. Better UX, more work. Start with OTP.

## 8. Security & abuse controls (must-have)

- **OTP**: notification-service already hashes codes, expires them, single-use, attempt-limited —
  keep those. Add **resend cooldown** (e.g. 60s) and a **max resends per session**.
- **Rate limit** the registration + verify endpoints at the gateway (per IP / per email / per phone).
- **EID data**: prefer storing only an **`eid_reference` + hash**, not the raw EID number or any card
  images. Never log EID numbers or OTP codes (mask in logs). This is PII — treat like PAN.
- **No enumeration**: verify responses shouldn't reveal whether an email/phone is already registered.
- **Idempotency**: each verify step is idempotent; completing twice returns the same customer.
- **Audit**: write `change_log` entries for session start, each verification, and completion (the
  change-log infra already exists in `customer-service`).

## 9. State machine (registration → customer)

```
STARTED ──(email+mobile+eid verified)──► VERIFIED ──(complete → onboard)──► COMPLETED
   │                                                                          │
   └────────────────── expires_at passed ──────────────► EXPIRED             creates Customer:
                                                                              email_verified=true,
                                                                              phone_verified=true,
                                                                              eid_verified=true,
                                                                              then KYC/screening/risk
```
`complete` is rejected unless all three flags are true. `resolveOnboarding()` stays the final
authority for `ACTIVE/PENDING/BLOCKED` (KYC/screening still apply on top of channel+EID verification).

## 10. Delivery phases

- **Phase 1 (buildable now, all stubs)**: `RegistrationSession` + endpoints + `OtpVerificationPort`
  (HTTP → notification-service, real) + `EidVerificationPort` (**stub**) + migrations + tests. This
  gives the full self-service flow working locally end-to-end.
- **Phase 2**: real EID adapter (UAE PASS or ICP) behind the port; name/DOB match; secrets in
  `.env`/Vault; gateway rate-limits.
- **Phase 3**: magic-link email option; resend/cooldown polish; fraud signals (device, velocity).

## 11. Testing

- Unit: session state machine (can't complete without all 3 flags; expiry).
- Integration (Testcontainers, existing pattern): start session → verify email/mobile via a **fake
  OtpVerificationPort** → verify EID via stub → complete → assert `Customer` created with
  `email_verified/phone_verified/eid_verified = true`.
- Negative: wrong OTP, expired session, EID mismatch, complete-before-verified → correct 4xx.

## 12. API summary (new, `customer-service`)

| Method | Path | Purpose |
|--------|------|---------|
| POST | `/api/v1/registrations` | start session, trigger email+mobile OTP |
| POST | `/api/v1/registrations/{id}/email/verify` | verify email OTP |
| POST | `/api/v1/registrations/{id}/email/resend` | resend email OTP (cooldown) |
| POST | `/api/v1/registrations/{id}/mobile/verify` | verify mobile OTP |
| POST | `/api/v1/registrations/{id}/mobile/resend` | resend mobile OTP (cooldown) |
| POST | `/api/v1/registrations/{id}/eid/verify` | verify EID via provider/stub |
| GET  | `/api/v1/registrations/{id}` | session status (which steps done) |
| POST | `/api/v1/registrations/{id}/complete` | finalise → create Customer |

Existing `POST /api/v1/customers` stays for internal/admin creation.
