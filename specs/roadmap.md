# Roadmap

## Working rules

- Work in small, verifiable steps.
- After each step: project compiles, runs, and existing tests pass.
- Write at least one test per slice (unit or integration). Tests are the primary verification signal, not just "it compiles".
- After each step, stop and wait for confirmation before starting the next. Summarize what was done and how to verify it.
- Never leave the project in a non-compiling state between steps.

## Decisions to lock upfront

- Build tool, language versions, base package structure.
- Flyway for DB migrations - all schema changes go through migrations from step 0. Naming: `V1__create_users.sql`, `V2__add_roles.sql`. Never edit an applied migration; always add a new one.
- API contract style (REST conventions, error format, pagination). Document it once, reuse everywhere.

## Build order

0. **Project scaffold.** Build tool, dependencies, package structure, DB connection, migration tooling wired up, one health-check endpoint. Must compile and run.
1. **First vertical slice (reference pattern).** Pick one simple domain entity. Take it fully end-to-end: migration, entity, repository, service, REST endpoint, one test. This becomes the pattern agents copy for everything else.
2. **Seed data.** Migration or seed script with realistic data, so every later step can be tested against real rows.
3. **Auth entities + schema.** User, role, permission via migrations. Entities + repositories + tests.
4. **Auth mechanism.** Filter chain, login endpoint, password handling, token issuing. Test login success/failure.
5. **Authorisation.** Wire roles/permissions to endpoint access. Test protected endpoint with/without rights.
6. **Remaining domain, slice by slice.** For each feature: migration, entity, repository, service (implemented), REST endpoint, test. No empty stubs - each slice ships working.

   Order features by dependency (e.g. order before time sheet). List them out before starting.
7. **Frontend scaffold.** Angular app, routing, auth interceptor, API client wired to the documented contract. Login screen end-to-end against the real backend.
8. **Screens, one at a time.** For each: dashboard, order detail, create order, servicer screens, time sheet, admin CRUD. Each screen wired to its endpoint and manually verifiable before moving on.
9. **Document views.**
10. **Final pass.** README (setup, run, seed, test commands). Run the primary end-to-end flow: log in, create an order, view it, confirm the expected result. Define this exact flow so "it works" is unambiguous.