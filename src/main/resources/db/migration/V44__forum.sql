CREATE TABLE forum_topics (
    id                 UUID PRIMARY KEY,
    course_section_id  UUID NOT NULL,
    title              VARCHAR(200) NOT NULL,
    author_user_id     UUID NOT NULL,
    pinned             BOOLEAN NOT NULL DEFAULT FALSE,
    locked             BOOLEAN NOT NULL DEFAULT FALSE,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by         VARCHAR(100),
    updated_by         VARCHAR(100),
    CONSTRAINT fk_forum_topics_section FOREIGN KEY (course_section_id) REFERENCES course_sections (id),
    CONSTRAINT fk_forum_topics_author FOREIGN KEY (author_user_id) REFERENCES users (id)
);

CREATE INDEX idx_forum_topics_section ON forum_topics (course_section_id, pinned DESC, updated_at DESC);

CREATE TABLE forum_posts (
    id                  UUID PRIMARY KEY,
    topic_id            UUID NOT NULL,
    parent_post_id      UUID,
    author_user_id      UUID NOT NULL,
    body                VARCHAR(4000) NOT NULL,
    sent_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at          TIMESTAMPTZ,
    deleted_by_user_id  UUID,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by          VARCHAR(100),
    updated_by          VARCHAR(100),
    CONSTRAINT fk_forum_posts_topic FOREIGN KEY (topic_id) REFERENCES forum_topics (id),
    CONSTRAINT fk_forum_posts_parent FOREIGN KEY (parent_post_id) REFERENCES forum_posts (id),
    CONSTRAINT fk_forum_posts_author FOREIGN KEY (author_user_id) REFERENCES users (id)
);

CREATE INDEX idx_forum_posts_topic_sent ON forum_posts (topic_id, sent_at ASC, id ASC);
