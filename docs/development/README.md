# Development

Day-to-day workflow for working on Operion: creating a migration, adding a new backend
module, and the conventions that keep new code consistent with what's already there.

## Creating a migration

Schema is entirely Flyway-managed — Hibernate only validates entities against it
(`spring.jpa.hibernate.ddl-auto=validate`), it never generates DDL. Every schema change
goes through a new migration file.

1. Add a new file under `src/main/resources/db/migration/`, named
   `V<next-number>__<snake_case_description>.sql` — numbers are sequential and never
   reused (check the highest existing `V*` file first).
2. Write plain SQL. Follow the existing convention: tenant-scoped tables carry an
   `organisation_id BIGINT NOT NULL` column with an FK to `organisations(id)`, plus the
   base audit columns (`created_at`, `updated_at`, `created_by`, `updated_by`).
3. A schema change that ships with new permission codes (a new module, a new protected
   action) typically gets its own follow-up migration seeding `permissions` /
   `role_permissions` rows — see `V6__seed_student_permissions.sql` /
   `V8__seed_attendance_permissions.sql` for the pattern.
4. Migrations run automatically the next time the backend starts (`./gradlew bootRun`
   or the `backend` container) — nothing to run by hand.

Never edit a migration that has already been merged/run anywhere — add a new one instead.

## Creating a new backend module

Modules live under `src/main/java/com/operion/<module>/` as a flat package (no further
nesting beyond an `api` subpackage for controllers/DTOs). Using `student` as the
reference shape:

```
com/operion/<module>/
├── <Entity>.java                 # extends TenantScopedEntity (or BaseEntity if global, not tenant-scoped)
├── <Entity>Repository.java       # Spring Data JPA interface
├── <Entity>Service.java          # business logic, transaction boundaries
├── <Enum>.java                   # any status/type enums the entity needs
└── api/
    ├── <Entity>Controller.java   # @RestController, thin — delegates to the service
    ├── Create<Entity>Request.java
    └── <Entity>Response.java
```

Conventions to follow:

- **Multi-tenancy**: entities scoped to an organisation extend `TenantScopedEntity`
  (adds `@TenantId organisationId`, enforced by Hibernate's tenant filter via
  `TenantContext`). A request handler needs `TenantContext.set(...)` populated before
  any repository call touches a `@TenantId`-scoped entity — normally that's already
  done for you by `JwtAuthenticationInterceptor` for any authenticated endpoint; only
  bootstrap flows with no token yet (org creation, invite claim, login itself) set it
  by hand. See `TenantContext`, `JwtAuthenticationInterceptor`.
- **Validation**: request DTOs use `jakarta.validation` annotations (`@NotNull`,
  `@NotBlank`, `@Email`, …); controller methods take `@Valid` on the request body param.
  `ApiExceptionHandler` maps validation failures to the standard `{"error": "..."}` shape.
- **Permissions**: any endpoint that isn't safe for every authenticated user gets
  `@RequirePermission("SOME_PERMISSION_CODE")` (see `com.operion.authorization`).
  New permission codes get seeded via a migration (see above) and, if relevant, granted
  to default roles.
- **Errors**: throw `IllegalArgumentException` for "not found"/bad input,
  domain-specific exceptions for business rule violations — `ApiExceptionHandler`
  (`src/main/java/com/operion/common/api/ApiExceptionHandler.java`) maps the known
  exception types to the right HTTP status; anything unmapped falls through to its
  catch-all 500 handler.
- **Audit logging**: services that create/update/delete meaningful records call
  `AuditLogService` (`com.operion.audit`) the same way existing modules do, so the
  action shows up in `GET /api/v1/audit-logs`.
- **Lists**: most list endpoints intentionally return the full unpaginated collection —
  many are consumed as pickers/lookups by other modules' UIs. Only add `Pageable`/
  `PageResponse<T>` (`com.operion.common.api.PageResponse`) to an endpoint whose result
  set is genuinely unbounded and has no existing "give me everything" consumers —
  check `web/src/api/` for existing callers before changing an endpoint's shape.

## Frontend module

Each backend module typically gets a matching folder under `web/src/modules/<module>/`,
plus a hand-typed API client file under `web/src/api/<module>.ts` (the DTOs there are
not code-generated — `npm run generate:api-types` regenerates `web/src/api/generated-types.ts`
straight from the backend's OpenAPI doc for reference/diffing, but existing modules
haven't migrated to consuming it directly). Gate any UI action behind the same
permission code the backend enforces using `<Can anyOf={[...]}>` (`web/src/auth/Can.tsx`)
— this is UX sugar only, the backend still enforces it independently.

## Running the app while developing

See [docs/setup](../setup) for the native (hot-reload) vs Docker Compose options.
See [docs/testing](../testing) for running the test suites.
