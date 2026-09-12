# REST API

REST endpoints of the Contractor backend. Source: the controllers of the template project in `/home/mbusic/code/Obsolete/Contractor/backend`, adapted to `specs/domain-model.md`. Changes from the template are marked [A1], [A2], ... and listed in "Changes from the template". The business logic behind the endpoints is in `specs/services.md`.

## Conventions

- Base path `/api`. JSON in and out (UTF-8), except file upload (multipart), file download, and documents (`text/html`).
- IDs are `Long`. Enum values are sent as their English names (e.g. `IN_PROGRESS`). Timestamps are ISO-8601 in UTC (e.g. `2026-09-11T08:30:00Z`).
- Auth: `POST /api/auth/login` returns a JWT. Every other call (except the public ones below) sends `Authorization: Bearer <token>`. The token is valid for 24 hours. There's no refresh - the user logs in again.
- Public endpoints (no token): `POST /api/auth/login`, `GET /api/health`, `GET /api/files/{filename}`.
- POST creates, PUT replaces, PATCH changes one thing (status), DELETE removes.
- PUT is a full replace: the request carries all fields of the form, and a missing or null field means "empty". [A3] The one exception is the password on user updates: empty means "keep the current password".
- No pagination - lists return all rows. The POC has little data.
- Optimistic locking: [A13] every editable row (branch, client, location, user, order) has a `version`, and the DTOs return it. A request that changes an existing row sends back the version it read: all PUT bodies, StatusChangeRequest, AssignmentRequest, and the actual-costs body. No version on create, delete, accept (no body, it relies on `@Version` on the server), and adding notes or photos (they add rows, the order itself doesn't change). Portal submit has no body either - decided with the portal slice. Missing on an update -> 400 "Version is required". Different from the row's version (someone saved in between) -> 409 "Changed by someone else. Reload and try again." The response of a successful write carries the new version.
- Validation: request DTOs use Bean Validation (`@Valid`, `@NotBlank`, ...).
- Errors use the RFC 9457 Problem Details format, through Spring's built-in support (`spring.mvc.problemdetails.enabled=true`). Services throw exceptions from the `exception` package, and `ApiExceptionHandler` turns them into Problem Details with the message in the `detail` field (see services.md "Errors"). [A12] The one exception: a 401 for a missing or invalid token comes from the security filter and has an empty body.

  ```json
  { "type": "about:blank", "title": "Conflict", "status": 409, "detail": "Order is already taken or not pending", "instance": "/api/orders/7/accept" }
  ```

| Status | When                                                                         |
|--------|------------------------------------------------------------------------------|
| 200    | OK (GET, PUT, PATCH, POST actions like accept)                               |
| 201    | Created (POST that creates a row)                                            |
| 204    | Deleted                                                                      |
| 400    | Invalid request: validation failed, unknown ID in the body, wrong file type, missing version |
| 401    | No token, invalid or expired token, wrong username/password                  |
| 403    | Wrong role for the endpoint, or the row isn't yours (e.g. another client's order) |
| 404    | The row in the path doesn't exist                                            |
| 409    | State conflict: status change not allowed, IN_PROGRESS without a servicer, order already taken, order not in the right status, username taken, a branch/client/location still in use, stale version |

## Roles

Every endpoint checks the role through `@PreAuthorize` on its controller method (e.g. `hasAnyRole('ADMIN','OFFICE')`). [A1] A wrong role gets 403 with detail "Access denied". On top of that, the services check rows (e.g. a servicer only sees their own and unassigned orders).

- `PreAuthorizeCoverageTest` fails if an endpoint has no `@PreAuthorize`. Only AuthController and HealthController are left out, because they're public. Without the annotation, any logged-in user could call the endpoint, a CLIENT user included.
- Each new endpoint gets tests for an allowed and a refused role (see BranchControllerTest).

- "Employee" below means ADMIN, OFFICE or SERVICER.
- ADMIN has full access to every employee endpoint.
- CLIENT (client users) can only call `/api/portal/**`, and no employee endpoint allows CLIENT. [A2]

## Controllers

| Controller          | Path                                   | Roles                          |
|---------------------|----------------------------------------|--------------------------------|
| AuthController      | `/api/auth`                            | public                         |
| HealthController    | `/api/health`                          | public                         |
| BranchController    | `/api/branches`                        | employees read, ADMIN writes   |
| UserController      | `/api/users`                           | ADMIN, OFFICE (read only)      |
| ClientController    | `/api/clients`                         | ADMIN, OFFICE                  |
| OrderController     | `/api/orders`                          | employees (varies per endpoint) |
| FileController      | `/api/files`                           | public                         |
| PortalController    | `/api/portal`                          | CLIENT                         |

## Endpoints

### Auth and health

| Method | Path              | Roles  | Request      | Response      | Notes                                   |
|--------|-------------------|--------|--------------|---------------|-----------------------------------------|
| POST   | `/api/auth/login` | public | LoginRequest | LoginResponse | 401 on wrong username or password       |
| GET    | `/api/health`     | public | -            | `{"status":"UP"}` | For the step 0 scaffold check       |

### Branches

| Method | Path                  | Roles     | Request       | Response        | Notes                    |
|--------|-----------------------|-----------|---------------|-----------------|--------------------------|
| GET    | `/api/branches`       | employees | -             | List<BranchDto> |                          |
| GET    | `/api/branches/{id}`  | employees | -             | BranchDto       | [A11]                    |
| POST   | `/api/branches`       | ADMIN     | BranchRequest | BranchDto (201) |                          |
| PUT    | `/api/branches/{id}`  | ADMIN     | BranchRequest | BranchDto       |                          |
| DELETE | `/api/branches/{id}`  | ADMIN     | -             | 204             | 409 while users or orders point to it (domain-model Q3) |

### Employees

`/api/users` handles employee accounts only (ADMIN, OFFICE, SERVICER). Client-user accounts are under `/api/clients/{id}/users`. [A10]

| Method | Path               | Roles         | Request         | Response      | Notes                                           |
|--------|--------------------|---------------|-----------------|---------------|-------------------------------------------------|
| GET    | `/api/users`       | ADMIN, OFFICE | -               | List<UserDto> | Active and deactivated employees, sorted by displayName. Optional `?role=SERVICER` - the office uses it to pick a servicer (the UI shows only active ones). 400 for `role=CLIENT` |
| POST   | `/api/users`       | ADMIN         | EmployeeRequest | UserDto (201) | 409 if the username is taken                    |
| PUT    | `/api/users/{id}`  | ADMIN         | EmployeeRequest | UserDto       | Empty password = keep the current one. `active: true` reactivates. 409 if you remove your own ADMIN role or deactivate yourself. A servicer who is deactivated or gets another role releases their IN_PROGRESS orders (back to PENDING, unassigned) |
| DELETE | `/api/users/{id}`  | ADMIN         | -               | 204           | Deactivates the account, nothing is deleted (domain-model Q3). 409 for your own account. A deactivated servicer releases their IN_PROGRESS orders (back to PENDING, unassigned) |

- 404 "Employee not found" for an unknown ID or the ID of a client user.
- 400 for a role/branch mismatch, an unknown `branchId`, a missing password on create, or a password over 72 bytes.

### Clients, locations, client users

| Method | Path                                          | Roles         | Request           | Response        | Notes                          |
|--------|-----------------------------------------------|---------------|-------------------|-----------------|--------------------------------|
| GET    | `/api/clients`                                | ADMIN, OFFICE | -                 | List<ClientDto> | With locations. Sorted by name |
| GET    | `/api/clients/{id}`                           | ADMIN, OFFICE | -                 | ClientDto       |                                |
| POST   | `/api/clients`                                | ADMIN, OFFICE | ClientRequest     | ClientDto (201) |                                |
| PUT    | `/api/clients/{id}`                           | ADMIN, OFFICE | ClientRequest     | ClientDto       |                                |
| DELETE | `/api/clients/{id}`                           | ADMIN, OFFICE | -                 | 204             | Deletes its locations. 409 "Client has users" / "Client has orders" while client users or orders point to it (domain-model Q3) |
| POST   | `/api/clients/{id}/locations`                 | ADMIN, OFFICE | LocationRequest   | LocationDto (201) |                              |
| PUT    | `/api/clients/{id}/locations/{locationId}`    | ADMIN, OFFICE | LocationRequest   | LocationDto     | [A11]. 404 if the location belongs to another client |
| DELETE | `/api/clients/{id}/locations/{locationId}`    | ADMIN, OFFICE | -                 | 204             | 404 if the location belongs to another client. 409 "Location is used by orders" |
| GET    | `/api/clients/{id}/users`                     | ADMIN, OFFICE | -                 | List<UserDto>   | Client users of this client, sorted by displayName [A10] |
| POST   | `/api/clients/{id}/users`                     | ADMIN, OFFICE | ClientUserRequest | UserDto (201)   | Role is always CLIENT. 409 if the username is taken (by anyone) |
| PUT    | `/api/clients/{id}/users/{userId}`            | ADMIN, OFFICE | ClientUserRequest | UserDto         | Empty password = keep the current one |
| DELETE | `/api/clients/{id}/users/{userId}`            | ADMIN, OFFICE | -                 | 204             | Deletes the account for real (domain-model User) |

- 404 "Client user not found" for a user of another client or an employee ID, 404 "Client not found" for an unknown client.

### Orders

| Method | Path                                   | Roles                  | Request            | Response               | Notes |
|--------|----------------------------------------|------------------------|--------------------|------------------------|-------|
| GET    | `/api/orders`                          | employees              | -                  | List<OrderSummaryDto>  | ADMIN/OFFICE: all orders. SERVICER: orders assigned to them + all unassigned PENDING orders. Newest first |
| GET    | `/api/orders/{id}`                     | employees              | -                  | OrderDto               | Row check for SERVICER (403 "You can't see this order"). Includes `allowedNextStatuses` [A5] |
| POST   | `/api/orders`                          | ADMIN, OFFICE          | OrderRequest       | OrderDto (201)         | Creates a DRAFT without a number. Location by `locationId` [A4] |
| PUT    | `/api/orders/{id}`                     | ADMIN, OFFICE          | OrderRequest       | OrderDto               | Order data + estimated costs, in any status. 400 if a submitted order loses its client or location |
| DELETE | `/api/orders/{id}`                     | ADMIN, OFFICE          | -                  | 204                    | In any status. Also deletes the photo files |
| PATCH  | `/api/orders/{id}/status`              | employees              | StatusChangeRequest | OrderDto              | SERVICER: only on orders assigned to them (403). 409 if StatusTransition doesn't allow it, or IN_PROGRESS without a servicer. "Submit" of a draft = change to PENDING: 400 without client or location, takes the order number [A5]. A change to PENDING clears the servicer |
| POST   | `/api/orders/{id}/accept`              | SERVICER               | -                  | OrderDto               | Order must be PENDING and unassigned, else 409. Assigns it to the caller and moves it to IN_PROGRESS. When two servicers accept at once, the second also gets 409 [A6] |
| PUT    | `/api/orders/{id}/assignment`          | ADMIN, OFFICE          | AssignmentRequest  | OrderDto               | PENDING: assign + move to IN_PROGRESS. IN_PROGRESS: reassign. Other statuses: 409. 400 if the user is unknown, isn't a SERVICER, or is deactivated [A6] |
| PUT    | `/api/orders/{id}/actual-costs`        | employees              | ActualCostsRequest | OrderDto               | Full replace of the actual costs, in any status. SERVICER: only on orders assigned to them (403) [A7] |
| POST   | `/api/orders/{id}/notes`               | employees              | NoteRequest        | OrderDto (201)         | SERVICER: only on orders assigned to them |
| POST   | `/api/orders/{id}/photos`              | employees              | multipart `file`   | OrderDto (201)         | JPEG, PNG, GIF or WebP only. Max 6 per order (400). SERVICER: only on orders assigned to them |
| DELETE | `/api/orders/{id}/photos/{photoId}`    | employees              | -                  | 204                    | SERVICER: only on orders assigned to them [A9] |
| GET    | `/api/orders/{id}/documents/{type}`    | ADMIN, OFFICE          | -                  | HTML page (`text/html`) | `{type}` = DocumentType value (`QUOTE`, `WORK_ORDER`, `REPORT`, `INVOICE`). Built from the current order data, not stored [A8] |

- Documents: the frontend fetches the HTML with the JWT and opens it in a new tab as a blob, as in the template. The user prints it or saves it as PDF through the browser.
- The servicer's list is one endpoint. The UI splits it into "my orders" and "available" by `assignedServicerId`.

### Files

| Method | Path                     | Roles  | Response                         | Notes |
|--------|--------------------------|--------|----------------------------------|-------|
| GET    | `/api/files/{filename}`  | public | File bytes (`image/jpeg`, `image/png`) | Photos. 404 if missing |

- File names are random UUIDs (e.g. `3f2a...c9.jpg`), so a URL can't be guessed. A user gets the URL only through an authenticated endpoint (order detail, portal order detail), and after that it works without a token. This is needed because an `<img>` tag can't send the JWT header - on the order detail screen and in the document pages.

### Client portal

For client users (role CLIENT). Everything is limited to the user's own client. [A2]

| Method | Path                                          | Request            | Response                    | Notes |
|--------|-----------------------------------------------|--------------------|-----------------------------|-------|
| GET    | `/api/portal/orders`                          | -                  | List<PortalOrderSummaryDto> | All orders of the user's client, including drafts. Newest first |
| GET    | `/api/portal/orders/{id}`                     | -                  | PortalOrderDto              | 403 if it's another client's order |
| POST   | `/api/portal/orders`                          | PortalOrderRequest | PortalOrderDto (201)        | Creates a DRAFT for the user's client |
| PUT    | `/api/portal/orders/{id}`                     | PortalOrderRequest | PortalOrderDto              | Only while DRAFT, else 409 |
| POST   | `/api/portal/orders/{id}/submit`              | -                  | PortalOrderDto              | DRAFT -> PENDING, checks the required fields. 409 if not a DRAFT |
| POST   | `/api/portal/orders/{id}/photos`              | multipart `file`   | PortalOrderDto (201)        | Only while DRAFT. JPEG, PNG, GIF or WebP only, max 6 |
| DELETE | `/api/portal/orders/{id}/photos/{photoId}`    | -                  | 204                         | Only while DRAFT |
| GET    | `/api/portal/orders/{id}/documents/{type}`    | -                  | HTML page (`text/html`)     | Only the types clients may see (domain-model Q5), else 403 |
| GET    | `/api/portal/locations`                       | -                  | List<LocationDto>           | Locations of the user's client |
| POST   | `/api/portal/locations`                       | LocationRequest    | LocationDto (201)           | New work site, for an order at a new address |

## Data shapes

Requests end in `Request`, responses in `Dto`. "?" = optional.

### Auth and users

| Name              | Fields |
|-------------------|--------|
| LoginRequest      | username, password |
| LoginResponse     | token, userId, username, role, displayName, branchId?, clientId? |
| UserDto           | id, username, role, displayName, branchId?, branchName?, clientId?, clientName?, active, version |
| EmployeeRequest   | username, password (required on create, max 72 bytes), role (ADMIN, OFFICE or SERVICER), displayName, branchId (required for OFFICE and SERVICER, empty for ADMIN), active (required, so a PUT that forgets it can't deactivate the account), version (required on update) |
| ClientUserRequest | username, password (required on create, max 72 bytes), displayName, version (required on update) |

### Branches and clients

| Name            | Fields |
|-----------------|--------|
| BranchRequest   | name, city?, version (required on update) |
| BranchDto       | id, name, city, version |
| ClientRequest   | type, name, contactPerson?, phone?, email? (format checked), address? (billing address), version (required on update) |
| ClientDto       | id, type, name, contactPerson, phone, email, address, locations: List<LocationDto>, version - adding or removing a location doesn't change the client's version |
| LocationRequest | name?, address, city, version (required on update) |
| LocationDto     | id, name, address, city, version |

### Orders

| Name                | Fields |
|---------------------|--------|
| OrderRequest        | branchId?, clientId?, locationId?, contactPerson?, phone?, email?, description?, urgency?, estimatedCosts: CostsRequest?, version (required on update) - all optional while DRAFT, client and location are checked on submit |
| OrderSummaryDto     | id, orderNumber, status, urgency, branchId, branchName, clientName, locationText, assignedServicerId, assignedServicerName, createdAt |
| OrderDto            | id, orderNumber, status, allowedNextStatuses: List<OrderStatus>, urgency, branch: BranchDto, client: {id, type, name}, location: LocationDto, contactPerson, phone, email, description, assignedServicer: {id, displayName}, estimatedCosts: CostsDto, actualCosts: CostsDto, costDifference: CostsDto, notes: List<NoteDto>, photos: List<PhotoDto>, createdAt, updatedAt, version |
| CostsRequest        | km?, workHours?, numberOfWorkers?, materialCost? - none negative. workHours max 9999.99, materialCost max 99999999.99 (the NUMERIC columns) |
| CostsDto            | km, workHours, numberOfWorkers, totalHours, materialCost - `totalHours` is calculated. In `costDifference` every field is actual - estimated, or null if either value is missing |
| ActualCostsRequest  | costs: CostsRequest (required, `{}` clears all four fields), version (the order's version) |
| StatusChangeRequest | status, version |
| AssignmentRequest   | servicerId, version |
| NoteRequest         | text |
| NoteDto             | id, text, authorId, authorName, createdAt |
| PhotoDto            | id, url |

- `locationText` is the location as one line ("address, city"), for list columns.
- ActualCostsRequest wraps CostsRequest instead of adding a version to it, because CostsRequest is also nested in OrderRequest, where the order's version is already on the top level.
### Portal

| Name                  | Fields |
|-----------------------|--------|
| PortalOrderRequest    | locationId?, contactPerson?, phone?, email?, description?, urgency?, version (required on update) - location is checked on submit |
| PortalOrderSummaryDto | id, orderNumber, status, urgency, locationText, createdAt |
| PortalOrderDto        | id, orderNumber, status, urgency, location: LocationDto, contactPerson, phone, email, description, photos: List<PhotoDto>, createdAt, updatedAt, version |

- The portal view has no costs, notes, servicer or branch. See "Decisions to confirm".

## Changes from the template

| #   | Template | This project |
|-----|----------|--------------|
| A1  | No role checks on endpoints | `@PreAuthorize` role check on every endpoint |
| A2  | Client users call the same endpoints as employees, filtered by role in the service | Client users have their own endpoints under `/api/portal`, with their own request/response shapes |
| A3  | PUT is partial (null = keep) | PUT is a full replace |
| A4  | Order location is free text | `locationId` (domain-model C1) |
| A5  | Any status can be set | Checked against StatusTransition. The order detail returns `allowedNextStatuses` |
| A6  | Servicer is set through PUT order | Separate `accept` (servicer) and `assignment` (office) endpoints, both move PENDING -> IN_PROGRESS |
| A7  | Actual costs are part of PUT order | Separate `actual-costs` endpoint, so the servicer can enter them without editing the rest of the order |
| A8  | 4 fixed paths (`/quote`, `/workorder`, `/report`, `/invoice`) | Same approach (HTML built on every request, nothing stored), but one path with `{type}` = DocumentType value. Client users get their own document path in the portal |
| A9  | Document and photo-delete endpoints don't check order access | Same access checks as the other order endpoints |
| A10 | `/api/users` for all accounts | `/api/users` for employees, `/api/clients/{id}/users` for client users |
| A11 | No GET branch by ID, no location update | Both added |
| A12 | Spring's default error body, no validation | Problem Details + Bean Validation |
| A13 | No versions, the last save wins | Optimistic locking: DTOs return a `version`, writes send it back, 409 if someone saved in between |

## Decisions to confirm

My choices in this document that nobody has confirmed yet:

- The portal order view has no costs, notes, servicer or branch. Clients see the costs in the quote and invoice documents.
- A client user can change an order (fields, photos) only while it's a DRAFT, and can't cancel or delete it.
- Documents for employees: ADMIN and OFFICE only, as in the template (it shows the document buttons only to the office). Servicers can't open them.
