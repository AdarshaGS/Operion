# Operion — School ERP: Full System Plan

## Context

Operion is a multi-tenant School ERP SaaS for Indian schools (Java 17 / Spring Boot 4.1 / MySQL, modular monolith, REST API). The repo is currently an empty Gradle skeleton (a few misspelled placeholder files under `com.operion.common.orgnasation`, no entities, no migrations). This document plans the entire system — domain model, DB schema, relationships — for review before building module by module.

Agreed scope:
- **Deep design** (full entity model + DB schema + relationships): Foundation (Organisation/Identity/RBAC/Audit/Config), Academic Structure, Student Management, Parent/Guardian, Attendance, Fees.
- **Light sketch only** (entities + how they hook into the above, no schema): Examinations, Communication, Transportation, Library, Inventory, HR, plus the future SaaS/Billing layer.
- **Domain model only** — no concrete API endpoint lists or auth/token strategy decisions yet (deferred to per-module implementation).
- **Frontend/mobile**: backend plan is primary; also includes a short direction for a management/dean web portal and a parent/staff mobile app.

Status: repo survey done, Foundation design done. Academic/Student/Parent and Attendance/Fees+sketches are still being designed — this file is being assembled incrementally.

---

## Repo Notes (from current-state survey)

- `settings.gradle`: `rootProject.name = 'demo'` — not yet renamed.
- `build.gradle`: group `com.operion`, Java 17, Spring Boot 4.1.0. Has `spring-boot-starter-data-jpa`, `spring-boot-starter-webmvc`, `mysql-connector-j`. **Missing**: Flyway/Liquibase, spring-security, spring-boot-starter-validation, Lombok.
- Existing placeholder files are empty and misspelled: `com.operion.common.orgnasation.api.OrganisationAPIResource`, `.service.OrganisationServiceImpl`, `.service.OrganisationSerice` — rename `orgnasation`→`organisation`, `Serice`→`Service` before real work starts.
- No migration tooling, no docker-compose, no datasource config yet.

---

## 1. Foundation Module (Organisation, Identity, RBAC, Audit, Config)

### 1.0 Prerequisite
Add Flyway (`flyway-core` + `flyway-mysql`) to `build.gradle` before creating any table — schema must be migration-driven, not `ddl-auto`, for a multi-tenant production system. Rename the misspelled placeholder package/classes.

### 1.1 Entities

**Organisation** — tenant root, no `organisation_id` column itself.
Fields: `id`, `name`, `legal_name`, `slug` (unique, subdomain/lookup key), `status`, audit fields.
Lifecycle: `TRIAL → ACTIVE → SUSPENDED → ACTIVE` (reversible), `ARCHIVED` (terminal, never hard-deleted — history must stay queryable).
Billing seam: no `Subscription`/`Plan` columns on this table now — future `subscriptions` table FKs to `organisation_id` later, kept decoupled today.

**Campus** — physical branch/location, org-level, 1:many from Organisation.
Fields: `id`, `organisation_id`, `name`, `code`, address fields, `timezone` (nullable, inherits org default), `status`. Never deleted (historical student/staff records reference it).

**Department** / **Designation** — org-defined structural catalogs, part of "Organisation Structure" alongside Campus (see #93/#94). Minimal shape (name + status only, no industry-specific fields), same pattern as GradeLevel/ItemCategory. `StaffProfile.designation`/`department` FK into these instead of free text.
Fields (each): `id`, `organisation_id`, `name`, `status`.

**AcademicYear** (foundation-owned shape; consumption by grades/sections designed in §2) — org-level, 1:many.
Fields: `id`, `organisation_id`, `name` (e.g. "2025-2026"), `start_date`, `end_date`, `is_current`, `status` (`DRAFT → ACTIVE → CLOSED`). Past years never deleted.

**User** — auth identity ONLY, no name/role/business meaning. **Global**, not org-scoped — the same human may authenticate into multiple orgs (a teacher at two schools) with one identity; scoping `User` to an org would force duplicate accounts.
Fields: `id`, `email` (unique), `phone` (unique, nullable), `password_hash`, `status`, `last_login_at`.

**Person** — the human/business record (name, DOB, contact, photo). Org-level. `user_id` nullable (a young student may have no login). Role-specific profiles (Student, Teacher, Staff, ParentGuardian) live in *other* modules and FK to `person_id` — keeps Person from becoming a fat cross-module table.
Fields: `id`, `organisation_id`, `user_id` (nullable), `first_name`, `last_name`, `date_of_birth`, `gender`, `phone`, `email`, `photo_url`, `status`.

**Role** — named permission bundle, org-level, freely configurable (not a hardcoded enum).
Fields: `id`, `organisation_id`, `name`, `description`, `is_system_default` (protects a fallback admin role from deletion/lockout), `status`.

**Permission** — fixed capability catalog (`STUDENT_VIEW`, `FEE_COLLECT`, …). **Global**, seeded via migration, not tenant-editable — tenants configure *which* permissions a role has, never *what permissions exist*. This boundary is what keeps RBAC from turning into EAV.
Fields: `id`, `code` (unique), `module`, `description`.

**OrganisationMembership** — the join answering "is user X part of org Y, as which role, acting as which person, optionally scoped to which campus." A user can hold multiple membership rows in the same org (one per role) — this is how "simultaneously student, parent, teacher" is modeled without a multi-valued role column.
Fields: `id`, `organisation_id`, `user_id`, `person_id`, `role_id`, `campus_id` (nullable = org-wide), `status`.

**AuditLog** — single reusable ledger for "who changed what, when, for which org," designed once and shared by every future module, not just Foundation.
Append-only. Fields: `id`, `organisation_id` (nullable only for pre-org/platform events), `actor_user_id` (nullable), `entity_type` + `entity_id` (polymorphic, no FK by design — audit must survive independent of each module's schema evolving), `action` (free-form code string, not a rigid enum), `before_value`/`after_value` (JSON — the one legitimate JSON use in this module: immutable audit snapshots, not queryable business data), `occurred_at`, `ip_address` (nullable), `metadata` (small JSON, optional).
Events captured here: org status transitions, campus create/status-change, academic year open/close, role/permission changes, membership grant/revoke, login success/failure.

**OrganisationConfiguration** — rarely-changing org settings, split out so the hot-path `organisations` table (looked up on every request by slug) stays narrow. 1:1 with Organisation. Core/generic only — see the platform boundary contract (`ai-context/platform-boundaries.md`).
Fields: `organisation_id` (PK/FK), `timezone`, `default_currency`, `date_format`, `working_days_mask` (bitmask int — not JSON, not a child table, for 7 static days), `logo_url`, `primary_color`.

**AcademicConfiguration** (`com.operion.academic`) — School-vertical settings split out from `OrganisationConfiguration` (`school_start_time`/`school_end_time` are School vocabulary, not core). Same 1:1-keyed-by-organisation-id shape.
Fields: `organisation_id` (PK/FK), `school_start_time`, `school_end_time`, `updated_at`, `updated_by`.

### 1.2 Relationships

| Relationship | Cardinality | Why |
|---|---|---|
| Organisation → Campus | 1:many | Chain schools have multiple branches. |
| Organisation → AcademicYear | 1:many | Years accumulate over time. |
| Organisation → Role | 1:many | Roles are per-org configurable. |
| Organisation → Person | 1:many | People tracked per school. |
| Organisation → OrganisationMembership | 1:many | Every membership belongs to one org. |
| Organisation → OrganisationConfiguration | 1:1 | Narrow settings extension, not repeating. |
| User ↔ Organisation | many:many via OrganisationMembership | Global identity, multi-org capable. |
| User → Person | effectively 1:1 per org | One login → at most one Person per org; Person can exist without a login. |
| Role ↔ Permission | many:many via RolePermission | Roles bundle permissions; permissions reused across roles/orgs. |
| OrganisationMembership → Role/Campus/Person | many:1 (Campus nullable) | One role + optional campus scope per membership row. |
| AuditLog → Organisation | many:1, nullable | Nearly always org-scoped; nullable for platform-level events. |

### 1.3 DB Schema Sketch

```
organisations(id PK, name, slug UNIQUE, status, created_at, updated_at, created_by, updated_by)

organisation_configurations(organisation_id PK/FK, timezone, default_currency, date_format,
  working_days_mask, logo_url, primary_color, updated_at, updated_by)

academic_configurations(organisation_id PK/FK, school_start_time, school_end_time, updated_at, updated_by)

campuses(id PK, organisation_id FK, name, code, address_line1, address_line2, city, state, pincode,
  timezone NULL, status, audit fields)
  UNIQUE(organisation_id, code); INDEX(organisation_id)

academic_years(id PK, organisation_id FK, name, start_date, end_date, is_current, status, audit fields)
  UNIQUE(organisation_id, name); INDEX(organisation_id, is_current)  -- hot path: "current year for org"

users(id PK, email UNIQUE, phone UNIQUE NULL, password_hash, status, last_login_at, created_at, updated_at)

persons(id PK, organisation_id FK, user_id FK NULL, first_name, last_name, date_of_birth, gender,
  phone, email, photo_url, status, audit fields)
  UNIQUE(organisation_id, user_id); INDEX(organisation_id)

roles(id PK, organisation_id FK, name, description, is_system_default, status, audit fields)
  UNIQUE(organisation_id, name)

permissions(id PK, code UNIQUE, module, description)   -- seed data, no organisation_id

role_permissions(role_id FK, permission_id FK, PK(role_id, permission_id))

organisation_memberships(id PK, organisation_id FK, user_id FK, person_id FK, role_id FK,
  campus_id FK NULL, status, audit fields)
  INDEX(organisation_id, user_id)  -- most important index: permission checks + "users in org"
  INDEX(user_id)                  -- "orgs a user belongs to" (org switcher)
  UNIQUE(organisation_id, user_id, role_id, campus_id) -- NULL campus_id: enforce true single-null
                                                        -- membership at app layer (MySQL treats
                                                        -- NULLs as distinct in unique indexes)

audit_logs(id PK, organisation_id NULL, actor_user_id FK NULL, entity_type, entity_id, action,
  before_value JSON NULL, after_value JSON NULL, metadata JSON NULL, occurred_at, ip_address NULL)
  INDEX(organisation_id, entity_type, entity_id)  -- "history of this record"
  INDEX(organisation_id, occurred_at)             -- "org's recent activity feed"
  INDEX(actor_user_id)                            -- "everything this user did"
```

### 1.4 Tenant Isolation

**Recommendation: Hibernate `@TenantId` + `CurrentTenantIdentifierResolver`** (Spring Boot 4.1 ships Hibernate 6.6, which supports this). A request-scoped holder is set once (right after auth resolves the caller's org), and Hibernate auto-injects `organisation_id = :tenantId` into every query for `@TenantId`-annotated entities — impossible to forget per-repository-method.

Kept as defense-in-depth regardless: `organisation_id NOT NULL` + FK on every tenant table, so even a missed filter can't write into the wrong tenant. Alternative considered: a `TenantScopedRepository` base forcing `organisation_id` as a mandatory parameter on every finder — simpler to reason about, but purely discipline-based (one missed method = a real leak). Use Hibernate filtering as the primary mechanism, the base-repository pattern as a secondary safety net for native/batch queries that bypass the ORM.

**Spike result (done)**: verified against the actual Spring Boot 4.1 / Hibernate 7.4 in this repo (`OrganisationTenantIsolationTest`) - `@TenantId` + a `CurrentTenantIdentifierResolver` bean wired via `HibernatePropertiesCustomizer` works as intended: an inserted `Campus` is auto-stamped with the current `TenantContext` organisation id, and `CampusRepository.findAll()` auto-filters to it, with zero per-repository-method code. One real constraint the spike surfaced: **the tenant identifier resolves once per Hibernate session, at session-open time** - not per query. In production this is a non-issue (each request/transaction opens its own EntityManager, so a filter/interceptor setting `TenantContext` before the transaction starts works correctly per request); it only bit the *test*, which had to force each repository call onto its own transaction (`@Transactional(propagation = NOT_SUPPORTED)`) to simulate separate requests. Worth remembering once the auth filter is built: `TenantContext` must be set **before** the request's transaction/EntityManager opens, not lazily mid-request.

### 1.5 RBAC Mechanics

- `Permission` catalog is closed and code-owned (migration-seeded). Tenants never invent new permission types, only new combinations via `RolePermission`.
- `Role` is org-owned, freely nameable. `RolePermission` is the only tenant-configurable surface — still a plain many:many join, not a generic attribute table.
- Provisioning: seed a fixed starter role set ("Org Admin", "Teacher", "Accountant", "Front Desk") with default `RolePermission` rows via an app-level constant list at org creation — not a nullable-organisation_id "template role" pattern, which would force nullable-org special-casing everywhere. `is_system_default=true` on the admin role; block removing all its permissions so an org can't lock itself out.
- Runtime check: one indexed join — `organisation_memberships(user_id, organisation_id, status=ACTIVE) → roles → role_permissions → permissions.code`. No caching layer (no Redis) needed yet — revisit only if profiling says otherwise.

### 1.6 Security Seams (noted, not designed here)

- Passwords: bcrypt/argon2 hash only. `spring-security` + `spring-boot-starter-validation` need adding to `build.gradle` when auth work starts.
- Session/token strategy (JWT vs server-side session) — open decision, deferred (affects whether `users` needs a `sessions`/`refresh_tokens` table).
- `organisation_id NOT NULL` doubles as both a schema rule and a security guarantee (§1.4).
- Status/lifecycle transitions validated in the service layer against an explicit allowed-transition map (prevents e.g. `ARCHIVED → TRIAL`).

### 1.7 Open Decisions

1. **Campus optionality** — recommend: every org gets a default "Main Campus" at provisioning (avoids null-campus special-casing spreading into Attendance/Fees later).
2. **AcademicYear scope** — recommend: org-level, not per-campus, until a real multi-calendar chain shows up.
3. **Person scope** — recommend: org-scoped (cross-org person merging is speculative/YAGNI right now).
4. **Tenant isolation mechanism** — recommend Hibernate `@TenantId` (pending version spike).
5. **JWT vs session** — no recommendation yet, genuinely deferred to auth implementation.
6. **AcademicYear package placement** — recommend keeping lifecycle/shape in `foundation`; `academic` module depends on it, doesn't own it.

---

## 2. Academic Structure, Student Management, Parent/Guardian

Cross-cutting pattern used consistently across all three sub-modules below (and worth naming once rather than reinventing per table): **"current state" is an explicit flag/pointer for cheap reads; history is insert-only rows.** Every place a real business fact must survive time (enrollment, teacher assignment) gets its own insert-only table with an `is_current`/`status` flag enforced in a service-layer transaction — the same convention `AcademicYear.is_current` already uses in §1. Soft attributes (occupation, relationship type) are allowed to mutate in place, relying on the Foundation `AuditLog` for a change trail.

### 2.1 Academic Structure

**GradeLevel** (catalog, org-scoped, NOT year-scoped) — master list of grades a school can offer (Nursery…Grade 12). Decoupled from year-instances so promotion logic has a stable ordering to walk. Fields: `id, organisation_id, name, sequence_order, stage (nullable), status`. Unique `(organisation_id, sequence_order)`, `(organisation_id, name)`.

**AcademicYear** — entity shape/lifecycle owned by Foundation (§1.1); consumed here. Confirmed org-level (not per-campus) by both the Foundation and Academic design passes — one calendar per org until a real multi-campus-different-calendar case appears. "Current year" is an explicit `is_current` flag, not date-derived (schools work in next year's admissions before the calendar range starts, so date-derivation gives the wrong answer during transition windows). Overlap/uniqueness enforced at the service layer inside a transaction (MySQL has no exclusion constraints) backed by index `(organisation_id, start_date, end_date)`.

**SchoolClass** — a grade instantiated within a year+campus ("Grade 5 at Campus X in AY2025-26"). Exists *within* a year so structure can change year to year without touching prior years. Fields: `id, organisation_id, academic_year_id, campus_id, grade_level_id, display_name (nullable), status`. Unique `(academic_year_id, campus_id, grade_level_id)`.

**Section** — `id, organisation_id, school_class_id, name, capacity (nullable), room (nullable), status`. Unique `(school_class_id, name)`. Capacity enforced at the app layer (soft limit schools routinely override), not a DB trigger. Homeroom teacher is deliberately **not** a raw FK column here — see TeacherAssignment, to stay consistent with the module's own history rule.

**Subject** (catalog, org-scoped, not year-scoped) — `id, organisation_id, name, code (nullable), is_elective, status`.

**ClassSubject** — subject-to-class mapping, at `school_class_id` level (all sections of a grade share the subject list; per-section electives are a future `StudentSubjectSelection` concern, not built now). Fields: `id, organisation_id, school_class_id, subject_id, is_mandatory, status`. Unique `(school_class_id, subject_id)`.

**TeacherAssignment** — unifies subject-teacher AND homeroom-teacher, insert-only history. This is the general fix for "teacher assignments must preserve history across changes," applied once rather than solved per-role. Fields: `id, organisation_id, academic_year_id (denormalized off section→class→year — documented, intentional, avoids a 3-way join on every "assignments for year X" report), section_id, subject_id (nullable = homeroom role), teacher_person_id (FK Person), assignment_type (HOMEROOM/SUBJECT/CO_TEACHER), start_date, end_date (nullable = ongoing), status (ACTIVE/ENDED)`. Mid-year teacher change = close the row (`end_date`, `status=ENDED`) + insert a new one — never UPDATE `teacher_person_id` in place. Indexes: `(section_id, status)`, `(teacher_person_id, status)`, `(academic_year_id)`.

*Frequent queries → indexes*: current year for org → `AcademicYear(organisation_id, is_current)`; subjects for a class → `ClassSubject(school_class_id)`; current teacher for a section/subject → `TeacherAssignment(section_id, subject_id, status)`; all active assignments for a teacher → `TeacherAssignment(teacher_person_id, status)`.

*Open decisions*: (1) homeroom via TeacherAssignment — recommended, vs a simple FK column on Section (simpler but reintroduces the "current pointer" anti-pattern); (2) ClassSubject at class-level (recommended) vs section-level for electives — defer electives to a future entity; (3) Section capacity app-check (recommended) vs DB trigger.

### 2.2 Student Management

**Student** — the school-specific extension of a Foundation `Person` (1:1). Holds attributes that don't change every year — NOT the year-by-year placement (that's StudentEnrollment). Fields: `id, organisation_id (denormalized off person_id for tenant-query performance), person_id (FK, unique), admission_number (unique per org), status, blood_group (nullable), category (nullable, access-controlled — RTE/reservation category), nationality, remarks`. Lifecycle: `ADMITTED → ACTIVE → {TRANSFERRED_OUT | GRADUATED | WITHDRAWN} → ALUMNI`.

*Open decision — flagged strongly*: should an `ENQUIRY` pre-admission stage live in this module? **Recommend no** — Student starts at `ADMITTED`; a prospect/admissions-funnel pipeline is a distinct future "Admissions" module, and building it now just because a state name suggested it is exactly the over-engineering trap the project's own principles warn against.

**StudentEnrollment** — the year-by-year placement, THE core historical entity, insert-only across years (this is what makes "2025-26 Grade 4 Section B → 2026-27 Grade 5 Section A" two rows, never one mutated `class_id`). Fields: `id, organisation_id, student_id, academic_year_id, section_id, roll_number (nullable), enrollment_status (ENROLLED/PROMOTED/REPEATED/TRANSFERRED/WITHDRAWN/GRADUATED), is_current, enrolled_date, exit_date (nullable)`. Unique `(student_id, academic_year_id)`.

Promotion mechanics (insert-only, recommended): close the old row (`enrollment_status=PROMOTED, is_current=false, exit_date=<year end>` — bookkeeping fields only, the substantive academic facts for that year stay untouched) + insert a new row for the new year/section. Repeating a grade uses the same insert with `enrollment_status=REPEATED`. `is_current` uniqueness per student enforced in the same transaction (same pattern as `AcademicYear.is_current`).

Indexes: `(student_id, is_current)` → "get a student's current class" (single-row lookup); `(section_id)` → "class roster for a year" (a Section already belongs to exactly one year, no extra join); `(organisation_id, academic_year_id)` → org-wide year reports.

Mid-year section reassignment (rare edge case, deliberately lightweight): **recommend mutating `StudentEnrollment.section_id` on the current row**, relying on Foundation `AuditLog` for the trail — a dedicated insert-only sub-history table is a premature abstraction for something that happens a handful of times a year; upgrade only if "which section on date Y" becomes a real reporting need.

**StudentAdmission** — admission-specific data (admission_date, source, previous school, TC number, entrance score, etc.). *Genuine open decision, flagged rather than decided*: merge onto `Student` (simplest) vs. a separate insert-only 1:many entity (correctly models re-admission after withdrawal — a real Indian-school scenario). Recommend the separate entity **if** re-admission is in scope, otherwise the merged columns are fine for MVP.

**StudentDocument** — `id, organisation_id, student_id, document_type, file_reference (metadata only — DB never stores file bytes), file_name, mime_type, uploaded_at/by, verification_status (PENDING/VERIFIED/REJECTED), verified_by/at, status`. Re-upload = new row, old row `status=SUPERSEDED` — never overwrite `file_reference` in place (inspection/compliance trail).

**StudentExit** — insert-only, no one-per-student uniqueness (a student can withdraw and later re-admit). `id, organisation_id, student_id, exit_type (TRANSFER/WITHDRAWAL/GRADUATION/EXPULSION), exit_date, reason, destination_school (nullable), initiated_by`.

*Constraints*: `admission_number` unique per org; exactly one `is_current=true` StudentEnrollment per active student, enforced in the service layer; promotion must target a non-CLOSED year with correct grade sequencing unless `REPEATED`; `roll_number` unique per section when not null.

### 2.3 Parent/Guardian Management

**Guardian** — a thin extension of `Person` (1:1, reuses name/phone/email/address from Foundation rather than duplicating contact data — avoids the classic "two places hold the phone number, they drift" bug). Fields: `id, organisation_id, person_id (FK, unique), occupation (nullable), status`. Created lazily the first time a Person is linked to a student as a guardian.

**StudentGuardian** — the many-to-many with relationship attributes, exactly as specified in the product brief. Fields: `id, organisation_id, student_id, guardian_id, relationship_type (FATHER/MOTHER/LEGAL_GUARDIAN/GRANDPARENT/SIBLING/OTHER/EMERGENCY_CONTACT_ONLY), is_primary_guardian, is_emergency_contact, can_pickup, can_receive_communication, contact_priority (int), status`. Unique `(student_id, guardian_id)`. `is_primary_guardian` uniqueness per student enforced in a service-layer transaction — same convention as `AcademicYear.is_current` / `StudentEnrollment.is_current`. Booleans are intentionally orthogonal (no correlation constraint — a primary guardian can lack pickup rights under a custody order; an emergency contact needn't be a legal guardian).

Indexes: `(student_id)` → "all guardians for a student" (the hottest query in the module: report cards, receipts, pickup verification, comms fan-out); `(guardian_id)` → "all children for a guardian" (needed once a parent portal exists); `(student_id, contact_priority)` → ordered emergency-contact list.

**Parent-portal seam**: no new FK needed. `Guardian.person_id → Person`, and `Person ↔ User` is entirely Foundation's existing concern. Portal access later reuses that seam plus a `PARENT` role in the existing `OrganisationMembership → Role → Permission` chain — zero speculative schema added now for a feature that's explicitly future.

*Constraints*: zero guardians per student is **allowed at the DB level** (hostel wards, adult students, migration gaps would be broken by a hard minimum) — enforce "add a guardian" as a soft admission-workflow nudge, not a DB rule. No max-guardian limit (extended-family pickup arrangements routinely exceed two — don't hardcode father+mother).

### 2.4 Cross-module note

Three modules independently converged on the same **"explicit current/primary flag + service-layer transaction"** pattern (`AcademicYear.is_current`, `StudentEnrollment.is_current`, `StudentGuardian.is_primary_guardian`). Worth treating as one named, documented convention going forward — Attendance's "current term" and Fees' "current fee structure" (below) should follow it too rather than each reinventing the mechanism.

---

## 3. Attendance & Fees (deep design) + Light Sketches (Exams, Communication, Transport, Library, Inventory, HR)

### 3.1 Attendance

**Decision: daily-only for v1.** Period-level attendance needs a Timetable/Period structure that doesn't exist yet and multiplies row volume by periods/day. The extensibility seam is architectural, not columnar: a future `StudentPeriodAttendance` table would key off `(student_enrollment_id, attendance_date)` plus a period reference, added as a new table once Timetable exists — not by bolting nullable `period_id` columns onto today's table speculatively.

**StudentAttendance** — one row per `student_enrollment_id` per date (tied to the *enrollment*, not the student directly — a re-enrolling student gets a fresh attendance history next year). Fields: `id, organisation_id, student_enrollment_id, academic_year_id (denormalized), class_id, section_id` (**snapshotted at marking time**, not derived live — if a student transfers sections mid-year, historical attendance reflects where they actually sat that day). `attendance_date`, `attendance_status` (PRESENT/ABSENT/LATE/HALF_DAY — deliberately named `attendance_status`, not the generic `status` column, to avoid colliding record-lifecycle-status with domain value), `is_excused`, `remarks`, `marked_by`, `marked_at`. No `HOLIDAY` value — holidays are a calendar concept where no row is created at all.

**ClassAttendanceRegister** — header row per class+section+day (`register_status` DRAFT/SUBMITTED/LOCKED). Justifies a lock policy ("attendance older than 3 days needs admin override") and a "classes pending attendance today" dashboard without an existence-scan — a narrow, purpose-built table, not a generic catch-all.

**AttendanceCorrection** — every edit to `StudentAttendance.attendance_status` after initial marking writes one of these (`previous_status, new_status, reason, corrected_by, corrected_at`) in the same transaction, then mutates the `StudentAttendance` row in place. Two-layer audit: this typed table serves the attendance-specific "correction history" UI; a parallel entry also goes to Foundation's generic `AuditLog` for cross-entity search — don't build a second bespoke generic-audit mechanism. Event-sourced/append-only versioning was considered and rejected as over-engineering for a low-frequency, manually-edited record.

**StaffAttendance** — a genuinely separate table from StudentAttendance (different FK target — Person/Membership, not StudentEnrollment; different fields — `check_in_time`/`check_out_time`; different reporting axis — campus-wide). A polymorphic "party_type/party_id" merge was considered and rejected as exactly the generic-catch-all anti-pattern the project avoids.

Key indexes: `student_attendance` unique `(organisation_id, student_enrollment_id, attendance_date)` + index `(organisation_id, class_id, section_id, attendance_date)` for daily register/defaulter aggregation + `(organisation_id, academic_year_id, attendance_date)` for monthly rollups. `staff_attendance` unique `(organisation_id, person_id, attendance_date)` + `(organisation_id, campus_id, attendance_date)` for per-campus reports.

### 3.2 Fees

This module handles money — baseline rule throughout: **payments and refunds are never edited or deleted; a wrong entry is voided and re-entered.**

**FeeCategory** — org-scoped lookup (Tuition, Transport, Lab, Exam Fee…). Unique `(organisation_id, code)`.

**FeeStructure** — amount of category X for class Y in year Z. Requires an **explicit row per class**, not a nullable "applies to all classes" wildcard — a wildcard needs fallback/precedence resolution logic at read time, which is the same implicit-workflow anti-pattern the project avoids elsewhere. Unique `(organisation_id, academic_year_id, class_id, fee_category_id)`.

**FeeStructureInstallment** — child rows (`installment_number, due_date, amount`) — explicit uneven splits (schools commonly split 40/30/30, not even thirds), validated server-side that the sum equals `FeeStructure.amount`.

**StudentFeeAssignment** — links a student's enrollment to a fee structure, carrying any discount. Fields include `base_amount` (**snapshotted** from `FeeStructure.amount` at assignment time — a later class-wide rate edit must never silently change what an already-committed student owes) and `effective_amount` (**stored, not a generated column** — forces any revision through an explicit, audited action rather than silently recomputing). `discount_reason` + `approved_by` required if discounted, gated by a distinct `FEE_DISCOUNT_APPROVE` permission separate from `FEE_COLLECT` (front-desk staff must never self-approve a scholarship). Mutable pre-invoice; must be superseded (new row, old row `SUPERSEDED`) rather than mutated once an invoice has been generated off it — protects invoice provenance once money is on the line.

**Invoice** — `invoice_number` via a per-`(organisation_id, academic_year_id)` atomic counter table (never `SELECT MAX()+1`, which races under concurrency). **Outstanding balance is a stored, indexed `amount_paid` column (`outstanding = total_amount - amount_paid`), not computed live via SUM** — this is the module's central design call: outstanding-by-class/collection-by-date-range reports need to scan thousands of invoices, and a stored column makes that a plain indexed range query. Drift risk is mitigated by exactly one transactional writer (payment recording updates `amount_paid` in the same transaction as the ledger write) plus a periodic reconciliation job comparing it against `SUM(PaymentAllocation) − SUM(Refund)` and alerting on drift — the ledger table remains the source of truth; the stored balance is a maintained projection (same pattern as a bank's cached balance vs. its transaction ledger).

**Payment** — an actual money-received event; `status` includes `CLEARED`/`BOUNCED` (a payment isn't final just because it was recorded — cheques bounce). **Receipt = Payment, not a separate entity** — a receipt is a rendering of a Payment plus its allocations; `receipt_number` lives directly on Payment (same per-org/year counter approach as invoices); reprints re-render existing data.

**PaymentAllocation** — the actual ledger join (`payment_id, invoice_id, allocated_amount`); a single payment can cover multiple invoices (lump-sum covering last month's balance + this month's installment is routine). This table, not `Invoice.amount_paid`, is the source of truth backing the cached balance above. Advance/credit payments with no invoice yet: **recommend requiring an invoice to exist first**, not building an unallocated-credit/wallet ledger speculatively — matches how schools actually generate invoices ahead of collection.

**Refund** — always references an original `Payment`/`Invoice` (never free-floating), requires `reason` + `approved_by`; an additive reversing event, never an edit/delete of the original payment.

**Billing seam note**: this is money the *school* collects from *parents* — a completely separate domain from Operion's own future SaaS billing (`Organisation → Subscription → Plan → Usage → Billing`, money the school pays Operion). Same vocabulary, no shared tables, no shared code paths.

*Open decisions*: (1) invoice numbering per-year (recommended) vs global sequence; (2) stored+reconciled outstanding balance (recommended) vs purely computed; (3) fee assignment mutable-pre-invoice/versioned-post (recommended) vs always-versioned; (4) require invoice before payment (recommended) vs a credit/wallet ledger.

### 3.3 Light Sketches — build later, not now

- **Examinations** — `Exam`, `ExamSchedule`, `MarksEntry`, `GradingScale`, `ReportCard`; hooks into `StudentEnrollment`, `Subject`, `Section`, `TeacherAssignment`. Biggest risk: report-card layout/weightage is deeply board-specific (CBSE/ICSE/State) — ship one or two concrete formats, don't build a generic template engine speculatively.
- **Communication** — `Announcement`, `NotificationTemplate`, `NotificationRecipient`, `NotificationPreference`; hooks into `User`/`Person`/`OrganisationMembership` and `Section`/`Student` for audience targeting. Biggest risk: outbound delivery is async/third-party-dependent — a DB-backed outbox + polling worker is enough, still no Kafka.
- **Transportation** — `Vehicle`, `Route`, `RouteStop`, `StudentTransportAssignment`, `TripLog`; hooks into `StudentEnrollment`, `Campus`, and a shared `FeeCategory` for the transport fee line. Biggest risk: real-time GPS ingestion is a fundamentally different workload than the rest of this CRUD-shaped ERP — evaluate it as its own concern when actually built, don't force it into the synchronous request model.
- **Library** — `Book`, `BookCopy`, `BorrowRecord`, `Fine`; borrower is `Person` (students AND staff). Biggest risk: resist an over-rich library-science model (acquisitions, vendors, multi-branch transfers) — this is a school library, not a public one.
- **Inventory** — `Item`/`ItemCategory`, `StockEntry`, `StockIssue`, `StockAdjustment`; scoped per `Campus`. Biggest risk: don't build a warehouse/procurement system (multi-location transfers, vendor POs) unless a school actually needs it — most school inventory is just stock-in/stock-out/balance.
- **HR** — `StaffProfile` (extends Person), `LeaveType`, `LeaveRequest`/`LeaveBalance`, `StaffDocument`; hooks into `Person`, `OrganisationMembership`, and `StaffAttendance` (approved leave should be able to set attendance to `LEAVE` — a future seam, don't build the leave workflow now, just don't let the attendance enum block it later). Biggest risk: payroll is a deep statutory-compliance domain (PF/ESI/TDS) — treat as external-system-integration territory unless a later, deliberate decision commits to building it in-house.

---

## 4. Frontend & Mobile Direction

Backend (above) is the primary deliverable; this section is intentionally a direction, not a design — revisit in real depth only once the API layer it depends on actually exists.

**Personas → clients**:
- **Web admin portal** — management/dean/school-admin/accountant/front-desk. CRUD-heavy, desk-based, many screens (the classic "admin panel" shape).
- **Mobile app (parents + staff)** — one app, role-gated views. Parents: attendance/fee/exam visibility, notifications, (later) transport tracking. Staff/teachers: mark attendance, view their timetable/assignments, receive/send communication. Same login mechanism, different screens by `OrganisationMembership` role — do not build two separate apps for parent vs. staff, the role already exists in the data model to gate views within one app.

**Recommended stack**:
- **Web**: React + TypeScript SPA, calling the versioned REST API (`/api/v1/...`) directly — no separate BFF needed at this scale. A component library (MUI or Ant Design) to move fast on CRUD screens rather than hand-building form/table primitives.
- **Mobile**: one cross-platform codebase rather than native iOS+Android separately. **React Native recommended to start** — shares TypeScript types/DTOs with the web portal, and the team is already building in React for the web app, so there's no separate skill investment. Flutter is the alternative if native performance/offline capability becomes a real, measured problem (e.g., the Transport safety module's live tracking) — don't switch preemptively.
- **Auth**: whatever token/session strategy Foundation settles on (§1.6, still open) — both clients are pure API consumers, so this is a single decision, not one per client.
- **Push notifications**: Firebase Cloud Messaging, one integration serving both Android/iOS for the Communication module's outbound alerts.
- **Offline**: not needed for v1 — admissions/fees/attendance are done with connectivity present. The one place it's a genuine future concern is Transport's live tracking in low-connectivity areas; treat it as that module's problem to solve when it's actually built, not a cross-cutting requirement now.

---

## 5. Milestone Sequencing & Next Steps

**Build order** (matches the original milestone plan, confirmed by this design — nothing here changes the sequencing, it just makes each step concrete):

1. **Foundation** (§1) — Organisation, Campus, AcademicYear (shape only), User, Person, Role, Permission, OrganisationMembership, AuditLog, OrganisationConfiguration. Everything else FKs into this — build it first, in full, before touching Academic/Student.
2. **Academic Structure** (§2.1) — GradeLevel, SchoolClass, Section, Subject, ClassSubject, TeacherAssignment.
3. **Student + Parent/Guardian** (§2.2–2.3) — Student, StudentEnrollment, StudentDocument, StudentExit, Guardian, StudentGuardian.
4. **Attendance** (§3.1) — StudentAttendance, ClassAttendanceRegister, AttendanceCorrection, StaffAttendance.
5. **Fees** (§3.2) — FeeCategory, FeeStructure, StudentFeeAssignment, Invoice, Payment, PaymentAllocation, Refund.
6. **Later** (§3.3, light sketch only until picked up) — Examinations, Communication, Transportation, Library, Inventory, HR, in whatever order actual usage/priority dictates.

**Prerequisites before any code lands** (apply regardless of which module is picked up first):
- Rename the misspelled placeholder package/classes (`orgnasation`→`organisation`, `Serice`→`Service`).
- Add Flyway (`flyway-core`, `flyway-mysql`) to `build.gradle` — schema must be migration-driven from the first table.
- Add `spring-boot-starter-validation` and `spring-security` when Foundation's auth work starts.

**Decisions that need your sign-off before/while building Foundation** (full reasoning is in each module's "Open decisions" — this is just the checklist):
- Campus optionality (default "Main Campus" per org — recommended).
- Tenant isolation mechanism (Hibernate `@TenantId` — recommended, pending a version-compatibility spike).
- JWT vs. session-based auth (genuinely undecided, needed before Foundation's auth layer is built).
- `ENQUIRY` admissions stage — in Student module or deferred to a future Admissions module (recommended: deferred).
- StudentAdmission — merged onto Student vs. separate re-admission-capable entity (flagged, no default).
- Fee invoice/receipt numbering scope — per-year (recommended) vs. global.

**Immediate next step**: once you've reviewed this document, the first real implementation task is the Foundation module — starting with the Organisation/Campus/AcademicYear/User/Person schema and the tenant-isolation spike, since every other module in this plan depends on it existing first.
