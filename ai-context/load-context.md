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

## Identity model

- `User` = authentication identity, separate from `Person`.
- A person can be student, parent, teacher, staff, admin — and can hold multiple relationships at once (e.g. one person can be a parent of multiple students AND a staff member).
- Don't assume a 1:1 person↔user mapping.

## RBAC

`User → Organisation Membership → Role → Permissions`. Roles should be configurable, not hardcoded. Permissions granular (e.g. `STUDENT_VIEW`, `FEE_COLLECT`, `ATTENDANCE_MARK`) but not overbuilt.

## Academic structure

`Organisation → Academic Year → Grade/Class → Section → Student`. Student's identity ≠ their academic enrollment — enrollment is modeled separately per academic year so history survives promotions/transfers. Never just put a `class_id` column on the student.

## Parent/Guardian model

Many-to-many with students (a parent can have multiple children; a student can have more than 2 guardians). Relationship carries attributes: relationship type, is primary guardian, is emergency contact, can pickup, can receive communication, contact priority.

## Student lifecycle

Admission → Enrollment → Academic Year → Class/Section → Attendance → Exams → Fees → Promotion → Transfer → Graduation. Historical records must be preserved, not overwritten.

## Tech direction

Modular monolith (not microservices) in Java/Spring Boot, MySQL, Redis (only when there's a real use case), Docker, REST APIs, Gradle. Don't add Kafka/event streaming prematurely.

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

Fresh repo (Gradle/Java skeleton only, no domain code yet). First real objective: design the Organisation module and multi-tenant foundation — nothing else — per milestone 1.
