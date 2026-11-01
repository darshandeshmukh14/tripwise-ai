-- Itemised cost estimate per itinerary version. Relational (not JSONB) because it is summed and filtered.
CREATE TABLE trip_cost_items (
    id                BIGSERIAL      PRIMARY KEY,
    trip_id           BIGINT         NOT NULL,
    itinerary_version INT            NOT NULL,
    category          VARCHAR(30)    NOT NULL,
    mode              VARCHAR(20),
    label             VARCHAR(255)   NOT NULL,
    amount_min        NUMERIC(12, 2) NOT NULL,
    amount_expected   NUMERIC(12, 2) NOT NULL,
    amount_max        NUMERIC(12, 2) NOT NULL,
    season            VARCHAR(10)    NOT NULL,
    source            VARCHAR(20)    NOT NULL DEFAULT 'ESTIMATE',
    confidence        VARCHAR(10)    NOT NULL,
    assumptions       TEXT,
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_trip_cost_items_trip
        FOREIGN KEY (trip_id) REFERENCES trips (id) ON DELETE CASCADE,
    CONSTRAINT ck_trip_cost_items_version CHECK (itinerary_version >= 1),
    CONSTRAINT ck_trip_cost_items_category
        CHECK (category IN ('TRANSPORT', 'STAY', 'FOOD', 'ACTIVITY', 'LOCAL_TRANSPORT', 'MISC')),
    CONSTRAINT ck_trip_cost_items_mode CHECK (mode IS NULL OR mode IN ('TRAIN', 'FLIGHT', 'BUS', 'CAR')),
    CONSTRAINT ck_trip_cost_items_amounts
        CHECK (amount_min >= 0 AND amount_min <= amount_expected AND amount_expected <= amount_max),
    CONSTRAINT ck_trip_cost_items_season CHECK (season IN ('PEAK', 'SHOULDER', 'OFF')),
    CONSTRAINT ck_trip_cost_items_source CHECK (source IN ('ESTIMATE')),
    CONSTRAINT ck_trip_cost_items_confidence CHECK (confidence IN ('LOW', 'MEDIUM', 'HIGH'))
);

CREATE INDEX idx_trip_cost_items_trip_version ON trip_cost_items (trip_id, itinerary_version);
