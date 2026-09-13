# Tech stack

## Backend

- Java 21
- Spring Boot 3.5.x
- REST API
- Gradle
- Base package `hr.kricco.contractor`, split by layer (as in the template project): `controller`, `service`, `repository`, `entity`, `dto`, `exception`, `config`, `security`
- Lombok

## Persistence

- Spring Data JPA
- PostgreSQL, running on a separate database server
- Flyway manages the schema: the migrations in `backend/src/main/resources/db/migration` are the source of truth, and the backend applies the missing ones on startup. `V1__initial_schema.sql` is the former schema.sql. Every schema change is a new versioned migration (`V2__short_description.sql`, ...) in the same change that needs it, and a migration is never edited once it has been applied anywhere (Flyway checks the checksums). Hibernate `ddl-auto` stays `none`.
- The backend reads the database connection settings from the standard Postgres environment variables `PGHOST`, `PGPORT`, `PGDATABASE` (default `localhost`, `5432`, `contractor`), `PGUSER` and `PGPASSWORD` (no default, not stored in git). Railway sets the same names for its Postgres service. The port comes from `PORT` (default 8080) and the public address for the document photo links from `APP_BASE_URL` (default `http://localhost:8080`). The JWT signing key comes from `APP_JWT_SECRET` (at least 32 bytes, no default). Tests use a fixed test-only key from the test application.properties
- Databases: `contractor` for development, `contractor_test` for automatic tests. Tests connect to `contractor_test` on localhost (fixed in the test config), with the same `PGUSER` / `PGPASSWORD`. No Docker / Testcontainers for tests
- The test schema is rebuilt on every test run: `CleanDatabaseBeforeTests` (test sources) replaces Flyway's startup step with `clean()` + `migrate()`, and only the test config sets `spring.flyway.clean-disabled=false`. So `contractor_test` always matches the migrations, and nobody touches it by hand. `PGUSER` must own the `public` schema in `contractor_test`. Everywhere else clean stays disabled, so the dev and Railway databases only ever get `migrate()`
- Demo data lives in `backend/src/main/resources/seed.sql`, separate from the migrations, and Flyway never runs it. It's applied to the dev database (and to the Railway demo database) by hand (`psql ... --single-transaction -v ON_ERROR_STOP=1 -f src/main/resources/seed.sql`, full command in the file). It empties all tables first (`TRUNCATE ... RESTART IDENTITY CASCADE`), so every run gives the same data and the same IDs, and it deletes anything entered by hand. Tests don't load it - `SeedDataTest` only checks that it still runs against the current schema

## Frontend

- Angular 19
- Responsive - must be fully usable on a phone-sized screen as well as desktop
- Croatian only: UI labels and printable documents are in Croatian (stakeholder document, PR11). Code, API field names and enum values stay in English - the UI shows the Croatian labels listed in `specs/domain-model.md`
- Code in `/frontend`. Starting point: the Angular app of the template project (`/home/mbusic/code/Obsolete/Contractor/frontend`), copied and amended to this API
- During development the Angular dev server proxies `/api` to the backend (`localhost:8080`). The frontend calls relative `/api/...` URLs, so it has one origin and the backend needs no CORS configuration. Photo URLs (`/api/files/...`) work as they are
- The router uses hash URLs (`/#/orders/42`, `withHashLocation()`). The browser never sends the part after `#`, so whatever serves the built app (the dev server now, Spring later) only gets `/` and needs no index.html fallback for deep links
- The frontend lets each role do exactly what the backend allows (the `@PreAuthorize` rules in rest-api.md). ADMIN: everything, including employees and branches. OFFICE: clients with their locations and client users; employees and branches read-only. A button for an action the role can't do isn't shown
- No frontend tests for now (Angular schematics with `skipTests`). Frontend screens are verified by hand against the running backend
- Screens: every role starts on its own page ("/" redirects). ADMIN and OFFICE: order list, new order, order detail (edit, servicer, status, costs, photos, notes), admin page (ADMIN: employees, branches, clients; OFFICE: clients, the rest read-only). SERVICER: home page, order list split into "Moji" and "Dostupni", order detail. CLIENT: portal order list, new order (with a new location), order detail (only drafts can be changed and submitted), locations
- API errors are shown in Croatian: the known backend messages and Bean Validation messages are translated in `core/errors.ts`, anything else gets a general text

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