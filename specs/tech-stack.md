# Tech stack

## Backend

- Java 21
- Spring Boot 3.5.x
- REST API
- Gradle

## Persistence

- Spring Data JPA
- PostgreSQL, running on a separate database server
- Flyway for database migrations - all schema changes go through versioned migrations
- The backend reads the database connection settings from application.properties

## Frontend

- Angular 19
- Responsive - must be fully usable on a phone-sized screen as well as desktop

## Photo storage

- Backend local filesystem (a ./uploads folder), served via a REST endpoint
- No cloud storage

## Document generation

- Printable HTML for each document
- PDF through the browser's print to PDF
- No PDF library

## Repository layout

- A single monorepo with /backend and /frontend folders
- A top-level README.md explaining how to run both

## Version notes

- Spring Boot stays on 3.5.x. Spring Initializr generated 4.1.1, and we downgraded it on purpose.
- Angular is pinned to 19. Angular 22 needs Node 24.15 or newer, and the dev machine runs Node 24.2.0. We decided to keep Node as it is and use Angular 19.
- Create the frontend with `npx @angular/cli@19 new`, not plain `ng new` - the globally installed CLI is 20.x.