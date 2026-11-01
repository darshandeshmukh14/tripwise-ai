-- =====================================================================
-- TripWise AI - Initial schema
-- =====================================================================

-- Automatically maintain updated_at on UPDATE
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- ---------------------------------------------------------------------
-- users
-- ---------------------------------------------------------------------
CREATE TABLE users (
    id            BIGSERIAL    PRIMARY KEY,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    full_name     VARCHAR(150) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_users_email UNIQUE (email)
);

CREATE TRIGGER trg_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ---------------------------------------------------------------------
-- trips
-- ---------------------------------------------------------------------
CREATE TABLE trips (
    id               BIGSERIAL      PRIMARY KEY,
    user_id          BIGINT         NOT NULL,
    destination      VARCHAR(255)   NOT NULL,
    start_date       DATE           NOT NULL,
    end_date         DATE           NOT NULL,
    travellers_count INT            NOT NULL DEFAULT 1,
    budget_amount    NUMERIC(12, 2),
    travel_style     VARCHAR(50),
    status           VARCHAR(30)    NOT NULL DEFAULT 'DRAFT',
    preferences      JSONB          NOT NULL DEFAULT '{}'::jsonb,
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_trips_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_trips_dates CHECK (end_date >= start_date),
    CONSTRAINT ck_trips_travellers_count CHECK (travellers_count > 0),
    CONSTRAINT ck_trips_budget_amount CHECK (budget_amount IS NULL OR budget_amount >= 0)
);

CREATE INDEX idx_trips_user_id ON trips (user_id);

CREATE TRIGGER trg_trips_updated_at
    BEFORE UPDATE ON trips
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ---------------------------------------------------------------------
-- trip_itineraries (one itinerary per trip)
-- ---------------------------------------------------------------------
CREATE TABLE trip_itineraries (
    id                   BIGSERIAL      PRIMARY KEY,
    trip_id              BIGINT         NOT NULL,
    version              INT            NOT NULL DEFAULT 1,
    total_estimated_cost NUMERIC(12, 2),
    plan_data            JSONB          NOT NULL,
    created_at           TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_trip_itineraries_trip UNIQUE (trip_id),
    CONSTRAINT fk_trip_itineraries_trip
        FOREIGN KEY (trip_id) REFERENCES trips (id) ON DELETE CASCADE,
    CONSTRAINT ck_trip_itineraries_version CHECK (version >= 1),
    CONSTRAINT ck_trip_itineraries_cost CHECK (total_estimated_cost IS NULL OR total_estimated_cost >= 0)
);

CREATE TRIGGER trg_trip_itineraries_updated_at
    BEFORE UPDATE ON trip_itineraries
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ---------------------------------------------------------------------
-- chat_messages
-- ---------------------------------------------------------------------
CREATE TABLE chat_messages (
    id          BIGSERIAL   PRIMARY KEY,
    trip_id     BIGINT      NOT NULL,
    sender_role VARCHAR(20) NOT NULL,
    content     TEXT        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_chat_messages_trip
        FOREIGN KEY (trip_id) REFERENCES trips (id) ON DELETE CASCADE,
    CONSTRAINT ck_chat_messages_sender_role CHECK (sender_role IN ('USER', 'ASSISTANT', 'SYSTEM'))
);

CREATE INDEX idx_chat_messages_trip_created_at ON chat_messages (trip_id, created_at);
