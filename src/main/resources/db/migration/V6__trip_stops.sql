-- Multi-city trips: ordered stops (max 6). Single-destination trips have no rows; trips.destination stays the
-- first stop for backward compatibility.
CREATE TABLE trip_stops (
    id           BIGSERIAL    PRIMARY KEY,
    trip_id      BIGINT       NOT NULL,
    seq          INT          NOT NULL,
    city         VARCHAR(255) NOT NULL,
    country_code VARCHAR(2)   NOT NULL DEFAULT 'IN',
    nights       INT          NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_trip_stops_trip FOREIGN KEY (trip_id) REFERENCES trips (id) ON DELETE CASCADE,
    CONSTRAINT uq_trip_stops_trip_seq UNIQUE (trip_id, seq),
    CONSTRAINT ck_trip_stops_seq CHECK (seq BETWEEN 1 AND 6),
    CONSTRAINT ck_trip_stops_nights CHECK (nights >= 1),
    CONSTRAINT ck_trip_stops_country CHECK (char_length(country_code) = 2 AND country_code = upper(country_code))
);
