# Load Context — School ERP Platform (Operion)

> Load this at the start of a session for full project context. Housekeeping: after filling in `flush-context.md` at the end of a session, fold anything durable back here, then clear `flush-context.md`, then re-run `/graphify`. **Keep this file a compact "current state" doc, not a session-by-session narrative** — git history and the test suite are the record of how we got here; only durable architecture decisions and gotchas that would bite again belong here.

## What this is

Multi-tenant School ERP SaaS for schools in India, priced per-student/year (₹100–200, not finalized). Target: schools with 500–2,000+ students. One connected system for admin/academics/students/parents/teachers/finance/transport/communication/attendance/exams, replacing Excel/WhatsApp coordination. Must be: multi-tenant, modular, secure, scalable, auditable, API-first, mobile-friendly, configurable, multi-campus.

## Core principle

Build module by module. Simple now, extensible later — not complex architecture from day one. For every feature: what problem it solves, who uses it, what data it owns, what depends on it, is it MVP-required, what's the simplest architecture that supports growth.

## Architecture

**Organisation** = root tenant. Nearly everything scopes to it.

**Multi-tenancy**: shared DB/schema, `organisation_id`-based isolation. Hibernate `@TenantId` on `TenantScopedEntity` + `CurrentTenantIdentifierResolver` reading `TenantContext` (a `ThreadLocal`). **Load-bearing gotcha, hit 3 times**: the tenant identifier resolves once per Hibernate session/transaction, not per query — `TenantContext.set(...)` must happen *before* the transaction that will touch a tenant-scoped entity opens, or it silently no-ops. Fixed each time (org provisioning, login's membership check, `BillingService.generateInvoice`) by not wrapping the method in `@Transactional`, so each call gets its own fresh session.

**Identity**: `User` (auth) ≠ `Person` (business identity). One person can be student + parent + staff simultaneously — never assume 1:1 person↔user.

**RBAC**: `User → OrganisationMembership → Role → Permissions`. Configurable roles, granular permissions (`STUDENT_VIEW`, `FEE_COLLECT`, etc). Enforcement is built and closed — see Current Status. Org-wide only for now, no campus-scoping.

**Auth**: JWT (stateless), `POST /api/v1/auth/login` scoped to one org at a time (slug+email+password). BCrypt via `spring-security-crypto` only — deliberately not full `spring-boot-starter-security`, to avoid its default filter chain. JJWT with the **gson** backend, not jackson (this project runs Jackson 3.x under `tools.jackson`; jjwt-jackson would clash).

**Academic structure**: `Organisation → AcademicYear → Grade/Class → Section → Student`. Enrollment is modeled per academic year, separate from student identity — never a bare `class_id` column on Student.

**Parent/Guardian**: many-to-many with students; relationship carries type, is-primary, is-emergency-contact, can-pickup, can-receive-communication, contact-priority.

**Student lifecycle**: Admission → Enrollment → AcademicYear → Class/Section → Attendance → Exams → Fees → Promotion → Transfer → Graduation. History preserved, never overwritten.

## Tech direction

Modular monolith (not microservices), Java/Spring Boot 4.1, Hibernate 7.4, MySQL, Redis only with a real use case, Docker, REST, Gradle. No Kafka/event streaming.

**Spring Boot 4.1 gotcha**: several autoconfig classes split into their own per-feature modules not pulled in by the base library alone — `spring-boot-hibernate` (`HibernatePropertiesCustomizer`), `spring-boot-data-jpa-test` (`@DataJpaTest`), `spring-boot-flyway`, `spring-boot-starter-restclient` (`RestClient.Builder`). If a class "should" be on the classpath but isn't, check for a newly-split module before assuming misconfiguration — read the actual `spring-boot-dependencies` BOM `.pom` in the Gradle cache if search engines don't surface the module name.

**`@DataJpaTest` gotcha**: no Jackson `ObjectMapper` bean in this slice. A service that (transitively) depends on `AuditLogService` can't be pulled in via `@Import` — and `@Import`-ing a test-local `@Configuration` just to supply a bean breaks `@EnableAutoConfiguration` base-package detection. Fix: construct the service under test by hand (real repos autowired, `new AuditLogService(repo, new ObjectMapper())` passed in), not `@Import`.

**Cross-cutting bug patterns worth checking first, not re-diagnosing from scratch**:
- CORS preflight (`OPTIONS`) requests carry no bearer token — any custom auth interceptor needs `OPTIONS` to bypass auth unconditionally, or every cross-origin call breaks with 401.
- An unhandled `DataIntegrityViolationException` (DB unique-constraint violation) leaks as a raw 500 instead of this codebase's `{error: string}` shape unless mapped in `ApiExceptionHandler` — affects any module with a unique constraint.
- `@Query` named parameters need an explicit `@Param` — relying on the `-parameters` javac flag breaks silently if the app is ever launched via an IDE debugger instead of Gradle.
- A service method that mutates a controller-loaded (detached) entity via a setter without calling `repository.save(...)` again is a silent no-op. Write tests that re-query the repository independently, not just assert on the returned object.
- Any endpoint returning a list/lookup needs deliberate tenant scoping even when the entity itself has no `organisation_id` — scope through a tenant-scoped join table instead (e.g. `User` has none, but `OrganisationMembership` does).
- `JwtAuthenticationInterceptor.isPublic()` keeps its own hardcoded allowlist, separate from service-level auth logic — a new public endpoint left off it dies as a silent, bodyless 401 before reaching the controller. Check this first whenever an intentionally-public endpoint 401s unexpectedly.
- Frontend picker/dropdown dialogs that fetch their options once at mount go stale if a sibling panel on the same page adds new data — refetch when the dialog opens, not just at page mount.

Conceptual module layout: organisation, identity, authorization, academic, student, parent, attendance, examination, finance, transport, communication, library, inventory, reporting, common — plus `platform` (SaaS/billing, a deliberately separate plane, see Current Status).

## Database / API / Audit / Security principles

**DB**: normalized, explicit FKs, unique constraints, indexes where queries need them, audit fields (`id`, `organisation_id`, `created_at`, `updated_at`, `created_by`, `updated_by`, `status`) only where they make sense. Soft-delete only where justified. Preserve history where the business needs it.

**API**: RESTful, versioned (`/api/v1/...`), validated, authenticated, authorized. Don't design APIs before the domain model is settled.

**Audit**: track who changed what, when, for which org — for meaningful business events (student created/transferred, fee collected/refunded, marks modified, permissions changed).

**Security**: non-negotiable from day one — auth, authz, tenant isolation, token security, input validation, SQLi/CSRF protection, sensitive data protection, secure config.

**Billing**: `Organisation → Subscription → Plan → Usage → Billing`, kept decoupled from module logic, built after the core product (see Current Status).

## Milestone order (re-evaluate before each, not fixed in stone)

1. Foundation — Organisation, User, Auth, Membership, Roles, Permissions, Config, Audit log
2. Academic Foundation — Academic Year, Grade/Class, Section, Subject, Teacher assignment
3. Student Management — Student, Guardian, Enrollment, Documents, Lifecycle
4. Attendance
5. Fees
6. Examinations
7. Communication
8. Transportation
9. Additional ERP — Library, Inventory, HR, advanced reports

## How to work with me

- One module at a time, never the whole app.
- New module: cover business purpose, actors, entity model, DB schema, constraints, APIs, security, implementation plan, test cases — then wait for a decision before moving on.
- Per entity: ownership, scope (org/campus/academic-year/global), lifecycle, relationships, history-preservation need, uniqueness, indexing, authorization, audit needs.
- Multiple valid approaches → lay out Option A vs Option B with a recommendation. Priority: correctness > simplicity > maintainability > security > scalability > performance. Don't optimize for scale the product doesn't have yet.
- GitHub issues: EPIC → Feature → Sub-feature → Technical task — meaningful units, not hundreds of tiny issues.
- A module is "done" only with: domain model, DB schema, relationships, validation, business rules, authorization, APIs, error handling, audit coverage, tests, migrations, docs, seed data where needed.

## Avoid

Building everything at once; unneeded microservices; generic catch-all tables; overusing JSON columns; hardcoding school-specific workflows or a single academic year; assuming uniform school structure or exactly-two-parents; storing current class directly on student when history matters; mixing auth identity with business entities; fat controllers/services; adding Redis/Kafka without a real need; billing before the core product; dashboards before transactional workflows are stable.

---

## Current status

Full system design for all 9 milestones: `ai-context/erp-system-plan.md` — read alongside this file for anything beyond this summary.

### Backend — all 9 milestones + 5 additional modules, built and tested

organisation/identity/authorization (V1–V2) · academic (V3–V4 — thinnest coverage of any module, the five `changeStatus` methods added later still have no unit tests) · student/parent (V5–V6, V23) · attendance (V7–V8) · finance/fees (V9–V10) · examination (V11–V12) · communication (V13–V14) · transport (V15–V16) · library (V17–V18) · inventory (V19–V20) · hr (V21–V22) · authorization RBAC enforcement (V24) · platform/billing (V25) · parent-portal guardian login (V26–V27) · finance payment gateway (V28).

**Recurring design conventions** (apply by default in new modules rather than re-deciding): one row = one explicit target/scope, no wildcards (`FeeStructure` per class, `ExamSchedule` per class+subject, `Announcement` per audience); require an explicit row over inferring a default (invoice-before-payment, LeaveBalance-before-approval); "one ACTIVE/current row" invariants enforced in the service layer, not the DB (`StudentEnrollment.is_current`, `StudentGuardian.is_primary`, transport assignment, borrow record, `Subscription`); insert-only history tables wherever an audit trail matters; live-computed vs. stored-and-maintained balances is a deliberate per-module scale call (Inventory/HR compute live off ledger sums; Fees stores+maintains `amountPaid` for invoice volume). Money is always `BigDecimal`, never `Double` (marks/leave-days/entrance-score are legitimately `Double`).

### RBAC / permission enforcement — closed

`com.operion.authorization`: `@RequirePermission` (method-level, class-level default; no annotation = reachable by any authenticated org member — deliberate for cross-cutting lookups like Campus/AcademicYear/Person/Permission-catalog listings), `PermissionInterceptor`, `AuthorizationDeniedException` → 403. ~50 permission codes across 11 modules (V24).

**Gotchas**: `@ManyToMany` fields serialized outside a transaction (LAZY default) throw `LazyInitializationException` — use `EAGER` for small bounded catalogs (`Role.permissions`) rather than fighting transaction boundaries. Any new global-looking entity (no own `organisation_id`) needs tenant-leak scrutiny — `OrganisationController`/`UserController` both once returned cross-org data to any authenticated caller before being fixed (org-listing moved behind platform-admin, user-listing scoped through `OrganisationMembership`).

`FEE_DISCOUNT_APPROVE` is seeded but unused — still gated under the coarser `FEE_ASSIGNMENT_MANAGE`. Open, not urgent.

### React admin portal (`web/`)

Vite + React + TS + MUI + React Router v7. Hand-written typed API client (`src/api/`) — an OpenAPI generator (`npm run generate:api-types`) exists for drift-detection only, not source of truth. Nav is RBAC-aware but per-button gating inside modules isn't retrofitted — every action is UI-reachable regardless of caller's permissions (backend still enforces 403).

**Design system**: "school ledger" identity (paper/ink/crimson, monospace headings, tabular numerals, stamp-style `Seal`/`Wordmark`) as a real MUI theme (`theme.ts`), not per-page CSS. Fees got the deepest treatment (semantic status chips, live stat tiles) as the rollout pattern for the rest. Public marketing page at `/welcome`. Content capped at 1280px at the shell level (`AppLayout`/`PlatformLayout`).

Platform-admin frontend (`web/src/platform/`) has its own isolated token store/API client/`PlatformAuthContext`, mirroring the backend's cryptographically separate JWT secret.

**Known gaps**: no true field-level edit anywhere (every mutation is a status toggle/transition, never a rename); Attendance has no enroll-student or staff-attendance screen; Fees has no revise-assignment/bounce-payment/refund screens; Examinations has no bulk marks entry or persisted report-card listing; Communication has no admin visibility into other users' data; Transport has no crew/vehicle-reassignment screen; Library has no book search/withdraw UI or fine-by-borrower view; Inventory has no low-stock/reorder concept (schema itself lacks a threshold field) or discontinue action; HR has no staff-attendance screen despite backend support. No production build/deploy story; no parent-portal or payment-gateway frontend.

### SaaS/Billing (Option B — separate platform-admin plane)

Chose a wholly separate, non-org-scoped identity plane (`PlatformAdmin`, `com.operion.platform`) over a `PLATFORM_ADMIN` permission bolted onto the existing org-scoped auth — keeps "everything tenant-scoped by default" airtight and confines cross-tenant access to one clearly-audited place (avoids repeating the tenant-leak bug shape above). Own JWT secret (`app.jwt.platform-secret`), own interceptor mounted only on `/api/v1/platform/**`, never runs on the same request as the org-scoped auth plane.

`Plan` (catalog) / `Subscription` (insert-only per-org history, one ACTIVE per org, price snapshotted at subscription time so later plan-price changes don't retroactively alter an org's rate) / `PlatformInvoice` (usage folded in via snapshotted `studentCountAtBilling`, no separate `UsageRecord`). No `@RequirePermission` on this plane — just "does this token belong to a `PlatformAdmin`."

Seed: `admin@operion.platform` / `ChangeMe123!` — **rotate before any real deployment**.

**Not built**: self-service billing view for Org Admins; payment gateway for platform billing itself (separate from the fee gateway below).

### Parent Portal — Guardian login (phase 1: login only, no read API yet)

**Key finding that shaped this**: `Person.user` looks like the login mechanism but is dead code (only ever set once at org-provisioning bootstrap, never read for real auth). The actual mechanism everywhere is `OrganisationMembership(user, person, role, campus)` — Guardian portal access reuses that exact path via a new "Guardian" role, not a parallel identity plane. Full design brief for the read API/mobile app: `ai-context/mobile-app-context.md`.

`com.operion.parent`: `PortalInvite` (tenant-scoped, insert-then-claim; `token_hash` bcrypt via the same `PasswordEncoder` as `User.passwordHash`, raw token shown once, never stored/logged; 7-day validity checked live, not a stored status). Issue via staff + `GUARDIAN_MANAGE` (`POST /api/v1/guardians/{id}/grant-portal-access`); claim is public (`POST /api/v1/auth/claim-invite`, org slug travels explicitly since no token exists yet to carry `TenantContext`). Reusing an existing `User` by email never overwrites its password.

"Guardian" role (`PARENT_PORTAL_ACCESS`) auto-seeds for **new** orgs only (`DefaultRoles`) — pre-existing orgs (e.g. `demo-school`) need it created manually via the Roles API.

**Not built**: `/api/v1/me/**` read API scoped to a guardian's linked children via `StudentGuardian` (the actual payoff — nothing can fetch guardian data yet beyond `/auth/me`); no frontend; no student-own login (guardian-only for v1); no automated invite delivery (v1 is a staff member copying a claim link manually).

### Fee Payment Gateway (Razorpay)

**Scope call, confirmed explicitly**: one shared platform Razorpay account for every org, not per-org credentials — per-org is the architecturally correct choice once a second real paying school onboards (a shared account is payment-aggregator territory, a real compliance question in India). Built the shared-account version anyway to prove link → checkout → webhook → `recordPayment` end to end fastest, but credential lookup sits behind one seam (`RazorpayCredentialsProvider.resolveFor(Organisation)`, ignores the parameter today) so swapping later touches one class. **Flag before a second real school onboards.**

`com.operion.finance`: `PaymentGatewayOrder` (tenant-scoped, one per invoice; `link_token` stored plain — same trust model as any real payment link; `amount` is always the invoice's live outstanding balance at checkout time, never a stale snapshot). Flow: `createLink` (staff, `FEE_COLLECT`) → `getLinkStatus`/`initiateCheckout` (public) → `handleWebhook` (public, HMAC-verified — the only path that ever calls `FeeService.recordPayment()`; client-side success callbacks are deliberately never trusted). Idempotent against webhook redelivery.

**Gotcha**: Razorpay's webhook carries only its own order id, no org context — the one deliberate cross-tenant exception in this codebase. `PaymentGatewayOrderRepository.findOrganisationIdByGatewayOrderId` is one native SQL query (bypasses Hibernate's `@TenantId` filter, HQL-only) that resolves just the org id before the real tenant-scoped lookup runs. Every other access to this repository stays tenant-scoped — a narrow, audited exception, not a pattern to reuse.

Config: `app.razorpay.key-id`/`key-secret`/`webhook-secret`, blank by default — app boots fine regardless, calls fail cleanly as a 502 (`PaymentGatewayException`) until real test-mode keys are set.

**Not built/verified**: never exercised against the real Razorpay API, only a hand-written stub in tests; no frontend at all (no "send payment link" staff action, no parent-facing checkout page); no multi-invoice "pay all outstanding" links.

---

**Not yet decided** (surface options before picking): backfilling Academic Foundation's test gap vs. `FEE_DISCOUNT_APPROVE` vs. a production build/deploy story vs. per-button RBAC retrofit vs. rolling the Fees module's deeper design-system treatment out to the remaining nine modules.

**Competitive gap analysis (edumerge)**: a session compared Operion against edumerge.com's School ERP feature-by-feature (published as an Artifact, conclusions only — not fetchable from here). Highest-leverage finding, now half-closed: parents/students had no login path at all, which blocked most of the P0/P1 backlog. Parent Portal + Payment Gateway close the identity half. Still not started: the `/api/v1/me/**` read API (the actual payoff), a timetable/period structure (Attendance is capped at daily-only because of this), bulk data import, and a first org-admin dashboard.
