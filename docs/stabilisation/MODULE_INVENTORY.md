# Module inventory — stabilisation phase (2026-10-21 → 2026-11-30)

Feature freeze: bug fixes, security fixes, sanity automation and disabling unfinished work only.
Audience for go-live: **principal and school staff, website only**. No mobile app, no parent/student logins.

"Default" = whether a new school gets the module switched on (`AppModule.defaultEnabled()`).
"Sanity" = which phase in `operion-automation/tests/sanity/` walks it end to end.
"API/UI" = test counts in `operion-automation/tests/api|ui/<module>`.

## Core — keep on for go-live

| Module | Backend packages | Screens | Default | Sanity | API / UI tests | Notes |
|---|---|---|---|---|---|---|
| Platform console | platform, billing, backup, support | platform console | n/a | 01, 08b, 13 | 9 / 13 | Seeded platform admin password must be rotated before go-live |
| Organisation & setup | organisation, onboarding, customfield, calendar | setup wizard, settings | on | 01–03 | 65 / 24 (+ settings 77 / 32) | |
| Members, roles, auth | identity, authorization, audit | settings > users/roles, login | on | 04 | 66 / 21 (+ auth 46 / 26) | Role design for go-live is a separate task (item 3) |
| Dashboard | dashboard | /dashboard | on | 01–06 | 32 / 17 | |
| Students & admissions | student, parent, certificate | /students, /admissions | on | 06, 07, 14 | 119 / 34 + 52 / 16 | #354 (student ID per school) likely fixed by school/V6 — confirm |
| Academics & timetable | academic, timetable | /academics | on | 03, 08 | 167 / 27 | Academic module has thin unit-test coverage |
| Attendance | attendance | /attendance | on | 09 | 77 / 21 | |
| Examinations & results | examination | /examinations | on | 10, 11 | 112 / 28 | |
| Fees | finance | /fees | on | 05, 07 | 189 / 38 | Online payment (Razorpay) never tested against the real API — disable for go-live |
| Communication | communication, email | /communication | on | 12 | 3 / 1 | Very thin API/UI tests; only sanity 12 covers it |
| HR | hr | /hr | on | 04 | 95 / 34 | |
| Reports | reporting | /reports | off | 13 | 111 / 40 | Principal-facing; consider switching on by default |
| Promotion / year closure | student, academic | /students/promotion | — | 14, 15 | in students | Withdrawal has no screen |
| Documents (PDF receipts, report cards, certificates) | document, certificate | inside fees/exams/students | — | — | certificates UI only | Built 2026-10-20/21; no sanity phase yet |

## Optional — decide per school (off by default)

| Module | Default | Sanity | API / UI tests | Suggestion |
|---|---|---|---|---|
| Library | off | — (switched on in 02 only) | 56 / 16 | Keep available; add to sanity if a launch school wants it |
| Payroll | off | — | 47 / 26 | Keep available, off by default |
| Inventory | off | — | 71 / 10 | Keep off for go-live |
| Purchase | off | — | 36 / 15 | Keep off |
| Sales | off | — | 35 / 16 | Keep off |
| Performance (teacher KPIs) | off | 08b | 15 / 45 | Built 2026-10-17; keep off until stable |

## Disable until after the freeze

| Module / area | Why |
|---|---|
| Learning (homework, quizzes, answer sheets) | Guardian-facing; no parent/student logins at go-live |
| Messaging | 1 automation file only |
| Transport | GPS deferred; only light coverage (6 files mention it); no API/UI folder |
| Regulatory & privacy (Compliance) | No regional templates; 1 automation file |
| Guardian / teacher portal (`/portal/**`) | Not in go-live audience (website for principal + staff). Teachers sign in to the admin shell with a Teacher role. |
| Online fee payment (Razorpay) | Never exercised live; shared-account compliance question unresolved |
| SMS / WhatsApp senders | 1 automation file each; no live provider accounts |

## Known issues already found (feed into the bug list)

1. **#354** — student ID unique key per school. The school-DB migration `school/V6__students_student_id_unique_per_organisation.sql` already re-keys it to `(organisation_id, student_id)`; likely fixed, confirm on a fresh DB and close the issue.
2. `web/src/modules/__pp/` — unreferenced stale copy of the Examinations pages (7 files), committed in `4525ea0`. Cleanup, low.
3. Platform admin seed password `ChangeMe123!` — `PlatformAdminPasswordBootstrap` rotates it at startup only if `PLATFORM_ADMIN_PASSWORD` is set; there is no screen to change it. Go-live checklist item: set the variable in production. Whether startup refuses to run in production while the seed is still active is unverified. Security, high.
4. `FEE_DISCOUNT_APPROVE` seeded but unused (discounts gated under `FEE_ASSIGNMENT_MANAGE`). RBAC, low — revisit in role design.
5. Communication has 3 API / 1 UI tests — coverage gap, not yet a known bug.
6. Last unified automation report (2026-10-20) was a partial certificates run: 7 passed / 10 failed of 17. Not representative; full fresh run is part 2.
