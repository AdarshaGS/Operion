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
| SEC-M1 | `MEMBERSHIP_MANAGE` holder can change the **Owner's** email (no owner guard on update) → account takeover once reset emails go live | `identity/UserService.java` `update` | Confirmed | open |
| SEC-M2 | Password-reset link (with token) written to the application log | `identity/auth/PasswordResetService.java` `requestReset` | Confirmed | fixed — reset link is emailed (same path as verification); raw reset/verification tokens logged only when `app.dev.log-auth-tokens=true` (automation stack sets `APP_DEV_LOGAUTHTOKENS=true`) |
| SEC-M3 | Public token endpoints (refresh, reset, claim-invite, verify-email…) bcrypt-compare against every live token in the org with no rate limit → cheap CPU DoS | `PasswordResetService`, `RefreshTokenService`, `PortalInviteService` | Confirmed (DoS impact plausible) | open |
| SEC-M4 | `POST /api/v1/organisations` is public and passes client `planId` to billing → anyone can create schools / pick a plan | `organisation/api/OrganisationController.java` | Confirmed (billing effect unverified) | open |
| SEC-M5 | `/uploads/**` served without auth or tenant check; references not bound to the uploading org (only UUID names protect staff docs, photos, answer sheets) | `common/WebConfig.java`, `storage/LocalDiskAssetStorageService.java` | Confirmed | open |
| SEC-M6 | `GET /api/v1/persons` has no permission check → any staff role (driver, librarian) can list every student/guardian's phone, email, address, DOB | `identity/api/PersonController.java` | Confirmed | open |
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

_Pending: full API/UI + sanity run in progress on the throwaway stack (:8091 / :5184)._

## Checked and fine (security review)

Tenant isolation on per-school entities (`TenantScopedEntity` everywhere checked; missing tenant resolves to `-1`, fails closed); only two native queries, both justified; CORS is an explicit allow-list with no wildcard; upload path traversal, content-type sniffing and 5 MB cap are in place; certificate-verify public endpoint returns minimal fields; JWT verified per request with user/org/membership re-check; `SecretsGuard` does block **prod** startup while the seeded platform password is active.
Not verified: Hibernate 7.4 tenant filtering on `findById`, webhook signature code, ungated controllers outside core modules (162 controllers, not all read line by line).
