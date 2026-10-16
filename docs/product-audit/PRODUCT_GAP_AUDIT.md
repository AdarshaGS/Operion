# Operion — Product Gap & Readiness Audit

| | |
|---|---|
| **Document type** | Living audit. Update in place; log changes in §26. |
| **Audit date** | 2026-09-30 |
| **Codebase state** | `main` @ `1e6503d` (40 commits ahead of `origin/main`), migrations V1–V130 |
| **Method** | Five parallel read-only code reviews (first pass) (tenancy/security · attendance/notifications/mobile · student→fee lifecycles · platform/ops/reporting · authorization/web UX), plus the repo's own docs (`ai-context/`, `docs/`). **Nothing was executed**; no test suite, migration, or server was run for this audit. |
| **Second pass** | 2026-09-30: Library, Inventory, Purchase, Sales, Platform CRM, Learning/LMS, Payroll and Regulatory/Privacy were read in full with six further read-only reviews; findings are in §3.8–3.14. Still nothing was executed. Residual unknowns: runtime and MySQL behaviour of everything, the reporting isolation function `reporting_current_org_id()` (not read), whether production overrides the dev defaults, and whether any school actually uses these modules. |

## How to read the evidence tags

| Tag | Meaning |
|---|---|
| **[V]** | Verified by reading source during this audit (file/line evidence was captured; see Appendix A for the highest-stakes ones). Not executed. |
| **[D]** | Stated in repo docs; not independently re-verified. |
| **[U]** | Unverified inference. Plausible from the code but needs a test or a run to confirm. |
| **[A]** | Assumption about how Indian schools typically work. Must be validated with a pilot school. Not a fact. |
| **[R]** | Needs external verification (legal, regulatory, telecom, payment-provider rules). Nothing here is asserted as law. |

Severity: **Critical** = cross-tenant exposure, data loss, or blocks go-live. **High** = likely to cause serious operational failure or data damage in the first term. **Medium** = real friction or risk, workable around. **Low** = polish.

MVP column: **Yes** = before first school · **Soon** = within the first term · **Later** = after · **No** = don't build yet. "Future?" from the brief is implied by anything that is not "Yes".

---

# 1. Executive Summary

**Operion is broad and structurally sound, but not yet safe or complete enough to hand to a 1,000-student school.**

**What is genuinely strong** [V]: Hibernate `@TenantId` isolation that fails closed (null context → sentinel `-1`), 10 tenant-isolation test classes, permissions re-queried from the DB on every gated request (role changes bite immediately on those endpoints), a cryptographically separate platform-admin plane, insert-only history for corrections/adjustments/waivers/refunds/salary structures, gap-free per-org receipt counters under a pessimistic lock, per-row import isolation with validate-only preview on most importers, money always `BigDecimal`, and a written module-boundary rule enforced by an ArchUnit test.

**What the audit found, in one paragraph.** The breadth (1,212 backend files, 130 migrations, ~137 controllers, 20 sidebar items) has outrun the depth of the daily workflows a school actually lives on. There is **no RFID/device subsystem at all** (zero code), the mobile app is an admin-only prototype that calls none of the parent/teacher APIs, and the core operating loops each have a hole: fees have no bulk invoice generation, no receipt print, no day-close, and no lock against concurrent payments; there is no year-end promotion or rollover (and no UI even for single-student promote/exit); report cards cannot aggregate across exams; attendance has no notion of source, timestamp, or teacher scope. Separately, four **verified security defects** would be serious the moment more than one tenant exists — and one (unguarded `PersonController`) is serious with just one school, because a parent's portal token can list every person's phone/email/address.

**Five headline findings**

1. **Tenant boundary leaks in the global identity plane** [V]. `PUT /users/{id}`, `POST /users/{id}/status`, `POST /users/{id}/resend-invite` never check the target belongs to the caller's org; the invite path returns a raw claim token, which yields account takeover. `PATCH /organisations/{id}/status` never compares the path id to the caller's tenant, so any org Owner can suspend or archive any other org. (SEC-01, SEC-02)
2. **"Disabled" and "suspended" do not actually stop anything** [V]. Org suspension is enforced nowhere (login, refresh, per-request, schedulers). Refresh ignores user status and membership. Revoking a member leaves unannotated endpoints reachable until the JWT expires, and the refresh token renews for 30 days on a rolling basis. Staff exit and student exit cascade to nothing. (SEC-03, SEC-04, AUTH-06, STU-03)
3. **Data-loss exposure** [V]. Backups are documented, not automated, with no restore drill; uploads live on local disk in a compose container with **no volume**, outside the backup plan; default DB credentials are root/empty; default JWT secrets and a seeded platform admin (`ChangeMe123!`) have no boot guard. (PLT-06, PLT-08, SEC-06)
4. **The parent never gets in** [V]. Guardian portal invites are a manual copy-paste of a raw token plus a separately-communicated org slug. For ~1,000 students that is ~1,000+ manual hand-offs. Password reset only *logs* the token and never emails it. Mobile stores tokens unencrypted, never refreshes, and has no parent screens. (NOT-06, SEC-09, MOB-01)
5. **RFID is the stated differentiator and does not exist** [V]. The attendance model (one row per enrollment+date, DRAFT→LOCKED register, no source/timestamp/idempotency field) cannot absorb device events without redesign. (RFID-01, ATT-01)

**Overall stance.** The right next move is *not* more modules. It is (a) a short security hotfix pass, (b) closing the six daily-workflow holes above, and (c) a decision session on ~30 product questions the code has silently answered by default (§20). Several already-built areas (LMS-lite, Platform CRM, regulatory framework, live chat) should be **frozen or hidden behind a module switch** until those are done (§21, D-MOD1).

**Second pass (eight modules audited in depth).** None of them is ready to hand to a school unattended, and none is unusable. The findings that matter most: **Payroll** pays full salary to late joiners and people on unpaid leave with no flag, and a correction can silently drop a leaver (PAY-01, PAY-02, Critical); **Learning** files are public links and parents see class ranks even when the school turned ranking off (LMS-01, LMS-02); **Sales** has no refund or void and accepts overpayment (SAL-02, SAL-03) and sits outside the fee ledger (SAL-01); **Library** fines cannot be collected from the UI and a copy can be issued twice (LIB-01, LIB-03); **Regulatory/Privacy** records consent that nothing enforces and an erasure that only flips a status (REG-01, REG-02); **Platform CRM** convert can strand a half-built school and the platform login has no lockout (CRM-01, CRM-02). The pragmatic stance is to switch Purchase, Sales and the Learning extras off for the first school, and to fix the listed minimums before enabling Payroll or Library (§22.1, §24 phase 2e).

---

# 2. Current Product Understanding

## 2.1 Feature inventory: what a school can do today [V]

Plain-language summary of what is built. **Status:** ✅ usable end to end · ◐ works, with gaps that matter · ✖ absent. Gap IDs point to the register in §3.

### Running the school

| Area | What a school can do today | Used by | Status | Main limits |
|---|---|---|---|---|
| School setup | Create the school, campuses, academic years, departments, designations; set logo, stamp, signature, timezone, currency, working days; edit admission, invoice and receipt number formats; optionally apply a ready-made template (grades LKG–10, departments, designations, 12 roles, fee categories, grading scale) | School admin | ◐ | Template does not create classes, subjects or the year; no attendance or late rules; creation is not all-or-nothing (SEC-10, MIG-02) |
| Users, roles and access | Invite staff by email; about 94 permissions grouped into custom roles; Owner role; mark a role as a parent/teacher portal role; audit-log viewer; approve profile-change requests | School admin | ◐ | Access is school-wide only, no per-class limits; some screens are open to any logged-in user; removed users keep access for a while (AUTH-01, SEC-04, SEC-05) |
| Academics | Classes per year and campus, sections, subjects, teacher assignments, weekly timetable with clash checks, holiday and working-day calendar, school events, bulk import of classes, sections and subjects | Academic admin, teachers | ◐ | No terms, no year-end rollover, no electives, no substitution (STU-01, ACD-01) |
| Admissions | Public inquiry form per school, applicant pipeline (inquiry, under review, converted, rejected), document checklist, convert an applicant into a student | Reception | ◐ | Convert does not create the guardian or enrolment; no entrance test or offer steps (STU-09) |
| Students | Student profile, documents with verification, enrolment per year, custom fields, ID-card templates, campus transfer requests, exit records, Excel/CSV import and export | Admin, reception | ◐ | Promote, section change and exit exist only as API (no screen); no duplicate check; no transfer certificate (STU-01, STU-05, STU-06) |
| Guardians | Link parents to students (primary, emergency contact, may pick up, may receive messages), send portal invites, bulk import | Reception, parents | ◐ | Invite is a manually copied link, not delivered; siblings create duplicate guardian records (NOT-06, STU-05) |
| Staff and HR | Staff profiles, documents, bank details, leave types, balances and requests with approval, job applications, exit records, import | HR, principal | ◐ | Exit does not remove access or teaching duties; staff attendance has no screen (AUTH-06, ATT-06) |
| Payroll | Salary components, dated salary structures, monthly runs (draft, finalised, corrected), payslips, staff can view their own | HR, staff | ◐ | No proration for joiners, leavers or unpaid leave; a correction can drop leavers and adjustments; adjustments and bank details are not audited; no paid status (PAY-01 to PAY-07) |

### Daily teaching and money

| Area | What a school can do today | Used by | Status | Main limits |
|---|---|---|---|---|
| Attendance | Daily class register (draft, submitted, locked), corrections with a reason and audit trail, monthly percentage, absence email to guardians | Teachers, admin | ◐ | Any teacher can mark any class or date; alert email has no student name and can repeat; teachers cannot mark from their portal (ATT-02, ATT-04, ATT-06) |
| Examinations | Exams and schedules, grading scales, marks entry then submit then approve, pass/fail rules, ranks, report-card publish and republish, print view, guardian notification | Teachers, principal | ◐ | One exam per report card; marks above the maximum are accepted; no PDF (EXM-01, EXM-02) |
| Fees | Fee categories and structures with installments, assign to students, invoices, payments by cash, cheque, UPI, card, transfer or online, partial payments, refunds, adjustments, waivers, cheque bounce, overdue list with bulk reminders, online payment link, opening-balance import | Accountant | ◐ | Invoices are created one at a time; no receipt print, day-close or collection report; concurrent payments are not protected (FEE-01 to FEE-06) |
| Transport | Vehicles, drivers, routes and stops, assign students (linked to a fee), trip logs, boarding marks, vehicle and driver documents, import | Transport manager | ◐ | Boarding is manual; no proactive alerts; no driver screen (TRN-01) |
| Library | Book catalogue, copies, borrow and return, loan limits, manual fines, manual overdue reminder, book import | Librarian | ◐ | Fines cannot be found or collected from the screen; lost or damaged copies are dead ends; two desks can issue one copy; borrower picker is a plain list; no audit log (LIB-01, LIB-03, LIB-05, LIB-06) |
| Inventory | Items, stock in, out and adjustments, campus transfers, low-stock list, assets, suppliers, item import | Store | ◐ | Stock can go negative under two concurrent users; items cannot be edited; whole units only; no audit log (INV-01, INV-05, INV-11) |
| Purchase and sales | Purchase orders with approval and receiving, supplier returns; store sales with receipts and part payments | Store, accountant | ◐ | Sales has no refund, accepts overpayment, is outside the fee ledger; a PO receive can hit the wrong order; no PO number or total; cashier role does not work out of the box (SAL-01 to SAL-04, PUR-01, PUR-03); recommended to hide (§21) |
| Learning | Homework with submissions and feedback, quizzes, study materials, lesson plans with approval, answer-sheet marking, leaderboards and rewards | Teachers, parents | ◐ | Uploaded files are public links; parents see class rank even when ranking is off; a published quiz cannot be corrected; nobody is notified (LMS-01, LMS-02, LMS-03, LMS-11) |

### Communication, reports and portals

| Area | What a school can do today | Used by | Status | Main limits |
|---|---|---|---|---|
| Communication | Announcements to chosen audiences (can be scheduled), notification queue with email, SMS and WhatsApp senders, per-person preferences, templates, in-app inbox, real-time chat | Admin, teachers, parents | ◐ | No retry when a send fails; no push; message text has no variables; chat is open to any member (NOT-01, NOT-02, SEC-19) |
| Reports and exports | Saved reports on curated data views, sharing by user or role, 4 ready-made reports (sales, stock, purchases), CSV export for 11 kinds of records | Principal, accountant | ◐ | No ready-made attendance, fee, marks or defaulter reports; no PDF or Excel export (RPT-01, RPT-03) |
| Parent portal (web) | Switch between children; today's attendance and monthly summary; fees; published report cards; announcements; homework, quizzes and materials; request profile changes | Parents | ◐ | Cannot pay fees, see timetable, transport or attendance history (MOB-02) |
| Teacher portal (web) | See assigned sections, leave balance and requests, payslips, learning tools | Teachers | ◐ | Cannot mark attendance or see the timetable (ATT-06) |
| Mobile app | Sign in, dashboard, announcements, notifications, student and staff lookup, leave approvals for principal | Principal | ◐ | Admin prototype only; nothing for parents or teachers (MOB-01) |
| Compliance | Consent records, data-subject requests with due dates, regulatory export builder (ships empty), transfer hand-off log, screens under Settings > Compliance | Admin | ◐ | Nothing consults consent; erasure only flips a status; data export has seven fields; needs a permission no role holds by default (REG-01 to REG-03) |

### Operion's own team (platform)

| Area | What Operion staff can do today | Used by | Status | Main limits |
|---|---|---|---|---|
| Tenants and plans | Create schools, list them, change status, manage plans and subscriptions | Platform admin | ◐ | Suspension is not enforced; no limits or module switches (SEC-03, PLT-01) |
| Billing | Generate invoices by student count, mark paid, view monthly recurring revenue | Platform admin | ◐ | Fully manual; no reminders or trial handling (PLT-02) |
| Support and sales | Support tickets, lead pipeline, contacts, follow-ups, convert lead to school, platform settings, health and usage view | Platform admin | ◐ | Convert is not atomic; platform login has no lockout; schools cannot raise tickets; health is three yes/no checks (CRM-01, CRM-02, PLT-04, PLT-09) |

### Not present at all

| Missing capability | Why it matters | Register |
|---|---|---|
| RFID cards, readers and tap-based attendance | Stated core differentiator | RFID-01 |
| Push notifications | Mobile app cannot alert anyone | NOT-01 |
| PDF generation | Blocks receipts, report cards, certificates, ID cards | RPT-03 |
| Student login | Guardian-only by decision | MOB-03 |
| Multi-factor sign-in | Platform and school admins | SEC-11 |
| Bulk invoice generation, year-end promotion, transfer certificates | Daily and yearly school routines | FEE-04, STU-01, STU-06 |
| School offboarding and data export | First school that leaves | PLT-03 |
| Metrics, error tracking, tenant-level logs | Cannot diagnose a school's problem | PLT-09 |

## 2.2 Technical facts about what exists [V/D]

| Aspect | State |
|---|---|
| Stack | Spring Boot 4.1 / Java 17 modular monolith, Hibernate 7 + MySQL 8.4, Flyway (130 migrations), React 19 + Vite + MUI admin web, Expo React Native mobile (separate repo `operion-mobile`) |
| Tenancy | Shared DB/schema, `organisation_id` via `@TenantId`; global tables: `users`, `login_attempts`, `permissions`, `organisations`, platform-plane tables |
| Auth | JWT HS256 (480 min), refresh token (30 days, bcrypt-hashed, rotated), MVC interceptors (no Spring Security filter chain), 5-failure/15-min lockout keyed by email |
| RBAC | `User → OrganisationMembership → Role → Permissions` (~94 enforced codes); Owner flag bypasses; unannotated controllers = any authenticated member (~30 controllers) |
| Portals | Admin shell; `/portal` shell for guardian and teacher (`Role.isPortal` flag). **No student login by decision.** |
| Modules present | Organisation, identity, academic (+timetable), student/admissions, parent, attendance, examination, finance (+Razorpay), transport, communication (+STOMP chat), library, inventory/purchase/sales, HR, payroll, learning, reporting, privacy/regulatory, platform (billing, CRM, support), dashboard, custom fields, imports (11 entities) |
| Integrations coded | Email (Brevo/Resend), SMS (Brevo), WhatsApp Business API sender, Razorpay (one shared platform account) |
| Not present at all | **RFID/device/card**, push notifications, Redis (deliberately), S3/object storage (interface only), PDF generation, metrics/tracing, MFA |
| Tests | 132 backend test files / 546 tests (116 `@DataJpaTest` on **H2**, 3 `@SpringBootTest`), 7 frontend vitest files, 32 Playwright specs (**not in CI**), no load tests |
| Scale | ~1,212 main Java files, ~341 web files |

## 2.3 Assumptions this audit makes about the business [A]

- Target: 500–2,000+ student Indian schools, K–10 template today (template stops at Grade 10), single or few campuses.
- Buyers: Principal/owner and accountant decide; front-office and teachers determine daily adoption; parents determine perceived value.
- Pricing per student/year (₹100–200, not finalised — [D]).
- The school will keep *some* paper and Excel processes for a long time; "replace WhatsApp/Excel" is the pitch [D].

## 2.4 Scale reasoning (arithmetic, not statistics)

| Quantity | 500 students | 2,000 students | After 3 years (2,000) |
|---|---|---|---|
| Attendance rows (≈220 days) | ~110k/yr | ~440k/yr | ~1.3M |
| RFID raw events if stored (2–4 taps/day) | ~0.2–0.4M/yr | ~0.9–1.8M/yr | ~3–5M |
| Peak tap rate (arrival over ~30 min) | ~0.3/s | ~1.1/s average, bursts higher | same |
| Invoices (4 installments/yr) | ~2k/yr | ~8k/yr | ~24k |

**Implication:** raw volume is *not* the risk — MySQL handles this comfortably with correct indexes. The risks are unbounded lists (`GET /students`, `/persons`, overdue invoices, report cards are `findAll()`), `LIKE '%x%'` student search, EAGER `@ManyToOne(optional=false)` N+1 chains, and unbounded growth of `audit_logs`/`notification_recipients` with no retention. Redis/Kafka/microservices are not justified by these numbers.

---

# 3. Product Gap Register

Every gap: **ID · Area · Type · Gap · Why it matters · Severity · Users affected · Depends on · Current state · Recommendation · MVP · Decision required?**
Types: Product · UX · Tech · Ops · Business · Sec (security/compliance).

## 3.1 Security & tenancy

| ID | Area | Type | Gap | Why it matters | Sev | Users | Depends | State | Recommendation | MVP | Dec? |
|---|---|---|---|---|---|---|---|---|---|---|---|
| SEC-01 | Identity | Sec | `User` is global (no `@TenantId`); `PUT /users/{id}`, `POST /{id}/status`, `POST /{id}/resend-invite` don't verify the user belongs to the caller's org. `resend-invite` returns the raw claim token for another org's PENDING user; claiming it sets that user's global password. [V] | Cross-tenant account takeover/disable by any `MEMBERSHIP_MANAGE` holder of any org | **Critical** | All tenants | — | Existing (defective) | Scope every `User` mutation through `OrganisationMembership` of the caller's org; add a tenant-isolation test per endpoint | Yes | No |
| SEC-02 | Organisation | Sec | `PATCH /organisations/{id}/status` uses `findById(pathId)` with no comparison to `TenantContext`; gated only by `ORGANISATION_MANAGE`. [V] | Any org's Owner can suspend/archive any other org | **Critical** | All tenants | — | Existing (defective) | Remove from tenant plane (platform-only) or enforce `id == TenantContext.org` | Yes | No |
| SEC-05 | Identity | Sec | `PersonController` has no `@RequirePermission`: any authenticated member, **including a guardian portal token**, can list/read/create/edit every Person (phone, email, address, DOB). [V] `V24` comment claims it is gated. | A parent can enumerate all students', parents' and staff contact data; child-safety and privacy exposure with a single school | **Critical** | Parents, students, staff | — | Existing (defective) | Gate all; portal roles get own-record endpoints only; add a test asserting a portal-role token gets 403 | Yes | No |
| SEC-03 | Platform | Sec/Ops | Org status TRIAL/ACTIVE/SUSPENDED/ARCHIVED is stored but never enforced: not at login, refresh, per request, or in the four schedulers (which loop `findAll()` orgs). Trial expiry only computed for display. [V] | Cannot suspend a non-paying or abusive tenant; "suspend" is cosmetic | High | Platform admin | SEC-02 | Partial | Enforce in login, refresh, and interceptor (cached status); skip non-ACTIVE/TRIAL orgs in schedulers | Yes | Yes (D-PL5) |
| SEC-04 | Identity | Sec | Refresh ignores `User.status` and membership state; disabling a user or revoking a membership revokes no refresh tokens; each refresh mints a new 30-day token; unannotated endpoints check nothing but the JWT signature. Password change/reset revoke nothing. [V] | A fired teacher or disabled account keeps access indefinitely to ungated endpoints (messaging, notifications, uploads, persons) | High | Schools, Principal | — | Partial | Check user + active membership on refresh; revoke all refresh tokens on disable/revoke/password change; add short access-token life or a membership check in the interceptor | Yes | No |
| SEC-06 | Deploy | Sec | Default `JWT_SECRET`, `PLATFORM_JWT_SECRET`, `INTEGRATION_SECRET_KEY` baked into `application.properties`; default profile is `dev`; seeded platform admin `admin@operion.platform / ChangeMe123!` in `V25`; no boot-time refusal. [V] | A prod deploy that forgets one env var is fully compromisable | High | Platform | — | Existing (unsafe default) | Fail startup in non-dev profile if any default secret is present; remove seeded password, require first-run set | Yes | No |
| SEC-07 | Reporting | Sec | Reporting datasource defaults to `root/mysql` rather than the SELECT-only `reporting_ro`; `SqlGuard` is a regex and its own javadoc says it isn't the boundary; user variables (`SELECT @reporting_org_id := …`) not blocked [U]; `docker-compose` sets no `REPORTING_DB_*` so it can't connect in a container. [V] | If the datasource is root, a saved report is a cross-tenant SQL read; the DB boundary is the only real one | High | All tenants | — | Partial | Boot-check the reporting user has no privileges beyond the views; block `@`/`:=` in guard; test on real MySQL | Yes | No |
| SEC-08 | Storage | Sec | Uploads served at unauthenticated `/uploads/**`; one flat dir, no org in the path; type check trusts client `Content-Type`; any member (incl. parents) can upload with no quota; `Person.photoUrl` accepts arbitrary client strings. [V] | Student documents, ID proofs, answer sheets protected only by UUID unguessability; no per-tenant export/delete possible | High | Parents, students | — | Partial | Private bucket/dir, org-prefixed keys, authenticated download or short-lived signed URL, magic-byte check, size/quota | Yes | Yes (D-D2) |
| SEC-09 | Identity | Sec/Product | Forgot-password token is **only logged, never emailed**; verification/reset tokens logged at INFO; no password policy; email verification not required for login. [V] | Self-service recovery doesn't work; every reset becomes a support ticket needing log access | High | All users | Email provider configured | Partial | Send reset email via existing `EmailDeliveryService`; stop logging tokens; min-length policy | Yes | No |
| SEC-10 | Platform | Sec | `POST /api/v1/organisations` is unauthenticated, accepts `planId`, has no rate limit/captcha/approval; provisioning is deliberately non-atomic so failures leave half-built orgs. [V] | Anyone can create tenants and pick a plan; abandoned partial orgs | Medium | Platform | — | Existing | Platform-only or invite-gated; make provisioning resumable/idempotent | Yes | Yes |
| SEC-11 | Platform | Sec | Platform admin: no MFA, no lockout/rate-limit, no per-request status check (disabled admin lives until token expiry), no granular roles ("not enforced anywhere yet"), login/CRM/support actions unaudited. No school-admin MFA either. [V] | One stolen platform credential = every tenant | High | Platform | — | Partial | MFA (TOTP) for platform admins first; audit login; disable→revoke | Yes (platform) / Soon (school admin) | No |
| SEC-12 | Authorization | Sec | `ROLE_MANAGE`/`MEMBERSHIP_MANAGE` can grant `ALL_FUNCTIONS` and any role; no caller-vs-granted comparison. [V] | Permission-bound delegate can self-escalate | Medium | School admins | — | Existing | Rule: cannot grant what you don't hold; `ALL_FUNCTIONS` Owner-only | Soon | Yes |
| SEC-13 | Auth | Sec/Tech | Rate limiting is in-memory and keyed by `getRemoteAddr()` (behind nginx every visitor shares one IP → one bucket per school); `/refresh`, reset, `/organisations`, job-applications unthrottled; refresh/reset/invite tokens found by bcrypt-matching **all** unrevoked rows (O(n), never purged); lockout can be used to lock out any known email. [V] | Cheap DoS; public admission form blocks all applicants after 10 hits school-wide | Medium | Public | — | Partial | Index tokens by id prefix + hash; `forward-headers-strategy`; per-IP+per-email limits; purge job | Soon | No |
| SEC-14 | Deploy | Sec | No security headers/TLS in compose; springdoc `/v3/api-docs`+Swagger UI likely public in all profiles [U]; CORS default `localhost:5173`. [V] | Standard hardening gaps | Medium | All | — | Missing | Headers at proxy; disable springdoc outside dev | Yes | No |
| SEC-15 | Integration | Sec | Per-org secrets encrypted with `Encryptors.text` (AES-CBC, no integrity), constant salt, one global key, no rotation; changes not audited. [V] | Weak envelope; losing the key makes all stored secrets undecryptable (backup doc says otherwise) | Medium | Platform | — | Partial | GCM + per-record IV, key versioning, audit writes | Soon | No |
| SEC-16 | Export | Sec | Student/guardian/staff exports use class-level `*_VIEW`, return unpaged `findAll()` JSON, are unaudited; CSV is built client-side with no formula-injection sanitising. [V] | Bulk PII leaves the system with no trace and no dedicated permission | Medium | Principal, HR | — | Existing | Dedicated `*_EXPORT` permissions + audit + row limits + sanitise cells | Soon | Yes |
| SEC-17 | Logging | Sec/Ops | MDC has only `requestId` (client-supplied, unvalidated); no org id; payment-link tokens appear in logged URIs; login failures log the email. [V] | Cannot filter logs by school; secrets in logs | Low | Ops | — | Partial | Org+user in MDC; redact token path segments | Soon | No |
| SEC-18 | HR | Tech/Sec | `HrService.submitJobApplication` is `@Transactional` but calls `TenantContext.set` *inside* — the exact pattern the team's own docs say silently no-ops. Only tested with a bogus slug. [V code, U behaviour] | Public careers form may fail or write to the wrong tenant; endpoint has no rate limit/honeypot | Medium | Applicants | — | Unknown | Test the happy path; restructure like `BillingService.generateInvoice` | Soon | No |
| SEC-19 | Messaging | Sec | WebSocket decodes JWT once at CONNECT (expiry/revocation never re-checked); membership resolved via `findFirst()` with no ACTIVE filter; `openDirectThread` lets any member DM any person id; class-group participants snapshotted at first open. [V] | Ex-staff keep live chat; guardians can message arbitrary staff/guardians; child-safety/moderation exposure | Medium | Parents, teachers | SEC-05 | Partial | See D-N4 and §21 (freeze/restrict) | Soon | Yes |
| TEN-01 | Student | Tech | `uq_students_student_id UNIQUE (student_id)` is **global** while ids are generated per org per year (`STU-2026-00001`). Issue #354 confirmed. [V] | The second tenant to admit a student in a year hits a raw constraint error | **Critical** | Every tenant after the first | — | Existing (defective) | Make unique `(organisation_id, student_id)`; migration + test | Yes | No |
| TEN-02 | Jobs | Tech | Schedulers have no distributed lock, run on one default thread, no try/catch per org (one org's exception aborts the rest of that tick); `NotificationDispatchWorker` has no claim step so 2 instances double-send; `SchedulerHeartbeat` is in-memory and only 2 of 4 jobs tick it. [V] | Blocks horizontal scaling; one bad tenant starves others | Medium | Ops | — | Partial | Per-org try/catch now; claim-with-`SKIP LOCKED` or ShedLock before 2nd instance | Soon | No |
| TEN-03 | Payments | Business/Sec | Shared Razorpay account for all orgs (team's own flag: aggregator territory). Webhook resolves org via one deliberate native query. [V/D] | Whose money settles where; contractual/regulatory exposure once a second paying school is live [R] | High | Schools, Platform | — | Partial | Decide model before 2nd school (§20 D-F5); code seam already exists | Yes (decision) | Yes |
| TEN-04 | Audit | Sec | `audit_logs` has manual `organisation_id`, not `@TenantId`; platform actor ids share the tenant `actor_user_id` column with no discriminator; insert-only by convention only; no retention or export. [V] | Reader is correct today; a future unscoped query would leak; ambiguous actors | Low | Compliance | — | Partial | Add `actor_type`; DB grants or trigger for append-only | Later | No |
| TEN-05 | Identity | Product | Login is slug+email+password per org; `User` global by email. A guardian with children in two schools needs two logins (mobile brief lists this as an open question). [V/D] | Decision pending; leaks into mobile UX | Medium | Parents | — | Partial | Decide (D-I3) | Later | Yes |

## 3.2 Attendance, RFID, transport, notifications

| ID | Area | Type | Gap | Why it matters | Sev | Users | Depends | State | Recommendation | MVP | Dec? |
|---|---|---|---|---|---|---|---|---|---|---|---|
| RFID-01 | RFID | Product | No RFID/card/reader/device/event code anywhere (grep: zero). Missing: card registry, device registry+auth, event ingestion, dedupe, offline sync, unknown/lost card flow, attendance derivation, health monitoring. [V] | Stated core differentiator; the whole subsystem is greenfield | **Critical** (vs. stated positioning) | Admin, parents | ATT-01, D-R1..R8 | Missing | Decide MVP scope (D-R1); design per §8 | Decision first | **Yes** |
| ATT-01 | Attendance | Tech | `student_attendances` unique `(enrollment, date)`; no `source`, event timestamp, device id, or idempotency key; register DRAFT→SUBMITTED→LOCKED presumes a human. [V] | Cannot record who/what marked a row, tell an RFID tap from a teacher entry, or represent first-in/last-out | High | Teachers, admin | RFID-01 | Missing | Add `source` (MANUAL/DEVICE/IMPORT/CORRECTION), `marked_at`, `marked_by`; separate raw-event table (§8) | Yes | Yes |
| ATT-02 | Attendance | Product/Sec | Marking isn't teacher-scoped (`TeacherAssignment` unused in attendance); any date incl. past/future; doesn't verify the enrollment belongs to the URL section; doesn't require whole-roster coverage; can be marked on holidays (calendar not consulted). [V] | Wrong-class marking, backfilled attendance with no trace, half-marked registers | High | Teachers, Principal | D-A1, D-A2 | Partial | Section-scoped permission for class/subject teachers; backdate window; roster completeness check | Yes | Yes |
| ATT-03 | Attendance | Product | Corrections take effect immediately, no approval; allowed while register is SUBMITTED; no date limit. [V] | Silent changes to a legal-ish record; parent already alerted | Medium | Principal, parents | D-A3 | Partial | Approval rule configurable per school (default: reason + audit, approval after N days) | Soon | Yes |
| ATT-04 | Attendance | Product | Absence alert: email-only template, static body with **no student name or date** (`sendTemplatedNotification` copies template text verbatim — no variable substitution); no dedupe (re-saving a DRAFT register or double-correction re-sends). [V] | The most-expected parent feature would send unactionable, duplicate emails | High | Parents | NOT-03 | Partial | Add template variables; dedupe key `(student,date,type)`; channel per D-N1 | Yes | Yes |
| ATT-05 | Attendance | Product | Monthly % denominator is marked days, not working days; `WorkingCalendarEntry` unused by attendance; `is_excused` ignored; half-day = 0.5; no per-school formula. [V] | Numbers won't match the school's own register; disputes | Medium | Principal, parents | D-A4 | Partial | Compute against working calendar; make excused rule configurable | Soon | Yes |
| ATT-06 | Attendance | UX/Product | Teacher cannot mark attendance from the portal/mobile (no `/me` attendance API; `ATTENDANCE_MARK` is admin-shell). Staff attendance has API but **no UI** anywhere. No period-wise attendance. [V] | Marking attendance is the teacher's most frequent task and it isn't on their surface | High | Teachers | MOB-01 | Missing | Teacher-scoped attendance endpoints + portal/mobile screen; staff attendance screen | Yes | No |
| ATT-07 | Attendance | Product | No student leave/absence-reason intake (parent → school); `LEAVE` status exists but no workflow feeds it for students. [V] | Parents will phone/WhatsApp instead | Low | Parents | MOB | Missing | After mobile | Later | No |
| TRN-01 | Transport | Product | Boarding is a manual batch by staff (no timestamp/stop); no boarding or trip start/complete notifications (only cancel and route/stop change; email-only); no driver/attendant surface; no expiry alerts (chip only). [V] | Transport is a top parent-anxiety area [A]; nothing proactive | Medium | Parents, Transport Mgr | NOT-01 | Partial | Keep manual; add trip-start/complete only after push exists | Later | No |
| TRN-02 | Transport | Product | Ending/reassigning a route doesn't touch the fee assignment; no stop-level pricing. [V] | Children billed for buses they no longer take | Medium | Accountant | FEE-* | Partial | Prompt to revise fee on route change | Soon | Yes |
| NOT-01 | Notifications | Product | No push channel, no device-token registration endpoint, no FCM/APNs anywhere. Channels are IN_APP/EMAIL/SMS/WHATSAPP only. [V] | Mobile app cannot notify | High | Parents, teachers | MOB-01 | Missing | Add device-token registry + push sender (Expo/FCM) when mobile ships | Yes (with mobile) | No |
| NOT-02 | Notifications | Tech | Outbox: no attempt counter, no backoff/retry, FAILED terminal, unbounded `findByDeliveryStatus(PENDING)`, provider HTTP call inside `REQUIRES_NEW`; transactional `EmailOutbox` has no UI. [V] | One provider blip permanently loses fee reminders/alerts; failures invisible | High | Parents, Ops | — | Partial | attempts, next_attempt_at, dead-letter status, batch limit, failed-notifications admin view | Yes | No |
| NOT-03 | Notifications | Sec/Compliance | SMS sender posts plain sender/recipient/content; no DLT template/entity id handling. **Whether DLT registration is required for the intended use requires verification.** [V code; R] | Indian SMS may be blocked/filtered without registered templates | Medium | Parents | D-N1 | Unknown | Verify before choosing SMS at all (see §14) | Decision | Yes |
| NOT-04 | Notifications | Product | No quiet hours; no locale on templates; web has no i18n at all (no Hindi/regional). [V] | Many guardians won't read English-only messages [A] | Medium | Parents | D-N2 | Missing | Decide languages first; add `locale` to templates | Soon | Yes |
| NOT-05 | Notifications | Product | Notification variable substitution absent in `sendTemplatedNotification`; whether fee/library/result callers build bodies themselves before calling it is **Unknown**. [V/U] | Personalisation missing in at least attendance | Medium | Parents | — | Unknown | Verify each caller; add substitution | Yes | No |
| NOT-06 | Onboarding | UX/Product | Guardian portal invite has **no delivery**: staff copies a claim link, a raw token, and must separately tell the parent the org slug (claim link doesn't embed it); 7-day expiry. Staff invites do send email best-effort. [V] | ~1,000+ manual hand-offs per school; the #1 adoption blocker | High | Front-desk, parents | Email/SMS decision | Partial | Bulk "send invites" (channel per D-N1), slug embedded in link, resend, invite status view | Yes | Yes |

## 3.3 Mobile & portals

| ID | Area | Type | Gap | Why it matters | Sev | Users | Depends | State | Recommendation | MVP | Dec? |
|---|---|---|---|---|---|---|---|---|---|---|---|
| MOB-01 | Mobile | Product/Sec | Mobile app implements only a Principal/VC-style admin flow; no role branching; calls **no** `/api/v1/me/**` endpoint; session incl. refresh token in **AsyncStorage** (not SecureStore); refresh endpoint never called (401 → forced login); no multi-child, role-switch, push registration, deep links, offline. `Teacher:Staff/` dir referenced in README doesn't exist; `Parent:Student/` holds one image. [V] | The parent and teacher mobile experience effectively doesn't exist; the stored token is exposed on a lost/rooted phone | High | Parents, teachers | NOT-01, SEC-04 | Partial | Rebuild role flow parent-first (§9) | Yes | Yes (D-M1) |
| MOB-02 | Portal | Product | Guardian portal cannot: pay fees (payment-link controller exists but isn't linked), view timetable, transport, library, events/holidays, attendance history (only today + monthly aggregate), in-app notifications, message teachers; class-targeted announcements excluded. Teacher portal: no attendance, timetable, notifications. [V] | Parents open the app for attendance, fees, notices; two of three are thin | High | Parents, teachers | — | Partial | Attendance history, fee pay link, notifications inbox first | Yes | No |
| MOB-03 | Identity | Product | No student login by decision (guardian-only). Mobile brief still lists "Parent/Student". [V/D] | Older-grade students (and their own timetable/homework) are unserved | Low | Students | D-I2 | Missing (deliberate) | Keep guardian-only for MVP; revisit for Grade 8+ | Later | Yes |

## 3.4 Student, academic, exams

| ID | Area | Type | Gap | Why it matters | Sev | Users | Depends | State | Recommendation | MVP | Dec? |
|---|---|---|---|---|---|---|---|---|---|---|---|
| STU-01 | Student | Product/UX | No bulk promotion / year-end rollover / class-section copy / fee-structure clone / balance carry-forward. Only per-student `promote` API exists and **the admin web calls none of promote, reassign-section, or record-exit**. The enrolment CSV import calls `enroll()` and fails for any student with a current enrollment. [V] | Every school hits this in its first March/April; mid-year exits/section changes have no UI | High | Admin, Principal | D-AC1 | Missing | Year-rollover wizard (dry-run, per-class rules) + UIs for promote/reassign/exit | Yes (before first year-end; exit/reassign UIs now) | Yes |
| STU-02 | Academic | Tech/Product | `AcademicYear.CLOSED` is checked nowhere but inside the entity: closed years still accept enrollments, attendance, invoices; a closed year can be re-marked current; one-current invariant is service-only with a non-unique index and no lock. [V] | Data written into finished years; two "current" years under a race | Medium | Admin | D-AC2 | Partial | Guard writes by year status; unique/generated-column constraint | Soon | Yes |
| STU-03 | Student | Product | Exit closes the enrollment only: fee assignments/invoices, transport assignment, library loans, guardian portal access, siblings' records untouched; no dues check. [V] | Left students keep getting fee reminders and portal access | High | Accountant, parents | D-S2 | Partial | Exit checklist (dues, books, transport) + cascades by policy | Yes | Yes |
| STU-04 | Student | Product | ALUMNI status unreachable (`markAlumni` no callers); no re-admission (exit requires ACTIVE; unique `(student, year)` blocks same-year return; WITHDRAWN never reactivates); exiting an ADMITTED student throws. [V] | "Left and came back" is common [A]; dead state | Medium | Admin | D-S3 | Partial | Explicit re-admit + graduate-to-alumni flows | Soon | Yes |
| STU-05 | Student | Product/Tech | No duplicate-student detection anywhere; guardian match by `personId` only; guardian import creates a new Person+Guardian per row (siblings get separate guardians); student import re-run duplicates every student; `persons.phone/email` un-indexed. Whether a guardian with two Guardian rows can see both children under one login is **Unknown**. [V/U] | Data quality rots on day one of import; parent with 2 children may need 2 logins or see one | High | Admin, parents | D-S1 | Missing | Match-or-create guardian by normalised phone(+email) within org; import dedupe with preview | Yes | Yes |
| STU-06 | Student | Product | No Transfer Certificate, bonafide, or character certificate generation (`DocumentType` = question-paper header + report card; `tc_number` is a free-text field). Certificate *content requirements* [R]. [V] | Among the first documents a school produces when a student leaves [A] | High | Admin | RPT-03 | Missing | Templated certificate + numbering + audit; verify mandatory fields with the pilot school | Yes | Yes |
| STU-07 | ID card | Product | ID card: template studio + single-student render; no bulk render/print, no PDF, no roll number/guardian phone/validity fields, QR is plain text (no signed token), no 1-D barcode, no card ↔ RFID link. [V] | ID cards are the physical entry to the RFID story | Medium | Admin | RFID-01, RPT-03 | Partial | Bulk PDF print with fixed layouts; add card UID once decided | Yes (with RFID) | Yes |
| STU-08 | Student | Sec/Compliance | No Aadhaar/APAAR/PEN/UDISE fields. Whether to store any national identifier, and under what rules, **requires verification** [R]. | Government exports/forms may need them; storing them adds obligations | Medium | Admin | D-CP1 | Missing | Do not add until verified; custom fields exist as an escape hatch | No | Yes |
| STU-09 | Admissions | Product | Converting an applicant only calls `admit()`: no guardian created from the applicant's guardian data, no enrollment, no fee; 5 hard-coded document flags (no upload/verification); no entrance/offer/seat/registration-fee steps. [V] | Front-desk re-keys everything after "convert" | Medium | Reception | — | Partial | Convert → student + guardian + optional enroll in one action | Soon | No |
| STU-10 | Enrollment | Tech | `enroll`/`promote`/`reassignSection` don't check section belongs to the academic year; promote doesn't check grade progression; `reassignSection` **mutates in place** (history only in audit log — contradicts the stated "history never overwritten"); roll-number uniqueness includes closed rows; capacity is soft. [V] | Bad data via UI/API; section-history reports impossible | Medium | Admin | — | Partial | Validate; add `enrollment_section_history` | Soon | No |
| ACD-01 | Academic | Product | No `Term` entity (exam names are free text); no student-level electives/optional subjects or streams (Class 11–12); no house/sports group; template stops at Grade 10. [V] | Blocks cumulative report cards and senior-school use | Medium | Academics | D-AC3, D-AC4 | Missing | Add Term; decide K–10 vs K–12 scope | Soon | Yes |
| ACD-02 | Academic | Tech | `assignTeacher` doesn't verify the subject is on the class's `ClassSubject` list; timetable is "current only" (no history); periods are one org-wide set. [V] | Timetable and teaching assignments can drift from curriculum; past timetables unrecoverable | Low | Academics | — | Partial | Validate; accept limits for MVP | Later | No |
| EXM-01 | Exams | Product | `ReportCard` is per single exam (one grade, one rank); no aggregation across exams/terms, weightage, best-of-N, internal assessment, co-scholastic areas, attendance/remarks on the card; scale chosen per publish request, default A+…F not CBSE-style. [V] Indian schools' cumulative report cards [A]. | The report card is the school's main deliverable to parents; a per-exam card won't be accepted as "the report card" | High | Principal, parents | ACD-01, D-E1 | Partial | Fix with the pilot school's actual template before building | Yes (scope via D-E1) | Yes |
| EXM-02 | Exams | Tech | `enterMarks`/`MarksEntry` don't validate `0 ≤ marks ≤ maxMarks` nor that the enrollment belongs to the schedule's class; publish is one-enrollment-at-a-time with immediate guardian notification and no visibility toggle. [V] | 105 marks typed out of 100 become a published grade; parents notified prematurely | High | Teachers, parents | — | Partial | Server validation; bulk publish with scheduled release | Yes | No |
| EXM-03 | Exams | Product | No hall tickets, PDF report card (browser print only), marks import, re-evaluation, supplementary exams, or link from results to promotion. [V] | Manual re-keying; result→promotion is a human spreadsheet | Medium | Teachers | RPT-03 | Missing | After PDF infra; import marks first | Soon | No |

## 3.5 Finance

| ID | Area | Type | Gap | Why it matters | Sev | Users | Depends | State | Recommendation | MVP | Dec? |
|---|---|---|---|---|---|---|---|---|---|---|---|
| FEE-01 | Finance | Tech | `recordPayment` reads and writes `invoice.amountPaid` with no lock, no `@Version` (none anywhere in the codebase), and no idempotency key; `payment_reference` isn't unique; Razorpay webhook idempotency is a plain status read without a lock. [V] | Two counters, a double-click or a webhook redelivery can lose or duplicate money | High | Accountant | — | Partial | Pessimistic lock or `@Version` on invoice; client idempotency key; unique gateway payment id | Yes | No |
| FEE-02 | Finance | Tech | `RecordPaymentRequest`/allocations lack `@Valid`/`@Positive`; no over-payment check; no invoice-year check; unrestricted payment dates. [V] | Negative/overpaid invoices | High | Accountant | — | Partial | Add validation and invariants | Yes | No |
| FEE-03 | Finance | Product/Sec | Refund applies immediately: no request→approve, requester can be the approver, not capped by paid amount (can drive `amountPaid` negative), no gateway refund; `approvedBy` comes from the request body and the policy checks only that person's permission, not the caller's; `FEE_DISCOUNT_APPROVE` seeded but unused. [V] | Money leaves without independent approval | High | Accountant, Principal | D-F2 | Partial | Two-person rule (configurable), cap, `approvedBy = caller` | Yes | Yes |
| FEE-04 | Finance | Product | Invoices are generated manually per (assignment, installment); no bulk or scheduled generation. [V] | 1,000 students × 4 installments = ~4,000 clicks per year | High | Accountant | D-F1 | Missing | "Generate invoices for class/all for installment N" with preview | Yes | Yes |
| FEE-05 | Finance | Product | No daily collection report, cash book, day-close, bank/gateway reconciliation, defaulter report beyond the overdue list, or class-wise dues summary. [V] Daily cash reconciliation is a typical accountant routine [A]. | The accountant's end-of-day has no tool | High | Accountant, Principal | RPT-01 | Missing | Collection-by-day/method/user report + close-day lock | Yes | Yes |
| FEE-06 | Finance | UX | Receipt is a confirmation dialog; no printable/PDF receipt; no receipt void other than cheque bounce. [V] | Parents expect a paper/PDF receipt at the counter [A] | High | Accountant, parents | RPT-03 | Partial | Print-styled receipt now, PDF later | Yes | No |
| FEE-07 | Finance | Tech | `assignFee` doesn't prevent assigning the same structure to the same student twice; only the opening-balance import guards. [V] | Double billing | High | Accountant | — | Partial | Unique on (student, structure, year) or service guard | Yes | No |
| FEE-08 | Finance | Business | No sibling discount, staff-child concession, or scholarship model — only ad-hoc adjustments/waivers; late fee **deliberately none**; credit/wallet **deliberately deferred**. [V/D] | Schools do have these policies [A]; ad-hoc entries are unauditable as policy | Medium | Accountant | D-F3 | Partial | Decide the concession model once, not per-invoice | Soon | Yes |
| FEE-09 | Finance | Product | No year-end carry-forward of outstanding balances; invoice statuses lack VOID/CANCELLED; `FeeStructure` clone for new year absent. [V] | New-year starts with re-keyed dues | Medium | Accountant | STU-01 | Missing | Part of the rollover wizard | Yes (with STU-01) | No |
| FEE-10 | Finance | Tech | Editable number formats (`OrganisationBranding`) are only `@NotBlank`; a format without `{YYYY}`/`{AY}`/`{SEQ}` resets yearly and collides with `uq_students_org_admission_number`/`uq_payments_org_receipt_number`/`uq_invoices_org_number`. Admission counter is calendar-year, fee counter academic-year. [V] | A settings edit can make next year's first receipt fail | Medium | Admin | — | Partial | Validate format on save | Yes | No |

## 3.6 Authorization & usability

| ID | Area | Type | Gap | Why it matters | Sev | Users | Depends | State | Recommendation | MVP | Dec? |
|---|---|---|---|---|---|---|---|---|---|---|---|
| AUTH-01 | RBAC | Product | Everything is org-wide. `OrganisationMembership.campus/department` are stored but nothing reads them. Section/class scoping exists only in `learning` and `portal`. [V] | Any `MARKS_ENTER` holder edits any class; multi-campus schools can't separate campuses | High | Principal, teachers | D-P1..D-P3 | Missing | Add a small scope layer (§6) | Yes (section scope); Soon (campus) | Yes |
| AUTH-02 | RBAC | Sec | Default for unannotated controllers is "any authenticated member" (~30 controllers, incl. Person, Messaging, Upload, Notification, FeeApprover). Docs claim checks are "never left to individual controllers". [V] | Default-allow is how SEC-05 happened | High | All | — | Partial | Flip to default-deny with an explicit `@AllAuthenticated` opt-out; ArchUnit test | Yes | No |
| AUTH-03 | RBAC | Product | Missing/blurred permissions: no `*_EXPORT`, no `GUARDIAN_PHONE_VIEW`; `HR_PAYROLL_VIEW` gates both **read and write** of bank details; `PAYROLL_MANAGE` covers generate+finalize+correct+delete; audit-log view shares `ORGANISATION_MANAGE` (24 usages); no import permission; no backdating permission; `REPORT_VIEW` unused; V100 template references deleted codes (`STUDENT_CREATE/UPDATE`) and silently drops them. [V] | Cannot separate duties (accountant vs principal vs HR) | Medium | All | §6 | Partial | See §6.3 | Soon | Yes |
| AUTH-04 | RBAC | Product | Provisioning seeds only Owner; the 12-role school template is opt-in and lacks Vice Principal, Class Teacher, Subject Teacher; the lazy Guardian role is created on first claim. [V] | Fresh school starts with one all-powerful user; the "Class Teacher" concept (central in schools) isn't a role or a scope | Medium | Principal | D-P2 | Partial | Auto-offer template at first login; model class teacher as assignment, not role | Yes | Yes |
| AUTH-05 | Web | UX/Sec | Per-button gating is not retrofitted (45 `<Can>` vs 666 buttons); no per-route permission guard; permissions loaded once at login (changes appear after re-login). [V] | A teacher sees Finalize Payroll and gets a 403 after clicking | Medium | All staff | — | Partial | Route-level guards + `<Can>` on destructive actions first | Soon | No |
| AUTH-06 | HR | Product/Sec | `recordExit` flips `StaffProfile` status only: membership stays ACTIVE, TeacherAssignments remain, timetable and class-teacher duty remain, no event bus to cascade. [V] | Ex-teacher keeps access and appears on timetables; sections have a phantom teacher | High | Principal, HR | SEC-04 | Partial | Exit checklist: revoke membership, end assignments, flag timetable gaps | Yes | No |
| AUTH-07 | Reporting | Product | `REPORT_CREATE` lets any holder author raw SQL (seeded to Principal and Accountant). [V] | Non-technical roles given a SQL editor; only real safety is the DB user | Medium | Principal | SEC-07 | Existing | Reserve raw-SQL for platform/power users; ship canned reports | Soon | Yes |

## 3.7 Platform, operations, reporting, migration, UX

| ID | Area | Type | Gap | Why it matters | Sev | Users | Depends | State | Recommendation | MVP | Dec? |
|---|---|---|---|---|---|---|---|---|---|---|---|
| PLT-06 | Ops | Ops | Backups documented (nightly dump, PITR, RPO ≤15 min, RTO ≤4 h, quarterly drill) but **none automated**; drill log "none yet"; doc says no file storage exists, but uploads do and aren't covered; losing `INTEGRATION_SECRET_KEY` makes stored secrets unreadable. [V/D] | "What data must never be lost" has no protection | **Critical** | All | — | Missing | Automated dump + binlog + off-host copy of DB **and** uploads; run one restore drill before go-live | Yes | No |
| PLT-08 | Deploy | Ops | Dockerfile: no non-root user, no HEALTHCHECK, no JVM flags, tests skipped; compose: MySQL port published, backend runs as DB root, no uploads volume, missing `REPORTING_DB_*`/`INTEGRATION_SECRET_KEY`, frontend nginx proxies only `/api` (so `/uploads` and `/ws` unreachable), no TLS, no graceful shutdown, default Hikari. Docs say there is "no production build/deploy story". [V/D] | There is no proven way to run this safely in production | High | Ops | — | Partial | One prod reference deployment + runbook | Yes | Yes (D-O1 hosting) |
| PLT-07 | CI | Tech | CI runs tests on H2 `create-drop` with Flyway **disabled**; nothing applies the 129 migrations to MySQL; e2e (32 specs) not in CI; H2 divergences already bit (`value`/`year` reserved words, read-only-tx audit write passes H2 fails MySQL). [V] | Migration/DB bugs reach prod; the team discovered two only by running the jar | High | Dev | — | Partial | CI job: Flyway on MySQL 8.4 + smoke e2e | Yes | No |
| PLT-09 | Observability | Ops | Only `/actuator/health` exposed; plain-text logs; no metrics/Sentry/tracing; no org id in logs; platform "system health" is 3 booleans; usage = active-student count + last audit time; no alerting; webhook failures and `payment.failed` silently ignored. [V] | When a school says "fees didn't update", nobody can find out why | High | Ops | — | Missing | See §17 | Yes (minimal) | No |
| PLT-03 | Platform | Ops/Business | No tenant offboarding, deletion, per-tenant export, or per-tenant backup; `DataSubjectRequest` deletion "records that it was handled — does not erase". [V] | First churn or data-return request has no procedure | High | Platform, schools | D-PL3 | Missing | Documented manual runbook first; automate later | Soon | Yes |
| PLT-04 | Support | Business/Ops | Support tickets are platform-admin-only (platform admin types requester email/org); tenants cannot raise a ticket; no impersonation/support-access; no SLA fields. [V] | The customer has no in-product way to ask for help | Medium | Schools | D-PL4 | Partial | Tenant "Contact support" form; audited, time-boxed support access | Soon | Yes |
| PLT-01 | Platform | Business | No plan limits (students/users/storage) and no module toggles/feature flags: `Plan` = code, name, price. All modules on for all orgs; nav gated by permission only. [V] | Cannot sell tiers, cap abuse, or hide unfinished modules | Medium | Platform | D-PL1, D-PL2 | Missing | Decide pricing dimensions; add a per-org enabled-modules set | Soon | Yes |
| PLT-02 | Billing | Business | Billing is manual (`generateInvoice` by platform admin), no proration, no dunning, no trial→paid, no auto-suspend, no tenant billing view, no gateway for platform invoices. [V] | Fine for 1–3 pilot schools; not beyond | Medium | Platform | SEC-03 | Partial | Keep manual for pilots; write the billing policy | Later | Yes |
| RPT-01 | Reporting | Product | Only 4 seeded reports (all sales/inventory/purchase). No standard reports for attendance, fee collection, defaulters, marks, staff, library, transport; reporting views don't exist for library/transport. [V] | A Principal's first request is "show me today's absentees / this month's collection" | High | Principal, Accountant | FEE-05 | Missing | Canned reports (§13) | Yes (minimal set) | No |
| RPT-03 | Cross-cutting | Tech | No PDF generation anywhere (no library); receipts, report cards, payslips, ID cards, certificates all rely on browser print or nothing; no XLSX export (POI used for import only). [V] | One missing capability blocks ~6 deliverables | High | All | — | Missing | Pick one server-side HTML→PDF approach once | Yes | Yes |
| RPT-04 | Performance | Tech | 3 of ~137 controllers page; `GET /students`, `/persons`, `/fees/invoices/overdue`, report cards, and exports are `findAll()` (43 `findAll()` in controllers); student search `LIKE '%x%'`; EAGER `@ManyToOne(optional=false)` chains; no batch fetch size. [V] | Fine at 200 students; sluggish at 2,000 × 3 years | Medium | All | — | Partial | Page + index the top 6 lists before go-live | Yes | No |
| MIG-01 | Migration | Product | Student import: no `validateOnly`, not recorded in import history, no dedupe. Whole-import rollback doesn't exist by design (per-row `REQUIRES_NEW`); `ImportRun` stores counts only. No photo, RFID-mapping, marks, fee-assignment or historical-attendance import; guardian import is per-student CSV. [V] | The biggest first-week task has the weakest tool | High | Admin | STU-05 | Partial | See §15 | Yes | Yes |
| MIG-02 | Onboarding | Product | Provisioning creates one campus + Owner + optional year; template (opt-in) creates grades, departments, designations, roles, fee categories, grading scale but **not** subjects, academic year, classes/sections, periods, calendar, exams. [V] | 2–3 days of setup before anyone can mark attendance | Medium | Admin | — | Partial | Guided setup wizard already partly exists (6-step checklist); add class/section/subject scaffold | Yes | No |
| UX-01 | UX | UX | No shared confirm dialog: revoke membership, end teacher assignment, delete period, cancel announcement, remove payslip line and **Finalize payroll** act immediately. [V] | Irreversible mis-clicks | Medium | All | — | Missing | `ConfirmDialog` on destructive/irreversible actions | Yes | No |
| UX-02 | UX | UX | Search/pagination present on Students, Admissions, Audit only; Staff and Inventory lists have neither; no shared empty/skeleton pattern; no multi-row bulk actions on any list; no mobile drawer (permanent sidebar). [V] | Poor at scale; unusable on a phone browser | Medium | Staff | RPT-04 | Partial | Shared list component (search, pagination, empty, loading) | Yes (top lists) | No |
| UX-03 | UX | UX | Terminology drift: nav "Members" vs settings key/routes `users`/`members`; Grade level/Class/"Class offering"/Section; "Academics" both a group and an item. Staff attendance, promote, reassign-section, record-exit exist only as API. [V] | Training burden | Low | Staff | — | Partial | Glossary + rename pass | Soon | No |
| UX-04 | UX | UX | Fee receipt, ID card and certificate have no print view; report card/payslip/sale use `window.print()` with only a `.no-print` class. Accessibility: 40 `aria-` attributes, 7 `alt`, one keyboard handler across the app. [V] | Print is core to schools; a11y untested | Low | Staff | RPT-03 | Partial | Print CSS system; basic a11y pass | Soon | No |

## 3.8 Second pass: deep audit of eight modules

The first pass treated these modules as *Needs validation*. A second pass on 2026-09-30 read each one in full (backend, migrations, tests, web UI) with six read-only reviews. Nothing was executed, so concurrency and MySQL behaviour are [I] unless stated. IDs use the prefixes LIB, INV, PUR, SAL, CRM, LMS, PAY, REG. A dash in the Depends column means no dependency was found. Module verdicts are in §4.

## 3.9 Library

**Verdict: usable with fixes.** The issue and return loop works and tenancy follows the platform pattern. Fines cannot be collected from the UI, damaged or lost copies are dead ends, issue is not safe under two desks, and nothing is audited. Issue #354 (student id uniqueness) is not a library bug: `uq_students_student_id` lacks `organisation_id` (V80:18) and library borrowers reference `persons.id`.

| ID | Area | Type | Gap | Why it matters | Sev | Users | Depends | State | Recommendation | MVP | Dec? |
|---|---|---|---|---|---|---|---|---|---|---|---|
| LIB-01 | Library | Product | A fine raised after a return cannot be found again in the UI: the fine table renders only for `lastRecord`, set right after return or mark-lost (`BorrowPanel.tsx:64,141-147,272-315`); `FineRepository.findByStatus` is unused; the overdue tab shows only open loans and loan history shows no fines. [V] | A librarian raises a fine, the parent says "tomorrow", and the next day no screen lists it: pending fines are uncollectable | High | Librarian | — | Partial | Add a Fines tab (pending list, pay, waive) and show fines in loan history | Yes | No |
| LIB-02 | Library | Product | Fines are typed by hand: no per-day rate, grace days, cap or auto-fine on return, lost or damaged (`Fine.java:18-25`, `LibraryService.java:123-125`); the "Fine due" column on the overdue tab is almost always empty (`OverduePanel.tsx:88`). [V] | Every overdue book needs mental arithmetic, so amounts differ between librarians and students | High | Librarian | LIB-01 | Missing | Fine-rate setting with grace days and a cap; suggest the amount at return | Yes | No |
| LIB-03 | Library | Correctness | A copy can be double-issued under concurrency: `issue()` reads status then saves (`LibraryService.java:66-90`), no `@Version` (`BaseEntity.java:18-42`), no lock, no unique guard on active loans (V17:66 is a plain index). H2 tests are single-threaded. [V] | Needs two desks or a double click; result is two students recorded holding one book and a permanently wrong history | High | Librarian | PLT-07 | Existing (defective) | Lock the copy row in issue, return and lost; add a DB guard on active loans | Yes | No |
| LIB-04 | Library | Correctness | Loan-limit and block-on-overdue checks are also non-atomic (count then insert, `LibraryService.java:75-84`). [V] | Two desks can push a student past the limit; single-desk use is unaffected | Medium | Librarian | LIB-03 | Existing (defective) | Lock the borrower's loan rows inside issue | Soon | No |
| LIB-05 | Library | Product | Damaged and lost copies are dead ends: issue accepts only AVAILABLE (`LibraryService.java:68`), there is no endpoint to repair, recover, reshelve or withdraw a copy (`BookController.java:46-115`), `WITHDRAWN` is set by nothing, LOST is terminal (`BorrowRecord.java:69-74`). [V] | The first damaged or "found later" book leaves circulation for good; counts drift from the shelf | High | Librarian | — | Missing | Allowed copy-status transitions and an undo-lost action | Yes | No |
| LIB-06 | Library | Product | Borrower and book pickers are plain dropdowns: every Person from an unpaged `GET /persons` (`BorrowPanel.tsx:72,338-344`, `PersonController.java:43-46`), name only (no class or admission number), book then copy list, no accession or barcode lookup. [V] | With about 1,000 students plus staff and guardians the list is unusable and duplicate names cause wrong-borrower errors | High | Librarian | — | Partial | Search autocomplete with admission-number filter and issue by accession number | Yes | No |
| LIB-07 | Library | Data | Nothing ties the library to student exit or staff deactivation: `StudentService.recordExit` has no library reference (`StudentService.java:195-210`), `issue()` never checks person or student status (`LibraryService.java:66-91`). [V] | Leavers keep unreturned books forever, any Person can be issued books, and no "no dues" check is possible before a transfer certificate | High | Librarian, registrar | STU-06 | Missing | Block issue to non-active borrowers; show open loans and unpaid fines in the exit flow | Soon | No |
| LIB-08 | Library | Product | No price on a book or copy (`Book.java:26-43`, `BookCopy.java:29-50`); mark-lost raises no fine, asks no confirmation and cannot be undone (`BorrowPanel.tsx:156-165,261`). [V] | No default replacement charge; one misclick corrupts a loan permanently | High | Librarian | LIB-05 | Missing | Price on copy or book, suggested lost-book fine, confirmation dialog | Yes | No |
| LIB-09 | Library | Data | Import loads the catalogue only: no copies or accession numbers (`BookRowImportService.java:28-47`); copies are added one at a time in a dialog (`BookDetailPage.tsx:247-274`). [V] | A school's first task is loading thousands of books with copies; that is days of typing | High | Librarian, admin | §3.7 | Partial | Import copies with accession, campus, shelf; "add N copies" action | Yes | No |
| LIB-10 | Library | Sec | `LibrarySettingsController` has no `@RequirePermission` on GET (`LibrarySettingsController.java:17-32`; SEC-05 pattern) and PUT uses `LIBRARY_BORROW_MANAGE` (`:35`), so any issue-desk librarian can change loan limits. [V] | Low sensitivity, but a governance gap | Medium | Admin | AUTH-02 | Existing (defective) | Gate GET with `LIBRARY_VIEW`; separate settings permission for PUT | Soon | No |
| LIB-11 | Library | Sec | Fine waiver is weak: `waivedBy` comes from the request body (`FineController.java:58`), reason not required server-side, UI hardcodes "Waived by admin" (`BorrowPanel.tsx:191`), one permission `LIBRARY_FINE_MANAGE` covers raise, pay and waive. [V] | One person can raise a fine, waive it and pocket cash with no trail | Medium | Principal | LIB-13 | Existing (defective) | Take the actor from `TenantContext`, require a reason, add a separate waive permission | Yes | No |
| LIB-12 | Library | Data | Fine payment has no collector, receipt, payment mode or partial payment; `payFine` accepts any `paidDate` (`Fine.java:47-56`, `LibraryService.java:127-130`); no link to Finance (the seeded "Library Fee" category is unused). [V] | Cash collected at the library never reaches the school's books and cannot be reconciled | Medium | Accountant | §3.5 | Missing | Post paid fines into the fee ledger or record collector, receipt and mode | Soon | No |
| LIB-13 | Library | Sec | The library never calls `AuditLogService`; issue, return, lost, fine pay and waive, settings and catalogue edits are unaudited. [V] | "Who marked this book lost or waived this fine" cannot be answered | Medium | Principal | — | Missing | Audit loan, fine, settings and copy-status changes | Soon | No |
| LIB-14 | Library | Correctness | Dates are not validated: `returnedDate` is nullable and stored unchecked (`BorrowRecord.java:61-67`, `LibraryService.java:94-96`); `issue()` accepts a due date before the borrowed date and arbitrary backdated or future dates (`LibraryService.java:86`); no `@Valid` in the module. [V] | The API can store a RETURNED loan with no return date; the UI always sends today, so a Friday return cannot be recorded on Monday | Medium | Librarian | — | Existing (defective) | Validate dates; let the UI choose the return date | Yes | No |
| LIB-15 | Library | Correctness | Overdue uses calendar days from server `LocalDate.now()` (`BorrowRecordController.java:81`) and UI UTC `toISOString()` for "today" (`BorrowPanel.tsx:41-43`); no holiday awareness although a working calendar exists; no renewals or reservations. [V] | A book due during vacation is flagged overdue; between midnight and 05:30 IST the UI date is off by one | Medium | Librarian | — | Partial | Use the organisation timezone and working days; add renewal | Soon | No |
| LIB-16 | Library | Ops | Reminders are manual, one click per loan: no scheduler, no "last reminded" stamp so repeat clicks duplicate sends, email-only static template without book, due date or fine, guardians not notified, also accepted for non-overdue or returned loans (`LibraryReminderService.java:13-46`, `BorrowRecordController.java:104-108`). [V] | Most children have no email on their Person record, so the UI says "No contact on file" (`OverduePanel.tsx:40`) | Medium | Librarian, parents | NOT-01 | Partial | Daily per-organisation scheduler, guardian channel, richer template, last-reminded stamp | Soon | No |
| LIB-17 | Library | Product | `mark-damaged` and book withdraw have endpoints but no UI caller (`BorrowPanel.tsx` imports only issue, list, lost, return). [V] | Dead code that looks like a feature; the catalogue can never be pruned | Low | Librarian | LIB-05 | Partial | Wire both into the UI or delete the endpoints | Later | No |
| LIB-18 | Library | Correctness | Withdrawing a book withdraws only the `Book` row (`LibraryService.java:52-55`); copies stay AVAILABLE and issuable because `issue()` checks neither book status nor copy campus; `addCopy` accepts a withdrawn book (`BookController.java:73-81`). [V] | Withdrawn textbooks are still handed out | Medium | Librarian | — | Existing (defective) | Check book status in issue and addCopy; cascade withdrawal | Soon | No |
| LIB-19 | Library | Correctness | Borrower type is "has any `StaffProfile`" regardless of status (`LibraryService.java:72`); limits are per type only; settings accept 0 or negative values (`LibrarySettingsController.java:39-43`). [V] | A terminated teacher still borrows on staff terms; a typo of -14 creates loans overdue on day one | Medium | Admin | AUTH-06 | Existing (defective) | `@Min` on settings; apply status rules | Soon | No |
| LIB-20 | Library | Correctness | Catalogue input is unvalidated (blank title accepted); duplicate accession gives a generic 409 (`ApiExceptionHandler.java:62-65`); ISBN neither unique nor format-checked (`Book.java:14-17`); bad `FineReason` returns 404 (`ApiExceptionHandler.java:38-40`), null gives 500. [V] | Unhelpful errors and duplicate titles pile up | Medium | Librarian | — | Existing (defective) | Bean Validation and explicit duplicate messages | Soon | No |
| LIB-21 | Library | Data | Import de-duplication uses only ISBN, so rows without one duplicate on every re-run; two existing books with one ISBN turn a row into an error; validate-only cannot see in-file duplicates; headers are case-sensitive (`BookRowImportService.java:32-38`, `ImportFileParser.java:62`). [V] | A repeated import leaves duplicate titles | Medium | Admin | §3.7 | Partial | Title-plus-author key, in-file duplicate check, case-insensitive headers | Soon | No |
| LIB-22 | Library | Sec | `GET /api/v1/persons` has no `@RequirePermission` (`PersonController.java:43-46`) and the library UI depends on it (`BorrowPanel.tsx:72`): any member can list every person's phone, email, DOB and address. [V] | The librarian needs a narrow picker; the endpoint exposes full contact data to every role | Medium | All | AUTH-02 | Existing (defective) | Search-limited picker returning minimal fields, gated by a view permission | Soon | No |
| LIB-23 | Library | Tech | Unpaged lists and N+1: `/persons`, book export `findAll`, a fine query per overdue row (`BorrowRecordController.java:81-84,110-115`), `BookDetailPage` loads the whole list and filters (`BookDetailPage.tsx:39-69`). [V] | Fine at a few thousand titles; degrades as history grows | Low | Librarian | — | Partial | Paging, search and fetch joins | Later | No |
| LIB-24 | Library | Correctness | `firstName + " " + lastName` with nullable `lastName` shows "Ira null" (`BorrowRecordResponse.java:15`, `OverdueBorrowResponse.java:15`). [V] | Cosmetic but on every screen for single-name people | Low | Librarian | — | Existing (defective) | Null-safe name formatting | Later | No |
| LIB-25 | Library | Correctness | `updateCopyShelfLocation` ignores the `{id}` path variable (`BookController.java:88-95`); tenant scoping still holds. [V] | Sloppy contract | Low | Dev | — | Existing | Verify the copy belongs to the book | Later | No |
| LIB-26 | Library | Product | Copies carry a campus but issue, summary counts and dashboard ignore it; settings are organisation-wide (`LibrarySummaryController.java:29-35`, `DashboardController.java:232-236`). [V] | Multi-campus schools get one mixed library | Low | Librarian | §3.1 | Partial | Campus filter and per-campus settings | Later | No |
| LIB-27 | Library | Product | No library reports: no accession register, stock verification, most-borrowed, fines summary, class-wise usage, member cards; only a book-list export without copies (`BookController.java:108-111`); nothing in `reporting` references library. [V] | Librarians and principals ask for these at year end | Low | Librarian, principal | RPT-01 | Missing | Basic reports through the reporting module | Later | No |
| LIB-28 | Library | Correctness | A concurrent first PUT to settings can hit the unique organisation constraint and return 409 (`LibrarySettingsController.java:37-44`, V103:21). [V] | Rare | Low | Admin | — | Existing | Upsert with retry | Later | No |
| LIB-29 | Library | Test | `LibraryTenantIsolationTest` says it covers the Library tables but tests only `Book.findAll` (`LibraryTenantIsolationTest.java:43-58`); no cross-tenant test for copies, loans or fines; no controller or permission tests; one e2e only adds a book. [V] | False confidence in tenant isolation | Low | Dev | PLT-07 | Partial | Per-entity and per-endpoint tests | Soon | No |

## 3.10 Inventory, Purchase and Sales

**Verdicts: Inventory usable with fixes; Purchase and Sales need rework before real use.** No cross-tenant leak and no unannotated controller method was found, amounts are `BigDecimal` and receipt numbers are gap-free. Stock and money are not concurrency-safe, Sales has no refund or void and accepts overpayment, and Sales is invisible to the fee ledger. Recommendation: keep Inventory basics; hide Purchase and Sales behind a module switch unless a pilot school already uses them (D-MOD1, D-MOD2).

| ID | Area | Type | Gap | Why it matters | Sev | Users | Depends | State | Recommendation | MVP | Dec? |
|---|---|---|---|---|---|---|---|---|---|---|---|
| INV-01 | Inventory | Correctness | The negative-stock guard is check-then-insert: it reads a live SUM of three tables with no lock, no `@Version`, no serialisation on item or campus (`InventoryService.java:144-163`); applies to sales, transfers and PO returns. Under MySQL defaults two transactions both see the old balance [I]; tests run on H2. | Two counters selling the last 5 notebooks, or a transfer racing a sale, drive stock negative with no error; balance and low-stock numbers are then silently wrong | High | Store | PLT-07 | Existing (defective) | Lock the item or campus row before computing the balance | Yes | No |
| INV-02 | Inventory | Product | Campus-to-campus transfer is instant: out and in adjustments are written together (`InventoryService.java:173-183`); `StockTransfer` has no status, received date, received quantity or reversal (V112). [V] | No in-transit state, partial receipt or cancel; stock lost on a truck vanishes from the source and appears at the destination | Medium | Store | §3.1 | Partial | SENT, RECEIVED, CANCELLED lifecycle and a transit balance (only if multi-campus) | Later | No |
| INV-03 | Inventory | Sec | The manual adjustment API accepts any reason via `valueOf` (`StockAdjustmentController.java:44-45`), including TRANSFER_IN, TRANSFER_OUT and SUPPLIER_RETURN; a positive delta has no cap or approval; the UI offers four reasons. [V] | An `INVENTORY_ADJUSTMENT_MANAGE` holder can create stock from nowhere labelled as a transfer or return | Medium | Store | — | Existing (defective) | Restrict the API to the four manual reasons | Soon | No |
| INV-04 | Inventory | Sec | Memberships can carry a campus but `PermissionInterceptor` checks codes only (`PermissionInterceptor.java:52-65`) and controllers take `campusId` from the request. [I] Whether the membership campus is meant to restrict inventory [U]. | A storekeeper scoped to campus A can post, transfer and view stock at campus B | Medium | Store | AUTH-01 | Missing | Enforce the caller's campus scope in `InventoryService` | Later | No |
| INV-05 | Inventory | Product | All quantities are `int` (`StockEntry.java:37-38`, V19) while `Item.unit` is free text such as "KG" (`Item.java:19-21`). [V] | Kilograms, litres and metres cannot be recorded fractionally | Medium | Store | — | Missing | Decimal quantities, or document "whole units only" | Later | No |
| INV-06 | Inventory | Data | Ledgers are insert-only with no void or reversal: no "return from recipient" for an issue, and a mis-keyed unit cost can never be corrected (`StockEntry.java:17-22`); only COUNT_CORRECTION fixes quantity. [V] | A typo in receipt quantity or cost permanently distorts valuation; a returned uniform has no honest record | Medium | Store | — | Missing | Reversal entry type referencing the original row | Soon | No |
| INV-07 | Inventory | Correctness | Low-stock rules disagree: the API counts every ACTIVE item with a reorder level using balance 0 for never-stocked items (`InventoryService.java:97-111`); the dashboard sums that over every campus including inactive ones (`DashboardController.java:258-260`); the report view lists only (item, campus) pairs with ledger rows and includes discontinued items (V58:194-229, `SavedReportService.java:81-88`). [V] | For a 3-campus school an item never stocked at campus B counts as low on the dashboard while the report omits it; numbers never match | Medium | Store, principal | — | Existing (defective) | One low-stock rule used in all three places | Soon | No |
| INV-08 | Inventory | Product | `Asset` has no cost, depreciation, disposal date or reason, or assignment history (`Asset.java:29-81`); `changeStatus` has no transition rules so DISPOSED can return to ACTIVE, and `assignAsset` works on a LOST or DISPOSED asset (`InventoryService.java:194-198`). [V] | No asset register an auditor would accept; no "who had the laptop last term" | Medium | Admin | — | Partial | Transition guard and an assignment event log | Later | No |
| INV-09 | Inventory | Sec | None of inventory, purchase or sales uses `AuditLogService` (grep); only `created_by` and `updated_by`, overwritten on each update. [V] | "Who marked that laptop LOST?" and "who approved that PO?" cannot be answered | Medium | Principal | — | Missing | Log status changes and approvals to the audit log | Soon | No |
| INV-10 | Inventory | Correctness | No `@Valid` and no DB CHECK constraints (V19, V47, V50); `IllegalArgumentException` maps to 404 (`ApiExceptionHandler.java:38-41`) so "quantity must be positive" returns 404; null request fields throw NPE and return 500; negative unit cost accepted. [V] | Wrong status codes confuse the UI and support; bad data gets in | Medium | Store | — | Existing (defective) | Bean validation and proper 400s | Soon | No |
| INV-11 | Inventory | Product | The item master cannot be edited: `ItemController` exposes only reorder level, storage location, default supplier and discontinue; no category edit or deactivate; no screen calls `discontinue`; `ItemDetailPage` finds the item in an ACTIVE-only list (`ItemController.java:62-64`) so a discontinued item's page cannot be opened. [V] | A typo in an item name or unit is permanent | Medium | Store | — | Missing | Item update and a discontinue button | Soon | No |
| INV-12 | Inventory | Data | The import UI says "Import inventory items and opening stock" (`importEntities.tsx:175`) but `ItemRowImportService` creates items only (`:51-52`); non-numeric `reorderLevel` gives a raw `NumberFormatException` row error (`:68-71`); duplicates are skipped, never updated. [V] | Staff expect opening stock to load | Low | Store, admin | §3.7 | Partial | Fix the copy or add stock columns | Soon | No |
| INV-13 | Inventory | Data | Supplier and Customer have no uniqueness or duplicate check (V43, V45); `Supplier` has no tax id, payment terms or bank details (`Supplier.java:28-37`). [V] | Duplicate vendors fragment spend history | Low | Store | — | Missing | Duplicate detection | Later | No |
| INV-14 | Inventory | Product | Module screens do no permission gating: `INVENTORY_VIEW`-only users see action buttons that return 403; the sidebar gates only on `*_VIEW` (`AppLayout.tsx:148-150`). [V] | Read-only staff see buttons that fail | Low | All | AUTH-02 | Partial | Hide actions by permission | Later | No |
| INV-15 | Inventory | Test | Tests are H2 `@DataJpaTest` only; the isolation test checks only `ItemCategory` (`InventoryTenantIsolationTest.java:43-58`); no concurrency, controller, permission, cross-organisation ledger or MySQL tests; three owner-only happy-path e2e specs. [V] | Race and lock bugs (INV-01, SAL-06, SAL-07, PUR-05) cannot show up | Medium | Dev | PLT-07 | Partial | MySQL Testcontainers concurrency test | Soon | No |
| PUR-01 | Purchase | Correctness | `receive` resolves `lineId` without checking the line belongs to this order (`PurchaseOrderController.java:95-97,125-127`, `PurchaseOrderService.java:75-85`): the line counter moves, stock goes to this order's campus and only this order's status updates; `recordReturn` does have the ownership check (`Controller:106`). [V] | An API call or stale tab on the wrong PO puts stock at the wrong campus; the other PO stays APPROVED though fully received and can still be cancelled | High | Store | — | Existing (defective) | Reject any line whose purchase order differs from the order | Yes | No |
| PUR-02 | Purchase | Sec | Create, submit and approve are all `PURCHASE_MANAGE` (`PurchaseOrderController.java:51-90`); no approver or approved-at field; `updated_by` is overwritten by later changes including receipt progress (`BaseEntity.java:39-41`); no amount threshold. [V] | The same person raises and approves their own PO and after receipt nobody can tell who approved it | Medium | Principal | AUTH-03 | Missing | `PURCHASE_APPROVE` and approved_by and approved_at columns | Soon | No |
| PUR-03 | Purchase | Product | A PO has no number, order date, total, tax, discount, freight or currency (V47); its reference is the global auto-increment id (`PurchaseOrderService.java:81`); no GST, HSN or GSTIN field on Item, Supplier, PO, Sale or Customer. [V] Which tax fields a school needs [U]. | No PO document to send a vendor and no total for budget checks; the id also reveals platform-wide volume (a school's third PO shows as #48231) | Medium | Store, principal | — | Missing | Per-organisation PO number sequence and total now; tax after talking to a real school | Soon | No |
| PUR-04 | Purchase | Correctness | A receive request with an empty `lines` list passes: the loop does nothing, `fullyReceived` is false, and `applyReceiptProgress(false)` moves an APPROVED PO to PARTIALLY_RECEIVED (`PurchaseOrderService.java:75-85`, `PurchaseOrder.java:76-80`). [V] | One stray call permanently blocks cancelling the PO | Medium | Store | — | Existing (defective) | Reject an empty lines list | Soon | No |
| PUR-05 | Purchase | Correctness | Receipt is a read-modify-write on `quantityReceived` with no lock or `@Version` (`PurchaseOrderLine.java:57-66`). Race is [I]. | Two concurrent receipts can each pass the over-receipt check and book double stock | Medium | Store | INV-01 | Existing | Lock the line or PO row during receive | Soon | No |
| PUR-06 | Purchase | Correctness | A return never decrements `quantityReceived` by design (`PurchaseOrderLine.java:16-22`); the PO stays RECEIVED. [V] | Returning 5 defective items and receiving 5 replacements on the same PO is impossible; no debit note | Medium | Store | — | Partial | Re-receive up to ordered plus returned, or a replacement flow | Later | No |
| PUR-07 | Purchase | Data | The receive-time unit cost override is unbounded and not stored on the PO (`PurchaseOrderService.java:79-81`); the spend report uses ordered cost (V58) while valuation uses `StockEntry.unit_cost`; the seeded spend report has no status filter, so DRAFT and CANCELLED count (`SavedReportService.java:89-98`). [V] | Spend and valuation disagree for the same PO; cancelled POs count as spend | Medium | Accountant | RPT-01 | Existing (defective) | Filter the report to approved and received; validate the override as positive | Soon | No |
| PUR-08 | Purchase | Data | Receipt passes no supplier to `recordEntry` (`PurchaseOrderService.java:80-81`); `createOrder` does not check for an inactive supplier or discontinued item (`:40-50`). [V] | The item ledger loses the structured supplier link; POs can be raised against inactive vendors and discontinued items | Medium | Store | — | Partial | Pass the supplier; validate statuses on create | Soon | No |
| PUR-09 | Purchase | Correctness | A partially received PO cannot be cancelled or closed short (`PurchaseOrder.java:68-72`); RECEIVED needs every line full (`PurchaseOrderLine.java:68-70`); the dashboard counts every PO not RECEIVED or CANCELLED as open (`DashboardController.java:250-252`). [V] | A supplier delivers 18 of 20 and never the rest: the PO stays open forever and inflates the widget | Medium | Store | — | Missing | "Close short" action | Soon | No |
| PUR-10 | Purchase | Tech | PO and sales lists call `findAll()` with no filter or paging (`PurchaseOrderController.java:63-66`, `SaleController.java:63-67`). [V] | Works now, slows with years of data | Low | Store | — | Partial | Pagination and filters | Later | No |
| PUR-11 | Purchase | Product | No link to finance: no bill, payable, supplier payment or payment status anywhere (grep of `finance/`, V47, V49). [V] | The school cannot see what it owes vendors or has paid; the module ends at goods received | Medium | Accountant | D-MOD2 | Missing | Decide whether payables are in scope before further investment | Later | Yes (D-MOD2) |
| PUR-12 | Purchase | Test | `PurchaseOrderLifecycleTest` has 7 single-threaded tests: no wrong-line, empty-receive, cancel-after-partial or return-after-full case, no tenant isolation. [V] | PUR-01 and PUR-04 exist because nothing tests them | Medium | Dev | PLT-07 | Partial | Add those cases | Soon | No |
| SAL-01 | Sales | Product | Sales is stand-alone: `PaymentMethod.java:3-7` says it is "deliberately independent of the finance module"; nothing in `finance/` references sale, purchase, inventory or stock; a sale to a student-linked customer (`Customer.java:33-36`) never touches the fee invoice, fee receipts or finance dashboard. [V] | Uniform, book and kit money is collected in a separate receipt series; the accountant's daily collection excludes it and unpaid store balances are invisible to fee-dues follow-up | High | Accountant, store | §3.5 | Existing | Decide whether store sales stay out of the fee ledger; if so add a day-end cash book, if not post to the student ledger | Soon | Yes (D-MOD2) |
| SAL-02 | Sales | Correctness | No refund, return, void or cancel for a sale or payment: `SaleController` has only create, list, detail, payment; `SaleStatus` is COMPLETED, PARTIALLY_PAID, PAID; stock is already issued (`SaleService.java:52`). [V] | A wrongly priced or wrong-item receipt is permanent; the only fix is a hand-made stock adjustment and the money side has no correction. A parent returning a uniform is a daily event | High | Store | SAL-01 | Missing | Void or return that writes a positive stock adjustment and a reversing payment | Yes | No |
| SAL-03 | Sales | Correctness | `recordPayment` checks only amount > 0 (`SaleService.java:57-65`); `Sale.applyPayment` adds with no cap (`Sale.java:72-75`). [V] | Overpayment (1000 typed for 100) is accepted; status shows PAID, outstanding goes negative, cash does not reconcile | High | Store | — | Existing (defective) | Reject an amount above the outstanding balance | Yes | No |
| SAL-04 | Sales | Product | No role template includes any `SALES_*` permission (only V51 defines them) and the sale screen calls customer, item and campus lists that require `INVENTORY_VIEW` (`CustomerController.java:25`, `ItemController.java:29`); creating a customer needs `INVENTORY_CUSTOMER_MANAGE`. [V] | A cashier granted only `SALES_MANAGE` opens the screen but cannot load customers or items; only Owner works out of the box, so Sales looks broken | High | Store | AUTH-03 | Existing (defective) | Add a Cashier or Store role template; relax the picker endpoints | Yes | No |
| SAL-05 | Sales | Correctness | Unit price is fully client-supplied: `Item` has no price field; `SaleService.java:45-47` and `SaleLine.java` accept zero or negative prices; `saleDate` is arbitrary (backdating, future). [V] | The cashier types the price every time so prices drift; a negative-price line lowers the total; backdating moves dashboard and report totals | High | Store | — | Missing | Selling price on Item, validated; restrict the date window | Yes | No |
| SAL-06 | Sales | Correctness | First-ever sale race can leave two counter rows: `nextReceiptNumber` does `findForUpdate().orElseGet(save(new counter))` (`SaleService.java:69-70`); no UNIQUE(organisation_id) on `sale_receipt_counters` (V50:9-18). Race is [I]; missing constraint is [V]. | After that race, `findForUpdate` returns two rows and throws: the whole Sales module fails for the organisation. Low probability, total impact | Medium | Store | — | Existing (defective) | UNIQUE(organisation_id) and seed the counter at organisation creation | Soon | No |
| SAL-07 | Sales | Correctness | Payment is a read-modify-write on `amountPaid` with no lock, `@Version` or idempotency key (`SaleService.java:62-64`). [V] | A double click or retry records the payment twice; two concurrent payments can lose an update | Medium | Store | — | Existing (defective) | Lock the sale row; disable the button while submitting | Yes | No |
| SAL-08 | Sales | Product | Printed receipt shows number, date, customer, lines and totals, with no school name, address or tax id (`SaleDetailPage.tsx`); no tax or discount on any line; organisation `taxIdentifier` unused. Legal receipt requirements [U]. | The receipt does not identify the school | Medium | Store, parents | — | Partial | Add the organisation header to the printable receipt | Soon | No |
| SAL-09 | Sales | Correctness | A sale is created as COMPLETED with stock deducted and amount paid 0 (`Sale.java:64`); credit sales have no limit and no outstanding or ageing report; the dashboard "sales" figure is booked totals, not collections (`SaleRepository.java:15-19`). [V] | "COMPLETED" reads as paid; the dashboard overstates cash and unpaid store balances go untracked | Medium | Store, principal | SAL-01 | Existing (defective) | Rename the initial status to UNPAID; add a receivables view | Soon | No |
| SAL-10 | Sales | Correctness | `reporting_sales` repeats `sale_total_amount` and `sale_amount_paid` on every line row (V58:171-192); the seeded report sums `line_total` (fine) but a custom sum over the header total over-counts; no collection-by-method report. [V] | Ad-hoc report authors get inflated totals; no day-end view | Medium | Accountant | RPT-01 | Partial | Expose header-level totals in a separate view | Later | No |
| SAL-11 | Sales | Correctness | Every line takes the stock counter lock and holds it to commit (`SaleService.java:49-52`), serialising all sales in an organisation; one organisation-wide "RCT-" series with no year reset or campus prefix; no UNIQUE(org, receipt_number) (V50:20-32). [V] | Fine for one campus; the series cannot be audited per year or campus and uniqueness rests on the lock alone | Medium | Store | — | Partial | UNIQUE(org, receipt_number); decide on year and campus prefix | Later | No |
| SAL-12 | Sales | Product | `SalePayment` has method, amount, date only (`SalePayment.java:26-38`): no cheque or UPI reference, no separate payment receipt. [V] | Cheque and UPI receipts cannot be reconciled against a bank statement | Medium | Accountant | — | Missing | Add a reference field | Soon | No |
| SAL-13 | Sales | Data | The sale's issue row sets neither the student recipient nor unit cost (`SaleService.java:52`) and stores the customer name as free text in `issuedTo`. [V] | A student's issue history omits store purchases; margin cannot be computed | Low | Store | — | Partial | Pass the student and cost through | Later | No |
| SAL-14 | Sales | Test | `SaleLifecycleTest` has 4 tests: no overpayment, refund, negative price, receipt-counter rollback, first-sale race or tenant isolation; one e2e creates a sale. [V] | The money-handling gaps above are untested | Medium | Dev | PLT-07 | Partial | Add those cases | Soon | No |

## 3.11 Platform CRM

**Verdict: usable with fixes.** The platform plane is cleanly separated (own JWT secret, excluded tenant interceptors, non-`@TenantId` entities). The risks are the non-atomic lead-to-school convert, an unguarded platform login with dev-default secrets, and no audit or erasure path for lead data. A founder selling to about 20 schools can run this in a spreadsheet; the convert hand-off is the part worth keeping.

| ID | Area | Type | Gap | Why it matters | Sev | Users | Depends | State | Recommendation | MVP | Dec? |
|---|---|---|---|---|---|---|---|---|---|---|---|
| CRM-01 | Platform CRM | Product | Lead convert is not atomic or retry-safe. `OrganisationService.provision` saves the org, then campus, config, roles, admin in separate transactions, the plan subscription last after `TenantContext.clear()` (`OrganisationService.java:75-116`); `LeadService.convert` is deliberately not `@Transactional` and marks the lead converted only after provision returns (`LeadService.java:67-94`). [V] | A failure midway leaves an orphan TRIAL org and a lead still WON and unconverted; retrying with the same slug gives a generic 409, a new slug makes a second org. The operator cannot finish it from the UI | High | Platform admin | SEC-03 | Existing (defective) | Make convert transactional or resumable; link or clean up a half-created org | Yes | Yes (D-CRM1) |
| CRM-02 | Platform CRM | Sec | Platform login has no lockout and no MFA (`PlatformAuthenticationService.java:25-40`); the tenant plane has a DB-backed lockout. [V] | One public endpoint guards a full-access plane with unlimited password guessing | High | Platform admin | SEC-11 | Missing | Reuse the tenant lockout pattern for platform admins | Yes | No |
| CRM-03 | Platform CRM | Sec | Platform JWT secret has a dev default (`application.properties:28`), `application-prod.properties` is logging only, no startup check; migration `V25` seeds `admin@operion.platform` with a documented password. [V for defaults; U whether production overrides them] | If production runs on the defaults, anyone can forge a platform token and read all lead PII and provision organisations | High | Platform admin | — | Missing | Fail startup under the prod profile on the default secret; force a password change on first login | Yes | No |
| CRM-04 | Platform CRM | Sec | Platform admin status is checked only at login; the interceptor decodes the JWT and nothing else (`PlatformAuthenticationInterceptor.java:43-46`); there is no admin-management endpoint. [V] | A suspended admin keeps a live token for up to 120 minutes; deactivating needs SQL | High | Platform admin | SEC-04 | Missing | Load the admin on each request or keep a revocation flag; add basic admin management | Soon | No |
| CRM-05 | Platform CRM | Data | No delete or anonymise for a lead's personal data: no lead DELETE, activities insert-only, foreign keys tie contacts, activities and tasks to the lead (`V129:39-84`, `ActivityService.java:9`). [V] | Owner names, emails, phones and free-text notes cannot be removed except by SQL. Facts only; legal position needs verification [R] | High | Platform admin | REG-02 | Missing | Add delete or anonymise that cascades to contacts, activities, tasks | Soon | No |
| CRM-06 | Platform CRM | Correctness | A converted lead can be moved off WON (`LeadService.changeStage:60-65`) and two concurrent converts can create two orgs (no `@Version`, check then save at 81-94). [V] | A customer reappears as an open lead; a double click can create duplicate schools | Medium | Platform admin | CRM-01 | Existing (defective) | Reject stage changes once converted; unique index on `converted_organisation_id` | Soon | No |
| CRM-07 | Platform CRM | Sec | No audit trail for CRM changes; no CRM service calls `AuditLogService`, the platform feed covers only Organisation, Plan, Subscription, Invoice, Setting (`PlatformAuditLogController.java:26-27`); no stage history. [V] | Cannot answer who moved a lead to WON or when; funnel reports need stage history | Medium | Platform admin | — | Missing | Audit stage, owner and convert events | Soon | No |
| CRM-08 | Platform CRM | Sec | Convert audit row has no actor (`TenantContext.set(org, null)` at `OrganisationService.java:77`), and the salesperson types the school admin's password in the convert dialog (`LeadDetailPage.tsx:286-293`). [V] | Highest-impact CRM action is unattributed; a password chosen by Operion staff is shared out of band | Medium | Platform admin | CRM-01 | Existing (defective) | Record the platform actor; invite the admin by email link instead | Soon | No |
| CRM-09 | Platform CRM | Correctness | Convert reuses any existing user with the typed email and keeps its old password (`OrganisationService.java:121-128`). [V] | The operator believes a fresh account was created with the typed password; it was not | Medium | Platform admin | SEC-01 | Existing (defective) | Detect the existing login and ask for confirmation | Soon | No |
| CRM-10 | Platform CRM | Product | No lost reason, close date or value on leads (`Lead.java:25-47`); any stage to any stage by design. [V] | Win rate cannot explain why deals die; no expected revenue despite the student-count field | Medium | Founder | — | Missing | Add `lost_reason`, `closed_at`, optional value | Soon | No |
| CRM-11 | Platform CRM | Product | Metrics are narrow: win rate is all-time count over WON+LOST, funnel is a current-stage snapshot, open/overdue task counts include tasks on lost or converted leads and of other admins (`CrmMetricsService.java:20-34`, `CrmTaskRepository.java:16-18`). [V] | Stale tasks inflate "overdue"; at about 20 schools a win-rate percentage means little | Medium | Founder | CRM-07 | Partial | Exclude tasks on closed leads; add a lead-to-won figure | Later | No |
| CRM-12 | Platform CRM | Ops | Follow-ups only show on screen; no reminder job in `platform/`, overdue is computed in the browser (`crmFormat.ts:42-51`), dashboard tasks are not filtered to the current admin. [V] | A follow-up due today is missed unless the founder opens the dashboard | Medium | Founder | — | Missing | Daily email digest of own due and overdue tasks; "mine" filter | Soon | No |
| CRM-13 | Platform CRM | Data | Lead has no email or phone column (`V129:7-18`); contact validation is name only (`ContactService.java:26-30`); no duplicate detection by name, email or phone. [V] | The same school is entered twice from a referral and an inbound lead; malformed emails are accepted | Medium | Founder | — | Missing | Duplicate warning on name and email; email format check | Soon | No |
| CRM-14 | Platform CRM | Correctness | Enum parse errors (`LeadStage.valueOf` and others, `LeadController.java:50-63`) map to 404 (`ApiExceptionHandler.java:38-41`); convert body has no `@Valid`; student count has no `@Min`. [V] | A bad stage reads as "not found"; missing slug or email fails deep in provisioning | Medium | Platform admin | — | Existing (defective) | `@Valid`, size limits, 400 on bad enum | Soon | No |
| CRM-15 | Platform CRM | Product | Convert carries over only the org name and three contact fields (`LeadService.java:100-111`); contacts, activities and tasks stay on the lead; no plan or subscription is created from the UI (`LeadDetailPage.tsx:44,115-125`). [V] | The new organisation page starts empty ("No contacts yet", asserted in `crm.spec.ts:58`); a school is live with no subscription | Low | Platform admin | PLT-01 | Partial | Move contacts to the org; offer plan step in the dialog | Soon | No |
| CRM-16 | Platform CRM | Tech | Lists are unpaged and filters run in Java (`LeadService.all():31-33`, `CrmTaskService.list():28-33`); dashboard fetches all leads and organisations. [V] | Fine at tens of leads; degrades as completed tasks accumulate | Low | Platform admin | — | Partial | Push filters into repository queries when volume grows | Later | No |
| CRM-17 | Platform CRM | Sec | Platform token kept in `localStorage` (`platformTokenStore.ts:15-27`); login skips bcrypt for an unknown email so timing reveals which emails exist (`PlatformAuthenticationService.java:30-31`). [V] | Token exposed to any XSS; email existence can be probed | Low | Platform admin | — | Existing | Accept for now or move to an httpOnly cookie | Later | No |
| CRM-18 | Platform CRM | Product | No CSV import or export and no public lead-capture form for Operion's own sales. [V: no CRM references to `CsvUtil`; the public inquiry controller belongs to school admissions] | Leads are typed by hand and cannot be backed up as a file | Low | Founder | — | Missing | CSV export first; public form optional | Later | No |
| CRM-19 | Platform CRM | Correctness | Due dates are `Instant`s built from browser-local time and "today" is computed in the browser (`TasksSection.tsx:56`, `crmFormat.ts:30-51`). [V] | Two admins in different time zones disagree about "due today" | Low | Platform admin | — | Existing | Note only | Later | No |
| CRM-20 | Platform CRM | Test | Tests mock `OrganisationService`, so failed provisioning, the CHECK constraint and real convert are untested; no controller or interceptor test that CRM endpoints reject missing or tenant tokens; one happy-path e2e (`crm.spec.ts:3-69`). [V] | A regression that opens a CRM endpoint without authentication goes unnoticed | Low | Dev | CRM-01 | Partial | Add 401 tests (no token, tenant token) and a failing-provision convert test | Yes | No |
| CRM-21 | Platform CRM | Product | Tasks can be created with past dates, cannot be reopened or deleted, any admin can complete or reassign another's task (`CrmTaskService.java:46-61`); task edit endpoint exists but has no UI. [V] | Wrongly created tasks stay until completed | Low | Platform admin | — | Partial | Task edit and delete in the UI | Later | No |

## 3.12 Learning (LMS-lite)

**Verdict: usable with fixes.** Section scope and guardian-link checks are applied consistently and no IDOR was found. Files are public links, the leaderboards ignore the school's own ranking and result-publication controls, and a published quiz cannot be corrected. Recommendation: keep homework, materials and the lesson-plan calendar; hold answer-sheet marking until files are private; hide quizzes, leaderboards and rewards behind a module switch.

| ID | Area | Type | Gap | Why it matters | Sev | Users | Depends | State | Recommendation | MVP | Dec? |
|---|---|---|---|---|---|---|---|---|---|---|---|
| LMS-01 | Learning | Sec | Uploads are served unauthenticated at `/uploads/**` (`WebConfig.java:50-58`); answer sheets, homework submissions and materials all use this store via opaque refs in API JSON (`AnswerSheetResponses.java:47`); the only protection is a UUID filename. [V] | A scanned answer sheet of a named minor is a capability URL that never expires or revokes; it leaks through shared links, history or forwards, and stays fetchable after a guardian is unlinked or a sheet should not have been released | High | Teachers, parents | PLT-06 | Existing (defective) | Serve learning files through an authenticated, scope-checked endpoint or short-lived signed URLs | Yes | No |
| LMS-02 | Learning | Product | The guardian "standing" view returns the child's ACADEMIC rank, "of N" and percentage from raw `MarksEntry` rows (`LeaderboardService.java:208-225`, `ChildLearningController.java:133-137`), ignoring the organisation's `isRankingEnabled` setting that gates ranking elsewhere (`ExaminationService.java:258-261`) and report-card publication; no exam-status filter. [V] | A school that turned ranking off, or has not published results, still shows parents a rank and percentage; unit tests and finals are pooled unweighted | High | Parents, principal | EXM-01, D-E3 | Existing (defective) | Remove the ACADEMIC track or gate it by the ranking setting and published report cards | Yes | Yes (D-E3) |
| LMS-03 | Learning | Correctness | A published quiz can never be edited, extended, cancelled, deleted or unpublished (no such endpoints; `QuizService.java:328-335`); `publish` tells the teacher to "reschedule the quiz first" (`:134`) but no reschedule exists; a DRAFT whose `closesAt` has passed can never be published. [V] | A wrong answer key or window on a live quiz is unrecoverable, is auto-scored and feeds the QUIZ leaderboard; only manual database edits fix it | High | Teachers | — | Missing | Draft edit or reschedule, plus cancel or void excluding the quiz from results | Soon | No |
| LMS-04 | Learning | Product | Quiz attempts are made by the guardian (`QuizService.java:259-298`); no timer or start record; questions can be fetched repeatedly during the window (`:246-256`); no randomisation; one attempt only. [V] | A score measures the parent's effort, not the child's, yet the QUIZ track ranks children on it (`LeaderboardService.java:198-205`); siblings and parents can share questions | High | Teachers, parents | D-LMS1 | Partial | Treat quizzes as practice and keep them off rankings, or add teacher-supervised attempts | Later | Yes (D-LMS1) |
| LMS-05 | Learning | Sec | No endpoint carries `@RequirePermission`; all 49 rely on each method calling `accessService.resolve` or `childAccess.resolve` (`PermissionInterceptor.java:39-41`). No IDOR found: every by-id fetch checks section scope or guardian link. No controller or integration tests prove it. [V] | One forgotten `resolve` call in a future endpoint is an open endpoint | Medium | All | AUTH-02 | Partial | Controller-level guard or a test asserting every learning endpoint rejects an unrelated member | Soon | No |
| LMS-06 | Learning | Sec | Attachment refs are client-supplied strings stored unchecked on homework, submissions, materials and answer-sheet pages (`HomeworkService.java:74`, `AnswerSheetService.java:101-118`, `LearningMaterialService.java:47`); `POST /api/v1/assets` is open to any member, guardians included (`AssetUploadController.java:11-33`). [V] Cross-tenant attach needs a known UUID [I]. | A file from another tenant can be attached if its UUID is known; any guardian can upload unlimited files | Medium | All | LMS-01 | Missing | Record uploader and tenant per asset; validate refs on attach | Soon | No |
| LMS-07 | Learning | Sec | Content type is whatever the client sends (`LocalDiskAssetStorageService.java:43`); no magic-byte check, no virus scan; PDFs stored as-is. [V] Active content served from the app origin [I]; whether the SPA shares an origin with `/uploads` [U]. | Mislabelled files are accepted; nothing scans files exchanged between teachers and parents | Medium | All | — | Missing | Magic-byte check; serve with `nosniff` and `Content-Disposition` | Later | No |
| LMS-08 | Learning | Ops | No quota, cleanup or retention for files: re-upload deletes page rows but not files (`AnswerSheetService.java:114`), cancelled items keep files, the UI uploads pages one by one so a partial failure orphans files (`AnswerSheetsPanel.tsx:81-85`); default directory is relative `./data/uploads` (`WebConfig.java:27`). [V] Whether the directory survives a redeploy [U]. | Up to 20 pages at 5 MB per student-exam with no monitoring | Medium | Ops | PLT-06, PLT-08 | Missing | Per-organisation quota, orphan sweep, object storage | Soon | No |
| LMS-09 | Learning | Correctness | The late check uses `ZoneId.systemDefault()` (`HomeworkService.java:152`) although the organisation has a timezone; `dueDate` is a DATE with the deadline at next-day 00:00 in the server zone. [V] | On a UTC server an IST school's submissions up to about 5.5 hours after the deadline are not flagged late | Medium | Teachers | — | Existing (defective) | Use the campus or organisation timezone | Soon | No |
| LMS-10 | Learning | Correctness | Resubmitting homework with text only wipes the earlier attachment (`HomeworkSubmission.java:68-77`); the UI resets the ref after submit (`ChildHomeworkPanel.tsx:50`) and never shows the existing attachment until reviewed (`:83-94`). [V] | A parent fixing a typo silently deletes the file the teacher sees | Medium | Parents | — | Existing (defective) | Keep the existing ref when none is supplied, and show it | Soon | No |
| LMS-11 | Learning | Product | Nothing in learning notifies anyone: no homework, review, release or quiz notification (grep empty); exams already notify through `CommunicationService` with the opt-in check (`ExaminationNotificationService.java:42-46`). [V] | Parents only learn of homework if they open the portal, which undermines the feature | Medium | Parents | NOT-01 | Missing | Reuse the templated notification path | Soon | No |
| LMS-12 | Learning | Product | Answer-sheet release needs no admin or exam gate and sends no notice (`AnswerSheetService.java:177-182`); it is irreversible (`AnswerSheet.java:77-83`); totals are not posted to the marks register (V124 comment); release exposes totals and annotations independent of report-card publication. [V] | A mis-click permanently shows a student's marks to parents; the sheet total and register mark can disagree | Medium | Teachers, parents | EXM-01 | Existing (defective) | Recall before any guardian view; tie release to exam publication | Later | No |
| LMS-13 | Learning | Correctness | Rewards can be gamed and never corrected: no per-student, per-day or per-granter cap beyond 100 points a grant (`LeaderboardService.java:108-128`), no idempotency so a double click adds two rows, no edit or revoke; any teacher of the section can grant. Not tied to money (no finance references). [V] | The REWARD track reflects teacher generosity and a wrong grant stays forever | Medium | Teachers | — | Partial | Revoke, soft cap, duplicate protection | Later | No |
| LMS-14 | Learning | Correctness | The QUIZ track averages only attempted quizzes with no minimum count (`LeaderboardService.java:198-205`). [V] | One 100% attempt outranks 20 attempts averaging 95%; the ranking rewards skipping quizzes | Medium | Teachers, parents | LMS-04 | Existing (defective) | Minimum attempts threshold, or drop the track | Later | No |
| LMS-15 | Learning | Product | Rankings of minors are shown to their parents as "#N of M" per track (`ChildStandingPanel.tsx:39`, `LeaderboardResponses.java:57-65`); other children are not named. [V] Whether schools want this is a design question. | A child ranked low is told so, and "of N" lets parents infer class size | Medium | Parents | D-E3 | Partial | Make parent-visible ranks an opt-in per school | Later | Yes (D-E3) |
| LMS-16 | Learning | Data | Learning tables are not linked to privacy or retention: no other package references them; `DataSubjectRequestService` has no learning coverage; no purge when a student leaves. [V] | Erasure or export requests would miss answer-sheet scans and quiz answers, which are kept indefinitely [I] | Medium | Admin | REG-03 | Missing | Include in export and erasure; set a retention rule | Soon | No |
| LMS-17 | Learning | Tech | Unbounded in-memory work: `QuizService.list` loads all quizzes then filters (`:180-181`), `LearningMaterialService.java:95`, `LessonPlanService.java:84`, `AnswerSheetService.java:66`, `LearningScopeService.java:45` use `findAll`; `teacherBoard` loads five whole tables (`LeaderboardService.java:151-156`); each parent standing view builds three boards. [V] | Fine today; lists and boards grow with history | Low | All | — | Partial | Year filters, paging, per-section caching | Later | No |
| LMS-18 | Learning | Correctness | No optimistic locking: marking deletes all annotations then re-inserts (`AnswerSheetService.java:167-172`); mark and release can interleave. [V] | Two teachers or tabs on one sheet silently overwrite each other | Low | Teachers | — | Missing | `@Version` on `AnswerSheet` | Later | No |
| LMS-19 | Learning | Correctness | A reviewer can approve their own lesson plan (`LessonPlanService.java:64-69`); resubmit clears the review note, reviewer and time (`LessonPlan.java:97-106`); an approved plan is locked with no revise path. [V] | No history of rejection feedback and no correction after approval | Low | Teachers, principal | — | Partial | Block self-approval; keep review history | Later | No |
| LMS-20 | Learning | Sec | The guardian lesson-plan API returns `reviewNote` and the reviewer name (`ChildLearningController.java:85`); the UI does not show them. [V] | Internal reviewer comments are exposed through the API | Low | Parents | — | Existing | Guardian-specific DTO | Later | No |
| LMS-21 | Learning | Sec | Per-question `correct` and `marksAwarded` are returned to a guardian while the quiz is still open (`ChildQuizResponses.java:63-67`, `QuizService.java:305-312`). [V] | Leaks answer information to anyone sharing results before close; small impact with one attempt | Low | Parents | — | Existing | Suppress per-question correctness until close | Later | No |
| LMS-22 | Learning | Correctness | No length validation on text fields; overlong input raises a constraint error reported as "conflicts with an existing one" 409 (`ApiExceptionHandler.java:57-60`); a homework due date in the past is allowed. [V] | Misleading errors | Low | Teachers | — | Existing | Validate lengths in services | Later | No |
| LMS-23 | Learning | Correctness | Double submit of a quiz or homework races check-then-insert (`QuizService.java:266-268`); the loser sees the generic 409; two guardians can overwrite each other's submission and the teacher cannot see which guardian submitted. [V] | Confusing on the rare race | Low | Parents, teachers | — | Partial | Clear message; show submitter | Later | No |
| LMS-24 | Learning | Product | The teacher board counts cancelled homework (`LeaderboardService.java:151`), weights raw volume, is all-time not per year and admin-only (`:145`). [V] | Ranking staff by activity count invites padding | Low | Principal | — | Partial | Drop it, or scope per year and exclude cancelled | Later | No |
| LMS-25 | Learning | Sec | A teacher of any subject in a section reads every quiz's answer key there (`QuizService.java:207-211`); subject teachers see and delete all bank questions (`:155-163`); a teacher of any section of a class can post class-wide materials to all parents (`LearningMaterialService.java:107-117`). [V] | Wider than needed | Low | Teachers | AUTH-01 | Partial | Tighten to subject scope | Later | No |
| LMS-26 | Learning | Sec | LINK materials accept any external http or https URL with no length or domain check (`LearningMaterialService.java:57`). [V] | Teachers can post arbitrary external links to parents | Low | Parents | — | Existing | Optional domain allowlist | Later | No |
| LMS-27 | Learning | Product | PDF scans are accepted but cannot be annotated; the UI falls back to a link (`AnnotatedPage.tsx:27`); the service does not reject PDF page refs. [V] | Marking silently does nothing on PDF sheets | Low | Teachers | — | Partial | Require image pages for marking | Later | No |
| LMS-28 | Learning | Product | No way to edit homework or materials, reopen a quiz, or delete a lesson plan or reward; the nav item is gated on `LEARNING_VIEW` or `LESSON_PLAN_REVIEW` only (`AppLayout.tsx:131`), so a `LEARNING_MANAGE`-only role would not see the menu [I]. [V] | Dead ends for teachers and admins | Low | Teachers | — | Missing | Edit actions; allow `LEARNING_MANAGE` in the nav | Later | No |
| LMS-29 | Learning | Test | Seven service-level test classes with hand-built actors; none cover controllers, `LearningAccessService`, `ChildAccessService` or cross-tenant access (single-tenant fixture, `LearningTestBase.java:70-96`); no learning e2e exists, so the Milestone 30 e2e claims cannot be verified. [V] | The riskiest path (actor resolution and guardian link) is untested | Low | Dev | PLT-07 | Partial | Integration test for the guardian link and another tenant's ids | Soon | No |

## 3.13 Payroll

**Verdict: usable with fixes, with a manual review step until PAY-01 to PAY-06 are fixed.** Structures, lifecycle and payslip snapshots are sound and rounding is consistent. There is no proration, corrections can drop leavers and adjustments, and the main manual tool (adjustments) is unaudited. Statutory calculation is out of scope by design and needs legal verification [R].

| ID | Area | Type | Gap | Why it matters | Sev | Users | Depends | State | Recommendation | MVP | Dec? |
|---|---|---|---|---|---|---|---|---|---|---|---|
| PAY-01 | Payroll | Correctness | No proration anywhere: a joiner on the 28th gets a full-month payslip; a structure effective mid-month applies to the whole month (resolved at month end, `PayrollService.java:158,162,165`, javadoc 19-24); leave status, leave requests and attendance are never read; the payslip has no days-worked or LOP field; the only hint is a draft-page alert (`PayrollRunDetailPage.tsx:98-101`). [V] | Staff on approved unpaid leave or joining late are paid in full unless the admin computes and keys a deduction per person; nothing flags who needs one, so it is a silent overpay | **Critical** | HR, accountant | PAY-20 | Missing | Days-in-period and LOP-days fields with an opt-in proration rule; at minimum list unpaid-leave days and joining dates on the draft | Yes | Yes (D-PAY1) |
| PAY-02 | Payroll | Correctness | A correction is a full regeneration from structures (`PayrollService.java:88,156-173`): adjustments on the original are not copied, and the staff filter uses current status so anyone resigned since month end is dropped; on finalize the old run becomes SUPERSEDED (140-145) and staff and finalized views show only FINALIZED runs (`PayslipPortalService.java:47-48`). [V] | Correcting March after a March leaver resigned erases that person's March payslip; a correction silently drops the original's LOP or recovery lines, so its net is higher than what was paid | **Critical** | HR, accountant | PAY-03 | Existing (defective) | Copy non-structure lines forward; build the staff set from the superseded run, not current status | Yes | No |
| PAY-03 | Payroll | Correctness | Staff exit is ignored by payroll: `recordExit` flips status to RESIGNED or TERMINATED at once regardless of `exitDate` (`HrService.java:115-120`); `createRun` includes only ACTIVE or ON_LEAVE (`PayrollService.java:160-161`); `StaffExit.exitDate` is never read. [V] | A teacher leaving on the 20th, or whose exit is recorded on the 2nd, gets no payslip for that month, or a full one if the run was generated earlier; last-month settlement is lost or inconsistent | High | HR | AUTH-06 | Existing (defective) | Include staff whose exit date falls on or after the month start, prorated to the exit date | Yes | No |
| PAY-04 | Payroll | Correctness | Two runs for one month are possible: the check is a read then insert (`PayrollService.java:70-77,151-154`), no DB unique constraint (V117:5,23), no `@Version` anywhere, and `finalizeRun` never checks for another FINALIZED run (125-149). Race is [I]; missing guards are [V]. | Two admins generating together can produce two FINALIZED March runs; staff see two March payslips and any summing report doubles | High | Accountant | PLT-07 | Existing (defective) | Partial-unique guard on (organisation, period, DRAFT or FINALIZED); lock the period at finalize | Yes | No |
| PAY-05 | Payroll | Sec | Adjustments are unaudited and hard-deleted: no `auditLogService` call in add or remove (`PayrollService.java:91-108`); removal is `lineRepository.delete`. [V] | Every LOP, bonus and recovery goes through this path; a large deduction or "bonus" can be added then removed before finalize with no evidence of who did it | High | Accountant, principal | AUTH-03 | Missing | Audit add and remove with label, type, amount, payslip; soft-void instead of delete | Yes | No |
| PAY-06 | Payroll | Sec | Bank details are unaudited, unmasked and plaintext: `upsertBankDetails` has no audit call (`HrService.java:130-139`); the response returns the full account number and tax id (`StaffBankDetailResponse.java:5-12`); no encryption or converter (`StaffBankDetail.java:30-43`); `HR_PAYROLL_VIEW` gates both read and write (`StaffProfileController.java:126-138`). [V] | Changing a staff account number is the classic payroll-fraud vector and leaves no trail; a read-named permission lets a holder redirect salary | High | HR | AUTH-03 | Missing | Audit changes; split read from write permission; mask on read | Yes | No |
| PAY-07 | Payroll | Product | There is no payment state: run status is only DRAFT, FINALIZED, SUPERSEDED (`PayrollRunStatus.java:3-7`); no paid date, reference or bank file anywhere in `payroll/`; a correction is a full replacement, not a difference against what was paid. [V] | After salaries are paid, a correction shows a full-net payslip with no arrears or recovery figure; paying its net again double-pays, and nothing records who was paid | High | Accountant | PAY-02 | Missing | PAID status with date and reference; compute correction deltas | Soon | No |
| PAY-08 | Payroll | Product | Staff without a structure are silently absent from a run (`PayrollService.java:165-169`); the UI warns only if zero payslips result (`PayrollRunDetailPage.tsx:151`). [V] | A new joiner whose structure was not entered gets no pay and the totals look normal | High | HR | — | Missing | "Active staff not in this run" list at draft and finalize | Yes | No |
| PAY-09 | Payroll | Correctness | A new structure must take effect after the latest one; there is no edit or delete (`SalaryService.java:80-85`, insert-only entity) and no upper bound on `effectiveFrom` (`CreateSalaryStructureRequest.java:10`). [V] | A typo (year 2062, wrong basic) becomes the latest version, cannot be removed, and blocks any later structure for that person short of DB surgery | High | HR | — | Existing (defective) | Allow voiding a not-yet-effective structure; cap future dates | Yes | No |
| PAY-10 | Payroll | Product | Raising a structure means re-entering every component: form state starts empty and sends only filled fields (`SalaryStructurePanel.tsx:27,46-48`); no pre-fill from the latest version or diff. [V] | A forgotten deduction on a raise is silently dropped, overstating net pay from then on | High | HR | — | Missing | Pre-fill from the latest structure with a before/after diff | Yes | No |
| PAY-11 | Payroll | Sec | `PAYROLL_MANAGE` covers salary changes, generate, adjust, finalize and correct with no maker-checker; the permission check ignores campus (`PermissionInterceptor.java:43-66` vs `OrganisationMembership.java:46-48`); no template role holds `PAYROLL_*` (V100, V116). [V] | One person can raise their own pay and finalize it; a campus-scoped holder of `PAYROLL_VIEW` would see every campus's salaries [I] | High | Principal | AUTH-01, AUTH-03 | Missing | Separate approve from generate; scope payroll by campus | Soon | Yes (D-P1) |
| PAY-12 | Payroll | Correctness | Deactivating a component does not stop it being paid: `buildPayslip` never checks component status (`PayrollService.java:183-190`; comment at `SalaryService.java:49`). [V] | An admin who "retires" a component believes it stopped; it keeps paying until each person gets a new structure | Medium | HR | — | Existing (defective) | Warn with a count of structures still using it; offer bulk revise | Soon | No |
| PAY-13 | Payroll | Data | The only way to refresh a stale draft is discard then generate (`PayrollService.java:72-76,110-122`), and discard deletes every line including adjustments. [V] | Adding a late structure means re-keying every adjustment for every person | Medium | Accountant | — | Missing | "Regenerate in place" that preserves adjustment lines | Soon | No |
| PAY-14 | Payroll | Product | No release step: finalize makes the payslip visible to staff at once (`PayslipPortalService.java:47-48`). [V] | Staff see a payslip before money moves, and a later correction swaps it out from under them | Medium | Staff | PAY-07 | Missing | Release or pay-date step | Soon | No |
| PAY-15 | Payroll | Correctness | `requireDraft` takes no lock and `finalizeRun` has no row lock (`PayrollService.java:208-214,125-149`), no `@Version`. [I] | An adjustment can land on a payslip at the moment of finalize, changing a locked payslip after the negative-net check | Medium | Accountant | PAY-04 | Existing | Lock the run row in add, remove and finalize | Soon | No |
| PAY-16 | Payroll | Correctness | Negative net is checked only at finalize; adjustments have no maximum or zero check (`PayslipLine.java:46-48`, `AddPayslipAdjustmentRequest.java:9-11`); amounts over DECIMAL(14,2) fail in the database [I]. [V] | A huge recovery or zero-amount line reaches a draft unchallenged | Medium | Accountant | — | Partial | Validate amount range and non-zero | Later | No |
| PAY-17 | Payroll | Correctness | Validation failures return 404: constructor checks throw `IllegalArgumentException` (`PayslipLine.java:46-48`, `SalaryStructureLine.java:34-40`, `SalaryStructure.java:43-45`), mapped to 404 (`ApiExceptionHandler.java:38-41`). [V] | A negative adjustment or a percentage over 100 shows "not found" instead of a clear error | Medium | Accountant | — | Existing (defective) | Map validation failures to 400 | Soon | No |
| PAY-18 | Payroll | Sec | No read audit, and write payloads are strings like "2026-03 (N payslips)" (`PayrollService.java:171`, `SalaryService.java:99`); FINALIZE records status, not totals (146). [V] | After a dispute the audit log cannot show the amounts or who viewed them | Medium | Principal | — | Partial | Record basic, line values and run totals in before and after | Soon | No |
| PAY-19 | Payroll | Product | Payslip is thin and print-only: no employer name, pay-period dates, days paid, bank or tax id (`PayslipView.tsx:66-93`); no PDF. [V] | A staff member cannot use it for a loan or visa request | Medium | Staff | RPT-03 | Partial | Employer header, pay period, PDF | Later | No |
| PAY-20 | Payroll | Product | `LeaveType` has no paid or unpaid flag (`LeaveType.java:20-33`); approval writes LEAVE attendance rows only for campus-assigned staff (`HrService.java:176-190`). [V] | Payroll has nothing to key proration on even if it wanted to | Medium | HR | PAY-01 | Missing | Paid or unpaid flag on leave types | Soon | No |
| PAY-21 | Payroll | Correctness | `isTeacher` counts any assignment ever: `findByTeacherPersonId` returns ended assignments (`TeacherAssignmentRepository.java:12`). [V] | A former teacher now in admin is counted as a teacher in run totals | Medium | Accountant | — | Existing (defective) | Count only active assignments | Later | No |
| PAY-22 | Payroll | Correctness | Runs can be generated for any month 2000 to 2100, including future and out of order (`GeneratePayrollRunRequest.java:7-8`). [V] | A run for an unfinished month can be finalized and shown to staff | Low | Accountant | — | Partial | Guard future periods | Later | No |
| PAY-23 | Payroll | Product | No loans or advances, arrears, reimbursements, one-off components, or statutory fields (only `taxIdentifier` exists on bank details). Statutory rules and rates are out of scope and need legal verification. [U] | An accountant hand-enters everything as adjustments each month | Low | Accountant | D-PAY1 | Missing | Plan one-off and recurring deductions | Later | Yes (D-PAY1) |
| PAY-24 | Payroll | Tech | `listRuns` loads every payslip of every run on each call (`PayrollController.java:41-42`, `PayrollService.java:55`). [V/I] | Slows as history accumulates | Low | Accountant | — | Partial | Totals in SQL or paging | Later | No |
| PAY-25 | Payroll | Sec | Portal lookup takes the first ACTIVE membership (`PayslipPortalService.java:55-57`) [I]. Enumeration is handled: non-owner, draft and superseded all give the same 403 (47-50). [V] | Edge case for someone who is both guardian and staff | Low | Staff | — | Partial | Pick the membership whose person has a staff profile | Later | No |
| PAY-26 | Payroll | Correctness | Rounding is consistent: per-line HALF_UP to 2 places, totals are exact sums of rounded lines. [V] Small gaps: a structure value with more than 4 decimals is truncated by the DB (V116:42) [I]; the UI sums run totals in floating point (`PayrollRunsPanel.tsx:95`). [V] | Display-only risk at worst | Low | Accountant | — | Implemented | None required | No | No |
| PAY-27 | Payroll | Test | Tests run on an entity-generated H2 schema, so V116 and V117 never execute under test (`application.properties:3-5`); arithmetic is asserted for one round-number case only (`PayrollServiceTest.java:99-101`); no tests for rounding, percent earnings, mid-month joiners or leavers, correction contents, concurrency, cross-tenant or permission layer; no e2e. [V] | A missing unique constraint (PAY-04) cannot be caught by tests | Low | Dev | PLT-07 | Partial | Flyway-on-MySQL test; proration and correction tests | Soon | No |

## 3.14 Regulatory and privacy

**Verdict: usable with fixes.** The export builder is safe (closed field catalogue, no SQL, no arbitrary table access). The module is committed (`85f798b`) with UI under Settings > Compliance. Consent, data-subject requests and transfer hand-offs are record-keeping only: nothing consults consent, erasure only flips a status, and the subject-data export holds seven fields. Fine as a labelled manual register; not a compliance control. Nothing here asserts any legal requirement [R].

| ID | Area | Type | Gap | Why it matters | Sev | Users | Depends | State | Recommendation | MVP | Dec? |
|---|---|---|---|---|---|---|---|---|---|---|---|
| REG-01 | Regulatory | Product | Nothing outside `com/operion/privacy/` references `ConsentService`, `ConsentRecord`, `ConsentPurpose` or DSRs; notifications, communication and portal never consult consent; `NotificationPreference.java:18-20` is a separate opt-out model with no link to `consent_records`. [V] A consent register nothing consults is documentation, not control. [R] | Recording "withdrawn" for messaging or photo publication changes nothing, yet a user can reasonably believe it does | High | Admin, data-protection owner | — | Partial | Wire one real gate (for example a `isGranted(person, purpose)` check in notification dispatch) or label the screen "record only, not enforced" | Soon | Yes (D-CP3) |
| REG-02 | Regulatory | Product | DELETION completion only flips a status and stores a note (`DataSubjectRequest.java:19-24,93-96`); no code anonymises, erases or restricts a person (grep finds none). The UI carries an info alert saying so (`DataSubjectRequestsTab.tsx:290-294`). [V] [R] | The UI offers a request type whose fulfilment is entirely manual; nothing records what was removed or retained | High | Admin | — | Partial | Structured outcome per module (erased, retained with reason) or hide the type until an anonymise path exists | Soon | Yes (D-CP4) |
| REG-03 | Regulatory | Correctness | `exportSubjectData` returns 7 Person fields and the consent history only (`DataSubjectRequestService.java:69-100`): no student record, guardians, enrolment, attendance, marks, fees, health, library, transport, documents, messages or audit. The button says "Download data". [V] | A requester handed this file receives a fraction of what the school holds; the payload carries no caveat | High | Admin | — | Partial | State the scope in the payload and UI; plan a per-module contributor interface | Soon | No |
| REG-04 | Regulatory | Ops | No overdue alerting: "overdue" is computed only in the response DTO with server-timezone `LocalDate.now()` (`DataSubjectRequestResponse.java:17`); no scheduler, notification, dashboard widget or email references DSRs. [V] | The due date is the core control of a request process and appears only if someone opens the tab | High | Admin | — | Missing | Overdue count on the dashboard; daily digest to the privacy owner | Soon | No |
| REG-05 | Regulatory | Data | Consent has no record of what was agreed: `ConsentPurpose.update` rewrites name and description in place (`ConsentPurpose.java:41-44`, exposed by PATCH `ConsentController.java:50-53`); `ConsentRecord` stores only `purpose_id` and the response shows the current name. [V] [R] | Editing a purpose silently changes the meaning of every past grant | High | Admin | — | Existing (defective) | Snapshot the purpose text or version into each record; version or freeze purposes | Soon | No |
| REG-06 | Regulatory | Product | Consent has no "on behalf of" model: a record is a person plus a free-text note up to 500 characters (`ConsentRecord.java:26-49`, V127:26); no guardian who agreed, no evidence attachment, no expiry, and no consent screen on student, guardian or staff profiles. [V] [R] | Minors are the main case in a school, yet the record cannot say who consented for whom | High | Admin, guardians | — | Missing | Add `given_by_person_id` and an evidence reference; show consent on profiles | Soon | No |
| REG-07 | Regulatory | Sec | The export catalogue exposes sensitive attributes without classification or warning: category, nationality, blood group, date of birth, gender, address, phone, email, TC number (`RegulatoryExportSources.java:39-48`); any student custom field is addressable via `custom:<key>` (`RegulatoryExportService.java:188-191`). [V] | One permission allows unmasked PII exports with no per-field gating | Medium | Admin | AUTH-03 | Partial | Per-field sensitivity metadata and a confirm step for sensitive columns | Soon | No |
| REG-08 | Regulatory | Sec | Two coarse permissions: `REGULATORY_EXPORT_MANAGE` covers layouts, all-PII exports and transfers; `PRIVACY_MANAGE` covers consent, DSR handling and the full data download; no view-only tier; neither is granted to any seeded role, so only the Owner reaches them by default (`PermissionInterceptor.java:54-63`). [V] | "View consent" forces full handling rights including data download | Medium | Admin | AUTH-03 | Partial | Split into view, manage and export permissions | Soon | No |
| REG-09 | Regulatory | Sec | No four-eyes or requester/subject model: one actor can verify identity and complete a request (`DataSubjectRequestService.java:43-63`); identity verification is one free-text string; the "Requested by" picker conflates requester and subject (`DataSubjectRequestsTab.tsx:242`). [V] | When a guardian asks for a child's data nothing records who asked or how entitlement was proved | Medium | Admin | REG-06 | Missing | Requester and relationship fields; optionally verifier not equal to completer | Later | No |
| REG-10 | Regulatory | Sec | CSV formula injection unguarded: `CsvUtil.formatLine` (`CsvUtil.java:48-62`) quotes commas, quotes and newlines but not cells beginning `=`, `+`, `-`, `@`; names, addresses, custom fields and defaults flow in (`RegulatoryExportService.java:132-143`). [V] | Staff open exports in spreadsheets and students or guardians influence those values | Medium | Admin | — | Missing | Opt-in safe mode that prefixes such cells (formats may need raw values) | Soon | No |
| REG-11 | Regulatory | Data | Transfer payloads are stored as plaintext PII in `transfer_connector_jobs.payload` (`TransferConnectorJob.java:41-42`); no retention, purge or redaction after submission; payload is returned in every list response (`TransferConnectorJobResponse.java:17`). Encryption at rest is [U]. | PII copies accumulate indefinitely, including for cancelled jobs | Medium | Admin | PLT-06 | Missing | Redact or expire the payload after SUBMITTED or CANCELLED; omit from the list DTO | Soon | No |
| REG-12 | Regulatory | Correctness | Transfer jobs: the migration says "one per student-exit" (V128:1) but the index is non-unique (V128:25) and `prepare` checks no duplicates (`TransferConnectorService.java:33-44`); `reprepare` rebuilds from the current schema without recording which version was used. [V] | Duplicate hand-offs and payloads that cannot be reproduced | Medium | Admin | — | Existing (defective) | Uniqueness rule on open jobs per exit; store a schema snapshot with the job | Later | No |
| REG-13 | Regulatory | Product | "Connector" overstates it: a job is a manual checklist; someone sends the payload elsewhere and types a reference (`TransferConnectorJob.java:18-22`); the payload shows only in a dialog, no file download (`TransferConnectorsPanel.tsx:189-201`). [V] | An admin expects integration and gets a status tracker that still needs re-keying | Medium | Admin | — | Partial | Rename to "Transfer hand-off log"; add payload download | Later | No |
| REG-14 | Regulatory | Product | The 30-day default due date is hard-coded (`DataSubjectRequestService.java:17`) and repeated in UI text (`DataSubjectRequestsTab.tsx:252`); not an organisation setting; cannot be edited later. Whether it matches any rule is [R]. | The only default in the module is a jurisdiction-flavoured constant, against the region-neutral direction | Medium | Admin | D-CP5 | Existing (defective) | Organisation-level setting, empty by default | Soon | Yes (D-CP5) |
| REG-15 | Regulatory | Correctness | `RecordConsentRequest` uses a primitive `boolean granted` (`RecordConsentRequest.java:4`); a client that omits it silently records a withdrawal. [V] | Wrong record with no error | Medium | Dev | — | Existing (defective) | Use `Boolean` and reject null | Soon | No |
| REG-16 | Regulatory | Sec | Audit coverage incomplete: purpose create, update and status change (`ConsentService.java:38-59`) and export-schema update and status change (`RegulatoryExportService.java:78-89`) write no audit row; `recordedBy` is stored but not shown; the audit tab needs `ORGANISATION_MANAGE` on top of `PRIVACY_MANAGE` (`AuditLogController.java:28`). [V] | A wording change (REG-05) leaves no trail; privacy staff cannot see who recorded what | Medium | Admin | — | Partial | Audit all mutations; show `recordedBy` by name | Soon | No |
| REG-17 | Regulatory | Tech | Export is synchronous and unbounded: `findByStatusIn` loads all matching students or staff (`RegulatoryExportService.java:135,142`), no row cap or async job; transfer and DSR lists unpaged. [V] | A large school can time out or exhaust memory on one GET | Medium | Admin | — | Partial | Stream rows or cap; background jobs for large exports | Later | No |
| REG-18 | Regulatory | Data | No retention or deletion policy for exports: generated CSVs are not stored (good) but the audit row records only a row count, so which file left the system is not traceable. Encryption at rest is [U]. | The only trace of an export is a count | Medium | Admin | — | Missing | Decide and document the retention policy | Later | Yes |
| REG-19 | Regulatory | Correctness | Validation errors return misleading statuses: every `IllegalArgumentException` maps to 404 (`ApiExceptionHandler.java:38-40`), null `entityType` gives 500, over-long values give a generic 409; no `@Valid` in these controllers. [V] | The UI cannot tell "not found" from "bad input" | Low | Admin | — | Existing (defective) | Request validation with 400 responses | Later | No |
| REG-20 | Regulatory | Sec | `Content-Disposition` embeds the unsanitised schema code (`RegulatoryExportController.java:84`); code is only trimmed. [V] | Header hygiene issue | Low | Admin | — | Existing | Restrict code to a slug pattern | Later | No |
| REG-21 | Regulatory | Correctness | Consent "insert-only" is convention only: no `@Immutable`, repository allows delete, no DB restriction (`ConsentRecord.java:18-19`, V127). [V] | Immutability is asserted in a comment, not enforced | Low | Admin | — | Partial | `@Immutable`; remove delete and update paths | Later | No |
| REG-22 | Regulatory | Correctness | `parseCustomFields` swallows JSON errors and returns null (`RegulatoryExportService.java:210-219`); a default then masks the data problem. [V] | An export can look complete when data is missing | Low | Admin | — | Existing | Fail or flag the affected row | Later | No |
| REG-23 | Regulatory | Product | No UI permission gating and no opt-in mechanism: every member sees the Compliance tiles and gets 403 on open (`settingsSections.tsx:207-229`); no per-organisation toggle exists, "opt-in" means only that nothing is seeded and no role holds the permission. [V] | Confusing first impression; the opt-in claim is not enforceable | Low | Admin | PLT-01 | Partial | Hide tiles the user lacks permission for, or add a real toggle | Later | No |
| REG-24 | Regulatory | Product | Consent purposes have a PATCH but no edit UI (`ConsentPurposesTab.tsx`); STAFF schema cannot read custom fields (`RegulatoryExportService.java:227-228`); the `custom:` column is free-typed. [V] | Minor capability gaps | Low | Admin | — | Partial | None urgent | Later | No |

---

# 4. Module-by-Module Audit

Columns: what exists · what is obviously missing · edge cases/failure states · approvals & audit · reports & school-level configuration. Dependencies are noted inline (→). All modules in this table were read in full by the second pass (§3.8 onward); behaviour is code-verified, not executed.

| Module | Exists [V] | Missing | Edge cases / failure states | Approvals & audit | Reports & configuration |
|---|---|---|---|---|---|
| **Organisation** | Org, campuses, config (tz/currency/date format/working days/branding/number formats), departments/designations, academic years, provisioning (non-atomic), opt-in school template | Status enforcement (SEC-03), atomic/resumable provisioning, plan limits, module toggles, attendance rules config, format validation (FEE-10) | Half-provisioned org after failure; closed year still writable; two "current" years under race | Org create + status change audited; config edits partly | Config: strong for formats/branding; **no** attendance/late/cutoff rules, no fee policy settings |
| **Identity / Auth** | Login (slug+email+password), refresh rotation, lockout, invite claim, email verification, staff invite email, profile-change requests | Working password reset email, password policy, MFA, revocation on disable, session list/"log out everywhere", org-status check | Locked-out real user vs attacker (email-keyed lockout); guardian in 2 schools; user with 2 Person rows | Login/logout/password events **not** audited | — |
| **Authorization** | Roles, permissions (~94 enforced), Owner bypass, `isPortal`/`isManaged`, live DB check, membership status chip | Data scope (campus/section), default-deny, split permissions (§6), delegation limits, permission-change preview | Role deactivated while users hold it; permissions cached in the UI until re-login | Role/permission/membership changes audited (6 sites) | Role templates opt-in; 12 roles; missing VP/Class Teacher/Subject Teacher |
| **Academic** | Year, grade, class (year+campus+grade), section, subject, class-subject, teacher assignment, timetable + periods, working calendar, events, class/section/subject imports | Terms, electives/streams, year lifecycle guards, rollover, per-day period variants, timetable history/substitution | Class deleted vs students; teacher on leave (no substitution); holiday vs marking | Limited; timetable changes not audited [U] | Thinnest test coverage per docs [D]; `changeStatus` methods untested |
| **Students / Admissions** | Inquiry→applicant→admit, enrolment, documents+verification, exit, campus-transfer request, custom fields, ID template, imports | Duplicate detection, certificates (TC/bonafide), rollover/promotion UI, exit checklist, re-admission, alumni, guardian creation on convert | Sibling admission; mid-year join (roll numbers); re-admit; TC issued but fees pending | 9 audit sites (admit, section change, exit…) | Student export unpaged/unaudited; no admission funnel report |
| **Guardians / Parent** | Guardian↔student M:N with primary/emergency/pickup/comms flags, portal invite, guardian role auto-created | Delivery of invites, sibling dedupe, guardian-level (family) view, consent at portal claim | Separated parents (who may receive what); guardian without phone/email; one guardian, two children | Grant-portal-access audited [U] | Guardian export unpaged; no "guardians without portal access" list |
| **Staff / HR** | Profile, documents, bank, leave types/balances/requests, staff attendance API, exit, recruitment (job apps), staff import | Exit cascade (AUTH-06), staff attendance UI, substitution, transfer between campuses, contract/expiry alerts | Staff with two roles (union — OK); teacher leaving mid-term; leave approved over a marked-present day | 7 audit sites | HR exports unpaged; no headcount/leave-liability reports |
| **Attendance** | Daily register per section (DRAFT/SUBMITTED/LOCKED), corrections (insert-only, reason), monthly %, absence email | Source/timestamp/idempotency, teacher scope, period-wise, holiday integration, dedupe, alert channels, staff attendance UI | Section change mid-month; marked on holiday; half-marked register; correction after alert | Submit/lock/unlock/correct audited | No attendance report canned; formula not configurable |
| **Examinations** | Exams, schedules (class vs section), grading scales, marks register workflow (DRAFT→SUBMITTED→APPROVED), pass/fail strategy, rank, report card publish/stale/republish | Terms, cumulative report cards, co-scholastic, marks validation, bulk publish, hall tickets, PDF, marks import | Absent students; marks correction after publish (handled); grade scale change after publish | 5 audit sites; separate `MARKS_CORRECT_AFTER_PUBLISH` permission — good pattern | No canned marks reports |
| **Finance** | Categories, structures/groups/installments, assignments, invoices, payments+allocations, refunds, adjustments, waivers, Razorpay link+webhook, overdue reminder, opening-balance import | Bulk invoicing, receipts (print/PDF), day-close, reconciliation, concurrency safety, refund approvals, concessions model, carry-forward, per-org gateway | Cheque bounce (handled); partial payment (handled); refund > paid (not handled); double webhook; year-end | Payments/refunds/adjustments/waivers audited via wrapper | Only overdue list; no collection/defaulter/cash-book |
| **Transport** | Vehicles, drivers, routes/stops, student assignment (+fee link), trip logs, manual boarding, docs, import | Expiry alerts, boarding/trip notifications, driver surface, fee revision on route change, live GPS (deferred by decision) | Vehicle breakdown; route reassignment; student on two routes | Not confirmed [U] | No transport reports/views |
| **Communication** | Announcements (audience-targeted, scheduled), notification outbox+worker, preferences, templates, delivery webhooks, STOMP chat | Retry/backoff, push, variable substitution, quiet hours, locale, failed-notification view, chat moderation | Provider outage → permanent FAILED; duplicate sends; guardian opted-out but critical notice | Announcement lifecycle audited [U] | No delivery report per announcement [U] |
| **Library** | Books, copies (accession, shelf, campus), borrow and return, manual fines, limits per borrower type, block-on-overdue, manual reminder, book import and export; 21 endpoints | Fine list and collection screen, auto-fine, copy repair and recover, scan or accession issue, bulk copy import, renewals, reservations, exit clearance, reports | Double issue under two desks (LIB-03); exited borrower keeps loans (LIB-07); backdated or null return dates (LIB-14); overdue across vacations (LIB-15) | **No audit log**; waiver actor taken from the request body (LIB-11) | No reports; settings organisation-wide |
| **Inventory** | Items, categories, insert-only stock ledger, campus transfers, assets, suppliers, customers, item import, low-stock | Item edit, fractional units, reversal entries, in-transit transfers, asset depreciation and history, stock-take | Negative stock under concurrency (INV-01); adjustment reasons unrestricted (INV-03); low-stock numbers differ in three places (INV-07) | No audit log; `INVENTORY_ADJUSTMENT_MANAGE` separate (good) | Stock and low-stock reports; dashboard tile |
| **Purchase** | Purchase orders (draft, submitted, approved, part and full receive, cancel), supplier returns | PO number, date, total, tax, approver, close-short, payables | Receive against a line of another order (PUR-01); empty receive moves status (PUR-04); over-receipt race (PUR-05) | No approver recorded, same permission raises and approves (PUR-02); no audit log | Spend report counts drafts and cancelled (PUR-07) |
| **Sales** | Store sales with lines, receipt number, part payments, customer history, stock deducted in the same transaction | Refund or void, price list, tax, discounts, receivables view, link to fee ledger | Overpayment accepted (SAL-03); client-typed prices (SAL-05); first-sale counter race (SAL-06); double payment (SAL-07) | No audit log; no role template holds `SALES_*` (SAL-04) | Sales totals report; no day-end collection by method |
| **Payroll** | Components, dated salary structures (insert-only), runs DRAFT, FINALIZED, SUPERSEDED, payslip snapshots, manual adjustments, staff self-service payslips | Proration, LOP days, paid status, bank file, loans and advances, PDF, reports, statutory fields [R] | Full pay for late joiners and unpaid leave (PAY-01); correction drops leavers and adjustments (PAY-02); exit ignored (PAY-03); duplicate runs (PAY-04); void-proof structure typos (PAY-09) | 7 audit sites but **adjustments and bank-detail changes are unaudited** (PAY-05, PAY-06); no maker-checker (PAY-11) | Print-only payslip; no payroll register |
| **Reporting** | Saved SQL reports on isolated views, shares, run/export audit, 5,000-row cap, 10 s timeout | Canned reports, XLSX/PDF, async exports, library/transport views | SQL author error; root datasource | Run/export audited | 4 seeded reports only |
| **Learning (LMS-lite)** | Homework and submissions, quizzes (single-answer, auto-scored), materials, lesson plans with review, answer-sheet marking, leaderboards, rewards; 49 endpoints, none with `@RequirePermission` but all scope-checked in services | Notifications, homework grades, quiz edit or cancel, file privacy, recall of released sheets | Public file links (LMS-01); rank shown although ranking is off (LMS-02); guardian-taken quizzes (LMS-04); late check uses server timezone (LMS-09) | Scope rules consistent; no review history, self-approval of lesson plans (LMS-19) | Leaderboards only |
| **Platform / Billing / Support** | Platform admin plane, plans, subscriptions, manual invoices, MRR, org list and status, tickets, settings | Enforcement (SEC-03), limits, toggles, tenant support and billing views, MFA, offboarding | Non-payment; trial expiry; tenant leaves | Partial (Plan, Subscription, Invoice, Setting, Organisation) | MRR only |
| **Platform CRM** | Leads (six stages), contacts, activities, follow-up tasks, pipeline dashboard, convert lead to school; separate platform JWT and interceptor | Reminders, lost reason, duplicate check, lead contact details, export, delete or anonymise, admin management | Convert is not atomic or retryable (CRM-01); double convert race (CRM-06); existing email keeps old password (CRM-09) | **No audit trail** (CRM-07); login without lockout (CRM-02) | Win rate and funnel snapshot only |
| **Regulatory and privacy** | Consent purposes and records, data-subject requests (access, export, correction, deletion), regulatory export builder on a closed field catalogue, transfer hand-off log; UI under Settings > Compliance | Consent enforcement, consent on profiles, on-behalf-of for minors, structured erasure, multi-module subject export, overdue alerts | Purpose wording editable after grants (REG-05); primitive boolean records withdrawal when omitted (REG-15); CSV formula injection (REG-10) | Purpose and schema changes unaudited (REG-16); no four-eyes (REG-09) | Nothing seeded, by design |
| **Mobile / Portals** | Web guardian + teacher portals; Expo admin prototype | Parent/teacher/student mobile flows, push, secure storage | Lost phone; new device; multi-child | — | — |
| **RFID** | Nothing | Everything (§8) | — | — | — |

---

# 5. End-to-End Workflow Audit

Status legend: ✅ exists · ◐ partial · ✖ missing · ? unknown.

## 5.1 Student lifecycle

| Step | Status | Gaps, exceptions, ownership |
|---|---|---|
| Inquiry (public form `/apply/:slug`) | ✅ | Rate limit is per-IP in memory and shared behind nginx (SEC-13). Slug in URL is fine. No duplicate-inquiry check. |
| Application / verification | ◐ | 5 boolean "received" flags, no uploads/verification, no entrance/interview/offer/seat. Owner: Reception. |
| Convert → Student | ◐ | Creates student only; **no guardian, no enrollment, no fee** (STU-09). |
| Guardian mapping | ◐ | M:N model is good; no dedupe across siblings; import creates new Person per row (STU-05). |
| Class/section allocation | ◐ | Enrollment API exists; **no promote/reassign UI**; no year-of-section validation; section change overwrites in place (STU-10). |
| ID generation | ◐ | Template render for one student; no bulk print/PDF; nothing binds a card UID (STU-07). |
| RFID assignment | ✖ | Nothing exists (RFID-01). |
| Fee setup | ◐ | Assignment is manual per student; duplicate assignment possible (FEE-07); transport fee via assign hook. |
| Academic participation | ✅ | Timetable, teachers, learning (portal). |
| Attendance | ◐ | Manual only; teacher can't mark from their portal (ATT-06). |
| Examination | ◐ | Per-exam report card only (EXM-01). |
| Promotion | ✖/◐ | Single-student API only; **no rollover, no carry-forward, no UI** (STU-01). |
| Transfer / exit | ◐ | Exit recorded; no TC, no dues/library/transport clearance, no portal-access cut-off (STU-03/06). Campus transfer only flips a request status. |
| Alumni / archive | ✖ | `ALUMNI` unreachable; no re-admission (STU-04). |
| Deletion / erasure | ✖ | No student delete endpoint by design; DSR "deletion" only records handling; retention policy undefined (D-D1). |

## 5.2 Staff lifecycle

| Step | Status | Gaps |
|---|---|---|
| Recruitment | ◐ | Job application form (no rate limit, possible tenant-context bug SEC-18); no interview/offer pipeline. |
| Staff creation | ✅ | With optional login invite; staff import exists. |
| Department / designation / role | ✅ | Catalogs are org-defined. Role ≠ designation is correct but explain in UI (UX-03). |
| Permission | ◐ | Org-wide only; no class-teacher scope (AUTH-01). |
| Class/subject allocation | ✅ | `TeacherAssignment` (homeroom/subject/co-teacher); no validation against `ClassSubject`. |
| Timetable | ✅ | Clash-checked; no substitution when a teacher is on leave. |
| Attendance | ◐ | API only, **no UI**, no source, no device. |
| Leave | ✅ | Balance-before-approval; approval writes LEAVE staff-attendance rows. No substitution linkage. |
| Payroll data | ◐ | Structures, runs, payslips; no proration/statutory by decision. |
| Transfer (campus) | ? | Not verified. |
| Exit | ◐ | Status flip only; **membership, assignments, timetable untouched** (AUTH-06). |

## 5.3 Academic lifecycle

| Step | Status | Gaps |
|---|---|---|
| Academic year | ◐ | Closing changes nothing downstream; can be re-opened; no lock (STU-02). |
| Term | ✖ | No entity (ACD-01). |
| Classes / sections | ✅ | Per year+campus+grade. Rollover copy missing. |
| Subjects | ✅ | Org-level, class-subject link with mandatory flag; no electives. |
| Teachers / timetable | ✅ | See above. |
| Students | ◐ | See 5.1. |
| Exams / marks | ◐ | Workflow good; no bounds validation (EXM-02). |
| Results | ◐ | Single-exam cards; rank recomputed on every publish. |
| Promotion | ✖ | Not linked to results; not bulk (EXM-03, STU-01). |

## 5.4 Attendance lifecycle (as it would run with RFID)

| Step | Status | Gaps |
|---|---|---|
| RFID assignment | ✖ | — |
| Card tap / device / ingestion | ✖ | — |
| Student identification | ✖ | — |
| Attendance determination | ◐ | Manual register only; no cutoff/late rules in config. |
| Duplicate prevention | ◐ | DB unique per (enrollment,date) prevents double *rows* but has no concept of multiple taps. |
| Late / absent rules | ✖ | Not configurable; `LATE` set by a human. |
| Parent notification | ◐ | Absence email, static body, no dedupe (ATT-04). |
| Correction | ◐ | Reason + audit, no approval (ATT-03). |
| Audit trail | ✅ | For submit/lock/unlock/correct; **not** for who marked a given row. |
| Reports | ✖ | No canned attendance report. |

## 5.5 Fee lifecycle

| Step | Status | Gaps |
|---|---|---|
| Fee structure | ✅ | Installments must sum exactly; per-frequency (V96). No year clone. |
| Student mapping | ◐ | Manual; duplicate possible. |
| Invoice / demand | ◐ | **Manual per installment per student** (FEE-04). |
| Payment | ◐ | Methods: cash/cheque/UPI/card/transfer/online; no idempotency/lock (FEE-01). |
| Receipt | ◐ | Number is gap-free and tenant-unique; **no printable receipt** (FEE-06). |
| Partial payment | ✅ | Allocations sum to payment; no advance/credit balance (deferred). |
| Concession / scholarship | ◐ | Waiver/adjustment per invoice; no policy model (FEE-08). |
| Refund | ◐ | Immediate, no approval workflow, no cap (FEE-03). |
| Outstanding | ◐ | Computed overdue list; no aging, no class-wise dues. |
| Reminder | ✅ | Bulk send + daily scheduler through the outbox (subject to NOT-02). |
| Reconciliation | ✖ | No day-close, no bank/gateway settlement match (FEE-05). |
| Year-end | ✖ | No carry-forward (FEE-09). |

---

# 6. Role & Permission Audit

## 6.1 Per-role view

*"Current" = what the code supports today. Recommendations are proposals for §20 decision, not decisions.*

| Role | Needs to see | Create / modify | Delete | Never | Approval needed | Audit | Current state |
|---|---|---|---|---|---|---|---|
| **Platform Admin** | Tenant list, plan, usage, health, tickets | Plans, subscriptions, invoices, tenant status | Nothing tenant-side | Tenant student/fee/marks data without an audited support grant | Suspend/archive, refund of platform invoices | Every action | Full access for every admin, no MFA/roles/lockout; no impersonation (which is right today, but needs a *bounded* support-access design) |
| **School Admin (Owner)** | Everything in tenant | Everything | Cannot delete students (no endpoint) | Other tenants (**SEC-01/02 break this**) | Refunds above threshold; role grants of `ALL_FUNCTIONS` | Role/permission/export/config changes | Owner bypasses all checks — no separation between "school owner" and "office administrator" |
| **Principal** | Dashboards, attendance, fee summaries, marks, staff leave, approvals | Announcements, approves leave/marks/refunds | Nothing | Raw bank/salary details unless HR-granted | Approves marks, leave, waivers | Approvals | Seeded template has Principal; holds `REPORT_CREATE` (SQL) — odd |
| **Vice Principal** | As Principal, scoped to wing/campus | Marks approval for their wing | — | Finance settings | Same, delegated | Same | **No role**; campus scope not enforced |
| **Teacher** | Own sections' roster, timetable, marks, homework | Attendance, marks entry, homework/quizzes for assigned sections | Own drafts | Other sections, fees, guardian phone/email beyond need, other staff HR | Marks *submit*; leave requests | Attendance edits, marks changes | Attendance/marks **not** scoped to assignments (ATT-02); learning is scoped (good) |
| **Class Teacher** | Full roster + guardian contacts + attendance summary of homeroom | Attendance, remarks, first-line leave approval | — | Other sections | Student leave (optional) | Same | Concept exists as `TeacherAssignment=HOMEROOM` but grants nothing |
| **Subject Teacher** | Their subject×section | Marks for that subject | — | Other subjects' marks | Submit marks | Marks changes | Same |
| **Accountant** | Fees, dues, collections, gateway status | Assign, invoice, collect, adjust | Voids (none today) | Marks, HR salary, student medical | **Refunds/waivers by someone else** | All money events | Refund approver is body-supplied (FEE-03); no day-close |
| **Librarian** | Catalogue, loans, fines | Borrow/return, fines | Books (archive) | Fees, marks | Fine waivers | Fine changes | Present in template with the four library permissions; waiver is not separated from raising a fine (LIB-11) |
| **Transport Manager** | Routes, vehicles, drivers, assignments, trips | Assign, log trips, boarding | — | Fees except transport | Route changes affecting fees | Assignment changes | Present; TRN-02 |
| **Transport staff (driver/attendant)** | Today's trip roster | Boarding marks | — | Everything else | — | Boarding | **No surface exists** |
| **Reception / Front-desk** | Inquiries, visitors, student contact basics | Applicants, admissions | — | Fees detail, salaries | Admission conversion | Convert | Present ("Receptionist"); `STUDENT_VIEW` exposes guardian phones |
| **HR / Admin staff** | Staff records, leave, payroll | Staff, leave, payroll runs | — | Student marks | Payroll finalize (separate person ideally) | Payroll | `PAYROLL_MANAGE` covers generate+finalize+delete (AUTH-03) |
| **Parent / Guardian** | Own children: attendance, fees, results, notices, timetable, transport | Profile-change request, homework submission, (pay fees) | Nothing | Other children, other parents, staff contact list (**SEC-05 breaks this**) | — | Consent, portal actions | Web portal partial; mobile absent |
| **Student** | Own timetable, homework, results | Submissions | Nothing | Others | — | — | **No login** (decision) |
| **Configurable roles** | Anything | Anything | Anything | — | — | — | Fully org-configurable; privilege escalation unbounded (SEC-12) |

## 6.2 Cross-role lifecycle questions (current behaviour)

| Question | Today [V] | Gap |
|---|---|---|
| Role change | Effective next request on gated endpoints; UI updates only after re-login | AUTH-05 |
| User leaves the school | `StaffProfile` status flips; membership, assignments, timetable stay; JWT ≤480 min and refresh 30 days survive | AUTH-06, SEC-04 |
| Permission revoked | Immediate on gated endpoints; ungated endpoints remain open | AUTH-02 |
| Staff with multiple roles | Union of active memberships' permissions | Fine; document it |
| Guardian's child exits | Guardian link/portal untouched | STU-03 |
| Teacher on leave | No substitution; timetable unchanged | Later |

## 6.3 Is "default roles → default permissions → school modifies" sufficient?

**Mostly, as the base mechanism. Insufficient in four specific ways:**

1. **No data scope** — permission answers "may you mark attendance?", never "for which sections?". This is the single largest RBAC gap (AUTH-01).
2. **Default-allow for unannotated controllers** (AUTH-02).
3. **Actions and approvals are fused** — one code both does and approves (`FEE_ASSIGNMENT_MANAGE` covers discounts; `PAYROLL_MANAGE` covers finalize; `HR_PAYROLL_VIEW` grants bank-detail writes).
4. **Roles seeded on-demand only**, so a school begins as "everyone is Owner".

## 6.4 Recommended model (clean, minimal) — *proposal*

- **Keep RBAC** with permission codes as-is. Do **not** adopt full ABAC.
- **Add exactly three data scopes**, resolved server-side by one `ScopeResolver` (generalise the existing `LearningAccessService`/`LearningActor`, which already does this correctly):
  1. `ORG` (default; today's behaviour) — optionally narrowed to `CAMPUS` from `OrganisationMembership.campus`.
  2. `ASSIGNED` — sections/subjects derived from active `TeacherAssignment` (HOMEROOM ⇒ whole section; SUBJECT ⇒ that subject in that section). No new table.
  3. `SELF` — portal users: linked children (`StudentGuardian`), own staff record.
  A role's permission is either org-wide or `ASSIGNED`; expressed as a boolean on the role-permission grant (e.g. `ATTENDANCE_MARK` vs `ATTENDANCE_MARK_ASSIGNED`) — simplest to build and explain.
- **Class Teacher is an assignment, not a role.**
- **Split, don't multiply**: add only these codes — `*_EXPORT` (student, guardian, staff, fees), `GUARDIAN_CONTACT_VIEW`, `STAFF_BANK_MANAGE` (separate from view), `PAYROLL_FINALIZE`, `FEE_REFUND_REQUEST` (vs existing APPROVE), `ATTENDANCE_BACKDATE`, `IMPORT_RUN`, `AUDIT_VIEW`, `REPORT_SQL_AUTHOR`.
- **Default-deny** with an explicit `@AuthenticatedOnly` opt-out; ArchUnit test fails the build on unannotated controllers.
- **Delegation rule**: you can grant only permissions you hold; `ALL_FUNCTIONS`/Owner flag is Owner-only.
- **Academic-year restriction = year-status guard** (closed year is read-only), not a per-user permission.
- **Approvals** only where money or a published academic record changes: refunds/waivers over a per-school threshold; marks (already); report-card republish (already); attendance correction after N days (configurable); payroll finalize.
- **Deliberately not**: per-field ACLs, department ABAC, per-year role variants, per-record sharing (beyond saved reports).

---

# 7. Multi-Tenant Audit

Classification per the brief. "Verified" = read in source; "Unverified" = inferred.

| # | Area | Finding | Sev | Notes |
|---|---|---|---|---|
| 1 | Tenant identification | JWT carries org id; slug+email for public flows; payment-link slug in path | Low | Sound |
| 2 | Context propagation | ThreadLocal set in interceptor, cleared in `afterCompletion`; bootstrap services use try/finally; null → `-1` sentinel (fail closed) [V] | Low | Load-bearing gotcha (context must be set before the transaction opens) hit 3× [D]; `HrService.submitJobApplication` looks like a 4th (SEC-18) |
| 3 | Global identity plane | `User`/`login_attempts` global; 3 `UserController` mutations unscoped (SEC-01) [V] | **Critical** | Only `GET /{id}` checks org |
| 4 | Admin/org APIs | `PATCH /organisations/{id}/status` unscoped (SEC-02) [V] | **Critical** | |
| 5 | Query isolation | `@TenantId` everywhere except documented global tables; 2 deliberate native queries (gateway order id, provider message id) return scalars only [V] | Low | Keep as-is; add ArchUnit rule "native queries only in whitelisted repos" |
| 6 | Repository/service layer | Fine for `@TenantId` entities; `findAll()` on students/persons is tenant-filtered by Hibernate but unpaged | Low (isolation) / Medium (perf) | |
| 7 | Unannotated endpoints | `PersonController` etc. (SEC-05) [V] | **Critical** | Not cross-tenant, but cross-role |
| 8 | Background jobs | Per-org context set/cleared; no per-org try/catch; single thread; no lock (TEN-02) [V] | Medium | |
| 9 | Async processing | None (`@Async` unused) [V] | Low | Good; keep it that way until a queue is justified |
| 10 | Cache / Redis | None exist [V] | Low | No isolation risk today. Do not add Redis without a use case |
| 11 | In-memory state | Inquiry rate limiter keyed IP+slug (never evicted), scheduler heartbeat | Low | |
| 12 | Object storage / uploads | Flat dir, no org prefix, public URLs (SEC-08) [V] | High | Cannot enforce per-tenant deletion/export/quotas |
| 13 | Notifications | Outbox tenant-scoped; provider-id lookup native by design; delivery webhooks verify signature/secret before resolving org [V] | Low | Brevo webhooks use a static shared header secret |
| 14 | Search / indexes | No external search | Low | |
| 15 | Logs | No org id in MDC; tokens in URLs/logs (SEC-17) [V] | Low–Medium | |
| 16 | Metrics | None | Low | Add per-tenant labels when metrics arrive |
| 17 | Audit logs | Not `@TenantId`; explicit filter in reader; platform ids share actor column (TEN-04) [V] | Low–Medium | |
| 18 | Scheduled jobs | Suspended orgs still processed (SEC-03) [V] | Medium | |
| 19 | Reports | Isolation is a DB-side view + session variable; security depends on datasource user; regex guard; `@var` bypass unverified (SEC-07) [V/U] | High | |
| 20 | Exports | `findAll()` per tenant, unaudited (SEC-16) [V] | Medium | |
| 21 | Imports | Per-row transactions in tenant context; no cross-tenant path seen [V] | Low | Student import isn't in history |
| 22 | Webhooks | Razorpay/Resend verified; org from native lookup post-verification; Razorpay is a **shared account** (TEN-03) [V] | Medium (isolation) / High (business) | Bodies parsed before signature check (fine if no side effects) |
| 23 | RFID events | N/A (missing). **Design rule:** tenant must come from the *registered device*, never from the payload | — | See §8 |
| 24 | WebSocket | Token checked once; membership `findFirst()` unfiltered (SEC-19) [V] | Medium | |
| 25 | Identifier collisions | `students.student_id` global unique (TEN-01) [V] | **Critical** | Other org-unique keys (admission number, receipt, invoice) are correctly scoped |
| 26 | Test coverage | 10 tenant-isolation test classes; none covers SEC-01/02/05 | Medium | Add a generic "every endpoint × foreign-tenant token" test |

---

# 8. RFID Audit

**Fact: no RFID/device code exists.** This section is a design audit — what has to be true for the workflow *Student taps → event reaches Operion → attendance recorded → notification/report generated* to work in a real school. Hardware type, protocol and vendor are **Unknown** and are decisions (§20 D-R*).

## 8.1 Physical layer (things software cannot fix)

| Item | Risk | Question to settle |
|---|---|---|
| Card technology (frequency/standard, UID exposure, clonability) | Cheap cards can be cloned/lent; UID-only auth is weak | D-R2: is spoof resistance a requirement? |
| Reader placement & throughput | Gate queue at arrival peak; multiple gates/campuses | ~1 tap/s average for 2,000 students over 30 min [arithmetic]; bursts higher — plan reader count per gate |
| Power & network at gate | Outages are normal [A] | Reader must buffer locally; know its offline capacity |
| Device identity & installation | Who registers a reader, and to which campus/location? | Registration flow owned by platform or school? |
| Card lifecycle | Print/issue, lost, damaged, replaced, returned at exit | Who issues; is there a fee for replacement? (D-R5) |
| Who *doesn't* have a card | New joiners, visitors, forgotten cards | Manual fallback is mandatory, not optional |

## 8.2 Software layer — required capabilities and current status

| Capability | Status | Design requirement |
|---|---|---|
| Device registry | ✖ | `devices(org, campus, location, status, secret_hash, last_seen_at, firmware, clock_offset)`; tenant derived from this row |
| Device authentication | ✖ | Per-device secret or client cert; HMAC-signed batches; rotation; revoke on loss; never accept org id from the payload |
| Card registry | ✖ | `rfid_cards(org, uid, holder_person_id, status ACTIVE/LOST/REPLACED/RETIRED, issued_at, replaced_by)`; **unique (org, uid) among ACTIVE**; history preserved |
| Event ingestion | ✖ | Append-only `attendance_events(org, device_id, card_uid, event_ts, received_at, seq/idempotency key, resolved_person_id, resolution)`; batch endpoint; 2xx only after durable write |
| Idempotency | ✖ | Key = `(device_id, seq)` or `(device_id, uid, event_ts)`; unique constraint; safe replay |
| Timestamp handling | ✖ | Store device time **and** server receive time; bound acceptable skew; flag suspicious (future/very old) events; school-timezone day boundaries from `OrganisationConfiguration.timezone` |
| Offline events | ✖ | Devices upload late batches; attendance derivation must be re-runnable for a day |
| Unknown cards | ✖ | Resolution = `UNKNOWN_CARD`; surfaced in an admin queue; never dropped |
| Lost / replaced cards | ✖ | Tap from a LOST card is recorded but resolved as `BLOCKED_CARD` and alerts the office |
| Reassignment | ✖ | New card never overwrites history; old events keep the old card id |
| Multiple rapid taps | ✖ | Debounce window (configurable seconds) collapses repeats; first-in/last-out semantics decided per school (D-R4) |
| Wrong card | Not solvable in software | A friend tapping another's card is invisible. Mitigation is process: the teacher confirms the register |
| Device failure | ✖ | `last_seen_at` heartbeat; "no events from device X in N minutes during school hours" alert |
| Network failure | ✖ | Device-side buffering; server accepts backfill |
| Incorrect reads | ✖ | Resolution states rather than silent failures |

## 8.3 Workflow states from tap to notification (proposed)

```
TAP → (device buffers) → POST /devices/events (signed, batch)
  → validate device+signature → tenant := device.org
  → dedupe by idempotency key           ── duplicate ──► ignore (200)
  → resolve card_uid → holder
       ├ unknown        → event.resolution = UNKNOWN_CARD  → admin queue
       ├ card LOST/RETIRED → BLOCKED_CARD → alert office
       ├ holder not currently enrolled  → NOT_ENROLLED → admin queue
       └ ok → event.resolution = MATCHED
  → attendance derivation (idempotent, per student per day, re-runnable)
       first MATCHED event ≥ open time … ≤ cutoff → PRESENT_SUGGESTED
       after cutoff, before late-cutoff           → LATE_SUGGESTED
       none by absence cutoff                     → ABSENT_SUGGESTED
  → apply per school policy (D-R3): (a) suggestions pre-fill the teacher's register for confirmation, or
                                    (b) device result is authoritative unless teacher overrides
  → manual entry always wins where present, with source = MANUAL and audit
  → notifications: "arrived" (opt-in), "absent" (after cutoff, once per student/day)
```

Every arrow above is an unbuilt state.

## 8.4 What conflicts with today's code

- `student_attendances` has no `source`, event time, or device (ATT-01); a single `(enrollment,date)` row cannot represent in/out.
- The `LOCKED` register model assumes a human closed the day; device days need a re-derivation path that respects locks.
- Attendance alert is email-only with a static body (ATT-04); a "child arrived" message would be spam without variables/opt-in.
- Guardian opt-in flags exist (`canReceiveCommunication`) but are per relationship, not per event type.
- ID card studio has no card-UID field (STU-07); Excel import has no card mapping (MIG-01).

## 8.4b Recommendation (proposal)

Build the **manual attendance path to production quality first** (ATT-02/04/06), then RFID as an *evidence source that pre-fills the register* (option (a) above). It is the lowest-risk way to ship: RFID failures degrade to today's process instead of corrupting the record. Whether RFID must be in the *first* release is a business decision (D-R1); the brief calls it expected, the code says it is greenfield — so this must be scoped explicitly, not assumed.

---

# 9. Mobile Audit

## 9.1 Current state [V]

Expo ~57 / RN 0.86 React Navigation app; Principal/VC-style admin experience only (Home, Approvals, Search, More, StudentProfile, StaffProfile, Notifications, Settings, Campuses); wired to `/auth/*`, `/dashboard/summary`, `/announcements`, `/notifications/me`, `/students/*`, `/hr/*`, `/campuses`. Calls **no** `/me/**`. Session (incl. refresh token) in AsyncStorage; refresh never used; single org via slug; no push, deep links, offline, multi-child, role switch.

## 9.2 What each role actually needs on mobile (proposal)

| Role | Essential (MVP) | Soon | Don't port |
|---|---|---|---|
| **Parent** | Child switcher; today's attendance + history; fee dues + pay link + receipts; notices/notifications; report cards; timetable; leave note to class teacher | Transport info, homework, events, teacher chat | Anything admin; profile edits beyond change-request |
| **Student** | *(no login by decision)* — child data appears through the guardian | Own login for upper grades (D-I2) | — |
| **Teacher** | Mark attendance for assigned section (offline-tolerant), today's timetable, notices, own leave, own payslip | Marks entry, homework, parent messages | Fee, admin, HR data |
| **School admin / Principal** | Approvals (leave, marks, refunds), today's attendance and collection summary, notices, quick student lookup | Alerts (device down, failed payments) | Configuration, imports, roles |
| **Transport staff** | Today's route roster + boarding tick | Trip start/end | Everything else |

## 9.3 Cross-cutting concerns

| Concern | Gap | Proposal |
|---|---|---|
| Auth/session | Token in AsyncStorage; no refresh; 480-min access token; sized for a desk admin [D] | Keychain/Keystore, silent refresh, per-device session list, server-side revoke (SEC-04) |
| Multiple children | Web portal has a switcher; mobile none | Child switcher as top-level state |
| Multiple roles | Backend returns GUARDIAN + TEACHER contexts; no mobile switch | Same chooser as web `PortalSelectPage` |
| Multiple schools | Slug login per org; global `User` | D-I3: one login spanning orgs, or one per school |
| Device change | Nothing | Login on new device; old session revocable |
| Push | None (NOT-01) | Device-token table, per-person topics, preference honoured |
| Deep links | None | Notification → screen (fee, absence, notice) |
| Offline | None | Teacher attendance queue with idempotency keys (ties to ATT-01); read-through cache for parents [A: connectivity is patchy in some areas] |
| Sync / failure | None | Explicit conflict rule: server register state wins if LOCKED |
| Invite/onboarding | Manual token + slug (NOT-06) | Deep-link invite that carries org slug |

---

# 10. Security Audit

| Area | State | Gap IDs |
|---|---|---|
| Authentication | Password + slug; lockout by email; no MFA; no password policy; reset unusable; verification not enforced | SEC-09, SEC-11, SEC-13 |
| Authorization | Live DB check for gated endpoints; default-allow otherwise; no data scope; escalation unbounded | AUTH-01/02, SEC-05, SEC-12 |
| Session mgmt | Stateless JWT 480 min; no per-session list; revocation ineffective | SEC-04 |
| Password handling | BCrypt via spring-security-crypto; no policy; unknown-user timing differs | SEC-09, SEC-13 |
| JWT | HS256, subject+org only (no `jti`), secrets defaulted | SEC-06 |
| Refresh tokens | 30-day rolling, bcrypt-hashed, O(n) match, never purged, no status checks | SEC-04, SEC-13 |
| MFA | Absent (platform and school) | SEC-11 |
| Rate limiting | In-memory, IP-keyed (broken behind nginx), only on public inquiry | SEC-13 |
| API security | No security headers; springdoc likely public; CORS default localhost | SEC-14 |
| File access | Public URLs, flat dir, header-trusting type check | SEC-08 |
| Sensitive info | `PersonController`; guardian phones under `STUDENT_VIEW`; tokens in logs | SEC-05, SEC-17, AUTH-03 |
| Logs / audit | Audit covers core mutations (59 call sites); not login, secrets, exports, CRM/support/platform login | TEN-04, SEC-15/16 |
| Exports | Unaudited, unpaged, formula-injectable | SEC-16 |
| Password reset / recovery | Token only logged | SEC-09 |
| School-admin privilege | Owner bypass; one Owner seeded; no break-glass or second-owner rule beyond "can't revoke the Owner" | SEC-12 |
| Platform-admin privilege | Full access, no MFA/lockout/roles; seeded password | SEC-06/11 |

**Security assumptions the code makes that must be made explicit (implemented + tested):**

1. "Every `User` operation is scoped through a membership of the caller's org" — currently false (SEC-01).
2. "A disabled/revoked principal cannot act" — currently false (SEC-04).
3. "Reporting connects as a SELECT-only, view-only user" — a config default contradicts it (SEC-07).
4. "Uploads are private to a tenant" — they are public by URL (SEC-08).
5. "Only the payment provider can mark an invoice paid" — true for signature-verified webhooks; **not race-safe** (FEE-01).
6. "A given portal user can only see their linked children" — enforced in `ChildAccessService`, but bypassable through ungated Person/Messaging (SEC-05/19).

**Compliance:** the Regulatory/Privacy modules record consent and data-subject requests (nothing enforces consent, erasure only flips a status: REG-01, REG-02), but **which legal obligations apply (e.g. the Indian data-protection statute, child-data rules, retention periods) requires verification with counsel** [R]; this audit asserts none.

---

# 11. Reliability & Failure Audit

| Subsystem | If it fails… | Today [V] | Needed |
|---|---|---|---|
| Database | Whole app down; partial-tx states | Provisioning explicitly non-atomic → half-orgs; one Hikari default pool ×2 datasources | Restore drill; resumable provisioning; pool sizing |
| Redis | N/A | Not used | Keep it unused |
| Object storage | Uploads lost or unreachable | Local disk, no volume in compose, not in backup | Volume + backup now; S3-compatible later |
| Notification provider | Alerts/reminders vanish | One send attempt → `FAILED` forever; `EmailOutbox` invisible | Retry/backoff/dead-letter + admin view (NOT-02) |
| RFID device | Attendance gap | N/A | Heartbeat, offline buffer, "device silent" alert |
| Network (school) | Teacher can't mark | Requires online | Offline queue for teachers |
| Mobile offline | Parent sees nothing | No cache | Read-through cache |
| Background job | Missed reminders/announcements | No try/catch per org; heartbeat only 2/4 jobs | Per-org isolation; heartbeat all |
| Duplicate job execution | Double SMS/email/reminders | No lock/claim; `fixedDelay` daily jobs re-run on restart [U] | `SKIP LOCKED` claim; idempotent reminder keys |
| Message delivery | Unknown state | DELIVERED only via Resend/Brevo webhooks | Failed view; retry |
| API timeout | Provider HTTP inside `REQUIRES_NEW` holds a DB connection | As stated | Move send outside transaction |
| Partial transaction | Half-created org; enrollment vs fee drift; exit without cascades | See STU-03, AUTH-06 | Cascade/checklists |
| Payment webhook | Missed/duplicate/failed events | `payment.failed` ignored; no event log; race on duplicates | Webhook event table + reconciliation job against Razorpay |
| Third-party (Razorpay) | Link created, never paid | Never exercised against the real API, only a stub [D] | Test-mode end-to-end before pilot |
| Deploy/migration | Bad migration | CI never applies Flyway on MySQL | MySQL migration CI |

Places where **manual correction** must exist: attendance (exists), fee payments (bounce only — add void), notifications (resend), imports (per-row error CSV exists; whole-run undo doesn't), RFID unknown-card queue (missing).

---

# 12. Data Model Audit

## 12.1 Strengths [V]
`@TenantId` base class; explicit FKs with default RESTRICT (no `ON DELETE CASCADE` anywhere); per-org unique keys for admission number, receipt, invoice; enrollment separate from student identity; insert-only ledgers; `DECIMAL(10,2)` money; effective-dated salary structures; snapshotted payslips/plan price.

## 12.2 Missing entities

| Entity | Why |
|---|---|
| `RfidCard`, `Device`, `AttendanceEvent` | §8 |
| `Term` | Report cards, fee frequency, attendance summaries |
| `EnrollmentSectionHistory` (or versioned enrollment) | Section change mutates in place (STU-10) |
| `StudentSubject` / elective mapping | Senior classes |
| `FeeConcessionPolicy` | Sibling/staff-child/scholarship (FEE-08) |
| `WebhookEvent` | Payment reconciliation and debugging |
| `DeviceToken` | Push |
| `Certificate` (issued TC/bonafide with number) | STU-06 |
| `TenantExport`/`OffboardingJob` | PLT-03 |
| `SessionRecord`/refresh token metadata (device, last used) | SEC-04, mobile |
| `ImportRunRow` detail | Whole-run audit/rollback |

## 12.3 Constraint / relationship problems

| Problem | Evidence | Fix |
|---|---|---|
| Global `students.student_id` unique | V80 | Tenant-scoped (TEN-01) |
| "One current" invariants service-only (`is_current`, current academic year, primary guardian, transport assignment) with non-unique indexes | V5, `AcademicYear` | MySQL generated-column unique or locked service path |
| No unique on (student, fee structure, year) | V9 | FEE-07 |
| `payment_reference` not unique | V97 | Unique per org where non-null |
| `roll_number` unique includes closed enrollments | V5 | Partial/current-only uniqueness |
| No DB-level `marks ≤ max` | V11 | CHECK or service |
| `persons.phone/email` un-indexed while dedupe scans by them | V1 | Index on normalised value |
| Student search `LIKE '%x%'` | `StudentRepository` | Prefix search + index or FULLTEXT |
| EAGER `@ManyToOne(optional=false)`; no batch size | Entities | Lazy + fetch joins on lists |
| No `@Version` anywhere | grep | Add to Invoice, Payment-affecting aggregates |
| `Student.custom_fields` JSON; H2/MySQL divergence | `Student.java:96` | Keep JSON to one column; test on MySQL |

## 12.4 History, soft delete, archival, retention

- **History:** good on money and corrections; weak on enrollment section, timetable (current-only), report-card ranks (recomputed).
- **Hard deletes exist** for calendar entries, events, timetable periods/entries, quiz questions, payroll runs/payslip lines, CRM contacts, template items. Historical reports over those are unstable (see §19 C-7).
- **Soft-delete/archive:** status fields, no generic policy. **No archival or retention job** for `audit_logs`, `notification_recipients`, `email_outbox`, `login_attempts`, refresh tokens. After 3 years these grow unbounded and are the tables that hurt first.
- **Academic-year boundaries:** enforced by unique keys, not by guards (STU-02).
- **Retention period for student/fee/audit data is undefined** — decision D-D1, with legal input [R].

---

# 13. Reporting & Data Export Audit

## 13.1 What exists [V]
Saved SQL reports over 9–10 curated `reporting_*` views (students, attendance_daily, fee_invoices, fee_payments, exam_results, staff, sales, inventory_stock, purchase_orders, organisation); shares by user/role; run/export audited; 5,000-row cap, 10 s timeout; 4 seeded reports (all sales/inventory/purchase); entity CSV/JSON exports for 11 entities; regulatory CSV (only server-side CSV response); browser-print for report card, payslip, sale.

## 13.2 What schools will need (assumption-labelled) and status

| Family | Reports a school will ask for in the first month [A] | Status | Notes |
|---|---|---|---|
| Student | Class-wise strength list, new admissions, strength/withdrawals, birthday list, sibling list, category-wise | Export exists (unpaged, unaudited) | Needs filters (class/section/year/status) and paging |
| Attendance | **Daily absentee list**, class-wise daily/monthly register, student-wise %, chronic absentees, late-comers, staff attendance | ✖ | View exists (`attendance_daily`); no canned report; % formula unresolved (ATT-05) |
| Academic | Class strength by year, teacher-subject allocation, timetable print | ◐ | Timetable on screen; no print |
| Exam | Marks register, subject-wise analysis, class result sheet, topper list, report cards (PDF) | ◐ | View exists; no report; PDF absent |
| Fee | **Daily/period collection (by method/user)**, defaulters with aging, class-wise dues, head-wise collection, refund/waiver register, cheque status | ◐ (overdue list only) | Views exist; no seeded definitions (FEE-05) |
| Staff | Directory, leave balance/liability, document expiry | ◐ | Export only |
| Transport | Route-wise roster, vehicle/driver document expiry | ✖ | No reporting views |
| Library | Issued/overdue, fines | ✖ | No reporting views |
| Inventory | Stock, low stock, valuation | ✅ (4 reports) | |
| Management | Principal one-page: today attendance, month collection vs target, pending approvals, alerts | ◐ | Dashboard exists (org-admin); content not audited |
| Regulatory | Government/board exports | ◐ | Builder works on a closed field catalogue and is safe from injection; ships empty, **no region layout by design**; unbounded synchronous CSV; whether any layout is mandatory requires verification [R] |

## 13.3 Gaps

| Gap | Recommendation |
|---|---|
| Only 4 canned reports (RPT-01) | Ship ~10 canned reports (see MVP §22) as seeded, parameterised, read-only definitions; not SQL for end-users |
| No academic-year filter convention | Every report takes `academicYear` + date range; default = current year |
| Exports: unpaged JSON, client-built CSV, no formula-injection guard, no XLSX/PDF | Server-side streaming CSV; XLSX where asked; sanitise `= + - @` prefixes (SEC-16) |
| No export permission/audit | `*_EXPORT` + audit row with filter + row count |
| Large exports | 5,000-row cap is fine for reports; entity exports need streaming or an async job when >N rows |
| Historical stability | Reports over hard-deletable or in-place-mutated data (§19 C-7) can change after the fact |
| No PDF (RPT-03) | One server-side HTML→PDF path serves receipt, report card, certificate, ID cards, payslip |

---

# 14. Integration Audit

Rule applied: an integration is listed as required only where a concrete school workflow depends on it. Existing code does not imply need.

| Integration | Business need (the actual requirement) | Class | Code today | Verdict / condition |
|---|---|---|---|---|
| **Email (transactional)** | Staff invites, password reset, verification, receipts, report-ready notices | **Required for MVP** | Brevo/Resend + outbox | Wire real keys; **fix reset email (SEC-09)**; guardian invites use it too (NOT-06) |
| **Object storage (S3-compatible)** | Student documents, photos, answer sheets — cannot sit on an unbacked-up container disk | **Required for MVP** (or a mounted, backed-up volume as a stopgap) | Local disk only; interface exists | Private + org-prefixed keys (SEC-08). Data-residency requirement [R] |
| **RFID hardware** | Attendance evidence | **Decision first (D-R1)** | None | Depends on scope decision |
| **Push notifications (FCM/APNs via Expo)** | Parent/teacher mobile alerts; replaces per-message SMS cost | **Required with mobile launch** | None | Preferred channel because it is free per message and needs no telecom registration |
| **Payment gateway** | Parents paying fees without visiting the counter | **Soon after launch** [A: cash/cheque/UPI-at-counter remain common] | Razorpay link+webhook, shared account, stub-tested only | Before the *second* paying school: settle the money model (TEN-03/D-F5); test in provider test mode end-to-end |
| **SMS** | Reaching guardians without smartphones/app | **Decide — establish the requirement first** | Brevo sender, no DLT | Do not adopt until D-N1 says who needs it and who pays. Telecom template-registration rules require verification [R] |
| **WhatsApp** | Parent communication preference [A] | **Avoid initially** | Sender coded, dormant | Adds provider approval, per-message cost, template rules [R]; keep dormant until a paying school demands it |
| **Maps** | Route display | **Avoid** | lat/long columns only | No workflow needs it before GPS (deferred) |
| **Cloud storage for backups** | Off-host DB dumps | **Required for MVP** (ops) | None | Any S3-compatible target |
| **Accounting (Tally etc.)** | Accountant re-keys collections | **Avoid initially** | None | Offer a daily-collection CSV export instead; verify actual demand |
| **Government/education systems** | Board/state uploads | **Requires verification; avoid** | Generic export framework, empty by design | Do not build connectors without the real schemas and credentials [R] |
| **Biometric devices** | Staff attendance | **Avoid** | None | RFID/manual cover it; biometric data adds legal weight [R] |
| **Import/export files** | Onboarding & reporting | **Required for MVP** | 11 importers | See §15 |
| **Observability (Sentry/metrics)** | Diagnose school-reported failures | **Required for MVP (minimal)** | None | Error tracking + request/tenant logging first |
| **Search (Elastic etc.)** | — | **Avoid** | None | MySQL indexes suffice at this scale |

---

# 15. Onboarding & Migration Audit

## 15.1 The starting condition [A]
A school has Excel/CSV, possibly another ERP export, and paper: students + guardians (often on one sheet, siblings repeated), staff, class lists, fee structure and *per-student outstanding*, and sometimes historical marks/attendance.

## 15.2 Step-by-step status

| Step | Status | Gap |
|---|---|---|
| School creation | ◐ | Public unauthenticated POST (SEC-10); non-atomic |
| Initial admin | ✅ | Owner + invite/claim |
| Academic year | ✅ | Optional at provisioning |
| Roles | ◐ | Opt-in template; missing VP/Class/Subject Teacher; unknown codes silently dropped |
| Class/section/subject | ✅ | Importers with `validateOnly` |
| Students | ◐ | Importer **without** validate-only, history, or dedupe; new Person per row |
| Parents/guardians | ◐ | Per-student CSV, new Person/Guardian per row, siblings duplicated |
| Staff | ✅ | Importer with validate |
| Fee setup | ✅ | Structure + opening-balance importers |
| Per-student fee assignment | ✖ | No importer; manual |
| RFID mapping | ✖ | No importer (needs the card model) |
| Photos | ✖ | No bulk photo import |
| Historical marks/attendance | ✖ | No import; decision D-D4 whether history is migrated at all |
| Data validation | ◐ | Row-level with error CSV |
| Duplicate detection | ✖ | STU-05 |
| Import rollback | ✖ | Per-row `REQUIRES_NEW`; no run-level undo; `ImportRun` holds counts only |
| Migration verification | ✖ | No reconciliation report (counts by class, totals of opening balances vs source) |
| Guardian portal activation | ✖ | Manual per guardian (NOT-06) |

## 15.3 Recommendations (proposal)

1. **Make every importer two-phase** (validate → commit), with `validateOnly` for students too, and record each in history.
2. **Stamp created rows with `import_run_id`** so an operator can *undo an import* (delete/deactivate rows created by that run if nothing downstream references them). Cheaper and more honest than a true transaction across thousands of rows.
3. **Dedupe on import:** student — (normalised name + DOB + guardian phone); guardian — normalised phone within the org; sibling linking as a first-class column ("Admission no. of sibling").
4. **Migration verification report** after each run: counts vs input, opening-balance totals vs input, students per section, guardians without phone, students without guardian.
5. **Onboarding order** with dependencies enforced in the wizard: year → grades/classes/sections → subjects → staff → students+guardians → fee structures → opening balances → assignments → periods/timetable → guardian invites.
6. **Bulk guardian invites** with delivery (NOT-06) — the last mile of onboarding.
7. Ask the pilot school what history they truly need on day one (D-D4); importing 3 years of attendance is rarely worth the risk [A].

---

# 16. SaaS / Platform Audit

| Requirement | State [V] | Gap / recommendation |
|---|---|---|
| Tenant provisioning | Public POST or platform POST; TRIAL default; non-atomic | Platform-only/invite-gated; resumable; "provisioning failed" visible to platform |
| Suspension / activation | Status stored, **not enforced** (SEC-03) | Enforce at login, refresh, interceptor; define grace and read-only mode (D-PL5) |
| Subscription | Per-org, insert-only, price snapshot | OK for pilots |
| Billing | Manual generate, manual mark-paid | Write billing policy before automation (D-PL7) |
| Plan limits (users/students/storage) | ✖ | Decide dimension; enforce at student admit / upload |
| Feature flags / module toggles | ✖ | Per-org enabled-modules set; hide unfinished modules |
| Platform admin | Full access, no MFA/lockout/roles | SEC-11 |
| Support access | ✖ (no impersonation) | Time-boxed, reason-required, audited support grant (D-PL4) |
| Tenant health | 3 booleans | See §17 |
| Usage metrics | active students + last audit time | Add logins, notification volume, storage, failed jobs |
| Platform audit | Thin; shared actor column | TEN-04 |
| Backups | Documented only | PLT-06 |
| Data retention | Undefined | D-D1 |
| Tenant deletion / export | ✖ | PLT-03; manual runbook first |
| Tenant-side billing/support | ✖ | Small "Billing" and "Contact support" pages |
| Platform CRM | Built (leads/contacts/tasks) | Non-essential (§21) |

---

# 17. Observability Audit

## 17.1 Current [V]
`/actuator/health` only; plain-text logs with `requestId`; request line logs user id but not org; 59 audit call sites; SchedulerHeartbeat (in-memory, 2/4 jobs); platform "system health" = API/DB/jobs booleans. No metrics, no error tracker, no tracing, no alerts.

## 17.2 Must be observable in production (proposal)

| Signal | Why | Minimum implementation |
|---|---|---|
| Structured logs with `org_id`, `user_id`, `request_id` | Answer "what happened to school X at 10:05" | JSON encoder + MDC set in the interceptor |
| Error tracking | Unhandled exceptions are `log.error` only | Sentry-compatible with org tag |
| API latency/error rate by route | Regression detection | Micrometer + Prometheus endpoint (internal) |
| DB pool, slow queries | Perf cliffs (RPT-04) | Hikari metrics; slow-query log |
| Job health (all 4 schedulers) | Silent reminder/notification stalls | Heartbeat per job, per-org success/failure counter |
| Notification delivery | Outbox states are invisible | Counts by status/channel/age; "failed in last 24 h" list |
| Payment/webhook | `payment.failed` ignored; no event record | `WebhookEvent` table; unmatched/failed counts |
| Import failures | Student import untracked | All importers in `ImportRun` |
| RFID device health | (future) | `last_seen_at`, silent-device alert |
| Tenant-level health | Which schools are quiet/erroring | Per-org: last login, last attendance, failed jobs, storage |
| Audit | Exists | Retention + export; login/secret/export events |

## 17.3 Platform dashboard should show
Tenants by status/plan; MRR (exists); per-tenant last activity, active users 7d, failed notifications 24h, failed jobs, storage used, import failures, open support tickets, trial-ending soon, unpaid invoices; a red list of "tenants with something broken."

---

# 18. Edge Cases

*Current = behaviour verified in code where stated; "Unknown" = not checked.*

| # | Edge case | Current behaviour | Gap / action |
|---|---|---|---|
| 1 | Student changes section | In-place overwrite; old attendance keeps section snapshot; history only in audit log | STU-10 |
| 2 | Student changes class mid-year | Promote API needs no progression check; **no UI** | STU-01 |
| 3 | Student transfers school (TC) | Exit recorded; no TC; no clearance | STU-03/06 |
| 4 | Student leaves and returns | Blocked (exit requires ACTIVE; unique student+year; WITHDRAWN never reactivates) | STU-04 |
| 5 | Parent with multiple children | Portal child switcher works if all children link to the same Guardian row | Whether two Guardian rows can share one login = **Unknown** (STU-05) |
| 6 | Multiple guardians per child | Model supports; primary invariant service-only | Constraint |
| 7 | Separated parents; who may receive/pick up | Flags per link (comms, pickup, emergency) | No notion of custody/court order; policy [R] |
| 8 | Teacher teaches many sections | `TeacherAssignment` per section/subject | Not used to scope attendance/marks |
| 9 | Staff has multiple roles | Union of permissions | Document |
| 10 | User loses access | JWT + 30-day refresh survive (SEC-04) | Fix |
| 11 | RFID card lost / replaced | N/A | §8 |
| 12 | RFID reader offline | N/A | §8 |
| 13 | Student taps twice | N/A; DB blocks second row but can't represent in/out | §8 |
| 14 | Wrong card / friend taps | N/A; process issue | Teacher confirms |
| 15 | Academic year changes | Manual close; nothing cascades; closed year still writable | STU-02 |
| 16 | School closes temporarily (weather/strike) | Working-calendar entries exist but attendance ignores them | ATT-05 |
| 17 | Absent but RFID event exists | N/A | Precedence rule D-R3 |
| 18 | Attendance correction after alert | Allowed instantly; parent already notified; no correcting notice | ATT-03/04 |
| 19 | Fee refund | Immediate, uncapped, self-approvable | FEE-03 |
| 20 | Fee concession | Ad-hoc waiver/adjustment | FEE-08 |
| 21 | Duplicate student records | No detection; import re-run duplicates | STU-05 |
| 22 | Duplicate staff records | Unknown (staff import has dedupe? not verified) | Verify |
| 23 | Deleted users | No hard delete; disable doesn't revoke tokens | SEC-04 |
| 24 | Archived data / historical reports | Some hard deletes; in-place mutations; rank recomputation | §19 C-7 |
| 25 | Payment double-click / webhook redelivery | Race possible | FEE-01 |
| 26 | Cheque bounce after refund | Not handled (`reversePayment` can go negative) | FEE-03 |
| 27 | Marks entered above max | Accepted | EXM-02 |
| 28 | Grading scale changed after publish | Cards keep their computed grade; stale/republish flow exists for corrections | Verify behaviour on scale change |
| 29 | Two siblings, one phone number | Two Guardian rows; two reminder messages | STU-05 |
| 30 | Student changes from day-scholar to bus mid-term | Fee assignment hook only at assign time | TRN-02 |
| 31 | Teacher on leave — substitution | Not modelled | Later |
| 32 | Section overflows capacity | Soft check | STU-10 |
| 33 | Roll number reuse after withdrawal | Blocked by unique including closed rows | Data model §12 |
| 34 | Mid-year admission, fee pro-rating | Not modelled; manual adjustment | D-F3 |
| 35 | Admission number format lacks year token | Next-year collision | FEE-10 |
| 36 | Public admission form spam | Honeypot + per-IP limiter (shared IP behind nginx) | SEC-13 |
| 37 | Guardian's phone changes | Profile-change request workflow exists | Fine |
| 38 | Notification to opted-out guardian for a *critical* event | Preferences are opt-out per channel; no "critical override" | D-N3 |
| 39 | Exam re-conducted for absent student | Not modelled | EXM-03 |
| 40 | Tenant needs its data back | No export | PLT-03 |
| 41 | Two admins edit the same setting | No optimistic locking (`@Version` absent) | Low |
| 42 | Two Owners / owner leaves | Owner can't be revoked; no break-glass | D-I5 |
| 43 | Guardian portal user is also staff | Both contexts; chooser | Works [D] |
| 44 | Timezone / academic day boundary | Org timezone exists | RFID must use it |

---

# 19. Product Contradictions

| # | Contradiction | Related | Impact | Recommended resolution | Decision? |
|---|---|---|---|---|---|
| C-1 | **RFID-first attendance** vs a manual-register data model (unique per enrollment+date, DRAFT→LOCKED, no source/timestamp/idempotency) | ATT-01, RFID-01 | Device events cannot be stored faithfully; two truths for one day | Introduce raw-event table + `source`; RFID derives *suggestions* that a register confirms | **Yes** (D-R3) |
| C-2 | "Disable/revoke a user" (UI action) vs 480-min JWT + 30-day rolling refresh + ungated endpoints | SEC-04, AUTH-06 | Removed staff/guardians keep access | Check status on refresh and in the interceptor; revoke tokens on disable | No |
| C-3 | Org status SUSPENDED/ARCHIVED (data model) vs no enforcement anywhere; plans carry only a price | SEC-03, PLT-01 | "Suspend" is decorative; nothing gates non-payment | Enforce; define grace | Yes (D-PL5) |
| C-4 | Guardian-only portal (no student login) vs mobile brief "Parent/Student" vs hope for a student experience | MOB-03, D-I2 | Mobile design ambiguity | Parent-first; revisit student login for upper grades | Yes |
| C-5 | Global `User` by email vs slug-scoped login vs "one parent, two schools" | TEN-05, SEC-01 | Cross-tenant IDOR arises from global identity; mobile UX unresolved | Scope all user ops via membership; decide spanning login | Yes (D-I3) |
| C-6 | Stated principle "history is preserved, never overwritten" vs `reassignSection` mutating in place, timetable "current only", report-card rank recomputation | STU-10, ACD-02 | Section/timetable history and past ranks unrecoverable | Add section-history; snapshot ranks at publish | Yes |
| C-7 | Historical reporting vs hard deletes (calendar, events, periods, timetable entries, payroll runs, quiz questions) and no retention policy | §12.4 | Historical reports drift; audit/legal exposure | Convert to archive/soft-delete for anything referenced by reports; define retention | Yes (D-D1) |
| C-8 | Multi-tenant shared **Razorpay account** vs per-school fee money | TEN-03 | Settlement ownership/aggregator question [R] | Decide model before second paying school | Yes (D-F5) |
| C-9 | Docs say checks are "never left to individual controllers" vs default-allow for ~30 controllers | AUTH-02 | Security assumptions wrong in docs and code | Default-deny + ArchUnit | No |
| C-10 | Fee-approval permission `FEE_DISCOUNT_APPROVE` (seeded, documented as the gate) vs enforcement by `FEE_ASSIGNMENT_MANAGE` and body-supplied `approvedBy` | FEE-03 | Approval is cosmetic | Enforce caller≠requester + permission | Yes (D-F2) |
| C-11 | Editable number formats vs unique constraints | FEE-10 | A settings change can break next year | Validate tokens | No |
| C-12 | Templated notifications vs static bodies (no variables) | ATT-04, NOT-05 | "Absent" email without the student's name | Add substitution | No |
| C-13 | Backup doc ("no file storage; DB is the only durable state") vs local uploads in production path | PLT-06 | Documented RPO/RTO false for files | Update doc and procedure | No |
| C-14 | Portal role model (`isPortal` flag) vs `PersonController`/Messaging/Upload ungated for "any authenticated member" | SEC-05, SEC-19 | Portal users can do staff-plane things | Default-deny | No |
| C-15 | Stale docs: `mobile-app-context.md` says RBAC "doesn't exist"; `V24` comment says Person controllers gated | — | Misleads future work | Refresh both | No |
| C-16 | "Teacher can view leave types without HR permission" (portal) vs teachers unable to mark attendance from portal | ATT-06 | Portal built around HR self-service, not the teacher's daily job | Reprioritise portal features | No |
| C-17 | Per-org "configurable" positioning vs hard-coded attendance %, late rules, template text | ATT-05, NOT-04 | School cannot match its own register | Config for the 3–4 rules that matter | Yes |
| C-18 | "Modular; modules optional" (product intent) vs all modules on for every tenant | PLT-01 | Cannot sell or hide | Per-org module set | Yes (D-PL2) |

---

# 20. Decisions Required

**Not made here.** The "Recommendation" column is a proposal to argue against. Blocking column shows which gaps wait on the answer.

## Business / scope
| ID | Decision | Options | Recommendation (proposal) | Blocks |
|---|---|---|---|---|
| D-B1 | Target segment for v1 | K–10 single/few campuses · K–12 · multi-campus chains | K–10, ≤3 campuses | ACD-01, AUTH-01 |
| D-B2 | First-release scope for the pilot | Attendance-only · Fees+attendance · Full | Fees + attendance + notices + parent app (parent-first) | Everything in §22 |
| D-B3 | Pilot school profile | Choose one 500–1,000 school willing to share real templates | One school; use its report card/receipt/TC as acceptance | EXM-01, STU-06 |

## Attendance / RFID
| ID | Decision | Options | Recommendation | Blocks |
|---|---|---|---|---|
| D-A1 | Who may mark attendance for a section | Any `ATTENDANCE_MARK` · assigned teachers only · assigned + admin override | Assigned (homeroom/subject) + admin override | ATT-02 |
| D-A2 | Backdating window | None · N days · unlimited with permission | N = 2 days; further needs `ATTENDANCE_BACKDATE` | ATT-02 |
| D-A3 | Corrections need approval? | Never · after N days · always | After the register is LOCKED or older than N days | ATT-03 |
| D-A4 | % formula & working days | Marked days · calendar working days; excused counts | Calendar working days; excused configurable | ATT-05 |
| D-A5 | Late/absent cutoffs | Fixed · per school · per class | Per school (2 values) | RFID, ATT-04 |
| D-R1 | RFID in the first release? | Yes · phase 2 · pilot at one gate | Phase 2 unless the pilot buys on it | RFID-01 |
| D-R2 | Card tech & spoof resistance | UID-only · protected | Ask vendor; UID-only is acceptable if teacher confirms | §8 |
| D-R3 | Device authoritative vs suggestion | Device wins · pre-fill register | Pre-fill | C-1 |
| D-R4 | Tap semantics | First-in only · in/out pairs | First-in for attendance; keep all taps as events | §8 |
| D-R5 | Lost-card policy | Free/paid replacement; block immediately? | Block on report; replacement fee is a school decision | §8 |
| D-R6 | Hardware ownership/registration | Operion supplies · school buys · vendor | Decide before build | §8 |
| D-R7 | Arrival notification | Opt-in · default on · off | Opt-in per guardian | ATT-04 |
| D-R8 | Raw event retention | 90 d · 1 yr · forever | 1 yr raw, derived kept | §8 |

## Identity / permissions
| ID | Decision | Options | Recommendation | Blocks |
|---|---|---|---|---|
| D-I1 | Multiple roles per user | Yes (union) · single role | Keep union; document | — |
| D-I2 | Student login | None · from grade N · all | None for MVP; revisit Grade 8+ | MOB-03 |
| D-I3 | One login across schools | Per-org login · global identity with org picker | Per-org for MVP; design the picker before mobile GA | TEN-05 |
| D-I4 | Password/MFA policy | Length only · +MFA for admins | Length policy; MFA mandatory for platform admins, optional for Owners | SEC-11 |
| D-I5 | Owner model | One Owner · multiple + break-glass | Allow ≥2 Owners | Edge #42 |
| D-P1 | Data scope model | Org-wide only · three scopes (§6.4) | Three scopes | AUTH-01 |
| D-P2 | Class teacher | Role · assignment | Assignment | AUTH-04 |
| D-P3 | Who exports / sees guardian contacts | Any viewer · dedicated permission | Dedicated | SEC-16 |
| D-P4 | Approval thresholds | Always · above amount | Per-school amount for refunds/waivers | FEE-03 |
| D-P5 | Custom roles allowed? Escalation cap | Free · bounded by grantor | Bounded | SEC-12 |

## Students / academic / exams
| ID | Decision | Options | Recommendation | Blocks |
|---|---|---|---|---|
| D-S1 | Duplicate policy | Warn · block · merge tool | Warn + merge later | STU-05 |
| D-S2 | Exit clearance | Advisory · blocking | Advisory checklist with override + audit | STU-03 |
| D-S3 | Re-admission / alumni | New student · reactivate | Reactivate same identity | STU-04 |
| D-S4 | What can a school delete | Nothing · drafts only · anything | Archive only; hard delete via platform runbook | Data policy |
| D-AC1 | Rollover rules | Auto-promote all · per-class map · manual | Per-class map + exceptions list | STU-01 |
| D-AC2 | Closed-year writes | Blocked · allowed with permission | Blocked; reopen requires Owner + audit | STU-02 |
| D-AC3 | Terms | Introduce Term | Yes | ACD-01 |
| D-AC4 | Electives/streams | Not in v1 · v2 | Not in v1 if D-B1 = K–10 | ACD-01 |
| D-E1 | Report-card template | Per-exam · cumulative | Take from pilot school | EXM-01 |
| D-E2 | Result visibility | Immediate on publish · scheduled | Scheduled release | EXM-02 |
| D-D4 | Migrate history? | None · last year · all | Opening balances + current-year only | MIG-01 |

## Finance
| ID | Decision | Options | Recommendation | Blocks |
|---|---|---|---|---|
| D-F1 | Invoice cadence | Manual · bulk by installment · scheduled | Bulk by installment with preview | FEE-04 |
| D-F2 | Refund/waiver approvals | Single · two-person | Two-person above threshold | FEE-03 |
| D-F3 | Concession model | Ad-hoc · policy (sibling/staff/scholarship) | Small policy table | FEE-08 |
| D-F4 | Receipt & day-close | Print-styled · PDF; void rule; day lock | Print now; day-close in MVP | FEE-05/06 |
| D-F5 | Gateway money model | Shared account · per-school · aggregator | Per-school credentials [R: verify] | TEN-03 |
| D-F6 | Carry-forward | Auto · manual | Auto with review | FEE-09 |
| D-F7 | Online payment in MVP | Yes · after | After | — |

## Communication / mobile
| ID | Decision | Options | Recommendation | Blocks |
|---|---|---|---|---|
| D-N1 | Channels & who pays | Email+push · +SMS · +WhatsApp | Email + push; SMS only where a school requires it [R] | NOT-03/06 |
| D-N2 | Languages | English · +Hindi · +regional | English + one regional, templates by locale | NOT-04 |
| D-N3 | Default-notified events; critical override | List | Absence, fee due, results, notices; critical ignores opt-out | Edge #38 |
| D-N4 | In-app chat | Off · guardian→class teacher only · open | Off for MVP | SEC-19 |
| D-M1 | Mobile scope/stack | Expo/RN (current) · Flutter | Keep Expo; parent-first, then teacher | MOB-01 |
| D-M2 | Offline | Read cache · teacher queue | Both, teacher queue with idempotency | §9 |

## Data / platform / ops
| ID | Decision | Options | Recommendation | Blocks |
|---|---|---|---|---|
| D-D1 | Retention periods | Set per data class with counsel [R] | Draft policy, then verify | §12.4 |
| D-D2 | Storage & residency | India region? provider | Verify requirement [R] | SEC-08 |
| D-D3 | Tenant deletion/export policy | Manual · self-service | Manual runbook | PLT-03 |
| D-CP1 | Store national IDs (Aadhaar/APAAR/PEN)? | No · yes with controls | No until requirement verified [R] | STU-08 |
| D-CP2 | Consent & child-data handling | Policy | Counsel review [R] | §10 |
| D-PL1 | Plan limits dimension | Students · users · storage | Students | PLT-01 |
| D-PL2 | Mandatory vs optional modules | Core = org, identity, academic, student, attendance, fees, comms; others toggled | As stated | PLT-01 |
| D-PL3 | Offboarding | — | 30-day grace then export + delete | PLT-03 |
| D-PL4 | Support model | Email · in-app · impersonation | In-app ticket + audited time-boxed access | PLT-04 |
| D-PL5 | Suspension behaviour | Hard block · read-only · grace | Read-only after grace | SEC-03 |
| D-PL6 | Trial | Days, limits | 14 days exists; enforce | SEC-03 |
| D-PL7 | Billing collection | Manual invoice · gateway | Manual for pilots | PLT-02 |
| D-O1 | Hosting & region | — | Decide before PLT-08 [R] | PLT-08 |
| D-O2 | Backup RPO/RTO promise | Doc says ≤15 min / ≤4 h | Only promise what is automated and drilled | PLT-06 |
| D-O3 | Support hours/SLA | — | Business decision | PLT-04 |

## Module scope and second-pass decisions
| ID | Decision | Options | Recommendation (proposal) | Blocks |
|---|---|---|---|---|
| D-MOD1 | Which non-core modules are visible to the first school | All on · core only, others behind a per-school switch · remove | Core only; Purchase, Sales, Learning quizzes/leaderboards/rewards and Payroll off by default (extends D-PL2) | SAL-*, PUR-*, LMS-03/04/13/14/15, PAY-* |
| D-MOD2 | Do store sales and purchases touch the finance ledger | Stand-alone with a day-end cash book (today) · post to the student ledger · remove Sales | Ask the pilot whether it sells uniforms or books; if not, hide | SAL-01, SAL-09, PUR-11 |
| D-PAY1 | Payroll scope | Not in MVP (salary sheet export) · calculation with proration, LOP and paid status · full statutory [R] | Hide until the pilot confirms it runs payroll in Operion; then fix PAY-01 to PAY-06 first | PAY-01/03/07/20/23 |
| D-LMS1 | Who takes a quiz | Guardian on behalf (today) · teacher-supervised · student (needs MOB-03) | Practice only, never ranked | LMS-04, LMS-14 |
| D-E3 | Parent-visible rankings of children | Never · opt-in per school · always | Opt-in per school, tied to the ranking setting and published results | LMS-02, LMS-15 |
| D-CP3 | Consent: record only or enforced | Record only · enforced gate in notification dispatch | Label as record-only now; enforce once D-CP2 is answered [R] | REG-01 |
| D-CP4 | Fulfilling erasure and subject-data requests | Manual with a structured outcome · per-module anonymise | Manual with a structured outcome first [R] | REG-02, REG-03, LMS-16 |
| D-CP5 | Default response period for data-subject requests | Fixed 30 days (today) · organisation setting, empty by default | Organisation setting [R] | REG-14 |
| D-CRM1 | Platform CRM | Spreadsheet (freeze) · keep lead-to-school convert only · full CRM | Keep convert, fix CRM-01 to CRM-03, freeze the rest | CRM-01 |

---

# 21. Defer / Remove / Simplify

*"Freeze" = keep what exists, invest no further until §22 "Must have" is done. Nothing here recommends deleting working code.*

| Item | Why it may be unnecessary now | Alternative | Timing |
|---|---|---|---|
| **Learning / LMS-lite** (quizzes, answer-sheet marking, leaderboards, rewards, teacher board; ~71 Java files) | Built before RFID, bulk invoicing, promotion, PDF or a working parent invite; no verified school demand [A]. Second pass: quizzes are taken by parents (LMS-04), leaderboards bypass the ranking setting (LMS-02), files are public (LMS-01) | Keep homework, materials, lesson-plan calendar. Hide quizzes, leaderboards, rewards and teacher board (D-MOD1); hold answer sheets until LMS-01 and LMS-12 | Fix LMS-01 and LMS-02, then freeze |
| **Platform CRM** (leads, contacts, tasks, metrics) | Sales tooling before tenant billing, support and suspension work. Second pass: convert is non-atomic (CRM-01), login unguarded (CRM-02) | A spreadsheet for the pipeline; keep the lead-to-school convert | Freeze; fix CRM-01 to CRM-03 only (D-CRM1) |
| **Regulatory export framework / transfer hand-off log** | Empty by design; no region layout. Second pass: the builder is safe and the module is committed, but consent is unenforced and erasure is status-only | Keep opt-in; label the privacy console "record only" (D-CP3) | Defer layouts until a customer names the exact form [R]; fix REG-01, REG-03 wording |
| **In-app chat (STOMP)** | Moderation/child-safety risk, ungated (SEC-19), no product decision | Announcements + guardian→class-teacher message later | Restrict/disable for MVP (D-N4) |
| **Saved-SQL report authoring by school roles** | Non-technical roles given a SQL editor; only the DB user protects tenants | Canned parameterised reports; SQL for platform staff only | Simplify now |
| **Sales / Purchase orders / Assets** (school shop, PO approvals) | Heavy for a 500 to 2,000 student school [A]; not the reason schools buy. Second pass: no refund or void, overpayment accepted, outside the fee ledger (SAL-01 to SAL-03), PO receive can hit the wrong order (PUR-01) | Keep Inventory basics; hide Purchase and Sales behind a module switch unless a pilot already uses them (D-MOD1, D-MOD2) | Hide; do not extend |
| **Payroll** | No statutory calculation by design and no proration (PAY-01); Indian schools often run payroll elsewhere [A] | Salary-sheet CSV export | Hide until D-PAY1; if used, PAY-01 to PAY-06 first |
| **ID-card layout studio** | Free-form designer before a print pipeline exists | 2–3 fixed layouts + bulk PDF | Simplify with STU-07 |
| **Per-org Razorpay credentials UI / platform payment gateway** | Premature before D-F5 and a second paying school | Decide model; no UI yet | After D-F5 |
| **WhatsApp sender, SMS DLT plumbing** | Requirement not established (D-N1) | Push + email first | Defer |
| **Live GPS transport tracking** | Deferred by decision; lat/long columns only | — | Keep deferred |
| **Redis / Kafka / microservices / search engine** | Scale (≈1 tap/s, ≤ ~1.5M attendance rows in 3 yrs) doesn't need them | MySQL + indexes | No |
| **Multi-industry (healthcare) generalisation** | Boundary test is good; further abstraction is speculative before first school | Keep ArchUnit rule only | No further work |
| **Custom fields for entities other than Student** | Only STUDENT is wired | Leave | No |
| **Per-button RBAC retrofit everywhere** | 666 buttons | Route-level guards + destructive actions only | Simplify (AUTH-05) |
| **Excess settings surface (22 sections)** | Each is a support question | Hide advanced ones behind role; default well | Simplify |

---

# 22. MVP Boundary

Basis: operational necessity and dependency, not preference. IDs point to §3.

## 22.1 Must have before the first school
**Security & isolation:** SEC-01, SEC-02, SEC-05, AUTH-02 (default-deny), SEC-04, SEC-06, SEC-07 (verify on MySQL), SEC-08 (private files), SEC-09 (reset email), SEC-11 (platform MFA), TEN-01, SEC-14, SEC-03 (enforcement).
**Ops:** PLT-06 (automated backups + one restore drill, DB + uploads), PLT-08 (one proven prod deployment), PLT-07 (MySQL migration CI), PLT-09 (minimal: JSON logs with org, error tracking, job heartbeats).
**Daily workflows:** FEE-04 (bulk invoices), FEE-05 (daily collection/defaulters/day-close), FEE-06 (receipt print), FEE-01/02/03/07/10 (money integrity), STU-01 (exit/reassign/promote UIs now; rollover before first year-end), STU-03 (exit checklist), STU-05 (dedupe), STU-06 (TC/bonafide per pilot template), ATT-02/04/06 (scoped marking, real alerts, teacher on portal/mobile), EXM-01/02 (pilot report card + validation), AUTH-01 (section scope), AUTH-06 (staff exit cascade), RPT-03 (one PDF path), RPT-01 (minimal canned set), RPT-04 (paging on top lists), UX-01/02 (confirm + shared list).
**Onboarding:** MIG-01 (validate-only, history, dedupe for students), NOT-06 (bulk guardian invites with delivery), NOT-02 (retry/dead-letter), NOT-05.
**Mobile:** MOB-01/02 for parent (attendance, fees, notices) and teacher attendance; NOT-01 push.
**Module minimums (only for modules switched on for the first school; otherwise hide the module, D-MOD1):** Payroll PAY-01..06 · Library LIB-01/02/03/05/06/08/11/14 · Sales SAL-02..05 · Purchase PUR-01 · Inventory INV-01 · Learning LMS-01/02 · Platform CRM CRM-01/02/03 · Privacy console labelled record-only (REG-01).
**Decisions before build:** D-MOD1, D-MOD2, D-PAY1, D-B1..B3, D-A1..A5, D-R1, D-F1..F5, D-E1, D-N1, D-AC1, D-PL5.

## 22.2 Should have shortly after launch
RFID (if D-R1 = phase 2) with §8 design; ATT-03/05; SEC-12/13/15/16/17/19; AUTH-03 split permissions, AUTH-05; TEN-02 (locking before second instance); PLT-03/04 (offboarding runbook, tenant support), PLT-01 (limits/toggles); STU-02/04/09/10, ACD-01 (Terms), FEE-08/09; EXM-03; TRN-02; NOT-04 (locale); online payments (per-school gateway); Guardian portal completions (timetable, transport, history).

## 22.3 Can wait
Student login (upper grades); ATT-07; TRN-01 push events; billing automation (dunning, gateways); platform audit hardening TEN-04; Parent-teacher messaging (if D-N4 allows); hall tickets/re-evaluation/supplementary; timetable substitutions; electives/streams (if K–12); XLSX exports.

## 22.4 Do not build yet
More LMS/leaderboard features · CRM extensions · statutory payroll · purchase payables · quiz supervision modes · region-specific regulatory templates or live government connectors · WhatsApp/SMS expansion beyond an established requirement · GPS live tracking · accounting-system connectors · biometric devices · Redis/Kafka/microservices/search engine · per-field ACLs/full ABAC · payroll statutory engines · further multi-industry abstraction.

---

# 23. Product Readiness Matrix

Facts only: status labels, no scores. Second pass (2026-09-30) replaced "Needs validation" for the eight modules; all statuses are code-verified, not executed. Academic and Transport keep "Needs validation" for their thin tests.

| Area | Completeness | Risk | Major gap | MVP status |
|---|---|---|---|---|
| Organisation | Partial | Production risk | Status not enforced; non-atomic provisioning; no limits/toggles | Needs work |
| Identity | Partial | **Production risk** | Global-user IDOR; revocation ineffective; reset unusable; no MFA | **Blocking** |
| Authorization | Partial | **Production risk** | Default-allow; no data scope; coarse permissions | **Blocking** |
| Academic | Partial | Needs validation | No terms/rollover/year guards; thinnest tests | Needs work |
| Students | Partial | Production risk | No dedupe/certificates/rollover/exit UI; #354 | Blocking (#354, exit/dedupe) |
| Staff | Partial | Medium | Exit cascade; staff attendance UI | Needs work |
| Attendance | Partial | Production risk | Not scoped, no source/dedupe, static alerts | Needs work |
| RFID | **Not started** | — | Entire subsystem | Decision (D-R1) |
| Examinations | Partial | Medium | Per-exam card, no validation, no PDF | Needs work |
| Finance | Partial | **Production risk** | Bulk invoice, receipts, day-close, race safety, refunds | **Blocking** |
| Transport | Partial | Needs validation | Manual boarding, no alerts | Acceptable for MVP |
| Communication | Partial | Production risk | No retry, no push, no variables | Needs work |
| Library | Partial | Production risk | Fines not collectable in UI, copy dead ends, double issue, no audit (LIB-01/03/05) | Needs work |
| Inventory | Partial | Medium | Unlocked stock check, no item edit, no audit (INV-01/11) | Needs work |
| Purchase | Partial | Medium | Wrong-line receive, no approver, no PO number/total (PUR-01/02/03) | Hide or freeze (D-MOD1) |
| Sales | Partial | **Production risk** | No refund/void, overpay, separate from fee ledger (SAL-01/02/03) | Hide or freeze (D-MOD1, D-MOD2) |
| Payroll | Partial | **Production risk** | No proration; correction drops people; unaudited adjustments (PAY-01/02/05) | Needs work; hide until D-PAY1 |
| Learning | Partial | Production risk | Public files, rank bypass, quiz not correctable (LMS-01/02/03) | Needs work; extras hidden (D-MOD1) |
| Platform CRM | Partial | Production risk | Non-atomic convert, no login lockout, default secrets (CRM-01/02/03) | Needs work; else freeze |
| Regulatory / privacy | Partial (record-keeping) | Medium | Consent unenforced, erasure status-only (REG-01/02) | Label honestly; keep opt-in |
| Reporting | Partial | Medium | 4 canned reports; no PDF/XLSX; unpaged exports | Needs work |
| Mobile | Partial (admin prototype) | **Production risk** | No parent/teacher flow; token storage; no push | **Blocking** (if in scope) |
| Platform | Partial | Production risk | Suspension, MFA, offboarding, support | Needs work |
| Billing | Partial (manual) | Low for pilots | No automation/limits | Acceptable for pilots |
| Security | Partial | **Production risk** | See §10 | **Blocking** |
| Observability | Missing/Partial | Production risk | No metrics/error tracking/tenant logs | Needs work |
| Onboarding | Partial | Medium | Manual guardian invites; template gaps | Needs work |
| Data migration | Partial | Production risk | Student import weakest; no dedupe/undo/verification | Needs work |

---

# 24. Recommended Next Actions

Sizes: S ≈ days, M ≈ 1–2 weeks, L ≈ several weeks (relative, not estimates).

| Phase | Action | Closes | Size |
|---|---|---|---|
| **0 — Hotfix (do first, small, testable)** | Scope `UserController` mutations and org-status endpoint; gate `PersonController`; tenant-scope `student_id`; check user/membership on refresh + revoke tokens on disable; enforce org status; add a generic "foreign-tenant token × every endpoint" test | SEC-01/02/03/04/05, TEN-01, AUTH-02 | M |
| **0b — Deployment safety** | Refuse default secrets outside dev; drop seeded admin password; reporting datasource must be `reporting_ro` (verify on MySQL); private uploads; reset-password email; nginx headers | SEC-06/07/08/09/14 | M |
| **1 — Decision session** | Run §20 "Must" decisions with the pilot school (D-B1..B3, A1..A5, F1..F5, E1, N1, R1, AC1, PL5, MOD1, PAY1) | unblocks Phase 2–4 | S |
| **2 — Money & daily loops** | Bulk invoicing; receipt print; day-close & collection/defaulter reports; payment lock/idempotency; refund approvals; duplicate-assignment guard; number-format validation | FEE-01…10 | L |
| **2b — Student lifecycle** | Exit/reassign/promote UIs; exit checklist + cascades; dedupe; certificates; year-guard; rollover wizard | STU-01…06, AUTH-06 | L |
| **2c — Attendance & notices** | Teacher-scoped marking (portal+mobile), backdate rule, template variables + dedupe, retry/dead-letter, failed-notification view, guardian invite delivery | ATT-02/04/06, NOT-02/05/06 | L |
| **2d — Cross-cutting infra** | One PDF path; paging/indexes for top lists; shared list + confirm components | RPT-03/04, UX-01/02 | M |
| **2e — Module minimums (only for modules switched on, else hide)** | Payroll proration, LOP, correction carry-forward, audited adjustments and bank changes; library fine list, copy lock, picker, status transitions; sales refund, overpay cap, item price; PO line ownership check; stock row lock; private learning files and ranking gate; CRM atomic convert and login lockout; label consent as record-only | PAY-01…06, LIB-01…06, SAL-02…05, PUR-01, INV-01, LMS-01/02, CRM-01…03, REG-01 | L |
| **3 — Ops** | Automated backups + restore drill; prod deployment reference; MySQL migration CI; JSON logs+org, error tracking, job heartbeats | PLT-06/07/08/09 | M |
| **4 — Mobile (parent-first)** | SecureStore, refresh, `/me/**`, child switcher, push, deep links; then teacher attendance | MOB-01/02, NOT-01 | L |
| **5 — RFID** | Per §8 once D-R* answered: card/device/event tables → ingestion → derivation → admin queues → notifications | RFID-01, ATT-01 | L |
| **6 — Platform** | Limits/toggles, offboarding runbook, tenant support/billing pages, MFA for admins | PLT-01/02/03/04, SEC-11 | M |
| **Housekeeping** | Update stale docs (backup doc, mobile brief, V24 comment); freeze §21 items | C-13, C-15 | S |

---

# 25. The Most Important Question

> **If Operion were deployed to a real 1,000-student school next month, what are the 10–20 things most likely to cause operational problems, user frustration, data problems, security issues, or implementation failure?**

Ordered by likelihood × damage. Each is backed by evidence above (`[V]` = code-verified).

| # | What goes wrong | Category | What the school sees | Evidence |
|---|---|---|---|---|
| 1 | **Parents can't get in.** Each guardian invite is a manual copy of a token plus a separately-told org slug; reset emails are never sent; the mobile app has no parent screens | Adoption | Front-desk spends days sending links; parents call the office; "the app doesn't work" | NOT-06, SEC-09, MOB-01/02 |
| 2 | **A parent's token can list everyone's phone/email/address** (ungated `PersonController`); messaging lets any member DM anyone | Security/privacy | A parent notices they can see other families' details → trust loss, possible legal exposure [R] | SEC-05, SEC-19, AUTH-02 |
| 3 | **Removed people keep access.** Fired teacher, disabled user, exited student's guardian; JWT + 30-day rolling refresh; exit cascades nothing | Security | Ex-staff can still message/see data; left students still get reminders | SEC-04, AUTH-06, STU-03 |
| 4 | **Data loss.** No automated backups, no restore drill, uploads on an unmounted container disk, secrets key loss = unreadable secrets | Data | One host/container mishap loses documents and possibly the DB | PLT-06, PLT-08 |
| 5 | **The fee season overwhelms the accountant.** ~4,000 manual invoice generations, no receipt print, no day-close/defaulter report | Operations | Accountant reverts to Excel; trust in Operion's numbers falls | FEE-04/05/06 |
| 6 | **Money integrity errors.** Concurrent/duplicate payments, overpayment, refund above paid, duplicate fee assignment, receipt-number collision from a format edit | Data | Invoice balances that don't add up, disputes with parents | FEE-01/02/03/07/10 |
| 7 | **Year-end arrives with no rollover.** No bulk promotion, no UI for single promotion/exit/section change, closed year still writable, no dues carry-forward | Implementation | Admin re-keys every student and balance; data written to the wrong year | STU-01/02, FEE-09 |
| 8 | **Attendance data is unreliable.** Any teacher can mark any class/date; half-marked registers; % ≠ the school's register; absence email lacks the student's name and can repeat | Data/UX | Parents get useless or duplicate alerts; principals distrust reports | ATT-02/04/05 |
| 9 | **RFID expectation gap.** The school expects card tap = attendance; nothing exists and the model can't hold events | Business | The headline feature isn't there or arrives as a bolt-on | RFID-01, ATT-01 |
| 10 | **Notifications silently die.** One attempt then FAILED forever; no push; static bodies; failures invisible | Operations | "Reminders never arrived"; nobody can tell why | NOT-01/02/05 |
| 11 | **Dirty onboarding data.** Student import has no dry run, no dedupe, doubles on re-run; each guardian row makes a new Person; no photos/RFID/marks import; no verification report | Data | Duplicate students/guardians from day one; siblings unlinked | MIG-01, STU-05 |
| 12 | **The report card isn't "the report card".** Per-exam only, no aggregation/co-scholastic, marks can exceed max, no PDF | Product | Principal rejects it; teachers correct typos after parents see results | EXM-01/02, RPT-03 |
| 13 | **First transfer/leaving certificate request has no tool** | Product | Admin makes it in Word, outside the system's numbering/audit | STU-06 |
| 14 | **Second school breaks the first assumption.** `student_id` global unique; cross-tenant user/org endpoints; shared payment account | Security/Business | Second onboarding fails or worse: one school can affect another | TEN-01, SEC-01/02, TEN-03 |
| 15 | **Defaults ship as secrets.** Default JWT/integration keys, seeded platform admin password, root DB, reporting datasource as root | Security | A missed env var = full compromise | SEC-06/07/11 |
| 16 | **Nobody can diagnose a school's problem.** No org in logs, no error tracking/metrics, CI never runs migrations on MySQL (two prod-only bugs already found by hand) | Operations | Slow, guess-based support; regressions reach prod | PLT-07/09 |
| 17 | **Performance cliffs at 2,000 students / year 2–3.** Unpaged `/students` `/persons`, `LIKE '%x%'`, EAGER chains, unbounded audit/notification tables | Performance | Screens that were fast in the pilot slow down after a term | RPT-04, §12.4 |
| 18 | **Everyone is an admin.** Fresh school = one Owner; no data scope; coarse permissions; principals hold SQL authoring; teachers see buttons they can't use | Security/UX | Segregation of duties fails; accountant/principal boundaries blur | AUTH-01/03/04/05, AUTH-07 |
| 19 | **No way to ask for help.** Tenants can't raise tickets; password reset needs log access; no support-access model | Support | School emails the founder directly | PLT-04, SEC-09 |
| 20 | **Roadmap drift.** LMS-lite, CRM, regulatory and chat were built ahead of the daily loops above, and the second pass found them unsafe to switch on as they are (payroll overpays silently, store sales cannot be refunded, learning files are public links) | Process | The pilot demo shows quizzes and leaderboards while the accountant lacks receipts | §21, PAY-01, SAL-02, LMS-01 |

**Read across:** items 1–4 and 14–15 are *stop-ship*; 5–13 are *first-term survival*; 16–20 determine whether the team can *keep* the school. Items 2, 3, 14 and 15 are small, well-bounded fixes relative to their impact — start there.

---

# 26. Change Log

| Date | Change | By |
|---|---|---|
| 2026-09-30 | Initial audit created from five read-only reviews of `main` @ `1e6503d` | Claude (audit) |
| 2026-09-30 | Added §2.1 feature inventory (plain-language table of what exists, with status and limits); subsections 2.2–2.4 renumbered | Claude (audit) |
| 2026-09-30 | Second pass: deep audit of Library, Inventory, Purchase, Sales, Platform CRM, Learning, Payroll, Regulatory/Privacy. Added §3.8–3.14 (171 gaps), module rows in §4, matrix rows in §23, module decisions in §20, module minimums in §22.1 and §24 phase 2e; updated §1, §2.1, §21, §25, Appendix A | Claude (audit) |

**How to keep this alive.** When a gap closes, change its **State** in §3 and add a change-log line (with commit/PR). When a decision is made, move it from §20 into a "Decided" table with date and owner, and re-run §19 against it. When code that an evidence tag depends on changes, downgrade the tag to `[U]` until re-verified. Re-audit the "not audited in depth" modules and the `[U]` items first.

---

# Appendix A — Evidence Index (verified by reading, not executed)

Paths under `src/main/java/com/operion/` unless prefixed. Line numbers are from the audit date and will drift.

| Gap | Evidence |
|---|---|
| SEC-01 | `identity/api/UserController.java:91-119` (only `GET` checks org at 91-99); `identity/UserService.java:24-42`; `identity/StaffInviteService` claim/resend (~84-96, approximate) |
| SEC-02 | `organisation/api/OrganisationController.java:46-51`; `organisation/OrganisationService.changeStatus` (audit ~158) |
| SEC-03 | `identity/auth/AuthenticationService.java:40-84, 117-135`; `identity/auth/JwtAuthenticationInterceptor.java:30-55`; four `@Scheduled` classes loop `organisationRepository.findAll()` |
| SEC-04 | `AuthenticationService.java:123-124`; `UserService.java:24`; `authorization/MembershipService.java` revoke (~83-99); `RefreshTokenService.java:21,45-46` |
| SEC-05 | `identity/api/PersonController.java:31-73` (`findAll` at 45); `src/main/resources/db/migration/V24…` comment lines 11-15 |
| SEC-06 | `src/main/resources/application.properties:2,19,28,53`; `V25__billing_schema.sql:81-82` |
| SEC-07 | `application.properties:58-66`; `V55` (16-17), `V58`; `reporting/SqlGuard.java:17-41`; `reporting/ReportExecutionService.java:57-66` |
| SEC-08 | `common/WebConfig.java:54-58`; `storage/LocalDiskAssetStorageService.java:25-27,43-51`; `storage/api/AssetUploadController.java:28-32` |
| SEC-09 | `identity/auth/PasswordResetService.java:20-27,65,78`; `EmailVerificationService.java:23-29,67` |
| SEC-13 | `student/InquiryRateLimiter.java`; `student/api/PublicAdmissionInquiryController.java:67` |
| SEC-18 | `hr/HrService.java:219-224` |
| SEC-19 | `messaging/ws/StompAuthenticationInterceptor.java:71-82,95-101`; `messaging/api/MessagingController.java:99-100` |
| TEN-01 | `V80__student_id_medical_alerts_applicant.sql:18`; `student/StudentIdGenerator.java:27-33` |
| TEN-02 | `communication/NotificationDispatchWorker.java:21-48`; `common/SchedulerHeartbeat.java` |
| ATT-* | `attendance/AttendanceService.java:49-143`; `V7__attendance_schema.sql`; `attendance/AttendanceAlertService.java:45-61`; `communication/CommunicationService.java:153,177-181`; `attendance/api/StudentAttendanceController.java:31,57-74` |
| NOT-02 | `communication/NotificationDispatchService.java:33-48`; `communication/api/NotificationDeliveryWebhookController.java` |
| FEE-01/02/03 | `finance/FeeService.java:72,212-224,233,246-280`; `finance/FeePaymentGatewayService.java:122-153`; `finance/FeeApproverPolicy.java:20`; `finance/StudentFeeAssignment.java:56` |
| FEE-10 | `organisation/api/UpdateOrganisationBrandingRequest.java`; `organisation/DocumentNumberFormatter` |
| STU-* | `student/StudentService.java:60-85,127-131,165-212`; `student/Student.java:147-165`; `student/ApplicantService.java:66-79`; `parent/ParentService.java:32-45`; `organisation/AcademicYearService.java:33-39`; `organisation/AcademicYear.java:49-53` |
| EXM-* | `examination/ExaminationService.java:196-245`; `examination/api/ReportCardController.java:72-84` |
| AUTH-* | `authorization/PermissionInterceptor.java:35-64`; `authorization/OrganisationMembershipRepository.java:33-40`; `hr/HrService.java:115`; `identity/api/PersonController.java`; `web/src/layout/AppLayout.tsx:85-190` |
| PLT-* | `Dockerfile`; `docker-compose.yml`; `.github/workflows/ci.yml`; `docs/setup/backup-and-recovery.md`; `application.properties:6`; `platform/auth/PlatformAuthenticationInterceptor.java:44`; `billing/BillingService.java:130,195,211` |
| RPT-* | `reporting/*`; `common/CsvUtil.java`; `student/api/StudentController.java:80,145`; `hr/api/StaffProfileController.java:200`; `web/src/utils/csv.ts` |
| MOB-01 | `operion-mobile/api/session.ts`, `api/client.ts`, `screens/MoreScreen.tsx:43-50`; `identity/auth/api/AuthController.java:63` |
| MIG-01 | `common/imports/*`; `student/StudentImportService`, `StudentRowImportService`; `parent/GuardianRowImportService.java:70-90`; `academic/StudentEnrolmentRowImportService.java:76` |
| UX-* | `web/src` grep counts: `<Can>` 45 files-usages vs 666 buttons; 3 files with `TablePagination`; 0 Skeleton; 1 `window.confirm` (platform CRM) |

## Second-pass evidence (highest-stakes items)

| Gap | Evidence |
|---|---|
| PAY-01, PAY-02 | `payroll/PayrollService.java:19-24,88,140-145,156-173`; `payroll/SalaryService.java`; `portal/PayslipPortalService.java:47-48` |
| PAY-04 | `payroll/PayrollService.java:70-77,125-154`; `V117__payroll_runs.sql:5,23` |
| PAY-05, PAY-06 | `payroll/PayrollService.java:91-108`; `hr/HrService.java:130-139`; `hr/api/StaffProfileController.java:126-138` |
| LMS-01 | `common/WebConfig.java:50-58`; `storage/LocalDiskAssetStorageService.java:25-27,43`; `learning/api/AnswerSheetResponses.java:47` |
| LMS-02 | `learning/LeaderboardService.java:208-225`; `learning/api/ChildLearningController.java:133-137`; `examination/ExaminationService.java:258-261` |
| REG-01, REG-02, REG-03 | `privacy/` package (no references outside it); `privacy/DataSubjectRequest.java:19-24,93-96`; `privacy/DataSubjectRequestService.java:69-100`; `communication/NotificationPreference.java:18-20` |
| SAL-01, SAL-02, SAL-03 | `sales/PaymentMethod.java:3-7`; `sales/SaleService.java:40-74`; `sales/Sale.java:64,72-75` |
| PUR-01 | `purchase/api/PurchaseOrderController.java:95-97,106,125-127`; `purchase/PurchaseOrderService.java:75-85` |
| INV-01 | `inventory/InventoryService.java:85-89,144-163` |
| LIB-01, LIB-03 | `web/src/modules/library/BorrowPanel.tsx:64,141-147,272-315`; `library/LibraryService.java:66-90`; `V17__library_schema.sql:66` |
| CRM-01, CRM-03 | `organisation/OrganisationService.java:75-116`; `platform/crm/LeadService.java:67-98`; `application.properties:28`; `V25__billing_schema.sql:79-82` |
