
# Walkthrough: notification-service

## Purpose (2 sentences)
What business capability does it own?
Notification-service owns customer alerts, one-time passcodes, and statement-style outbound delivery over customer channels. It records the notification state locally, keeps OTP codes hashed and short-lived, and logs each change in an append-only audit trail.

## Public API surface
- `POST /api/v1/notifications/alerts` send a customer alert
- `POST /api/v1/notifications/otp` issue a one-time passcode challenge
- `POST /api/v1/notifications/otp/verify` verify a one-time passcode
- Events published: none
- Events consumed: none

## Data model (1 paragraph)
The service owns three main tables: `notification`, `otp_challenge`, and `change_log`. `notification` stores the recipient, notification type, channel, message, and delivery status; `otp_challenge` stores only a hashed code plus expiry, attempt count, and consumed state; and `change_log` stores append-only audit rows scoped by organization and correlation id. These tables are not shared because delivery state, OTP security controls, and notification audit history must remain local to the service and independent of other bounded contexts.

## Key design decisions (3-5)
For each:
- Decision: Store only a hash of the OTP code.
- Why: The service never persists the raw passcode, so a database leak cannot reveal active verification codes.
- Alternative considered: Storing the OTP in plaintext for easier verification.
- Trade-off accepted: Verification needs hashing logic, but the security posture is much better.

- Decision: Make OTP challenges single-use with expiry and attempt limits.
- Why: A challenge should only be valid for a short window and should lock out after repeated failures.
- Alternative considered: Allowing unlimited retries until the code expires.
- Trade-off accepted: More state tracking, but less risk of brute-force reuse.

- Decision: Dispatch through a sender port with a logging adapter.
- Why: The service can swap a real SMS/email/push provider later while local development remains deterministic.
- Alternative considered: Calling a real delivery gateway directly from the application layer.
- Trade-off accepted: One more abstraction, but cleaner transport replacement.

- Decision: Keep notifications and OTPs as separate domain concepts.
- Why: An OTP is a special-purpose challenge, while alerts and statements are ordinary outbound messages.
- Alternative considered: Modeling every outbound message as the same entity.
- Trade-off accepted: Two related flows to maintain, but clearer business rules and security controls.

- Decision: Record a local change log for notification events.
- Why: It provides a traceable audit trail of what was sent, to whom, and under which tenant.
- Alternative considered: Relying only on application logs.
- Trade-off accepted: Extra storage, but much better operational traceability.

## Where the interesting code lives
- Domain logic: notification-service/src/main/java/com/bank/notification/domain/Notification.java, notification-service/src/main/java/com/bank/notification/domain/OtpChallenge.java, notification-service/src/main/java/com/bank/notification/domain/ChangeLog.java
- Adapters: notification-service/src/main/java/com/bank/notification/adapter/in/web/NotificationController.java, notification-service/src/main/java/com/bank/notification/adapter/out/sender/LoggingSenderAdapter.java, notification-service/src/main/java/com/bank/notification/adapter/out/persistence/*
- Configuration: notification-service/src/main/java/com/bank/notification/NotificationServiceApplication.java, notification-service/src/main/java/com/bank/notification/config/OpenApiConfig.java

## Interview flashcards
- Q: "Why did you hash the OTP instead of storing it directly?" → A: It prevents the database from becoming a source of active verification codes, which is a simple but important security improvement.
- Q: "How would you scale this to 10x?" → A: I would keep the same API contract but swap the logging sender for real gateways and move bulk delivery to async infrastructure if message volume grew sharply.
- Q: "What would you change with hindsight?" → A: I would likely split delivery templates and channel routing out if the notification matrix expanded much beyond the current alert and OTP cases.

## Follow-ups
- Add templating and localization if customer-facing copy needs to vary by language or channel.
- Replace the logging sender with real SMS/email/push adapters.
- Add metrics around OTP issuance, verification success, and delivery failures.
```