# Operion

A multi-tenant School ERP platform for schools in India — one connected system for
administration, academics, students, parents, teachers, finance, transport,
communication, attendance, and exams.

Backend: Java 17 / Spring Boot 4.1 / Hibernate 7.4 / MySQL / Flyway, built as a modular
monolith (`Organisation` as the root tenant, `organisation_id`-based tenant isolation).
Frontend: React 19 / TypeScript / Vite / MUI, a SPA admin portal calling the API
directly (no BFF).

Ten backend modules are built end to end: Foundation, Academic Foundation, Student
Management, Attendance, Fees, Examinations, Communication, Transportation, Library,
Inventory, and HR — each with a matching frontend module under `web/src/modules/`.
See `ai-context/erp-system-plan.md` and `ai-context/load-context.md` for the full
design and current status.

## Prerequisites

- Java 17 (JDK)
- MySQL 8+, running locally
- Node.js 18+ and npm (for the frontend)

The backend uses the bundled Gradle wrapper (`./gradlew`) — no separate Gradle install
needed.

## 1. Database

Create an empty database. The name must match `DB_URL` below (defaults to `operion`):

```bash
mysql -uroot -e "CREATE DATABASE operion;"
```

Schema is Flyway-managed — migrations in `src/main/resources/db/migration/` run
automatically on backend startup. Nothing to run by hand.

## 2. Backend

Configuration lives in `src/main/resources/application.properties`, all overridable via
environment variables:

| Variable | Default | Notes |
|---|---|---|
| `DB_URL` | `jdbc:mysql://localhost:3306/operion` | |
| `DB_USERNAME` | `root` | |
| `DB_PASSWORD` | *(empty)* | |
| `JWT_SECRET` | a dev-only placeholder | **must** be overridden for any real deployment |
| `JWT_EXPIRATION_MINUTES` | `480` | |
| `app.cors.allowed-origins` | `http://localhost:5173` | the Vite dev server origin |

Run it:

```bash
./gradlew bootRun
```

The API comes up on `http://localhost:8080`.

Run the test suite:

```bash
./gradlew test
```

### First login

Every organisation is a tenant, and its first admin account is created together with
it — there's no separate signup flow. Provision one, then log in:

```bash
curl -X POST http://localhost:8080/api/v1/organisations \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Demo School",
    "legalName": "Demo School Trust",
    "slug": "demo-school",
    "adminEmail": "admin@demo-school.test",
    "adminPassword": "changeme123",
    "adminFirstName": "Admin",
    "adminLastName": "User"
  }'

curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "organisationSlug": "demo-school",
    "email": "admin@demo-school.test",
    "password": "changeme123"
  }'
```

The login response returns a JWT scoped to that organisation — send it as
`Authorization: Bearer <token>` on subsequent requests.

## 3. Frontend

```bash
cd web
npm install
npm run dev
```

Opens on `http://localhost:5173` and talks directly to the backend at
`http://localhost:8080`. Log in with the organisation slug/email/password created
above.

## Project layout

```
src/main/java/com/operion/
├── organisation      # Organisation, Campus, AcademicYear (tenant root)
├── identity           # User, Person, auth (JWT)
├── authorization       # Role, Permission, OrganisationMembership
├── academic            # GradeLevel, Subject, SchoolClass, Section, TeacherAssignment
├── student             # Student, StudentEnrollment, StudentDocument, StudentExit
├── parent               # Guardian, StudentGuardian
├── attendance           # ClassAttendanceRegister, StudentAttendance, StaffAttendance
├── finance               # FeeStructure, Invoice, Payment, Refund
├── examination           # Exam, GradingScale, MarksEntry, ReportCard
├── communication          # Announcement, NotificationRecipient
├── transport               # Vehicle, Route, StudentTransportAssignment, TripLog
├── library                  # Book, BookCopy, BorrowRecord, Fine
├── inventory                 # Item, StockEntry/Issue/Adjustment
├── hr                          # StaffProfile, LeaveType, LeaveRequest
└── common                       # TenantContext, AuditLog, shared infra

web/src/
├── api/          # hand-typed fetch client
├── auth/         # login, protected routes
├── layout/       # app shell
└── modules/      # one folder per backend module above
```

## Known gaps

- No RBAC/permission enforcement yet — a valid JWT can call any endpoint. Largest open
  item before any real deployment.
- No production build/deploy story.
- No SaaS/billing layer (`Organisation → Subscription → Plan → Usage → Billing`),
  deliberately deferred.

See `ai-context/load-context.md` for the full, current list.

## Codebase knowledge graph (graphify)

This repo has a [graphify](https://github.com/Graphify-Labs/graphify) knowledge graph
built from the full codebase — 2,563 nodes, 8,619 edges, 162 communities covering both
`src/` and `web/`. Output lives in `graphify-out/` (gitignored, regenerate locally):

```bash
/graphify . --update                     # re-extract only new/changed files
/graphify query "<question>"             # ask a question, answered from the graph
/graphify path "AuthModule" "Database"   # shortest path between two concepts
/graphify explain "TenantScopedEntity"   # plain-language explanation of a node
```

Open `graphify-out/graph.html` directly in a browser for the interactive visualization,
or read `graphify-out/GRAPH_REPORT.md` for the audit report (god nodes, communities,
surprising connections).
