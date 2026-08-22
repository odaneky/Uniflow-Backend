CREATE TABLE announcement_reads (
    announcement_id UUID NOT NULL,
    user_id         UUID NOT NULL,
    read_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (announcement_id, user_id),
    CONSTRAINT fk_announcement_reads_announcement FOREIGN KEY (announcement_id) REFERENCES announcements (id),
    CONSTRAINT fk_announcement_reads_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX idx_announcement_reads_user ON announcement_reads (user_id);
