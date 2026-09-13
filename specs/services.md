# Services

Service layer of the Contractor backend. Source: the services of the template project in `/home/mbusic/code/Obsolete/Contractor/backend`, adapted to `specs/domain-model.md` and `specs/rest-api.md`. Changes from the template are marked [S1], [S2], ... and listed in "Changes from the template".

## Layers

- **Controller:** HTTP only - path, role check (`@PreAuthorize`), request validation, calls one service method, returns a DTO. Controllers never use repositories. [S1]
- **Service:** business rules, row-level access checks, transactions (`@Transactional`), mapping entity <-> DTO.
- **Repository:** Spring Data JPA interfaces, one per entity that is loaded on its own.
- A service that needs the logged-in user gets it as a `User currentUser` parameter. The controller takes it from `@AuthenticationPrincipal UserPrincipal`.

## Errors

Rule: services don't know about HTTP. They never import `org.springframework.http` or `org.springframework.web`, so no `ResponseStatusException` and no `HttpStatus`. [S10]

- A service reports an error by throwing an exception from the `exception` package, with a short message.
- `ApiExceptionHandler` (a `@RestControllerAdvice` in `controller`) is the only place that maps these exceptions to HTTP. Each one gets its status and a Problem Details body, and the message goes into `detail`.
- `ApiExceptionHandler` extends Spring's `ResponseEntityExceptionHandler`, so the base class answers Spring MVC's own errors (unreadable JSON, unknown URL, wrong method, ...) with Problem Details. Because of that, Spring Boot doesn't register its built-in `ProblemDetailsExceptionHandler`. `handleMethodArgumentNotValid` is overridden to add `fieldErrors` to Bean Validation errors (rest-api.md "Conventions"). A missing or invalid token is answered by the security filter (401, empty body).

| Exception                   | Status | Thrown when                                     | detail |
|-----------------------------|--------|-------------------------------------------------|--------|
| BadRequestException         | 400    | A business rule on the request fails, e.g. a servicer without a branch or an unknown ID in the body | The message |
| ForbiddenException          | 403    | The role may call the endpoint, but the row isn't yours (e.g. a servicer and another servicer's order) | The message |
| NotFoundException           | 404    | The row in the path doesn't exist               | The message |
| ConflictException           | 409    | The action isn't allowed in the current state   | The message |
| InvalidCredentialsException | 401    | Login with an unknown username or wrong password | "Invalid credentials" |
| AccessDeniedException (Spring Security) | 403 | `@PreAuthorize` refuses the user's role | "Access denied" |
| Any other exception         | 500    | A bug or an outage. Logged with the stack trace | "Unexpected error" - the real message stays in the log |

- The 500 catch-all only sees exceptions from controllers and services. Errors before Spring MVC (in a filter, or a URL the firewall rejects) go through Tomcat's forward to `/error` and get Spring Boot's default error body.

- A new kind of error gets a new exception class and a handler method, added by the slice that needs it first.
- `OptimisticLockingFailureException` (Spring's wrapper for the Hibernate `@Version` failure) -> 409 with the same detail as a stale version from `VersionCheck` (see "Optimistic locking").
- No generic exception with a status field. It would bring HTTP back into the services.

## Optimistic locking

[S11] Every editable entity (Branch, Client, Location, User, Order) has a `@Version` field. Rules for update methods:

- First the usual lookup (404) and row check (403), then `VersionCheck.check(request.version(), entity.getVersion())`: null -> 400 "Version is required", different -> 409 "Changed by someone else. Reload and try again." Only then the other rules.
- Save with `saveAndFlush`. Hibernate increases the version (and `updatedAt`) only on flush, and the response must show the new values.
- If another transaction saves the same row between the check and the flush, Hibernate's `@Version` check fails and `ApiExceptionHandler` answers 409 with the same detail.
- Accept has no request version: `@Version` alone stops two servicers from taking the same order. `acceptOrder` catches that failure itself and answers with its own 409 (see OrderService "Accept").
- Hibernate leaves `mappedBy` collections out of the version, so adding or removing a location doesn't change the client's version.

## Overview

| Service                 | Used by                             | Template                         |
|-------------------------|-------------------------------------|----------------------------------|
| AuthService             | AuthController                      | Logic was in AuthController [S1] |
| BranchService           | BranchController                    | BranchService                    |
| UserService             | UserController, ClientController    | UserService                      |
| ClientService           | ClientController                    | ClientService, plus the portal locations [S2] |
| OrderService            | OrderController, UserService        | OrderService, plus the portal orders [S2] |
| StatusTransitionService | OrderService                        | new [S3]                         |
| OrderNumberGenerator    | OrderService                        | OrderNumberGenerator             |
| DocumentService         | OrderService                        | DocumentService [S4]             |
| FileStorageService      | OrderService, FileController        | FileStorageService               |

## AuthService

| Method | Does |
|--------|------|
| `LoginResponse login(String username, String password)` | Finds the active user, checks the password with `PasswordEncoder`, issues a JWT through `JwtUtil`. 401 "Invalid credentials" if the user is missing, deactivated, or the password is wrong (the same message for all) |

## BranchService

| Method | Does |
|--------|------|
| `List<BranchDto> getAll()` | All branches |
| `BranchDto getById(Long id)` | 404 if missing |
| `BranchDto create(BranchRequest req)` | |
| `BranchDto update(Long id, BranchRequest req)` | Version check, full replace |
| `void delete(Long id)` | 409 "Branch has users" / "Branch has orders" while users or orders point to it (domain-model Q3) |

This is the roadmap step 1 reference slice, so it's the pattern for the other services.

## UserService

Employee accounts:

| Method | Does |
|--------|------|
| `List<UserDto> getEmployees(Role role)` | All employees, active and deactivated, sorted by displayName. Only one role if `role` isn't null (400 for CLIENT) |
| `UserDto createEmployee(EmployeeRequest req)` | Role must be ADMIN, OFFICE or SERVICER (400 otherwise). Password required (400) |
| `UserDto updateEmployee(Long id, EmployeeRequest req, User currentUser)` | Version check, full replace, except an empty password keeps the current one. `active` can reactivate |
| `void deleteEmployee(Long id, User currentUser)` | Sets `active = false`, nothing is deleted (domain-model Q3) |

- An unknown ID or a client user's ID -> 404 "Employee not found".
- When a servicer stops being an active SERVICER (delete, `active: false`, or another role), `OrderService.releaseOrdersOf` sends their IN_PROGRESS orders back to PENDING, so other servicers can accept them.
- Self-guard, so the last admin can't lock everyone out: an admin can't deactivate their own account (delete or `active: false`) or change their own role away from ADMIN -> 409.

Client users:

| Method | Does |
|--------|------|
| `List<UserDto> getClientUsers(Long clientId)` | Sorted by displayName. 404 for an unknown client |
| `UserDto createClientUser(Long clientId, ClientUserRequest req)` | Role is always CLIENT, client is set from the path, no branch. Password required (400) |
| `UserDto updateClientUser(Long clientId, Long userId, ClientUserRequest req)` | 404 if the user doesn't belong to that client. Version check. Empty password keeps the current one |
| `void deleteClientUser(Long clientId, Long userId)` | Same check. Deletes the row - nothing points to a client user |

Rules:
- Passwords are stored as BCrypt hashes. Max 72 bytes, checked in the service (400): BCrypt ignores the rest, and `BCryptPasswordEncoder.encode` throws above it. Not `@Size(max = 72)`, because it counts characters, and a Croatian letter like `č` takes 2 bytes.
- Username taken -> 409. Deactivated users keep their username.
- Role, branch and client must match: OFFICE and SERVICER have a branch, ADMIN has none, CLIENT has a client and no branch. Otherwise 400. An unknown `branchId` -> 400.

## ClientService

| Method | Does |
|--------|------|
| `List<ClientDto> getAll()` | With locations |
| `ClientDto getById(Long id)` | |
| `ClientDto create(ClientRequest req)` | |
| `ClientDto update(Long id, ClientRequest req)` | Version check, full replace |
| `void delete(Long id)` | Locations are deleted with it (cascade). 409 "Client has users" / "Client has orders" while client users or orders point to it (domain-model Q3) |
| `LocationDto addLocation(Long clientId, LocationRequest req)` | |
| `LocationDto updateLocation(Long clientId, Long locationId, LocationRequest req)` | [S5]. 404 if the location belongs to another client. Version check |
| `void deleteLocation(Long clientId, Long locationId)` | 404 if the location belongs to another client (the template gives 400). 409 "Location is used by orders". Removed through `client.getLocations()`, so orphan removal deletes it |

- Clients sorted by name, locations by ID (the order they were added).

Client portal (`/api/portal/locations`, CLIENT only). The client is always the user's own (`currentUser.client`; only its ID can be read, the user comes from a finished transaction):

| Method | Does |
|--------|------|
| `List<LocationDto> getPortalLocations(User currentUser)` | Through `getById` |
| `LocationDto addPortalLocation(LocationRequest req, User currentUser)` | Through `addLocation` |

## OrderService

Employee methods first, then the client portal methods (see "Client portal" below).

| Method | Does |
|--------|------|
| `List<OrderSummaryDto> getOrders(User currentUser)` | ADMIN/OFFICE: all. SERVICER: assigned to them + unassigned PENDING. Newest first |
| `OrderDto getOrder(Long id, User currentUser)` | Row check. Fills `allowedNextStatuses` from StatusTransitionService (empty if the user may not change the order, without IN_PROGRESS if no servicer is assigned), and calculates total hours and `costDifference` (every OrderDto response has them) |
| `OrderDto createOrder(OrderRequest req, User currentUser)` | New DRAFT without a number. `currentUser` is only used for `allowedNextStatuses` in the response |
| `OrderDto updateOrder(Long id, OrderRequest req, User currentUser)` | Version check, full replace of order data and estimated costs, in any status. For a submitted order, client and location stay required (400) |
| `void deleteOrder(Long id)` | Deletes the order (with its notes and photo rows), flushes, then deletes the photo files |
| `OrderDto changeStatus(Long id, OrderStatus newStatus, Long version, User currentUser)` | Row check, version check, then `applyStatus` |
| `OrderDto acceptOrder(Long id, User currentUser)` | Servicer takes an unassigned PENDING order |
| `OrderDto assignServicer(Long id, Long servicerId, Long version, User currentUser)` | Version check, then the office assigns or reassigns |
| `void releaseOrdersOf(User servicer)` | For UserService. The servicer's IN_PROGRESS orders go through `applyStatus(PENDING)`, which unassigns them. DRAFT, RESOLVED and CANCELLED orders keep the servicer as history |
| `OrderDto updateActualCosts(Long id, CostsRequest costs, Long version, User currentUser)` | Row check, version check, full replace of the actual cost fields. Any status |
| `OrderDto addNote(Long id, String text, User currentUser)` | Row check, any status. Author = currentUser. Added through `Order.notes` (cascade) and flushed, so the response has the note's ID and createdAt |
| `OrderDto addPhoto(Long id, UploadedFile file, User currentUser)` | Row check, any status, then `storePhoto`. The controller copies the MultipartFile into `UploadedFile(originalName, contentType, content)`, so services stay free of Spring Web types |
| `void deletePhoto(Long orderId, Long photoId, User currentUser)` | Row check [S6]. 404 "Photo not found" if the photo isn't on this order. Deletes the row, flushes, then deletes the file |
| `String getDocument(Long id, DocumentType type, User currentUser)` | ADMIN/OFFICE: every type. SERVICER: only `WORK_ORDER` of an order assigned to them, else 403 "You can't open this document" [S4]. Then `DocumentService.render` |

Internal (private), used by several of the methods above:

| Method | Does |
|--------|------|
| `void applyStatus(Order order, OrderStatus newStatus)` | 409 if StatusTransitionService doesn't allow it. 409 "Assign a servicer first" for IN_PROGRESS without a servicer. For a submitted status (PENDING, IN_PROGRESS, RESOLVED): 400 without client or location, and takes a number from OrderNumberGenerator if the order has none yet. A change to PENDING clears the servicer: a PENDING order never has one |
| `void storePhoto(Order order, UploadedFile file)` | Max 6 per order (400). Quick check on the file name extension and Content-Type, then `ImageType.detect` on the first bytes decides (both 400, see rest-api.md "Photos"). Stores the bytes through FileStorageService with the detected type's extension. Two uploads at the same moment can pass the limit together (accepted, no lock) |
| `void removePhoto(Order order, Long photoId)` | 404 "Photo not found" if the photo isn't on the order. Deletes the row, flushes, then deletes the file |

Rules:
- **Row check** (`checkAccess`): ADMIN and OFFICE may touch every order. A SERVICER may read an order assigned to them or an unassigned PENDING one, and may change only orders assigned to them. Otherwise 403.
- **Accept:** 409 "Order is already taken or not pending" unless the order is PENDING and unassigned. Then set the servicer and `applyStatus(IN_PROGRESS)`, and save with `saveAndFlush`. When two servicers accept at the same time, both pass the check, but only the first flush matches the version. The second gets the `@Version` failure, which `acceptOrder` turns into the same 409 "Order is already taken or not pending". `OrderAcceptRaceTest` forces this order of events with two threads.
- **Assign:** `servicerId` must be an existing (400 "Servicer not found"), active (400 "Servicer is not active") SERVICER (400 "User is not a servicer"). PENDING: set the servicer, then `applyStatus(IN_PROGRESS)`. IN_PROGRESS: just change the servicer. Any other status: 409 "Only PENDING and IN_PROGRESS orders can be assigned".
- **Location** must belong to the order's client (400), whenever it's set. A location without a client is refused too.
- **Unknown IDs in the body** (branch, client, location) -> 400 "Xxx not found", not 404: the path itself exists.
- **Lists** load branch, client, location and servicer with an `@EntityGraph`, so a row doesn't trigger four extra queries. Newest first, the ID breaks ties.

Client portal (`/api/portal/orders`, CLIENT only) [S2]. Every method takes `User currentUser` and works only with `currentUser.client` (only its ID can be read: the user comes from a finished transaction). Visible orders: the client's orders with `createdInPortal` or an order number. Anything else -> 403, unknown ID -> 404. Order of checks for a change: 404, 403, version, then DRAFT-only (409 "Only a draft can be changed"). The responses are PortalOrderDto / PortalOrderSummaryDto: no costs, notes, servicer or branch.

| Method | Does |
|--------|------|
| `List<PortalOrderSummaryDto> getPortalOrders(User currentUser)` | The visible orders, including the client's own drafts. Newest first |
| `PortalOrderDto getPortalOrder(Long id, User currentUser)` | |
| `PortalOrderDto createPortalOrder(PortalOrderRequest req, User currentUser)` | New DRAFT, client = the user's client, `createdInPortal = true`, no branch, no costs |
| `PortalOrderDto updatePortalOrder(Long id, PortalOrderRequest req, User currentUser)` | Version check, only while DRAFT (409), full replace. The location must be the client's (400) |
| `PortalOrderDto submitPortalOrder(Long id, Long version, User currentUser)` | Version check, only while DRAFT (409), then `applyStatus(PENDING)` |
| `PortalOrderDto addPortalPhoto(Long id, UploadedFile file, User currentUser)` | Only while DRAFT, then `storePhoto` |
| `void deletePortalPhoto(Long id, Long photoId, User currentUser)` | Only while DRAFT, then `removePhoto` |
| `String getPortalDocument(Long id, DocumentType type, User currentUser)` | Visible order (403), then `WORK_ORDER` -> 403 "You can't open this document" (domain-model Q5), then `DocumentService.render` |

## StatusTransitionService

[S3]

| Method | Does |
|--------|------|
| `boolean isAllowed(OrderStatus from, OrderStatus to)` | True if a row exists |
| `List<OrderStatus> allowedNext(OrderStatus from)` | For `allowedNextStatuses` in the order detail |

The rows come from the migrations, `V1__initial_schema.sql` (see domain-model StatusTransition).

## OrderNumberGenerator

| Method | Does |
|--------|------|
| `String next()` | Increases `last_sequence` for the current year (Europe/Zagreb) and returns `%03d/%02d` (e.g. `007/26`) |

- Called by `applyStatus` when an order is submitted for the first time (domain-model Q4).
- One statement through `JdbcClient`, no entity [S7]:
  `INSERT INTO order_sequences (seq_year, last_sequence) VALUES (:year, 1) ON CONFLICT (seq_year) DO UPDATE SET last_sequence = order_sequences.last_sequence + 1 RETURNING last_sequence`.
  Postgres locks the row until the transaction ends, so two orders submitted at the same moment don't get the same number - also for the first order of a year, where a read-then-insert would race.
- `@Transactional(propagation = MANDATORY)`: it must run in the transaction that saves the order, so a rollback gives the number back. The unique constraint on `order_number` is the safety net.

## DocumentService

[S4]

| Method | Does |
|--------|------|
| `String render(Order order, DocumentType type)` | Returns the whole HTML page of the document, built from the current order data. One private method per type (quote, work order, report, invoice) |

- Same approach as the template: fixed HTML layouts in Java text blocks, with inline CSS and Croatian labels. Content per type: see domain-model DocumentType. Nothing is stored.
- Access is checked by the caller (OrderService, employee or portal method) before `render`.
- Runs inside the caller's read-only transaction, because the order's notes and photos load lazily.
- The page has a "Ispis / PDF" button that calls `window.print()`. `@media print` hides the button. The user saves a PDF through the browser.
- Photos are `<img>` tags with an absolute URL: `app.base-url` + `/api/files/{filename}` (dev: `http://localhost:8080`). The frontend opens the page as a blob, where a relative URL doesn't work. That's why `/api/files/**` is public.
- Every value from the database is HTML-escaped before it goes into the page (`& < > " '`, a small method in DocumentService - Spring's `HtmlUtils` is in `org.springframework.web`, which services don't import). [S9]
- Dates are Croatian dates (`Europe/Zagreb`), whatever the server's time zone. Notes in the report run oldest first.
- The controller sets `text/html; charset=UTF-8` on the response itself (not `produces` on the mapping), so errors still come back as Problem Details JSON. The frontend opens a new tab in the click, then loads the HTML into it as a blob, so a popup blocker doesn't stop it.
- Dates: `dd.MM.yyyy`. Status and urgency use their Croatian labels from domain-model. Total hours are calculated (work hours x number of workers).

## FileStorageService

| Method | Does |
|--------|------|
| `String store(byte[] content, String extension)` | Saves the bytes as `<uuid>.<extension>` in the upload folder, returns the filename |
| `StoredPhoto loadPhoto(String filename)` | The file and its ImageType (from the extension). 404 "File not found" unless the file is directly in the upload folder (no `../`), has an image extension, and exists |
| `void delete(String filename)` | Deletes the file if it exists |

- The upload folder comes from `app.upload-dir` (`./uploads`, tests: `build/test-uploads`), as in the template. Max 10 MB per file (`spring.servlet.multipart.max-file-size`), a bigger upload gets 413.
- File errors (disk full, no rights) become `UncheckedIOException`, so a 500.
- Files and rows are kept in sync the simple way, as in the template, but with rows first: an upload writes the file before the row is committed, so a failed transaction can leave an orphan file (harmless). A delete removes the row and flushes before it deletes the file, so a failed delete never leaves a row without its file. A crash between the commit and the file delete can leave an orphan file.
- `ImageType` (JPEG, PNG, GIF, WEBP) holds the extension, the media type, and the magic-byte detection.

## Security components

Not services, but the auth steps (roadmap steps 3-5) need them. The same classes as in the template:

| Class | Does |
|-------|------|
| SecurityConfig | Stateless (no session), CSRF off. Public: `/api/auth/login`, `/api/health`, and GET `/api/files/**` (the documents load photos with `<img>`, which can't send a token; the file names are random UUIDs). Also public: GET `/`, `/index.html`, `/favicon.ico`, `/*.js`, `/*.css` - the built frontend, which the browser loads before anyone is logged in. `/index.html` is listed because `/` is forwarded to it and the forward is checked again. A new kind of file at the root (e.g. an image in `public/`) or a `media/` folder from CSS `url()` needs its own entry. Error forwards (`DispatcherType.ERROR`) are allowed too: Tomcat's forward to `/error` carries no authentication, so without this a real error would turn into a 401. Everything else needs a token, and a missing or invalid one gets 401 with an empty body. `@EnableMethodSecurity` turns on `@PreAuthorize`, which checks the role per endpoint [S8]. CORS is decided in step 7 (Angular dev proxy or CORS for `app.cors.origin`) |
| JwtUtil | Creates and checks HS256 tokens (subject = username, claim `role`). Secret from `APP_JWT_SECRET` (no default, at least 32 bytes, else the app doesn't start), lifetime 24h |
| JwtAuthFilter | Reads `Authorization: Bearer ...`, loads the user, puts it into the security context. If the user was deleted or deactivated after the token was issued, the request stays unauthenticated (the template throws, which gives a 500). Created in SecurityConfig, not a `@Component` |
| UserDetailsServiceImpl | Loads an active User by username for the filter. A deactivated user counts as not found |
| UserPrincipal | Wraps User. Authority = `ROLE_` + role name (e.g. `ROLE_OFFICE`) |

## Changes from the template

| #  | Template | This project |
|----|----------|--------------|
| S1 | AuthController and DocumentController use repositories directly | Controllers only call services. Login logic moves to AuthService |
| S2 | OrderService handles client users by role inside the employee methods | Client users have their own portal methods (`getPortalOrders`, ... in OrderService, `getPortalLocations`, ... in ClientService) with their own DTOs, next to the employee methods of the same object |
| S3 | No status rules | StatusTransitionService checks every status change |
| S4 | DocumentController loads the order and calls DocumentService with no access check | Same HTML approach, but it's called through OrderService, which checks access first. The layouts follow the new model (Location, Urgency labels, calculated total hours) |
| S5 | No location update | `updateLocation` |
| S6 | `deletePhoto` has no access check | Same row check as the other order methods |
| S7 | OrderNumberGenerator reads the year row without a lock | One upsert statement that locks the row, so parallel orders don't get the same number |
| S8 | No method security | `@EnableMethodSecurity` + `@PreAuthorize` on controllers |
| S9 | Document HTML puts database values in as they are | Values are HTML-escaped. Otherwise a description like `<script>...</script>` would run in the document tab, which the frontend opens as a blob with the app's origin, so the script could read the app's data |
| S10 | Services and controllers throw `ResponseStatusException` | Services throw exceptions from the `exception` package, and `ApiExceptionHandler` maps them to HTTP. Services stay free of HTTP types |
| S11 | No versions, the last save wins | `@Version` on every editable entity, requests send the version back (see "Optimistic locking") |
