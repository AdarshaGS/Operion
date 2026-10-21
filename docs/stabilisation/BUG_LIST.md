# Bug list — stabilisation phase (2026-10-21 → 2026-11-30)

Fix order: Security High → data integrity → broken workflow (sanity failures) → Security Medium → cosmetic/Low.
Status: `open` / `fixing` / `fixed (commit)` / `wontfix (reason)`.
Sources: **SEC** = read-only code security review 2026-10-21; **AUTO** = operion-automation full run 2026-10-21; **INV** = module inventory.
Verification: **Confirmed** = read in code (H1, H2 re-checked by hand); **Plausible** = needs a reproduction before fixing.

## Security — High

| ID | Bug | Where | Confidence | Status |
|---|---|---|---|---|
| SEC-H1 | Holder of `MEMBERSHIP_MANAGE` can grant **themselves** (or anyone) any role, incl. the org-admin role — no check that the caller already holds the role's permissions | `authorization/MembershipService.java` `grant()` | Confirmed | fixed — caller must hold every permission of the role (Owner / ALL_FUNCTIONS exempt) |
| SEC-H2 | Holder of `ROLE_MANAGE` can create a role, or edit one, with permissions they don't hold (incl. `ALL_FUNCTIONS`); combined with H1 = full takeover | `authorization/RoleService.java` `create()` / `updatePermissions()` | Confirmed | fixed — create and newly-added codes capped at the caller's own permissions |
| SEC-H3 | Saved-report SQL: `SqlGuard` blocks `SET` but not inline `@reporting_org_id := N`, letting a `REPORT_CREATE` holder read another school's data through the reporting views. Same as automation defect 5ay (CRITICAL); 5az (`#` comments, `INTO OUTFILE`, harmless words refused) fixed alongside | `reporting/SqlGuard.java` | **Confirmed on MySQL**: session org 99999999, `SELECT COUNT(*) FROM (SELECT @reporting_org_id := 1) x, reporting_staff` returned org 1's 76 rows; `reporting_ro` can read every row of every view | fixed in guard (refuses `@`, `:=`, `INTO`, `#` outside quotes; 16/16 SqlGuardTest pass) |
| SEC-H4 | _(owned by peer session operion-fa)_ Default profile is `dev` and docker-compose ships dev JWT secrets; `SecretsGuard` only enforces under `prod` / `SECRETS_GUARD=enforce` → a deploy without the prod profile accepts forged tokens | `application.properties`, `docker-compose.yml`, `common/SecretsGuard.java` | Confirmed (deploy-dependent) | open |
| SEC-H5 | _(owned by peer session operion-fa)_ Platform-admin login has no lockout/rate limit; disabled platform admin's token stays valid until expiry (120 min) | `platform/auth/PlatformAuthenticationService.java`, `PlatformAuthenticationInterceptor.java` | Confirmed | open |

## Security — Medium

| ID | Bug | Where | Confidence | Status |
|---|---|---|---|---|
| SEC-M1 | `MEMBERSHIP_MANAGE` holder can change the **Owner's** email (no owner guard on update) → account takeover once reset emails go live | `identity/UserService.java` `update` | Confirmed | fixed — only the Owner may change the Owner's contact; anyone else's only if the caller holds all of that user's permissions (also closes the same path against admins) |
| SEC-M2 | Password-reset link (with token) written to the application log | `identity/auth/PasswordResetService.java` `requestReset` | Confirmed | fixed — reset link is emailed (same path as verification); raw reset/verification tokens logged only when `app.dev.log-auth-tokens=true` (automation stack sets `APP_DEV_LOGAUTHTOKENS=true`) |
| SEC-M3 | Public token endpoints (refresh, reset, claim-invite, verify-email…) bcrypt-compare against every live token in the org with no rate limit → cheap CPU DoS | `PasswordResetService`, `RefreshTokenService`, `PortalInviteService` | Confirmed (DoS impact plausible) | open |
| SEC-M4 | `POST /api/v1/organisations` is public and passes client `planId` to billing → anyone can create schools / pick a plan | `organisation/api/OrganisationController.java` | Confirmed (billing effect unverified) | open |
| SEC-M5 | `/uploads/**` served without auth or tenant check; references not bound to the uploading org (only UUID names protect staff docs, photos, answer sheets) | `common/WebConfig.java`, `storage/LocalDiskAssetStorageService.java` | Confirmed | open |
| SEC-M6 | `GET /api/v1/persons` has no permission check → any staff role (driver, librarian) can list every student/guardian's phone, email, address, DOB | `identity/api/PersonController.java` | Confirmed | fixed — list/get return names+photo only unless the caller holds a people-viewing permission (STUDENT/GUARDIAN/HR/MEMBERSHIP view or manage, INVENTORY_CUSTOMER_MANAGE); pickers keep working |
| SEC-M7 | Any member can open a direct message thread with any person (guardian → other children) | `messaging/api/MessagingController.java` | Confirmed — moot if Messaging is disabled for go-live | open |

## Security — Low

| ID | Bug | Where | Status |
|---|---|---|---|
| SEC-L1 | Login skips bcrypt for unknown emails (timing reveals which accounts exist); per-account lockout lets anyone lock a known user out | `AuthenticationService.java` | open |
| SEC-L2 | Missing `@Valid` on several request bodies (students, payments, fee structure/assignment, refunds/waivers/adjustments, marks, roles, memberships); service-level checks exist for fee amounts | various controllers | open |
| SEC-L3 | `SecretsGuard` runs after the web server is listening; its seeded-admin check fails open if the platform DB read throws | `common/SecretsGuard.java` | open |
| SEC-L4 | Reporting DB role seeded with published password `changeme-reporting-ro` (caught by SecretsGuard under prod only) | `V55__reporting_restricted_role.sql` | open |

## Data integrity / product

| ID | Bug | Where | Status |
|---|---|---|---|
| INV-1 | #354 student ID unique key not per school | `db/migration/school/V6__students_student_id_unique_per_organisation.sql` now keys `(organisation_id, student_id)` | likely fixed — confirm on fresh DB, then close #354 |
| INV-2 | `web/src/modules/__pp/` — 7 unreferenced stale copies of Examinations pages | `web/src/modules/__pp/` | open (cleanup) |
| INV-3 | `FEE_DISCOUNT_APPROVE` permission seeded but never checked | finance | open — decide in role design |

## Automation failures

Run 2026-10-21 on a fresh `operion_auto` stack, jar built from `725f08f` (before any fix above).
**Sanity: 17/17 journeys pass (all 15 phases + legacy journey).** API/UI: 2662 passed, 65 failed, 3 skipped, 8 did not run.

### Product bugs (from the run)

| ID | Bug | Evidence | Severity | Status |
|---|---|---|---|---|
| AUTO-1 | Performance endpoints answer while the Performance module is switched off (module gate bypass) | PERF-01: `GET /api/v1/performance/metrics` → 200 with module off | Medium | open |
| AUTO-2 | Resending a staff invite fails: `POST /users/{id}/resend-invite` → 404 "No user with id" | AUTH-22 | Medium | open — investigate |
| AUTO-3 | A fee payment dated 2999-01-01 is accepted and gets a receipt number | FEE-25 | Medium (receipt data) | open |
| AUTO-4 | Unknown grade-level stage (`GALACTIC`) accepted | ACA-06 | Low | open |
| AUTO-5 | Three 500s instead of 400: audit-log sort by unknown field; report create/run with `parameters` / `parameterValues` omitted (NPE) | backend.log | Low | open |
| AUTO-6 | Two inputs both named "Search" on the student list (top-bar global search + page search) — ambiguous for screen readers; breaks ~10 UI tests | STU-U03/U05/U08/U10 strict-mode violations; `layout/TopbarParts.tsx:191` | Low | open |
| AUTO-7 | 116 "Unhandled exception … Broken pipe" ERROR lines when the browser leaves mid-response — log noise that would hide real errors in production | backend.log | Low | open |

### Automation out of date (screen/behaviour changed on purpose — fix the test, not the product)

| Tests | Why |
|---|---|
| SET-12 | Expects Communication off by default; it is now a core module on by default (sanity 12 relies on that) |
| ME-02 leave balance | Leave now counts working days (holiday-aware leave); the fixture asks 2 days over a weekly off |
| EXM-U01/U05/U06/U08 (10) | Examination settings moved behind a Settings tab on the Exams page |
| MEM-U04 | `getByLabel('Password')` now also matches the new "Show password" button |

### Not yet triaged (need the failure screenshots)

Fees UI (33 — student picker option not found / two "Class" comboboxes on the overdue page; likely one shared cause), Performance UI (3), CERT-U03, AUTH-U07 ×2 (email-verification page), ADM-U01 (empty-state text), ACA-U01 (a 5xx during class creation), COM-U04.

## Checked and fine (security review)

Tenant isolation on per-school entities (`TenantScopedEntity` everywhere checked; missing tenant resolves to `-1`, fails closed); only two native queries, both justified; CORS is an explicit allow-list with no wildcard; upload path traversal, content-type sniffing and 5 MB cap are in place; certificate-verify public endpoint returns minimal fields; JWT verified per request with user/org/membership re-check; `SecretsGuard` does block **prod** startup while the seeded platform password is active.
Not verified: Hibernate 7.4 tenant filtering on `findById`, webhook signature code, ungated controllers outside core modules (162 controllers, not all read line by line).
