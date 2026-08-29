# Operion E2E

Playwright suite covering all three of Operion's access tiers — the permission-gated
tenant app, the parent/guardian self-service portal, and the platform-admin app — driven
through the real UI wherever the thing under test is UI behavior, and via a thin API
client only for one-time test-data seeding.

## Architecture

- **Role fixtures are permission-driven, not name-driven** (`fixtures/roles.ts`). Operion
  seeds only an `Owner` role per org (GitHub #92) — every other role is a tenant-defined
  bundle of permission codes. Eight fixtures cover the model: `owner` (bypass-all),
  `allFunctionsAdmin` (the `ALL_FUNCTIONS` backend bypass, granted through a normal role),
  `readOnlyStaff`, `feesCollector` (a tight single-module bundle), `teacher` (a realistic
  classroom-teacher bundle — attendance/marks write, nothing org-wide), `noPermissions`
  (the floor), `guardian` (via the real claim-invite flow), and `platformAdmin` (the
  separate auth plane).
- **`global-setup.ts`** runs once per `npx playwright test` invocation: provisions one
  fresh, uniquely-slugged organisation via the public `POST /api/v1/organisations`
  endpoint, seeds a minimal academic structure (campus/year/grade/class/section, an
  enrolled student, a subject, and a teacher assignment), creates each role fixture's Role
  + membership, and writes a Playwright `storageState` file per role directly from each
  login response — no UI login roundtrip needed, since the session is just `localStorage`
  (see `tokenStore.ts`). `tests/flows/institution-owner-flow.spec.ts` is the one exception:
  it provisions its own separate, genuinely-empty organisation (see below) rather than
  reusing this shared one.
- **`playwright.config.ts`** maps each fixture to a `project`, each scoped via `testMatch`
  to the test directories it's meant to run (`tests/rbac/**` runs under every
  tenant-staff role; `tests/tenant/**` — full-access CRUD flows — runs owner-only;
  `tests/auth/**` runs unauthenticated; `tests/parent-portal/**`, `tests/platform-admin/**`,
  and each `tests/flows/*.spec.ts` run under their own role project).
- **`pages/`** is a Page Object Model, one file per module, mirroring
  `web/src/modules/<module>/*.tsx` — action methods built on `getByRole`/`getByLabel`
  locators (there are no `data-testid` attributes anywhere in the frontend). MUI's
  `<TextField select>` renders as `role="combobox"`, not a labelled form control the way
  a plain `TextField` does — use `getByRole("combobox", { name })` for those, not
  `getByLabel`. Its Stepper `StepButton`s expose `role="tab"`, not `"button"`.
- **`api/`** is a thin fetch client used only by `global-setup.ts` (and a couple of specs
  that need to issue a precondition — e.g. a staff invite, an exam schedule — without
  re-driving UI that's already covered elsewhere). It is not how the specs themselves
  exercise functionality.
- **`support/`** holds cross-cutting helpers used by `tests/flows/`: `diagnostics.ts`
  (`trackDiagnostics(page)`) watches for uncaught JS exceptions and failed/5xx network
  requests through a test, `scroll.ts` (`verifyFullPageScroll`) checks desktop layout has
  no horizontal overflow and that the main content area actually scrolls, and `auth.ts`
  (`tokenFor(role)`) reads a role fixture's bearer token straight out of the storageState
  file `global-setup.ts` wrote for it, for specs that need to hit the backend directly.

## Running

Two servers must already be running — this suite never starts or stops them itself:

1. **Backend**, on a port that isn't your normal dev backend (default `:8090` here, see
   `api/client.ts`'s `E2E_API_BASE_URL` override), pointed at a **separate database** from
   your real dev data (e.g. `operion_e2e`), with CORS opened for the frontend port below:

   ```
   SERVER_PORT=8090 \
   APP_CORS_ALLOWED_ORIGINS=http://localhost:5183 \
   DB_URL="jdbc:mysql://localhost:3306/operion_e2e?allowPublicKeyRetrieval=true" \
   DB_USERNAME=root DB_PASSWORD= \
   REPORTING_DB_URL="jdbc:mysql://localhost:3306/operion_e2e?allowPublicKeyRetrieval=true" \
   REPORTING_DB_USERNAME=reporting_ro REPORTING_DB_PASSWORD=changeme-reporting-ro \
   JWT_SECRET=dev-only-secret-change-me-before-any-real-deployment-32bytes-min \
   PLATFORM_JWT_SECRET=dev-only-platform-secret-change-me-before-any-real-deployment-32b \
   SPRING_PROFILES_ACTIVE=dev \
   java -jar build/libs/operion-0.0.1-SNAPSHOT.jar
   ```

   (Build the jar first with `./gradlew bootJar`. Running it as a plain jar, rather than
   `./gradlew bootRun`, avoids sharing Gradle's build daemon with any other `bootRun` you
   might have going — two daemon-attached runs can and did kill each other during this
   suite's own development.)

2. **Frontend**, on its own port, pointed at that backend:

   ```
   cd web && VITE_API_BASE_URL=http://localhost:8090 npm run dev -- --port 5183 --strictPort
   ```

Then, from `e2e/`:

```
npm install
npx playwright install chromium   # first time only
npx playwright test               # full suite
npx playwright test --project=owner tests/rbac       # one project/directory
npx playwright test --ui          # interactive mode
```

`E2E_API_BASE_URL` and `E2E_FRONTEND_BASE_URL` env vars override the `:8090`/`:5183`
defaults if you run the servers on different ports.

**Never point this suite at your real dev database or your normal dev backend/frontend
ports.** It provisions a new organisation on every run and is meant to run against a
throwaway database — see `env.ts` / `api/client.ts` for where the target ports live.

## Coverage

- `tests/rbac/` — nav-visibility (every sidebar item × every tenant-staff role, asserting
  the item's enabled state matches literal permission-code membership) and
  api-enforcement (proves the backend, not just the UI, is the real boundary — including
  the `ALL_FUNCTIONS` case, where the UI hides everything but the backend accepts it).
- `tests/tenant/<module>/` — one full-access, UI-driven creation/primary-workflow spec per
  module: students, academics, attendance (mark → submit → lock), fees (category +
  structure), examinations, communication, library, inventory, purchase (with its
  supplier/item setup), sales (with stock-entry precondition), HR (with its designation
  precondition), transport, reporting, members (invite issuance), profile, settings
  (custom role creation with permission checkboxes).
- `tests/auth/` — login (success + failure), password reset (including the
  non-enumerable-response guarantee), staff-invite claim.
- `tests/parent-portal/` — the guardian's real restricted experience: lands on
  `/students` per `IndexRedirect`, but every module is blocked since the app has no
  purpose-built guardian screens yet.
- `tests/platform-admin/` — organisation provisioning, plan creation.
- `tests/flows/` — one long, `test.step`-structured journey per role (Institution Owner,
  Teacher, Student/Guardian, Platform Admin), each sharing a single browser session end to
  end rather than many small isolated tests, per role. Every step is watched by
  `support/diagnostics.ts` (console errors, failed/5xx requests) and the dashboard-heavy
  steps also run `support/scroll.ts`'s full-page-scroll/no-horizontal-overflow check at a
  1440×900 desktop viewport. `institution-owner-flow.spec.ts` is the one spec in the whole
  suite that provisions its own fresh, genuinely-empty organisation (see Architecture) —
  it's the only place the setup checklist can be shown moving from 0 of 6 to 6 of 6 in
  real dependency order, driven entirely through real UI, including two concrete
  regression checks: partial Academic-setup data does *not* flip the checklist early, and
  a native-required-field submit is genuinely blocked. See "Known gaps" below for what
  each flow spec deliberately does *not* assert, and why.

## Known flakiness

`tests/auth/password-reset.spec.ts` and `tests/flows/institution-owner-flow.spec.ts`
occasionally time out when the **full** suite runs (135+ tests across many parallel
workers hitting one local dev backend) — the latter is the heaviest single test in the
suite (a real UI login plus a nine-step onboarding journey). Both pass reliably alone or
with `--repeat-each`. This is resource contention under heavy local parallelism, not a
logic bug — if it's disruptive, lower `fullyParallel`/worker count in
`playwright.config.ts` or run `tests/flows/` separately.

## Known gaps (found while building this suite, not test bugs)

A few real product gaps surfaced during development — some were fixed, some are still
open and the relevant specs assert the real, current behavior rather than the ticket's
aspirational one:

- **Fixed**: `LoginPage` hardcoded `navigate("/students")` after every login, bypassing
  `IndexRedirect`'s own `ORGANISATION_MANAGE`-aware Dashboard/Students logic.
- **Fixed**: a platform-admin-triggered organisation status change wrote its audit-log
  row with a null `organisation_id` (`AuditLogService.record()` reads `TenantContext`,
  which carries no org on the platform auth plane) — unattributed and unfindable via
  `AuditLogController`.
- **Fixed**: `StudentDetailPage` had no way to enroll a student into a section at all —
  `Student.status` only moves `ADMITTED` → `ACTIVE` via enrollment, so the dashboard's
  own "Students" setup-checklist step could never complete through the UI.
- **Open**: `TeacherAssignment` (who teaches what) is informational only — no backend
  endpoint filters attendance/exam/marks data by it, so any account holding the relevant
  write permission can act on any class, not just an assigned one (see
  `tests/flows/teacher-flow.spec.ts`).
- **Open**: the guardian/parent portal has no purpose-built screens — `PARENT_PORTAL_ACCESS`
  is the only permission the Guardian role carries, so attendance/fees/results/notices/
  library are all unreachable today, not just restricted (see
  `tests/flows/student-guardian-flow.spec.ts`).
- **Open**: the platform admin app has no module-catalogue/feature-flag/tenant-entitlement
  surface — only Organisations, Plans, Subscriptions, and Invoices exist. A Subscription is
  the closest real analogue to an "entitlement" (see `tests/flows/platform-admin-flow.spec.ts`).
- **Open**: `StudentDetailPage` still has no edit affordance — admission fields are
  view-only once set.
- **Open**: the campus/academic-year pills in the top bar (`ContextSelectors.tsx`) are
  pure display context — no dashboard or module data is actually filtered by them yet.
