-- trips.preferences holds a JSON array of strings; V1 defaulted it to an object.
ALTER TABLE trips ALTER COLUMN preferences SET DEFAULT '[]'::jsonb;

UPDATE trips SET preferences = '[]'::jsonb WHERE preferences = '{}'::jsonb;
