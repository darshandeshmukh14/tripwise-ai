-- H2 mirror of db/migration/V3__trip_planning_inputs.sql (JSON instead of JSONB)
ALTER TABLE trips ADD COLUMN origin VARCHAR(255);
ALTER TABLE trips ADD COLUMN comfort_level VARCHAR(20) DEFAULT 'STANDARD' NOT NULL;
ALTER TABLE trips ADD COLUMN transport_modes JSON DEFAULT JSON '[]' NOT NULL;
ALTER TABLE trips ADD COLUMN currency VARCHAR(3) DEFAULT 'INR' NOT NULL;

ALTER TABLE trips ADD CONSTRAINT ck_trips_comfort_level
    CHECK (comfort_level IN ('BUDGET', 'STANDARD', 'PREMIUM', 'LUXURY'));
ALTER TABLE trips ADD CONSTRAINT ck_trips_currency
    CHECK (CHAR_LENGTH(currency) = 3 AND currency = UPPER(currency));
