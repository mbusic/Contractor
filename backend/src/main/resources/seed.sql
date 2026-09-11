-- Demo data for the dev database. The app never runs this file.
-- Apply it by hand after schema.sql (from the backend folder):
--   psql -h localhost -U contractor -d contractor --single-transaction -v ON_ERROR_STOP=1 -f src/main/resources/seed.sql
-- It empties all tables first, so every run gives the same data and the same IDs (Zagreb = 1).
-- When schema.sql gets a new table, add the table to the TRUNCATE list and add its rows below.
-- No BEGIN/COMMIT here: psql --single-transaction does that, and SeedDataTest runs this file inside its own transaction.

TRUNCATE TABLE branches, clients, locations, users RESTART IDENTITY CASCADE;

INSERT INTO branches (name, city) VALUES
    ('Kricco Zagreb', 'Zagreb'),
    ('Kricco Split',  'Split'),
    ('Kricco Zadar',  'Zadar'),
    ('Kricco Osijek', 'Osijek'),
    ('Kricco Pula',   'Pula');

INSERT INTO clients (type, name, contact_person, phone, email, address) VALUES
    ('COMPANY',    'Petar Perić d.o.o.', 'Petar Perić', '097 587 6210', 'petar.peric@example.hr', NULL),
    ('INDIVIDUAL', 'Ana Anić',           'Ana Anić',    '091 234 5678', 'ana.anic@example.hr',    'Ilica 10, Zagreb 10000');

-- Work sites. Each seed order (added with the Orders slice) uses one of them.
INSERT INTO locations (client_id, address, city) VALUES
    (1, 'A.G. Matoša 42',          'Zagreb 10000'),
    (1, 'Vukovarska 18',           'Split 21000'),
    (1, 'Europska avenija 2',      'Osijek 31000'),
    (2, 'Ulica Stjepana Radića 5', 'Zadar 23000'),
    (2, 'Flanatička 14',           'Pula 52100');

-- One user per employee role. The password is the username (admin/admin, ...), stored as a BCrypt hash. Dev only.
INSERT INTO users (username, password, role, display_name, branch_id) VALUES
    ('admin',    '$2a$10$zKhVW0We3.8GpTfwzgIZ6.dpr7qmjhyKJpDI/SqdIbenIzlK4pDru', 'ADMIN',    'Administrator', NULL),
    ('office',   '$2a$10$fg7k3VG2VtZKeC2AWlkc0.kfjR6Fqvzb1svk8XuNLQlxZwCB5YrRm', 'OFFICE',   'Dispečer',      1),
    ('servicer', '$2a$10$NBlyEpBIiHhszCFvqifLBO41WEXfgXw7kqBl7tuaKNXLWBThv.8za', 'SERVICER', 'Ivan Horvat',   1);
