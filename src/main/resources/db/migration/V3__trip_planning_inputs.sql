-- Inputs needed by the AI planner: where the trip starts, comfort tier, allowed transport modes, currency.
-- origin stays nullable for trips created before this migration; the API requires it for new trips.
ALTER TABLE trips
    ADD COLUMN origin          VARCHAR(255),
    ADD COLUMN comfort_level   VARCHAR(20) NOT NULL DEFAULT 'STANDARD',
    ADD COLUMN transport_modes JSONB       NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN currency        VARCHAR(3)  NOT NULL DEFAULT 'INR';

ALTER TABLE trips
    ADD CONSTRAINT ck_trips_comfort_level CHECK (comfort_level IN ('BUDGET', 'STANDARD', 'PREMIUM', 'LUXURY')),
    ADD CONSTRAINT ck_trips_currency CHECK (char_length(currency) = 3 AND currency = upper(currency));
