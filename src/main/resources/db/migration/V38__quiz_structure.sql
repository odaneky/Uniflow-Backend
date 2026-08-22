-- Quiz structure and per-attempt answers for QUIZ/EXAM assessments.

CREATE TABLE quiz_questions (
    id             UUID          PRIMARY KEY,
    assessment_id  UUID          NOT NULL,
    position       INTEGER       NOT NULL,
    prompt         VARCHAR(4000) NOT NULL,
    question_type  VARCHAR(30)   NOT NULL,
    points         NUMERIC(7,2)  NOT NULL,
    scoring_mode   VARCHAR(30),
    required       BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ   NOT NULL,
    updated_at     TIMESTAMPTZ   NOT NULL,
    created_by     VARCHAR(100),
    updated_by     VARCHAR(100),
    CONSTRAINT fk_quiz_questions_assessment FOREIGN KEY (assessment_id) REFERENCES assessments (id) ON DELETE CASCADE,
    CONSTRAINT ck_quiz_questions_points CHECK (points > 0),
    CONSTRAINT ck_quiz_questions_type CHECK (question_type IN (
        'SHORT_ANSWER', 'MULTIPLE_CHOICE', 'MULTI_SELECT', 'FILE_UPLOAD')),
    CONSTRAINT ck_quiz_questions_scoring CHECK (
        scoring_mode IS NULL OR scoring_mode IN ('ALL_OR_NOTHING', 'PARTIAL'))
);
CREATE INDEX idx_quiz_questions_assessment ON quiz_questions (assessment_id);

CREATE TABLE quiz_options (
    id           UUID          PRIMARY KEY,
    question_id  UUID          NOT NULL,
    position     INTEGER       NOT NULL,
    label        VARCHAR(1000) NOT NULL,
    is_correct   BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMPTZ   NOT NULL,
    updated_at   TIMESTAMPTZ   NOT NULL,
    created_by   VARCHAR(100),
    updated_by   VARCHAR(100),
    CONSTRAINT fk_quiz_options_question FOREIGN KEY (question_id) REFERENCES quiz_questions (id) ON DELETE CASCADE
);
CREATE INDEX idx_quiz_options_question ON quiz_options (question_id);

CREATE TABLE quiz_answers (
    id              UUID          PRIMARY KEY,
    attempt_id      UUID          NOT NULL,
    question_id     UUID          NOT NULL,
    text_response   VARCHAR(8000),
    document_id     UUID,
    auto_score      NUMERIC(7,2),
    manual_score    NUMERIC(7,2),
    feedback        VARCHAR(2000),
    created_at      TIMESTAMPTZ   NOT NULL,
    updated_at      TIMESTAMPTZ   NOT NULL,
    created_by      VARCHAR(100),
    updated_by      VARCHAR(100),
    CONSTRAINT uk_quiz_answers_attempt_question UNIQUE (attempt_id, question_id),
    CONSTRAINT fk_quiz_answers_attempt FOREIGN KEY (attempt_id) REFERENCES assessment_attempts (id) ON DELETE CASCADE,
    CONSTRAINT fk_quiz_answers_question FOREIGN KEY (question_id) REFERENCES quiz_questions (id) ON DELETE CASCADE,
    CONSTRAINT ck_quiz_answers_auto CHECK (auto_score IS NULL OR auto_score >= 0),
    CONSTRAINT ck_quiz_answers_manual CHECK (manual_score IS NULL OR manual_score >= 0)
);
CREATE INDEX idx_quiz_answers_attempt ON quiz_answers (attempt_id);
CREATE INDEX idx_quiz_answers_question ON quiz_answers (question_id);

CREATE TABLE quiz_answer_options (
    answer_id UUID NOT NULL,
    option_id UUID NOT NULL,
    PRIMARY KEY (answer_id, option_id),
    CONSTRAINT fk_quiz_answer_options_answer FOREIGN KEY (answer_id) REFERENCES quiz_answers (id) ON DELETE CASCADE,
    CONSTRAINT fk_quiz_answer_options_option FOREIGN KEY (option_id) REFERENCES quiz_options (id) ON DELETE CASCADE
);
