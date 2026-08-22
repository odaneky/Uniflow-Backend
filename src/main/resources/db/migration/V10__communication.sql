-- Announcements and direct messaging.

CREATE TABLE announcements (
    id              UUID          PRIMARY KEY,
    title           VARCHAR(200)  NOT NULL,
    body            VARCHAR(4000) NOT NULL,
    audience        VARCHAR(30)   NOT NULL,
    audience_ref_id UUID,
    author_user_id  UUID          NOT NULL,
    published_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ   NOT NULL,
    updated_at      TIMESTAMPTZ   NOT NULL,
    created_by      VARCHAR(100),
    updated_by      VARCHAR(100),
    CONSTRAINT fk_announcements_author FOREIGN KEY (author_user_id) REFERENCES users (id),
    -- University-wide announcements have no scope id; every other audience must name one.
    CONSTRAINT ck_announcements_audience_ref CHECK (
        (audience = 'UNIVERSITY_WIDE' AND audience_ref_id IS NULL)
        OR (audience <> 'UNIVERSITY_WIDE' AND audience_ref_id IS NOT NULL)
    )
);
CREATE INDEX idx_announcements_audience  ON announcements (audience, audience_ref_id);
CREATE INDEX idx_announcements_published ON announcements (published_at);

CREATE TABLE conversations (
    id                 UUID         PRIMARY KEY,
    subject            VARCHAR(200) NOT NULL,
    created_by_user_id UUID         NOT NULL,
    course_section_id  UUID,
    created_at         TIMESTAMPTZ  NOT NULL,
    updated_at         TIMESTAMPTZ  NOT NULL,
    created_by         VARCHAR(100),
    updated_by         VARCHAR(100),
    CONSTRAINT fk_conversations_creator FOREIGN KEY (created_by_user_id) REFERENCES users (id),
    CONSTRAINT fk_conversations_section FOREIGN KEY (course_section_id)  REFERENCES course_sections (id) ON DELETE SET NULL
);

CREATE TABLE conversation_participants (
    id              UUID        PRIMARY KEY,
    conversation_id UUID        NOT NULL,
    user_id         UUID        NOT NULL,
    last_read_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL,
    created_by      VARCHAR(100),
    updated_by      VARCHAR(100),
    CONSTRAINT uk_conversation_participants UNIQUE (conversation_id, user_id),
    CONSTRAINT fk_conversation_participants_conversation FOREIGN KEY (conversation_id) REFERENCES conversations (id) ON DELETE CASCADE,
    CONSTRAINT fk_conversation_participants_user         FOREIGN KEY (user_id)         REFERENCES users (id)
);
CREATE INDEX idx_conversation_participants_user ON conversation_participants (user_id);

CREATE TABLE messages (
    id              UUID          PRIMARY KEY,
    conversation_id UUID          NOT NULL,
    sender_user_id  UUID          NOT NULL,
    body            VARCHAR(4000) NOT NULL,
    sent_at         TIMESTAMPTZ   NOT NULL,
    created_at      TIMESTAMPTZ   NOT NULL,
    updated_at      TIMESTAMPTZ   NOT NULL,
    created_by      VARCHAR(100),
    updated_by      VARCHAR(100),
    CONSTRAINT fk_messages_conversation FOREIGN KEY (conversation_id) REFERENCES conversations (id) ON DELETE CASCADE,
    CONSTRAINT fk_messages_sender       FOREIGN KEY (sender_user_id)  REFERENCES users (id)
);
CREATE INDEX idx_messages_conversation_sent ON messages (conversation_id, sent_at);
