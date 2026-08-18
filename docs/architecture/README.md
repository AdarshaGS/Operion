# Architecture

## Shape

Operion is a modular monolith: one Spring Boot application, one deployable JAR, one
database — organised into package-per-module boundaries under
`src/main/java/com/operion/` rather than separate services. The frontend is a single
React SPA (`web/`) calling the API directly — no BFF layer.

```
com/operion/
├── organisation    # Organisation, Campus, AcademicYear — the tenant root
├── identity        # User, Person, auth (JWT issuance, refresh tokens)
├── authorization   # Role, Permission, OrganisationMembership — RBAC
├── academic        # GradeLevel, Subject, SchoolClass, Section, TeacherAssignment
├── student         # Student, StudentEnrollment, StudentDocument, StudentExit
├── parent          # Guardian, StudentGuardian, PortalInvite (guardian portal login)
├── attendance      # ClassAttendanceRegister, StudentAttendance, StaffAttendance
├── finance         # FeeStructure, Invoice, Payment, Refund, Razorpay gateway
├── examination     # Exam, GradingScale, MarksEntry, ReportCard
├── communication   # Announcement, NotificationRecipient
├── transport       # Vehicle, Route, StudentTransportAssignment, TripLog
├── library         # Book, BookCopy, BorrowRecord, Fine
├── inventory       # Item, StockEntry/Issue/Adjustment
├── hr              # StaffProfile, LeaveType, LeaveRequest
├── audit           # AuditLog — generic, cross-module audit trail
├── billing         # Subscription/Plan/Usage — SaaS billing, platform-admin plane
├── platform        # Platform-admin identity plane (separate from tenant users/JWT)
├── reporting       # cross-module report endpoints
└── common          # TenantContext, BaseEntity/TenantScopedEntity, ApiExceptionHandler,
                     # PageResponse, RequestLoggingFilter — shared infra every module uses
```

Each module owns its entities, repository, service, and a `api/` subpackage for
controllers and request/response DTOs — see [docs/development](../development) for the
exact shape to follow when adding a new one.

## Multi-tenancy

`Organisation` is the tenant root. Every tenant-scoped entity extends
`TenantScopedEntity`, which carries a Hibernate `@TenantId organisationId` — Hibernate's
tenant filter uses this to transparently scope every query, so a service written
against a repository never needs to remember to add an `organisationId` predicate by
hand.

The tenant identifier is resolved per-request into `TenantContext` (a `ThreadLocal`
holding `organisationId` + the acting user's id):

- For any authenticated request, `JwtAuthenticationInterceptor` decodes the bearer
  token and populates `TenantContext` in `preHandle`, clears it in `afterCompletion`.
- A handful of bootstrap flows have no token yet to resolve a tenant from (organisation
  creation, login itself, guardian portal invite claim, refresh-token exchange) — those
  resolve the organisation from a slug in the request body and set `TenantContext` by
  hand, following the same shape as `AuthenticationService.login()`.

Because `TenantContext` must be set *before* any transaction touching a tenant-scoped
entity opens, several of these bootstrap-flow service methods are deliberately **not**
`@Transactional` — each repository call gets its own fresh Hibernate session, opened
after `TenantContext` is already populated.

## Auth

- Login: organisation slug + email + password → BCrypt-verified → JWT access token
  (`app.jwt.secret`, `app.jwt.expiration-minutes`) + a rotating refresh token.
- Refresh tokens: issued alongside every access token, stored hashed
  (`RefreshToken.tokenHash`, never the raw value), 30-day validity, **rotate on use** —
  exchanging one for a new pair immediately revokes the old one. See
  `RefreshTokenService`, `POST /api/v1/auth/refresh`.
- Guardian portal: `PortalInvite` issues a one-time, hashed, expiring token a guardian
  exchanges (via `POST /api/v1/auth/claim-invite`) for a real login — reuses the same
  `User`/`OrganisationMembership`/`Role` machinery as staff, not a parallel identity
  system.
- Platform-admin plane (`com.operion.platform`, SaaS billing/ops) is a **separate**
  identity and JWT plane (`app.jwt.platform-secret`) from tenant-organisation auth
  above — deliberately kept apart so a platform-admin token can never masquerade as a
  tenant user's token or vice versa.

## RBAC

`User` ←→ `OrganisationMembership` ←→ `Role` ←→ `Permission`. A user can hold multiple
active memberships (multiple roles) within an organisation. Endpoints declare the
permission code(s) they require via `@RequirePermission`, enforced centrally by an
interceptor — never left to individual controllers to check by hand. The frontend
mirrors the same permission codes via `GET /api/v1/auth/me` and `<Can anyOf={[...]}>`
to hide/disable actions in the UI, but that's UX sugar only; the backend enforces
independently.

## Request pipeline

For an authenticated `/api/v1/**` request:

1. `RequestLoggingFilter` (`OncePerRequestFilter`) — generates/reads an `X-Request-Id`,
   puts it in the SLF4J MDC for correlated log lines, times the request.
2. `JwtAuthenticationInterceptor` — decodes the bearer token, populates `TenantContext`.
3. `PermissionInterceptor` — checks the resolved user against the endpoint's
   `@RequirePermission`.
4. Controller → Service → Repository.
5. `ApiExceptionHandler` maps any thrown exception to the standard `{"error": "..."}`
   shape and the right HTTP status; anything unmapped falls through to a catch-all 500
   handler that logs the full stack trace server-side without leaking it to the client.

Filters wrap the whole dispatch (including a 401 short-circuit from the interceptor);
interceptors only wrap a successful dispatch — this is why `RequestLoggingFilter`'s
post-`chain.doFilter()` logging code can't read `TenantContext` directly (already
cleared by the interceptor's `afterCompletion` by then) and instead reads a request
attribute the interceptor stashes there first.

## Conventions worth knowing

- **API shape**: `/api/v1/` prefix, standard `{"error": "..."}` error body. List
  endpoints default to returning the full unpaginated collection (most are consumed as
  pickers by other modules); `PageResponse<T>` (`com.operion.common.api`) is opt-in,
  reserved for genuinely unbounded, single-purpose endpoints (e.g. `GET
  /api/v1/audit-logs`).
- **Audit trail**: `AuditLogService` gives every module a generic, queryable ledger
  (entity type/id, action, actor, before/after) without each module needing its own.
- **Logging**: structured, correlation-ID-tagged console logging via
  `logback-spring.xml`; log level and verbosity differ per Spring profile
  (`application-dev.properties` / `application-prod.properties`).

## Deployment

See [docs/setup](../setup) for running locally (native or Docker Compose) and
`.github/workflows/ci.yml` for what CI builds/tests on every push.
