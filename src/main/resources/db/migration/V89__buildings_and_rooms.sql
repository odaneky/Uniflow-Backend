-- G1: room clash detection already matched "Lab 3" and "Lab-3" as the same room (V72's
-- normalization fix), but nothing recorded how many seats that room actually has — a section could
-- be scheduled into a room smaller than its own enrollment cap and nothing would catch it. This is
-- the registry that makes a capacity check possible; section_meetings.room stays free text, since
-- not every meeting's room will necessarily be registered here from day one.
CREATE TABLE buildings (
    id          UUID          PRIMARY KEY,
    code        VARCHAR(20)   NOT NULL,
    name        VARCHAR(200)  NOT NULL,
    created_at  TIMESTAMPTZ   NOT NULL,
    updated_at  TIMESTAMPTZ   NOT NULL,
    created_by  VARCHAR(100),
    updated_by  VARCHAR(100),
    CONSTRAINT uk_buildings_code UNIQUE (code)
);

CREATE TABLE rooms (
    id               UUID          PRIMARY KEY,
    building_id      UUID          NOT NULL REFERENCES buildings (id),
    code             VARCHAR(50)   NOT NULL,
    normalized_code  VARCHAR(50)   NOT NULL,
    capacity         INTEGER       NOT NULL,
    created_at       TIMESTAMPTZ   NOT NULL,
    updated_at       TIMESTAMPTZ   NOT NULL,
    created_by       VARCHAR(100),
    updated_by       VARCHAR(100),
    CONSTRAINT uk_rooms_normalized_code UNIQUE (normalized_code),
    CONSTRAINT ck_rooms_capacity CHECK (capacity > 0)
);
CREATE INDEX idx_rooms_building ON rooms (building_id);
