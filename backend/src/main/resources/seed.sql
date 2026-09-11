-- Demo data for the dev database. The app never runs this file.
-- Apply it by hand after schema.sql (from the backend folder):
--   psql -h localhost -U contractor -d contractor --single-transaction -v ON_ERROR_STOP=1 -f src/main/resources/seed.sql
-- It empties all tables first, so every run gives the same data and the same IDs (Zagreb = 1).
-- When schema.sql gets a new table, add the table to the TRUNCATE list and add its rows below.
-- No BEGIN/COMMIT here: psql --single-transaction does that, and SeedDataTest runs this file inside its own transaction.

TRUNCATE TABLE branches RESTART IDENTITY CASCADE;

INSERT INTO branches (name, city) VALUES
    ('Kricco Zagreb', 'Zagreb'),
    ('Kricco Split',  'Split'),
    ('Kricco Zadar',  'Zadar'),
    ('Kricco Osijek', 'Osijek'),
    ('Kricco Pula',   'Pula');
