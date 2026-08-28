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

`com.operion.authorization`: `@RequirePermission` (method-level, class-level default; no annotation = reachable by any authenticated org member — deliberate for cross-cutting lookups like Campus/AcademicYear/Person/Permission-catalog listings), `PermissionInterceptor`, `AuthorizationDeniedException` → 403. 66 permission codes across 15 modules (V24, grown since via the organisation-structure/HR migrations, `MEMBERSHIP_VIEW`, and Milestone 4's `INVENTORY_SUPPLIER_MANAGE`/`INVENTORY_CUSTOMER_MANAGE`). An Owner's membership (`OrganisationMembership.isOwner`) bypasses this granular check entirely — see the Milestone 3 section below. The Roles panel's permission-edit dialog groups these into collapsible per-module accordion sections (collapsed by default, auto-expanded only where the role already has a permission checked) — the flat checkbox list stopped being readable once the catalog passed ~60 rows; re-apply the same treatment if a picker UI hits a similar count elsewhere.

**Gotchas**: `@ManyToMany` fields serialized outside a transaction (LAZY default) throw `LazyInitializationException` — use `EAGER` for small bounded catalogs (`Role.permissions`) rather than fighting transaction boundaries. Any new global-looking entity (no own `organisation_id`) needs tenant-leak scrutiny — `OrganisationController`/`UserController` both once returned cross-org data to any authenticated caller before being fixed (org-listing moved behind platform-admin, user-listing scoped through `OrganisationMembership`).

`FEE_DISCOUNT_APPROVE` is seeded but unused — still gated under the coarser `FEE_ASSIGNMENT_MANAGE`. Open, not urgent.

### React admin portal (`web/`)

Vite + React + TS + MUI + React Router v7. Hand-written typed API client (`src/api/`) — an OpenAPI generator (`npm run generate:api-types`) exists for drift-detection only, not source of truth. Nav is RBAC-aware but per-button gating inside modules isn't retrofitted — every action is UI-reachable regardless of caller's permissions (backend still enforces 403).

**Design system**: "school ledger" identity (paper/ink/crimson, monospace headings, tabular numerals, stamp-style `Seal`/`Wordmark`) as a real MUI theme (`theme.ts`), not per-page CSS. Fees got the deepest treatment (semantic status chips, live stat tiles) as the rollout pattern for the rest. Public marketing page at `/welcome`. Content capped at 1280px at the shell level (`AppLayout`/`PlatformLayout`) — as of 2026-08-27, individual pages no longer add their own narrower `maxWidth` on top of that (removed a per-page 640–1100px cap across all 25 module pages so content fills the full shell width), and pages whose `<h1>` just repeated the sidebar nav label (Inventory, HR, Transport, etc.) had that redundant title removed too; detail/create pages kept their real (dynamic) heading. Sidebar is collapsible (`operion.sidebarCollapsed` in `localStorage`). Settings is a landing grid of cards, each opening its own page (`SettingsSectionPage`), not one long stacked form.

Platform-admin frontend (`web/src/platform/`) has its own isolated token store/API client/`PlatformAuthContext`, mirroring the backend's cryptographically separate JWT secret.

**Known gaps**: field-level edit exists now for Business Settings (timezone/currency/date format/working days/logo/color) and Roles (permission checkboxes), but most other modules are still status-toggle/transition-only, never a rename. Attendance has no enroll-student or staff-attendance screen; Fees has no revise-assignment/bounce-payment/refund screens; Examinations has no bulk marks entry or persisted report-card listing; Communication has no admin visibility into other users' data; Transport has no crew/vehicle-reassignment screen; Library has no book search/withdraw UI or fine-by-borrower view; Inventory has no low-stock/reorder concept (schema itself lacks a threshold field) or discontinue action; HR has no staff-attendance screen despite backend support. No production build/deploy story; no parent-portal or payment-gateway frontend. Invite delivery is still manual copy/paste (see #105 below) — no email/SMS transport wired in yet.

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

### Organisation Structure, Dashboard, and staff onboarding (2026-08-27, epic #93 + #30/#97/#103)

`com.operion.organisation` grew `Department`/`Designation` (org-defined structural catalogs, FK'd from `StaffProfile` and `OrganisationMembership` — not free text) and `OrganisationConfiguration` grew business settings (timezone, default currency, date format, working days, logo, primary color) via a new settings panel. `AcademicConfiguration` (school hours) was split out of the core `OrganisationConfiguration` into the `academic` vertical first, to keep `OrganisationConfiguration` industry-neutral per `ai-context/platform-boundaries.md` — see `V31` for the pattern to copy if another School-specific field leaks into a core entity again.

**Global reference-table pattern** (second instance after `Permission`): a closed, tenant-agnostic catalog — plain `BaseEntity` (not `TenantScopedEntity`), Flyway-seeded, `GET`-only controller with no `@RequirePermission`. Used for a new `timezones` table (604 IANA zones, generated straight from the JVM's own `ZoneId.getAvailableZoneIds()` at migration-authoring time so every row is guaranteed valid — see `V36__seed_timezones.sql` for the generation approach if the catalog ever needs regenerating). Reuse this shape for the next global, admin-immutable lookup list rather than inventing a new one.

New `com.operion.dashboard` package (cross-cutting aggregator, documented as a vertical-adjacent exception in `platform-boundaries.md`): `GET /api/v1/dashboard/summary`, gated `ORGANISATION_MANAGE`, read-only rollup across every module (no new tables). It's now the post-login landing route for anyone holding `ORGANISATION_MANAGE` (`IndexRedirect` picks `/dashboard` vs `/students` by permission) and carries a dismissible onboarding checklist (4 steps: structure/roles/members/industry-data configured) computed server-side and auto-hiding once complete.

**Staff login-access** (#103, half of #104's scope): granting a staff member login access is now reachable two ways — a "Grant login access" action on Staff detail (post-creation), or an opt-in checkbox on "Add staff member" (single form submit). Both funnel through the same existing `StaffInviteService.issue()` + `MembershipService.grant()` backend calls `UsersPanel.tsx` already used, via a new shared `StaffInviteDialog` component.

### Milestone 3 — Identity & Access, completed (2026-08-27, epic #89 + #90/#91/#92/#98/#104/#105/#106/#107)

**Generic access-control foundation (#89–#92)**: `OrganisationMembership` grew `isOwner` (V37) — the org's real "\*" capability, checked first in `PermissionInterceptor.preHandle()` before the granular `grantedCodes` lookup, so a permission added to the catalog later automatically covers every existing Owner with no data backfill (the old approach explicitly granted every individual `Permission` row at seed time). `MembershipService.revoke()` now blocks removing the Owner outright, not just "the last systemDefault-role holder." `OrganisationService.seedDefaultRoles()` no longer seeds Teacher/Accountant/Front Desk/Guardian — a fresh org gets only its Owner and creates its own roles afterwards via the already-generic `RoleController`. This required decoupling `PortalInviteService.claim()` from a role literally named "Guardian" first (#91): `Role` grew a second flag, `isManaged` (V38, distinct from `isSystemDefault`), and `claim()` now finds-or-lazily-creates its Guardian role via `roleRepository.findFirstByManaged(true)` instead of a name lookup — keeps working for an org with zero self-created roles. `RoleService` blocks deactivating/stripping the last permission from either `isSystemDefault` or `isManaged` roles.

`MEMBERSHIP_VIEW` (#98, V39) splits member-list read access from `MEMBERSHIP_MANAGE` via a method-level `@RequirePermission` override on `OrganisationMembershipController.list()`; grant/revoke stay behind `MEMBERSHIP_MANAGE` (no distinct "edit" action exists on a membership yet).

**Member management UX (#104/#106/#107)**: `MemberStatus.of(User.status, OrganisationMembership.status)` (`com.operion.authorization`) computes a unified Invited/Active/Inactive status — a PENDING (invited, never claimed) user's membership no longer shows a misleading "Active" chip inherited from the membership row's own default. Rolled out via a new `MemberStatusChip` everywhere a membership is listed. `UsersPanel`'s "Add user" and `StaffCreatePage`'s login section were consolidated into shared `AddMemberFields`/`submitAddMember` (`web/src/components/AddMemberForm.tsx`) — base fields (name, email/phone, optional member ID, campus, optional department, optional joining date, role(s)) used by both; HR-only fields (designation, employment type) stay a separate opt-in extension. Role selection is multi-select (`grantMembership` called once per role) — mandatory in Settings > Users, optional in HR (preserves adding staff with no login). `OrganisationMembership` grew optional `memberId`/`joiningDate` columns (V40), decoupled from `StaffProfile`'s own required equivalents.

**Real email delivery (#105)**: new `com.operion.email` package — `EmailSender` interface (same stub-testability seam as `RazorpayGateway`) with `BrevoEmailSender`/`ResendEmailSender` implementations (`@Order(1)`/`@Order(2)`, RestClient-based, blank-by-default config that fails cleanly until real keys are set, same convention as Razorpay). `EmailDeliveryService.sendBestEffort()` tries each configured sender in order and falls back to the next on failure, recording the outcome in a new `EmailOutbox` table (V41) — never throws, so a provider outage never blocks the invite/verification it's attached to. Wired into `StaffInviteService`/`EmailVerificationService` via a new `app.frontend.base-url` config; the manual copy/paste fallback stays (`StaffInviteDialog` now also shows whether the email actually sent). Kept as its own package rather than reusing `com.operion.communication`'s `NotificationRecipient` outbox, which is a School-vertical concept (Announcement/Person fan-out) per `platform-boundaries.md` — this is generic transactional email with no Person/Announcement to hang off. **Not done**: no actual Brevo/Resend account wired up yet in any real deployment (config is blank until someone sets `BREVO_API_KEY`/`RESEND_API_KEY`); guardian portal invites (`PortalInviteService.issue()`) still hand off the raw token manually, same as before — only staff invites and email verification got real delivery.

### Milestone 4 — Master Data, completed (2026-08-28, #50/#51/#52)

Three small additions to `com.operion.inventory`, done as-scoped after re-confirming the known tension with `erp-system-plan.md`'s "don't build a warehouse/procurement system" warning (Supplier/Customer only exist to feed the not-yet-built Milestones 6/7 — this ticket stayed a plain address-book/master-data addition, no PO/GoodsReceipt/Sales workflow was built). `Item` grew an optional `reorderLevel` (V42, #51) — stored value only, no low-stock reporting logic yet; also the first time `Item` got any post-creation field edit at all (`POST /items/{id}/reorder-level`). `Supplier` (V43-44, #50) and `Customer` (V45-46, #52) both follow the Department/Designation "no dedicated service, status toggles not hard-deletes" shape rather than routing through `InventoryService` (which owns the stock ledger, not plain master-data CRUD). `Customer` optionally links to an existing `Student` or `Guardian` (mutually exclusive, validated in the controller) for purchase-history continuity, or stays a walk-in with just name/phone — laid down for the not-yet-built Sales module (Milestone 7), including a reusable `CustomerPicker` component with no consumer yet. **Not done**: Guardian-linking has no picker UI (no list-all-guardians endpoint exists to power one); no low-stock alerting/reporting built against the new `reorderLevel` field.

---

**Not yet decided** (surface options before picking): backfilling Academic Foundation's test gap vs. `FEE_DISCOUNT_APPROVE` vs. a production build/deploy story vs. per-button RBAC retrofit vs. rolling the Fees module's deeper design-system treatment out to the remaining nine modules. Milestone 3 (Identity & Access, #89–#107) is now fully closed out — next milestone/module choice not yet made.

**Competitive gap analysis (edumerge)**: a session compared Operion against edumerge.com's School ERP feature-by-feature (published as an Artifact, conclusions only — not fetchable from here). Highest-leverage finding, now half-closed: parents/students had no login path at all, which blocked most of the P0/P1 backlog. Parent Portal + Payment Gateway close the identity half. Still not started: the `/api/v1/me/**` read API (the actual payoff), a timetable/period structure (Attendance is capped at daily-only because of this), and bulk data import. The first org-admin dashboard (previously the last item on this list) shipped 2026-08-27 — see the Organisation Structure/Dashboard section above.
