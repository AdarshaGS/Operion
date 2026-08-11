# Mobile App Context — Parent/Student Companion (Operion)

> Paste/load this file (alongside `load-context.md` and `erp-system-plan.md`) at the start of a session working on the Parent/Student mobile app. Not started yet — this is the design brief to open with, not a status report.

## What this is

A companion mobile app for parents and students to check things from the ERP their
school already runs on Operion — read-mostly, not an admin tool. Not a rebuild of the
React admin portal for a different screen size.

## The blocker to resolve first

Parents and students currently have **no login capability**. Confirmed by reading the
code: `OrganisationMembership` (which `AuthenticationService.login` requires) is only
ever created during org provisioning, for the first admin, in `OrganisationService`.
Guardian and Student creation never create a `User` or `OrganisationMembership`.
Before any mobile screens get built, this needs a real design decision — cover it the
way this codebase covers every new entity (business purpose, actors, entity model,
relationships, DB schema, constraints, APIs, security, then wait for sign-off):

- How does a parent get an account? (School staff invites them with a claim link?
  Self-signup matched against an existing Guardian record by email/phone? Something
  else?)
- How does a student get an account, separate from their parent's? Some schools may
  not want students under a certain age to have independent logins at all — is there
  an age/grade cutoff, or is this admin-configurable per school?
- Does logging in as a parent need its own JWT scope (e.g. "which children can this
  token see") distinct from staff logins, which are already org-scoped only?
- Where does this identity-creation logic live — extend `ParentService`/
  `StudentService`, or is it its own thing? Follow the existing "explore, don't
  assume" discipline this codebase already uses.

## Scope once identity exists

Read-only or lightly-interactive views over the existing REST API, using a parent's
linked children (`StudentGuardian`) and a student's own record:

- Attendance history (`StudentAttendance`)
- Fees: outstanding invoices, payment history (`Invoice`/`Payment`) — no collection
  flow, that's staff-only
- Marks and published report cards (`MarksEntry`/`ReportCard`)
- Announcements and notifications targeted at them (`Announcement`/`NotificationRecipient`)
- Transport: which route/stop their child is assigned to (`StudentTransportAssignment`)
- Library: a student's own current borrows and any pending fines (`BorrowRecord`/`Fine`)

Do not build a parent-facing fee-payment flow, attendance marking, or anything staff
already owns in the admin portal — this is a viewing companion, not a second admin
surface. Flag anything that looks like it needs a write action back to the user
before building it.

## Constraints carried over from the rest of Operion

- RBAC/permission enforcement still doesn't exist anywhere in Operion (biggest
  standing gap, tracked in `load-context.md`). A mobile client hides admin actions in
  its UI, but that is not a security boundary — say this explicitly if it comes up,
  don't treat UI-hiding as sufficient.
- JWT auth, same as the web portal — but a mobile app needs secure on-device token
  storage (Keychain/Keystore, not localStorage's mobile equivalent) and a real
  decision on session length / refresh, since the current 480-minute expiry with no
  refresh token was sized for an admin at a desk, not a parent's phone.
- Multi-tenant: a parent could plausibly have children at two different schools
  (two different organisations) on the same platform — decide up front whether one
  login can span organisations or whether it's strictly one-org-per-login like the
  web portal, don't assume.

## Tech direction — needs a decision, don't assume

React Native (reuses the existing team's React/TypeScript investment from `web/`) vs.
Flutter (better native feel, new stack for this team) — lay out both with a
recommendation, then wait for a decision before scaffolding anything. Same
"Option A vs Option B" discipline as the rest of this project.

## Working style

Same as the rest of this project: work incrementally, don't build the whole app at
once, lay out entity/API/security considerations and wait for sign-off before
generating code, surface options rather than picking silently.
