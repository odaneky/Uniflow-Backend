-- Per-user notification channel preferences (defaults: enabled when no row exists).

CREATE TABLE notification_preferences (
    id                 UUID PRIMARY KEY,
    user_id            UUID         NOT NULL,
    notification_type  VARCHAR(30)  NOT NULL,
    channel            VARCHAR(20)  NOT NULL,
    enabled            BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by         VARCHAR(100),
    updated_by         VARCHAR(100),
    CONSTRAINT fk_notification_preferences_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX uk_notification_preferences_user_type_channel
    ON notification_preferences (user_id, notification_type, channel);

CREATE INDEX idx_notification_preferences_user ON notification_preferences (user_id);
