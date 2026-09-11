# Roadmap

## Working rules

- Work in small, verifiable steps.
- After each step: project compiles, runs, and existing tests pass.
- Write at least one test per slice (unit or integration). Tests are the primary verification signal, not just "it compiles".
- After each step, stop and wait for confirmation before starting the next. Summarize what was done and how to verify it.
- Never leave the project in a non-compiling state between steps.

## Decisions to lock upfront

- Build tool, language versions, base package structure.
- Database schema. During development there are no migrations: schema.sql holds the whole schema, gets updated in the same change that needs it, and is applied to the database by hand. Before go-live we switch to Flyway, turn schema.sql into the initial migration (`V1__initial_schema.sql`), and from then on every schema change is a new versioned migration that is never edited after it has been applied.
- API contract style (REST conventions, error format, pagination). Document it once, reuse everywhere. Documented in `specs/rest-api.md` ("Conventions").

## Build order

0. **Project scaffold.** Build tool, dependencies, package structure, DB connection, empty schema.sql in place, one health-check endpoint. Must compile and run.
1. **First vertical slice (reference pattern).** Branch - a simple domain entity that User needs in step 3. Take it fully end-to-end: schema.sql tables, entity, repository, service, REST endpoint, one test. This becomes the pattern agents copy for everything else.
2. **Seed data.** A separate seed script with realistic data, so every later step can be tested against real rows. Keep it out of schema.sql.
3. **Auth entities + schema.** `users` table added to schema.sql. The role is a string column holding the Role enum (ADMIN, OFFICE, SERVICER, CLIENT) - no role or permission tables (see `specs/domain-model.md`). User entity + repository + tests. This step covers employees only (ADMIN, OFFICE, SERVICER, with a FK to Branch). Client users need the Client entity, so they are added in step 6, together with the Client slice.
4. **Auth mechanism.** Filter chain, login endpoint, password handling, token issuing. Test login success/failure.
5. **Authorisation.** Wire roles to endpoint access (`hasRole` / `@PreAuthorize`). Row-level rules (e.g. a servicer sees only their own and unassigned orders) are checked in the services. Test protected endpoint with/without rights.
6. **Remaining domain, slice by slice.** For each feature: schema.sql tables, entity, repository, service (implemented), REST endpoint, test. No empty stubs - each slice ships working.

   Order features by dependency (e.g. order before time sheet). List them out before starting.
7. **Frontend scaffold.** Angular app, routing, auth interceptor, API client wired to the documented contract. Login screen end-to-end against the real backend.
8. **Screens, one at a time.** For each: dashboard, order detail, create order, servicer screens, time sheet, admin CRUD. Each screen wired to its endpoint and manually verifiable before moving on.
9. **Document views.** DocumentService builds the HTML page for the 4 document types from the current order data (see `specs/services.md`). Document buttons on the order detail (office) and in the client portal open it in a new tab. PDF through the browser's print. Nothing is stored. Test: each type returns HTML with the order number, values are HTML-escaped, and another client's order is refused.
10. **Final pass.** README (setup, run, seed, test commands). Run the primary end-to-end flow: log in, create an order, view it, confirm the expected result. Define this exact flow so "it works" is unambiguous.
11. **Switch to Flyway.** Only when go-live gets close, so past the POC. Add the Flyway dependency, move schema.sql to `src/main/resources/db/migration/V1__initial_schema.sql`, and let Flyway create its history table against an empty database. Verify by running the app on a fresh database and checking that the schema matches what the app expects. From this point schema.sql is gone and every schema change is a new versioned migration.