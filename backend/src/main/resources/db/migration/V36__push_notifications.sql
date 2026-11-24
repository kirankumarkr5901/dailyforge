-- Web Push: one row per browser/phone that allowed notifications. The keys are the
-- browser's own (RFC 8291); the server encrypts every message for them.
CREATE TABLE push_subscription (
    id          UUID          NOT NULL,
    user_id     UUID          NOT NULL,
    endpoint    VARCHAR(1000) NOT NULL,
    p256dh      VARCHAR(200)  NOT NULL,
    auth        VARCHAR(100)  NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_push_subscription PRIMARY KEY (id),
    CONSTRAINT fk_push_subscription_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT uq_push_subscription_endpoint UNIQUE (endpoint)
);

CREATE INDEX ix_push_subscription_user ON push_subscription (user_id);

-- The daily reminder goes out at most once per user per day, across restarts.
CREATE TABLE reminder_sent (
    user_id  UUID NOT NULL,
    sent_on  DATE NOT NULL,
    CONSTRAINT pk_reminder_sent PRIMARY KEY (user_id, sent_on),
    CONSTRAINT fk_reminder_sent_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE
);
