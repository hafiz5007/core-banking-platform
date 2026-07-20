-- Sprint 9: notifications and OTP challenges. Only a hash of each OTP code is stored.

CREATE TABLE notification (
    id                UUID         NOT NULL,
    recipient         VARCHAR(320) NOT NULL,
    notification_type VARCHAR(20)  NOT NULL,
    channel           VARCHAR(10)  NOT NULL,
    message           VARCHAR(500) NOT NULL,
    status            VARCHAR(10)  NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_notification PRIMARY KEY (id),
    CONSTRAINT chk_notification_type CHECK (notification_type IN
        ('TRANSACTION_ALERT','SECURITY_ALERT','OTP','STATEMENT')),
    CONSTRAINT chk_notification_channel CHECK (channel IN ('SMS','EMAIL','PUSH')),
    CONSTRAINT chk_notification_status CHECK (status IN ('PENDING','SENT','FAILED'))
);

CREATE TABLE otp_challenge (
    id         UUID         NOT NULL,
    recipient  VARCHAR(320) NOT NULL,
    code_hash  VARCHAR(64)  NOT NULL,
    consumed   BOOLEAN      NOT NULL DEFAULT FALSE,
    attempts   INTEGER      NOT NULL DEFAULT 0,
    expires_at TIMESTAMPTZ  NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_otp_challenge PRIMARY KEY (id)
);
