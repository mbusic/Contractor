-- Runs before schema.sql in tests only: drops every table so schema.sql starts from an empty database.
DROP SCHEMA public CASCADE;
CREATE SCHEMA public;
