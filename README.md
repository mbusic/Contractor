# Contractor

Order management for Kricco: the office takes repair and maintenance orders, servicers do the work on site,
and clients follow their orders in a portal. Proof of concept.

- `backend/` - Spring Boot 3.5 REST API (Java 21, PostgreSQL)
- `frontend/` - Angular 19 app, Croatian UI
- `specs/` - what the app does and why: `mission.md`, `roadmap.md`, `tech-stack.md`, `domain-model.md`,
  `rest-api.md`, `services.md`

## Prerequisites

- Java 21 (Gradle comes with the wrapper, `./gradlew`)
- PostgreSQL (tested with 17)
- Node.js with npm (tested with Node 24; Angular 19 officially supports 18.19+, 20 and 22)
- `psql` for applying the schema and the demo data

## Setup

### 1. Databases

Two databases on the same server, both owned by one user:

- `contractor` - development
- `contractor_test` - automatic tests. The tests drop and rebuild its `public` schema on every run,
  so the user must own that schema.

```bash
createdb -h localhost -U <user> contractor
createdb -h localhost -U <user> contractor_test
```

### 2. Environment variables

The backend reads these from the environment (they're not stored in git):

| Variable | Meaning |
|----------|---------|
| `PGUSER` | Database user |
| `PGPASSWORD` | Its password |
| `APP_JWT_SECRET` | Key that signs the login tokens, at least 32 bytes, e.g. `openssl rand -base64 48`. The backend doesn't start without it |
| `PGHOST`, `PGPORT`, `PGDATABASE` | Optional, default to `localhost`, `5432`, `contractor` |

```bash
export PGUSER=contractor
export PGPASSWORD=...
export APP_JWT_SECRET=$(openssl rand -base64 48)
```

A new `APP_JWT_SECRET` logs everyone out, since the old tokens stop working.

### 3. Schema and demo data (dev database)

There are no migrations yet: `schema.sql` is the whole schema and is applied by hand. `seed.sql` fills in demo data.
It empties all tables first, so it can be run again at any time to get back to a known state (it also deletes
everything entered by hand).

```bash
cd backend
psql -h localhost -U "$PGUSER" -d contractor -v ON_ERROR_STOP=1 -f src/main/resources/schema.sql
psql -h localhost -U "$PGUSER" -d contractor --single-transaction -v ON_ERROR_STOP=1 -f src/main/resources/seed.sql
```

The test database needs nothing: the tests build it from `schema.sql` themselves.

## Running

Backend (http://localhost:8080):

```bash
cd backend
./gradlew bootRun
```

Frontend (http://localhost:4200):

```bash
cd frontend
npm ci
npx ng serve
```

Open http://localhost:4200. The Angular dev server forwards `/api` to the backend (`frontend/proxy.conf.json`),
so there's no CORS setup. Uploaded photos go to `backend/uploads/`.

### Demo users

The password is the same as the username.

| User | Role | Starts on |
|------|------|-----------|
| `admin` | Administrator | Orders; admin page with employees, branches and clients |
| `office` | Dispečer (office, branch Zagreb) | Orders; clients with their locations and portal users |
| `servicer` | Serviser (Ivan Horvat, branch Zagreb) | Servicer home: own orders and orders to accept |
| `client` | Klijent (portal user of Petar Perić d.o.o.) | Client portal: own orders |

## Tests

```bash
cd backend
./gradlew test
```

Runs against `contractor_test` and needs the same environment variables (`PGUSER`, `PGPASSWORD`; the JWT key
for tests is fixed in the test configuration). The frontend has no automatic tests for now
(`specs/tech-stack.md`); `npx ng build` in `frontend/` checks that it compiles.

## End-to-end check

The one flow that defines "it works". It goes through every role and the main parts of the app.
Start from fresh demo data (step 3 of the setup), with the backend and the frontend running.

| # | Who | Do | Expected |
|---|-----|----|----------|
| 1 | - | Open http://localhost:4200 | The login page ("Prijava") |
| 2 | office | Log in as `office` / `office` | The order list "Nalozi" with 6 orders |
| 3 | office | "Dodaj nalog +". Poslovnica: Kricco Zagreb, Klijent: Petar Perić d.o.o., Lokacija: A.G. Matoša 42, Zagreb 10000, Hitnost: 1 tjedan, Opis naloga: "Popravak ulaznih vrata". Click "Kreiraj" | The order opens with status "Na čekanju", order number **006/26**, contact person Petar Perić (taken from the client) |
| 4 | office | In "Serviser", pick Ivan Horvat, click "Dodijeli servisera" | Status "U tijeku", servicer Ivan Horvat |
| 5 | servicer | "Odjava", log in as `servicer` / `servicer`, tile "Moji nalozi" | 006/26 is in the list |
| 6 | servicer | Open 006/26, "Unesi stvarne troškove": radni sati 3, broj radnika 2, kilometri 25, materijal 40. "Spremi troškove" | Stvarno: Ukupno sati 6, Kilometri 25, Materijal 40 |
| 7 | servicer | "Promjena statusa": Riješen, "Promijeni status" | Status "Riješen" |
| 8 | client | "Odjava", log in as `client` / `client` | "Moji nalozi" shows 006/26 as "Riješen" |
| 9 | client | Open 006/26, "Ispis dokumenata" -> "Račun" | A new tab "RAČUN – 006/26" with Ukupno sati 6.00, Kilometri 25 km, Materijal 40.00 EUR |

Afterwards, run `seed.sql` again to get back to the demo data.

## Before go-live

Not part of the POC (see `specs/roadmap.md`):

- Flyway instead of the hand-applied `schema.sql` (roadmap step 11)
- A production setup for the frontend (a built app served next to the API) and `app.base-url` for the photo links
  in the documents
- The time sheet, the legal invoice data (VAT, OIB, invoice number), and editable document templates
  (`specs/domain-model.md`, "Deferred" and DocumentType)
