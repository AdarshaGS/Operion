# Load Context — School ERP Platform (Operion)

> Paste/load this file at the start of a session so the assistant has full project context before touching any module.

## What this is

A multi-tenant School ERP SaaS for schools in India. Not a one-off app for a single school — a commercial product, priced per-student/year (₹100–200/student/year range, not finalized). Target: schools with ~500–2,000+ students.

## Vision

One connected system for administration, academics, students, parents, teachers, finance, transport, communication, attendance, and exams — replacing Excel sheets and WhatsApp-based coordination.

Must be: multi-tenant, modular, secure, scalable, auditable, API-first, mobile-friendly, configurable, multi-campus capable.

## Core principle: don't over-engineer

Build module by module. For every feature answer: what problem does it solve, who uses it, what data does it own, what depends on it, is it MVP-required, what's the simplest architecture that supports future growth.

Simple now → extensible later. Not: complex architecture from day one.

## Core architectural concept: Organisation

`Organisation` is the root tenant. Nearly everything (campuses, academic years, users, students, parents, teachers, staff, classes, fees, transport, etc.) is scoped to an organisation.

## Multi-tenancy

Shared database/shared schema with `organisation_id`-based tenant isolation (default choice — don't switch to database-per-tenant without a strong reason). Data across organisations must never leak. Every tenant-scoped table needs a clear boundary; auth/authz/audit must be tenant-aware.

**Implemented**: Hibernate `@TenantId` on a `TenantScopedEntity` base class + a `CurrentTenantIdentifierResolver` reading `TenantContext` (a `ThreadLocal`), verified end to end by `OrganisationTenantIsolationTest`. **Load-bearing gotcha, confirmed by two real bugs**: the tenant identifier resolves once per Hibernate session/transaction, not per query. `TenantContext.set(...)` must happen *before* the transaction/session that will touch a tenant-scoped entity opens — setting it mid-transaction (e.g. after the first repository call already opened one) silently does nothing. Bit both org provisioning (fixed by not wrapping it in one `@Transactional`) and login's own membership check (fixed by setting `TenantContext` right after the org is resolved, before querying `OrganisationMembership`).

## Identity model

- `User` = authentication identity, separate from `Person`.
- A person can be student, parent, teacher, staff, admin — and can hold multiple relationships at once (e.g. one person can be a parent of multiple students AND a staff member).
- Don't assume a 1:1 person↔user mapping.

## RBAC

`User → Organisation Membership → Role → Permissions`. Roles should be configurable, not hardcoded. Permissions granular (e.g. `STUDENT_VIEW`, `FEE_COLLECT`, `ATTENDANCE_MARK`) but not overbuilt.

**Implemented so far**: the data model, plus login and tenant-context wiring only — no permission-level enforcement yet. Auth is JWT (stateless, chosen over server-side sessions to suit the planned SPA + React Native app), issued by `POST /api/v1/auth/login` scoped to one org at a time (slug + email + password → a token embedding both ids, not a separate post-login org-switcher). Password hashing via `spring-security-crypto` (BCrypt) only — deliberately not the full `spring-boot-starter-security`, to avoid its opinionated default filter chain. JWT library is JJWT with the **gson** backend, not jackson (this project runs Jackson 3.x under the `tools.jackson` package per Spring Boot 4.1's rebrand; jjwt-jackson would clash with it). Provisioning an org also creates its first admin login (User + Person + OrganisationMembership on the Org Admin role) — the seeded role needs someone assigned to it. **Next Foundation loose end**: nothing currently checks a caller's actual permissions, only that their token is valid.

## Academic structure

`Organisation → Academic Year → Grade/Class → Section → Student`. Student's identity ≠ their academic enrollment — enrollment is modeled separately per academic year so history survives promotions/transfers. Never just put a `class_id` column on the student.

## Parent/Guardian model

Many-to-many with students (a parent can have multiple children; a student can have more than 2 guardians). Relationship carries attributes: relationship type, is primary guardian, is emergency contact, can pickup, can receive communication, contact priority.

## Student lifecycle

Admission → Enrollment → Academic Year → Class/Section → Attendance → Exams → Fees → Promotion → Transfer → Graduation. Historical records must be preserved, not overwritten.

## Tech direction

Modular monolith (not microservices) in Java/Spring Boot, MySQL, Redis (only when there's a real use case), Docker, REST APIs, Gradle. Don't add Kafka/event streaming prematurely.

Running Spring Boot 4.1 / Hibernate 7.4 (newer than what most docs/training data assume) — it split several autoconfiguration classes into their own per-feature modules that aren't pulled in by the underlying library alone: `spring-boot-hibernate` (`HibernatePropertiesCustomizer`), `spring-boot-data-jpa-test` (`@DataJpaTest`), `spring-boot-flyway` (Flyway autoconfiguration — just having `flyway-core`/`flyway-mysql` on the classpath is *not* enough). If a Spring Boot class "should" be on the classpath but isn't found, check for a newly-split module first before assuming it's misconfigured.

**`@DataJpaTest` gotcha**: the slice has no Jackson `ObjectMapper` bean. A service that (transitively) depends on `AuditLogService` — every module that writes to the shared audit ledger, which by design is most of them — can't be pulled into a test via `@Import(SomeService.class)` if that chain reaches `AuditLogService`. Worse, `@Import`-ing a plain test-local `@Configuration` class (e.g. just to supply an `ObjectMapper` bean) breaks `@EnableAutoConfiguration` base-package detection in this slice with an opaque `Unable to retrieve @EnableAutoConfiguration base packages` failure. The fix used in the Attendance module's tests: construct the service under test by hand (`new AttendanceService(...repos..., new AuditLogService(auditLogRepository, new ObjectMapper()))`) instead of `@Import`-ing it — `AuditLogRepository` itself is fine to `@Autowired` since it's picked up by the slice's normal repository scan. Expect to hit this again in Fees (payments/refunds are audit-heavy by design).

Conceptual module layout:
```
school-erp
├── organisation
├── identity
├── authorization
├── academic
├── student
├── parent
├── attendance
├── examination
├── finance
├── transport
├── communication
├── library
├── inventory
├── reporting
└── common
```

## Database principles

Normalized, explicit FKs, unique constraints, indexes where queries need them, audit fields (`id`, `organisation_id`, `created_at`, `updated_at`, `created_by`, `updated_by`, `status`) — but only where they make sense, not blindly on every table. Soft-delete only where justified. Preserve history where the business needs it.

## API principles

RESTful, versioned (`/api/v1/...`), consistent, validated, authenticated, authorized. Don't design APIs before the domain model is settled.

## Auditability

Track who changed what, when, for which organisation — for meaningful business events (student created/transferred, fee collected/refunded, attendance/marks modified, permissions changed).

## Security

Non-negotiable from day one: auth, authz, tenant isolation, password/session/token security, API security, input validation, SQL-injection/CSRF protection, sensitive data protection, secure config.

## SaaS/billing

`Organisation → Subscription → Plan → Usage → Billing` is a future layer. Keep billing decoupled from module logic now — don't build billing before the core product works.

## Milestone order (re-evaluate before each, not fixed in stone)

1. **Foundation** — Organisation, User, Auth, Org membership, Roles, Permissions, Config, Audit log
2. **Academic Foundation** — Academic Year, Grade/Class, Section, Subject, Teacher assignment
3. **Student Management** — Student, Parent/Guardian, Enrollment, Documents, Lifecycle
4. **Attendance** — Student/staff attendance, reports
5. **Fees** — Fee structure, assignment, collection, receipts, outstanding
6. **Examinations** — Exams, schedules, marks, grades, report cards
7. **Communication** — Notifications, announcements
8. **Transportation** — Bus, route, stops, student assignment, tracking, safety events
9. **Additional ERP** — Library, Inventory, HR, advanced reports

## How to work with me (the user)

- Work incrementally, one module at a time — never generate the whole app.
- When asked to build a module, first cover: business purpose, actors, responsibilities, entity model, relationships, DB schema, constraints, APIs, security considerations, implementation plan, test cases — then wait for a decision before moving on.
- For every entity: ownership, scope (org/campus/academic-year/global), lifecycle, relationships, whether history must be preserved, uniqueness, indexing, authorization, audit needs.
- When multiple valid approaches exist, lay out Option A vs Option B with a recommendation and reason. Priority order: correctness, simplicity, maintainability, security, scalability, performance. Don't optimize for scale the product doesn't have yet.
- GitHub issues follow EPIC → Feature → Sub-feature → Technical task. Meaningful units of work, not hundreds of tiny issues.
- A module is "done" only with: domain model, DB schema, relationships, validation, business rules, authorization, APIs, error handling, audit coverage, tests, migrations, docs, seed data where needed.

## Avoid

Building everything at once; unneeded microservices; generic catch-all tables; overusing JSON columns; hardcoding school-specific workflows or a single academic year; assuming uniform school structure or exactly-two-parents; storing current class directly on student when history matters; mixing auth identity with business entities; fat controllers/services; adding Redis/Kafka without a real need; billing before the core product; dashboards before transactional workflows are stable.

## Current status

Foundation module (milestone 1) is built and tested: Organisation/Campus/AcademicYear/Configuration, User/Person, Role/Permission/OrganisationMembership, AuditLog, all tenant-isolated via Hibernate `@TenantId` — plus a first auth pass (JWT login + tenant-context wiring, **still no permission-level enforcement**). A minimal REST API and a throwaway static-HTML smoke-test page exist to exercise it against real MySQL; the real React admin portal hasn't been started.

Academic Foundation module (milestone 2) is built on top of it as of commit `129ee3e`: `GradeLevel`/`Subject` catalogs, `SchoolClass`/`Section` structure, `ClassSubject` mapping, and history-preserving `TeacherAssignment`, each with repository + REST controller, migrations `V3__academic_schema.sql` and `V4__seed_academic_permissions.sql`. Empty package scaffolding was also added for the remaining milestone modules (student, parent, attendance, examination, finance, transport, communication, library, inventory, reporting).

**Loose end**: test coverage for Academic Foundation is still thin — only `TeacherAssignmentReassignmentTest` exists; `GradeLevel`, `SchoolClass`, `Section`, `Subject`, and `ClassSubject` have no tests of their own (their happy paths are only incidentally exercised as fixtures inside the Student module's tests below).

Student Management module (milestone 3) is built on top of it: `Student` (StudentAdmission fields merged onto it for MVP, per the sign-off recorded in §2.2's "Genuine open decision"), `StudentEnrollment` (insert-only year-by-year history, `is_current` uniqueness enforced in `StudentService`), `StudentDocument` (re-upload supersedes), `StudentExit`, and — in a separate `com.operion.parent` package, matching the conceptual module layout — `Guardian`/`StudentGuardian` (M:N, `is_primary_guardian` uniqueness enforced in `ParentService`). Migrations `V5__student_schema.sql` and `V6__seed_student_permissions.sql`. Test coverage: `StudentEnrollmentLifecycleTest` (promotion insert-only history + exit cascading into enrollment/student status), `StudentGuardianPrimaryTest` (primary-guardian uniqueness), `StudentTenantIsolationTest` (extends the Foundation isolation-test pattern to the new tables) — this module shipped with real coverage from the start, closing the gap Academic Foundation left open above.

Attendance module (milestone 4) is built on top of it, daily-only for v1 (no Timetable/Period structure exists yet for period-level attendance): `ClassAttendanceRegister` (header row per section+day, `DRAFT → SUBMITTED → LOCKED`), `StudentAttendance` (one row per student-enrollment per day, `schoolClass`/`section` snapshotted at marking time rather than derived live), `AttendanceCorrection` (insert-only trail for post-marking edits, mirrored into the shared `AuditLog`), and a genuinely separate `StaffAttendance` (Person/Campus-scoped, check-in/check-out). Migrations `V7__attendance_schema.sql` and `V8__seed_attendance_permissions.sql`. Test coverage: `StudentAttendanceLifecycleTest` (double-marking rejection, register transitions, correction allowed-after-submit/blocked-after-lock incl. the AuditLog mirror), `StaffAttendanceTest` (check-in/out, double-marking rejection), `AttendanceTenantIsolationTest`. Two decisions were made rather than left open: a shared `AttendanceStatus` enum across student/staff, and register locking as an explicit admin action only (no date-based auto-lock, which would need a scheduler that doesn't exist yet).

Fees module (milestone 5) is built on top of it, `com.operion.finance`: `FeeCategory`/`FeeStructure`/`FeeStructureInstallment` (explicit row per class, no wildcard; installments service-validated to sum to the structure amount), `StudentFeeAssignment` (tied to a `StudentEnrollment` not the bare student, `baseAmount`/`effectiveAmount` snapshotted+stored, mutable pre-invoice then superseded post-invoice), `Invoice` (one per assignment+installment, `totalAmount` = installment amount proportionally discounted and stored once at generation, `amountPaid` stored not computed-live), `Payment`/`PaymentAllocation` (one payment can cover multiple invoices, allocation sum must equal payment amount), `Refund` (additive reversal), and `FeeDocumentCounter` (atomic per-org/year invoice/receipt numbering under a pessimistic write lock — infrastructure, not a business entity). All money fields are `BigDecimal`, not `Double` (unlike `Student.entranceScore`, which is a score, not currency — this is a correctness requirement). Migrations `V9__fee_schema.sql` and `V10__seed_fee_permissions.sql`. Test coverage: `FeeStructureTest` (installment-sum validation), `StudentFeeAssignmentTest` (discount requires reason+approver, mutate-pre-invoice/supersede-post-invoice), `InvoiceAndPaymentTest` (proportional discount math, duplicate-invoice rejection, multi-invoice allocation, bounce/refund reverse `amountPaid` without deleting rows), `FeeTenantIsolationTest`.

Full system design (all 9 milestones) is written up in `ai-context/erp-system-plan.md` — read that alongside this file for anything beyond Foundation.

**Not yet decided**: RBAC/permission enforcement (still outstanding since Foundation, now spanning five modules' worth of unchecked permission codes) vs. backfilling Academic Foundation's test gap vs. picking up one of the light-sketch modules (Examinations/Communication/Transportation/Library/Inventory/HR — none deep-designed yet, see erp-system-plan.md §3.3). Surface options before picking.
