-- Demo data for the dev database. The app never runs this file.
-- Apply it by hand after schema.sql (from the backend folder):
--   psql -h localhost -U contractor -d contractor --single-transaction -v ON_ERROR_STOP=1 -f src/main/resources/seed.sql
-- It empties all tables first, so every run gives the same data and the same IDs (Zagreb = 1).
-- When schema.sql gets a new table, add the table to the TRUNCATE list and add its rows below.
-- No BEGIN/COMMIT here: psql --single-transaction does that, and SeedDataTest runs this file inside its own transaction.

TRUNCATE TABLE branches, users RESTART IDENTITY CASCADE;

INSERT INTO branches (name, city) VALUES
    ('Kricco Zagreb', 'Zagreb'),
    ('Kricco Split',  'Split'),
    ('Kricco Zadar',  'Zadar'),
    ('Kricco Osijek', 'Osijek'),
    ('Kricco Pula',   'Pula');

-- One user per employee role. The password is the username (admin/admin, ...), stored as a BCrypt hash. Dev only.
INSERT INTO users (username, password, role, display_name, branch_id) VALUES
    ('admin',    '$2a$10$zKhVW0We3.8GpTfwzgIZ6.dpr7qmjhyKJpDI/SqdIbenIzlK4pDru', 'ADMIN',    'Administrator', NULL),
    ('office',   '$2a$10$fg7k3VG2VtZKeC2AWlkc0.kfjR6Fqvzb1svk8XuNLQlxZwCB5YrRm', 'OFFICE',   'Dispečer',      1),
    ('servicer', '$2a$10$NBlyEpBIiHhszCFvqifLBO41WEXfgXw7kqBl7tuaKNXLWBThv.8za', 'SERVICER', 'Ivan Horvat',   1);
