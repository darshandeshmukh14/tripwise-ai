-- Currency, FX snapshot and price provenance for itineraries and cost items (amounts are in the itinerary currency).
ALTER TABLE trip_itineraries
    ADD COLUMN currency VARCHAR(3)     NOT NULL DEFAULT 'INR',
    ADD COLUMN fx_rate  NUMERIC(18, 8),
    ADD COLUMN fx_as_of TIMESTAMPTZ;

ALTER TABLE trip_itineraries
    ADD CONSTRAINT ck_trip_itineraries_currency CHECK (char_length(currency) = 3 AND currency = upper(currency)),
    ADD CONSTRAINT ck_trip_itineraries_fx_rate CHECK (fx_rate IS NULL OR fx_rate > 0);

ALTER TABLE trip_cost_items
    ADD COLUMN currency   VARCHAR(3)    NOT NULL DEFAULT 'INR',
    ADD COLUMN stop_seq   INT,
    ADD COLUMN leg_index  INT,
    ADD COLUMN source_url VARCHAR(2000);

ALTER TABLE trip_cost_items DROP CONSTRAINT ck_trip_cost_items_source;

ALTER TABLE trip_cost_items
    ADD CONSTRAINT ck_trip_cost_items_source CHECK (source IN ('ESTIMATE', 'FARE_RULE', 'WEB_HINT')),
    ADD CONSTRAINT ck_trip_cost_items_currency CHECK (char_length(currency) = 3 AND currency = upper(currency)),
    ADD CONSTRAINT ck_trip_cost_items_stop_seq CHECK (stop_seq IS NULL OR stop_seq BETWEEN 1 AND 6),
    ADD CONSTRAINT ck_trip_cost_items_leg_index CHECK (leg_index IS NULL OR leg_index BETWEEN 0 AND 6);
