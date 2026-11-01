-- H2 mirror of db/migration/V7__currency_and_price_sources.sql (one change per statement)
ALTER TABLE trip_itineraries ADD COLUMN currency VARCHAR(3) DEFAULT 'INR' NOT NULL;
ALTER TABLE trip_itineraries ADD COLUMN fx_rate NUMERIC(18, 8);
ALTER TABLE trip_itineraries ADD COLUMN fx_as_of TIMESTAMP WITH TIME ZONE;

ALTER TABLE trip_itineraries ADD CONSTRAINT ck_trip_itineraries_currency
    CHECK (CHAR_LENGTH(currency) = 3 AND currency = UPPER(currency));
ALTER TABLE trip_itineraries ADD CONSTRAINT ck_trip_itineraries_fx_rate CHECK (fx_rate IS NULL OR fx_rate > 0);

ALTER TABLE trip_cost_items ADD COLUMN currency VARCHAR(3) DEFAULT 'INR' NOT NULL;
ALTER TABLE trip_cost_items ADD COLUMN stop_seq INT;
ALTER TABLE trip_cost_items ADD COLUMN leg_index INT;
ALTER TABLE trip_cost_items ADD COLUMN source_url VARCHAR(2000);

ALTER TABLE trip_cost_items DROP CONSTRAINT ck_trip_cost_items_source;

ALTER TABLE trip_cost_items ADD CONSTRAINT ck_trip_cost_items_source
    CHECK (source IN ('ESTIMATE', 'FARE_RULE', 'WEB_HINT'));
ALTER TABLE trip_cost_items ADD CONSTRAINT ck_trip_cost_items_currency
    CHECK (CHAR_LENGTH(currency) = 3 AND currency = UPPER(currency));
ALTER TABLE trip_cost_items ADD CONSTRAINT ck_trip_cost_items_stop_seq
    CHECK (stop_seq IS NULL OR stop_seq BETWEEN 1 AND 6);
ALTER TABLE trip_cost_items ADD CONSTRAINT ck_trip_cost_items_leg_index
    CHECK (leg_index IS NULL OR leg_index BETWEEN 0 AND 6);
