-- Outbound notifications. Rows are written in the originating transaction and delivered
-- afterwards by a dispatcher, so no network call sits inside a database transaction.

CREATE TABLE notifications (
    id                UUID          PRIMARY KEY,
    recipient_user_id UUID          NOT NULL,
    notification_type VARCHAR(30)   NOT NULL,
    channel           VARCHAR(20)   NOT NULL,
    status            VARCHAR(20)   NOT NULL,
    title             VARCHAR(200)  NOT NULL,
    body              VARCHAR(2000) NOT NULL,
    sent_at           TIMESTAMPTZ,
    read_at           TIMESTAMPTZ,
    failure_reason    VARCHAR(500),
    created_at        TIMESTAMPTZ   NOT NULL,
    updated_at        TIMESTAMPTZ   NOT NULL,
    created_by        VARCHAR(100),
    updated_by        VARCHAR(100),
    CONSTRAINT fk_notifications_recipient FOREIGN KEY (recipient_user_id) REFERENCES users (id) ON DELETE CASCADE
);
CREATE INDEX idx_notifications_recipient ON notifications (recipient_user_id);
CREATE INDEX idx_notifications_status    ON notifications (status);
-- Partial index: the dispatcher only ever scans the pending backlog, which is a tiny fraction
-- of the table once the system has been running for a term.
CREATE INDEX idx_notifications_pending ON notifications (created_at) WHERE status = 'PENDING';
