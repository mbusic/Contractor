-- Demo data for the dev database. The app never runs this file.
-- Apply it by hand after schema.sql (from the backend folder):
--   psql -h localhost -U contractor -d contractor --single-transaction -v ON_ERROR_STOP=1 -f src/main/resources/seed.sql
-- It empties all tables first, so every run gives the same data and the same IDs (Zagreb = 1).
-- No photos: their rows would point to files that don't exist in app.upload-dir.
-- When schema.sql gets a new table, add the table to the TRUNCATE list and add its rows below.
-- status_transitions is not in the list: its rows are reference data from schema.sql.
-- No BEGIN/COMMIT here: psql --single-transaction does that, and SeedDataTest runs this file inside its own transaction.

TRUNCATE TABLE branches, clients, locations, users, order_sequences, orders, order_notes, order_photos RESTART IDENTITY CASCADE;

INSERT INTO branches (name, city) VALUES
    ('Kricco Zagreb', 'Zagreb'),
    ('Kricco Split',  'Split'),
    ('Kricco Zadar',  'Zadar'),
    ('Kricco Osijek', 'Osijek'),
    ('Kricco Pula',   'Pula');

INSERT INTO clients (type, name, contact_person, phone, email, address) VALUES
    ('COMPANY',    'Petar Perić d.o.o.', 'Petar Perić', '097 587 6210', 'petar.peric@example.hr', NULL),
    ('INDIVIDUAL', 'Ana Anić',           'Ana Anić',    '091 234 5678', 'ana.anic@example.hr',    'Ilica 10, Zagreb 10000');

-- Work sites. Each seed order uses one of them.
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

-- A client user of Petar Perić d.o.o. (client 1), password "client"
INSERT INTO users (username, password, role, display_name, client_id) VALUES
    ('client', '$2a$10$NU8q/YXIZUtATnpEtwcGNu1v6Rh5.Fq/jJzHZpsgwUcoRBSfMCt0y', 'CLIENT', 'Petar Perić', 1);

-- The template's five orders. Branches: 1 Zagreb, 2 Split, 3 Zadar, 4 Osijek, 5 Pula. User 3 = servicer.
-- The counter matches the numbers below, so the next submitted order in 2026 gets 006/26.
INSERT INTO order_sequences (seq_year, last_sequence) VALUES (2026, 5);

-- 001/26 is done: estimated and actual costs filled in
INSERT INTO orders (order_number, branch_id, client_id, location_id, contact_person, phone, email, description,
                    urgency, status, assigned_servicer_id,
                    estimated_km, estimated_work_hours, estimated_number_of_workers, estimated_material_cost,
                    actual_km, actual_work_hours, actual_number_of_workers, actual_material_cost,
                    created_at, updated_at) VALUES
    ('001/26', 1, 1, 1, 'Petar Perić', '097 587 6210', 'petar.peric@example.hr', 'Oštećena keramika na ulazu',
     'ONE_DAY', 'RESOLVED', 3,
     80, 8.00, 3, 50.00,
     76, 8.00, 3, 42.00,
     '2026-09-01 08:00:00+00', '2026-09-03 15:00:00+00');

INSERT INTO orders (order_number, branch_id, client_id, location_id, contact_person, phone, description,
                    urgency, status, assigned_servicer_id, created_at, updated_at) VALUES
    ('002/26', 2, 1, 2, 'Petar Perić', '097 587 6210', 'Kvar na instalaciji',
     'ONE_WEEK',   'IN_PROGRESS', 3,    '2026-09-02 08:00:00+00', '2026-09-02 10:00:00+00'),
    ('003/26', 3, 2, 4, 'Ana Anić',    '091 234 5678', 'Popravak klima uređaja',
     'ONE_MONTH',  'PENDING',     NULL, '2026-09-03 08:00:00+00', '2026-09-03 08:00:00+00'),
    ('004/26', 4, 1, 3, 'Petar Perić', '097 587 6210', 'Hitna intervencija - curenje vode',
     'SAME_DAY',   'PENDING',     NULL, '2026-09-04 08:00:00+00', '2026-09-04 08:00:00+00'),
    ('005/26', 5, 2, 5, 'Ana Anić',    '091 234 5678', 'Redovno održavanje sustava grijanja',
     'SIX_MONTHS', 'PENDING',     NULL, '2026-09-05 08:00:00+00', '2026-09-05 08:00:00+00');

-- Notes on the orders the servicer works on. User 2 = office, user 3 = servicer.
INSERT INTO order_notes (order_id, author_id, text, created_at) VALUES
    (1, 3, 'Zamijenjeno 12 pločica, fuge zapunjene.',              '2026-09-03 14:30:00+00'),
    (1, 2, 'Klijent potvrdio da je sve u redu.',                   '2026-09-03 16:00:00+00'),
    (2, 3, 'Potreban dodatni materijal, dolazim ponovno sutra.',   '2026-09-02 10:00:00+00');
