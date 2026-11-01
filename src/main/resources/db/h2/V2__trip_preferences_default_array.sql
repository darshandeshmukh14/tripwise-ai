-- H2 mirror of db/migration/V2__trip_preferences_default_array.sql
ALTER TABLE trips ALTER COLUMN preferences SET DEFAULT JSON '[]';

UPDATE trips SET preferences = JSON '[]' WHERE preferences = JSON '{}';
