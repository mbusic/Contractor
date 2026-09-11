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
- Bean Validation errors and other Spring MVC errors still go through Spring's built-in handler. A missing or invalid token is answered by the security filter (401, empty body).

| Exception                   | Status | Thrown when                                     |
|-----------------------------|--------|-------------------------------------------------|
| NotFoundException           | 404    | The row in the path doesn't exist               |
| ConflictException           | 409    | The action isn't allowed in the current state   |
| InvalidCredentialsException | 401    | Login with an unknown username or wrong password |

- A new kind of error gets a new exception class and a handler method, added by the slice that needs it first. Known ones still to come: 400 for an unknown ID in the body or a wrong file type, 403 for a row that isn't yours (e.g. another client's order).
- No generic exception with a status field. It would bring HTTP back into the services.

## Overview

| Service                 | Used by                             | Template                         |
|-------------------------|-------------------------------------|----------------------------------|
| AuthService             | AuthController                      | Logic was in AuthController [S1] |
| BranchService           | BranchController                    | BranchService                    |
| UserService             | UserController, ClientController    | UserService                      |
| ClientService           | ClientController, PortalService     | ClientService                    |
| OrderService            | OrderController, PortalService      | OrderService                     |
| PortalService           | PortalController                    | new [S2]                         |
| StatusTransitionService | OrderService, PortalService         | new [S3]                         |
| OrderNumberGenerator    | OrderService, PortalService         | OrderNumberGenerator             |
| DocumentService         | OrderService, PortalService         | DocumentService [S4]             |
| FileStorageService      | OrderService, FileController        | FileStorageService               |

## AuthService

| Method | Does |
|--------|------|
| `LoginResponse login(String username, String password)` | Finds the user, checks the password with `PasswordEncoder`, issues a JWT through `JwtUtil`. 401 "Invalid credentials" if the user is missing or the password is wrong (the same message for both) |

## BranchService

| Method | Does |
|--------|------|
| `List<BranchDto> getAll()` | All branches |
| `BranchDto getById(Long id)` | 404 if missing |
| `BranchDto create(BranchRequest req)` | |
| `BranchDto update(Long id, BranchRequest req)` | Full replace |
| `void delete(Long id)` | 409 "Branch has users" if users belong to it (domain-model Q3) |

This is the roadmap step 1 reference slice, so it's the pattern for the other services.

## UserService

Employee accounts:

| Method | Does |
|--------|------|
| `List<UserDto> getEmployees(Role role)` | All employees, or only one role if `role` isn't null |
| `UserDto createEmployee(EmployeeRequest req)` | Role must be ADMIN, OFFICE or SERVICER (400 otherwise) |
| `UserDto updateEmployee(Long id, EmployeeRequest req)` | Full replace, except an empty password keeps the current one |
| `void deleteEmployee(Long id)` | See domain-model Q3 |

Client users (step 6, together with the Client slice):

| Method | Does |
|--------|------|
| `List<UserDto> getClientUsers(Long clientId)` | |
| `UserDto createClientUser(Long clientId, ClientUserRequest req)` | Role is always CLIENT, client is set from the path |
| `UserDto updateClientUser(Long clientId, Long userId, ClientUserRequest req)` | 404 if the user doesn't belong to that client |
| `void deleteClientUser(Long clientId, Long userId)` | Same check |

Rules:
- Passwords are stored as BCrypt hashes. Max 72 bytes (`@Size(max = 72)` on the request): BCrypt ignores the rest, and `BCryptPasswordEncoder.encode` throws above it.
- Username taken -> 409.
- Role, branch and client must match: OFFICE and SERVICER have a branch, ADMIN has none, CLIENT has a client and no branch. Otherwise 400.

## ClientService

| Method | Does |
|--------|------|
| `List<ClientDto> getAll()` | With locations |
| `ClientDto getById(Long id)` | |
| `ClientDto create(ClientRequest req)` | |
| `ClientDto update(Long id, ClientRequest req)` | Full replace |
| `void delete(Long id)` | Locations are deleted with it (cascade). See domain-model Q3 |
| `List<LocationDto> getLocations(Long clientId)` | Used by the portal |
| `LocationDto addLocation(Long clientId, LocationRequest req)` | |
| `LocationDto updateLocation(Long clientId, Long locationId, LocationRequest req)` | [S5] |
| `void deleteLocation(Long clientId, Long locationId)` | 400 if the location belongs to another client (as in the template) |

## OrderService

For employees. Client users go through PortalService.

| Method | Does |
|--------|------|
| `List<OrderSummaryDto> getOrders(User currentUser)` | ADMIN/OFFICE: all. SERVICER: assigned to them + unassigned PENDING. Newest first |
| `OrderDto getOrder(Long id, User currentUser)` | Row check. Fills `allowedNextStatuses` from StatusTransitionService, and calculates total hours and `costDifference` |
| `OrderDto createOrder(OrderRequest req, User currentUser)` | New DRAFT with a number from OrderNumberGenerator |
| `OrderDto updateOrder(Long id, OrderRequest req)` | Full replace of order data and estimated costs |
| `void deleteOrder(Long id)` | Deletes the order (with its notes and photos), then the photo files |
| `OrderDto changeStatus(Long id, OrderStatus newStatus, User currentUser)` | Row check, then `applyStatus` |
| `OrderDto acceptOrder(Long id, User servicer)` | Servicer takes an unassigned PENDING order |
| `OrderDto assignServicer(Long id, Long servicerId)` | Office assigns or reassigns |
| `OrderDto updateActualCosts(Long id, CostsRequest req, User currentUser)` | Row check, full replace of the actual cost fields |
| `OrderDto addNote(Long id, String text, User currentUser)` | Row check. Author = currentUser |
| `OrderDto addPhoto(Long id, MultipartFile file, User currentUser)` | Row check, then `storePhoto` |
| `void deletePhoto(Long orderId, Long photoId, User currentUser)` | Row check [S6], deletes the row and the file |
| `String getDocument(Long id, DocumentType type, User currentUser)` | Row check [S4], then `DocumentService.render` |

Shared with PortalService (not exposed through REST directly):

| Method | Does |
|--------|------|
| `void applyStatus(Order order, OrderStatus newStatus)` | 409 if StatusTransitionService doesn't allow it. When the order leaves DRAFT (to anything other than CANCELLED), checks the required fields: client and location set, location belongs to the client. 400 if something is missing |
| `void storePhoto(Order order, MultipartFile file)` | JPEG, PNG, GIF or WebP only (400 otherwise). Max 6 per order (400). Stores the file through FileStorageService |

Rules:
- **Row check** (`checkAccess`): ADMIN and OFFICE may touch every order. A SERVICER may read an order assigned to them or an unassigned PENDING one, and may change only orders assigned to them. Otherwise 403.
- **Accept:** one conditional update, so two servicers can't take the same order:
  `UPDATE orders SET assigned_servicer_id = :me, status = 'IN_PROGRESS' WHERE id = :id AND assigned_servicer_id IS NULL AND status = 'PENDING'`.
  If 0 rows change -> 409 "Order is already taken or not pending". PENDING -> IN_PROGRESS must also be allowed by StatusTransition (it always is).
- **Assign:** the user must be a SERVICER (400). PENDING: set the servicer, then `applyStatus(IN_PROGRESS)`. IN_PROGRESS: just change the servicer. Any other status: 409.
- **Location** must belong to the order's client (400), whenever both are set.

## PortalService

[S2] Everything for client users. Every method takes `User currentUser` and works only with `currentUser.client`. Another client's order -> 403.

| Method | Does |
|--------|------|
| `List<PortalOrderSummaryDto> getOrders(User currentUser)` | Orders of the user's client, including drafts |
| `PortalOrderDto getOrder(Long id, User currentUser)` | |
| `PortalOrderDto createOrder(PortalOrderRequest req, User currentUser)` | New DRAFT, client = the user's client, no branch, no costs |
| `PortalOrderDto updateOrder(Long id, PortalOrderRequest req, User currentUser)` | Only while DRAFT (409) |
| `PortalOrderDto submitOrder(Long id, User currentUser)` | Only while DRAFT (409), then `OrderService.applyStatus(PENDING)` |
| `PortalOrderDto addPhoto(Long id, MultipartFile file, User currentUser)` | Only while DRAFT, then `OrderService.storePhoto` |
| `void deletePhoto(Long id, Long photoId, User currentUser)` | Only while DRAFT |
| `String getDocument(Long id, DocumentType type, User currentUser)` | 403 if clients may not see this type (domain-model Q5), then `DocumentService.render` |
| `List<LocationDto> getLocations(User currentUser)` | Through ClientService |
| `LocationDto addLocation(LocationRequest req, User currentUser)` | Through ClientService |

## StatusTransitionService

[S3]

| Method | Does |
|--------|------|
| `boolean isAllowed(OrderStatus from, OrderStatus to)` | True if a row exists |
| `List<OrderStatus> allowedNext(OrderStatus from)` | For `allowedNextStatuses` in the order detail |

The rows come from `schema.sql` (see domain-model StatusTransition).

## OrderNumberGenerator

| Method | Does |
|--------|------|
| `String next()` | Increases `lastSequence` for the current year and returns `%03d/%02d` (e.g. `007/26`) |

- Runs in the same transaction as the order insert.
- Reads the year row with a row lock (`@Lock(PESSIMISTIC_WRITE)`, i.e. SELECT ... FOR UPDATE), so two orders created at the same moment don't get the same number. [S7] The unique constraint on `orderNumber` is the safety net.

## DocumentService

[S4]

| Method | Does |
|--------|------|
| `String render(Order order, DocumentType type)` | Returns the whole HTML page of the document, built from the current order data. One private method per type (quote, work order, report, invoice) |

- Same approach as the template: fixed HTML layouts in Java text blocks, with inline CSS and Croatian labels. Content per type: see domain-model DocumentType. Nothing is stored.
- Access is checked by the caller (OrderService or PortalService) before `render`.
- Runs inside the caller's read-only transaction, because the order's notes and photos load lazily.
- The page has a "Ispis / PDF" button that calls `window.print()`. `@media print` hides the button. The user saves a PDF through the browser.
- Photos are `<img>` tags with an absolute URL: `app.base-url` + `/api/files/{filename}`. That's why `/api/files/**` is public.
- Every value from the database is HTML-escaped (Spring's `HtmlUtils.htmlEscape`) before it goes into the page. [S9]
- Dates: `dd.MM.yyyy`. Status and urgency use their Croatian labels from domain-model. Total hours are calculated (work hours x number of workers).

## FileStorageService

| Method | Does |
|--------|------|
| `String store(MultipartFile file)` | Saves an upload (photo) as `<uuid>.<ext>` in `./uploads`, returns the filename |
| `Path load(String filename)` | Resolves the file in `./uploads`. Rejects names that leave the folder (e.g. `../`) |
| `void delete(String filename)` | Deletes the file if it exists |

The upload folder comes from `app.upload-dir`, as in the template.

## Security components

Not services, but the auth steps (roadmap steps 3-5) need them. The same classes as in the template:

| Class | Does |
|-------|------|
| SecurityConfig | Stateless (no session), CSRF off. Public: `/api/auth/login`, `/api/health`, `/error` (Spring's error page, so real errors aren't hidden behind a 401), and `/api/files/**` once the Files slice adds it. Everything else needs a token, and a missing or invalid one gets 401 with an empty body. `@EnableMethodSecurity` for `@PreAuthorize` comes in step 5 [S8]. CORS is decided in step 7 (Angular dev proxy or CORS for `app.cors.origin`) |
| JwtUtil | Creates and checks HS256 tokens (subject = username, claim `role`). Secret from `APP_JWT_SECRET` (no default, at least 32 bytes, else the app doesn't start), lifetime 24h |
| JwtAuthFilter | Reads `Authorization: Bearer ...`, loads the user, puts it into the security context. If the user was deleted after the token was issued, the request stays unauthenticated (the template throws, which gives a 500). Created in SecurityConfig, not a `@Component` |
| UserDetailsServiceImpl | Loads a User by username for the filter |
| UserPrincipal | Wraps User. Authority = `ROLE_` + role name (e.g. `ROLE_OFFICE`) |

## Changes from the template

| #  | Template | This project |
|----|----------|--------------|
| S1 | AuthController and DocumentController use repositories directly | Controllers only call services. Login logic moves to AuthService |
| S2 | OrderService handles client users by role | Client users have their own PortalService |
| S3 | No status rules | StatusTransitionService checks every status change |
| S4 | DocumentController loads the order and calls DocumentService with no access check | Same HTML approach, but it's called through OrderService / PortalService, which check access first. The layouts follow the new model (Location, Urgency labels, calculated total hours) |
| S5 | No location update | `updateLocation` |
| S6 | `deletePhoto` has no access check | Same row check as the other order methods |
| S7 | OrderNumberGenerator reads the year row without a lock | Row lock, so parallel orders don't get the same number |
| S8 | No method security | `@EnableMethodSecurity` + `@PreAuthorize` on controllers |
| S9 | Document HTML puts database values in as they are | Values are HTML-escaped. Otherwise a description like `<script>...</script>` would run in the document tab, which the frontend opens as a blob with the app's origin, so the script could read the app's data |
| S10 | Services and controllers throw `ResponseStatusException` | Services throw exceptions from the `exception` package, and `ApiExceptionHandler` maps them to HTTP. Services stay free of HTTP types |
