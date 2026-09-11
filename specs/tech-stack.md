# Tech stack

## Backend

- Java 21
- Spring Boot 3.5.x
- REST API
- Gradle
- Base package `hr.kricco.contractor`, split by layer (as in the template project): `controller`, `service`, `repository`, `entity`, `dto`, `config`, `security`
- Lombok

## Persistence

- Spring Data JPA
- PostgreSQL, running on a separate database server
- During development, `backend/src/main/resources/schema.sql` is the source of truth for the database schema. Whenever the schema changes, update schema.sql in the same change and apply it to the database by hand (psql or the IDE). Spring does not run it on startup and Hibernate `ddl-auto` stays `none`.
- No Flyway during development. Before go-live we introduce Flyway, generate the initial migration from schema.sql, and from then on all schema changes go through versioned migrations.
- The backend reads the database connection settings from application.properties: `jdbc:postgresql://localhost:5432/contractor`, with the username and password taken from the environment variables `DB_USER` and `DB_PASSWORD` (not stored in git)
- Databases: `contractor` for development, `contractor_test` for automatic tests. Tests connect to `contractor_test` on the same server, with the same `DB_USER` / `DB_PASSWORD`. No Docker / Testcontainers for tests
- The test schema is rebuilt on every test run: the test config (`src/test/resources/application.properties`) sets `spring.sql.init.mode=always` and runs a reset script (`DROP SCHEMA public CASCADE; CREATE SCHEMA public;`) followed by schema.sql. So `contractor_test` always matches schema.sql, and nobody applies it by hand. `DB_USER` must own the `public` schema in `contractor_test`. This happens only in tests - the dev database is still updated by hand

## Frontend

- Angular 19
- Responsive - must be fully usable on a phone-sized screen as well as desktop
- Croatian only: UI labels and printable documents are in Croatian (stakeholder document, PR11). Code, API field names and enum values stay in English - the UI shows the Croatian labels listed in `specs/domain-model.md`

## Photo storage

- Backend local filesystem (a ./uploads folder), served via a REST endpoint
- No cloud storage

## Document generation

- Printable HTML for each document, built by the backend from the current order data every time it's opened (as in the template project)
- PDF through the browser's print to PDF
- No PDF library, and documents are not stored

## Repository layout

- A single monorepo with /backend and /frontend folders
- A top-level README.md explaining how to run both

## Version notes

- Spring Boot stays on 3.5.x. Spring Initializr generated 4.1.1, and we downgraded it on purpose.
- Angular is pinned to 19. Angular 22 needs Node 24.15 or newer, and the dev machine runs Node 24.2.0. We decided to keep Node as it is and use Angular 19.
- Create the frontend with `npx @angular/cli@19 new`, not plain `ng new` - the globally installed CLI is 20.x.