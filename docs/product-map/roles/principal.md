---
role: Principal
role_key: principal
kind: role
session: admin
person_type: staff
designations: [Principal, Vice Principal]   # guidance only. Designations are not linked to roles in the data. Vice Principal has no role of its own
permissions: [ACADEMIC_CONFIGURATION_VIEW, CLASS_VIEW, GRADE_LEVEL_VIEW, SUBJECT_VIEW, TEACHER_ASSIGNMENT_VIEW, TEACHER_ASSIGNMENT_MANAGE, WORKING_CALENDAR_VIEW, TIMETABLE_VIEW, TIMETABLE_MANAGE, STUDENT_VIEW, STUDENT_ENROLLMENT_MANAGE, STUDENT_TRANSFER_MANAGE, STUDENT_EXIT_MANAGE, ATTENDANCE_VIEW, ATTENDANCE_CORRECT, ATTENDANCE_LOCK, ATTENDANCE_UNLOCK, STAFF_ATTENDANCE_VIEW, EXAM_VIEW, EXAM_MANAGE, MARKS_APPROVE, MARKS_CORRECT_AFTER_PUBLISH, GRADING_SCALE_MANAGE, REPORT_CARD_PUBLISH, HR_VIEW, HR_LEAVE_MANAGE, GUARDIAN_VIEW, COMMUNICATION_VIEW, ANNOUNCEMENT_CREATE, ANNOUNCEMENT_PUBLISH, REPORT_VIEW, REPORT_CREATE, MEMBERSHIP_VIEW, CERTIFICATE_VIEW, CERTIFICATE_REQUEST, CERTIFICATE_APPROVE]
scoped_by: whole-org
how_created: starter-template
requires_steps: [academic-year, starter-template, members-and-roles]
ui_routes: [/settings/roles, /settings/designations, /settings/school-template, /members]
source: school/V1__school_baseline.sql:3101 (31 codes); school/V28__certificates.sql:134 (+3 certificate codes); school/V35__timetable_cover_role_grants.sql:23-30 (+2 timetable codes); onboarding/SchoolTemplateService.java:438-454
verified_on: 2026-11-11
verified_by_running_app: no
---

# Principal

> Runs the academic side of the school: approves marks, publishes report cards, manages exits and transfers, and oversees attendance and leave.

## What this person does
- Approves marks and publishes report cards.
- Corrects marks after they are published.
- Corrects, locks and unlocks attendance.
- Manages student enrolment, transfers and exits.
- Manages teacher assignments and the timetable.
- Reviews and approves leave; views staff attendance.
- Creates and publishes announcements.
- Approves certificate requests (certificates such as TC are issued by Administrator, which holds the issue permission).

## Who gets this role
- **Typical people / designations:** the Principal. A Vice Principal also needs access, but "Vice Principal" is a designation with no matching role in the template. The school must either give them this role or create one.
- **Role vs designation:** the designation "Principal" is the HR job title set in Settings → Designations. The role "Principal" controls what they can do. Setting one does not set the other.
- **How many people usually hold it:** usually one. Not enforced.

## How to create the login
1. Apply the starter template (Settings → School setup template, `/settings/school-template`). The role "Principal" does not exist until it is applied. The template runs the role step only if that item is included.
2. Create the person as staff (HR → new staff, `/hr/staff/new`) or add a member (`/members/new`).
3. Give them the "Principal" role. The person receives a staff invite to set their login (`identity/auth/StaffInviteService.java:69`).

A resigned or terminated staff member cannot be given access (`hr/StaffAccessService.java:95-107`).

## Permissions granted
36 codes. Meanings below are plain-language readings of the code names. They are NOT individually confirmed in the code unless a source is shown.

| Module | Permission code | What it allows |
|---|---|---|
| academic | `ACADEMIC_CONFIGURATION_VIEW`, `CLASS_VIEW`, `GRADE_LEVEL_VIEW`, `SUBJECT_VIEW`, `TEACHER_ASSIGNMENT_VIEW`, `WORKING_CALENDAR_VIEW`, `TIMETABLE_VIEW` | view academic structure, calendar and timetable (unverified meaning) |
| academic | `TEACHER_ASSIGNMENT_MANAGE`, `TIMETABLE_MANAGE` | change teacher assignments and the timetable (unverified meaning) |
| student | `STUDENT_VIEW` | see student records |
| student | `STUDENT_ENROLLMENT_MANAGE`, `STUDENT_TRANSFER_MANAGE`, `STUDENT_EXIT_MANAGE` | enrol, transfer and exit students (unverified meaning) |
| attendance | `ATTENDANCE_VIEW`, `STAFF_ATTENDANCE_VIEW` | see student and staff attendance |
| attendance | `ATTENDANCE_CORRECT`, `ATTENDANCE_LOCK`, `ATTENDANCE_UNLOCK` | correct, lock and unlock attendance (unverified meaning) |
| examination | `EXAM_VIEW`, `EXAM_MANAGE`, `GRADING_SCALE_MANAGE` | see and manage exams and the grading scale (unverified meaning) |
| examination | `MARKS_APPROVE`, `MARKS_CORRECT_AFTER_PUBLISH`, `REPORT_CARD_PUBLISH` | approve marks, correct after publishing, publish report cards (unverified meaning) |
| hr | `HR_VIEW`, `HR_LEAVE_MANAGE` | view HR and manage leave (unverified meaning) |
| parent | `GUARDIAN_VIEW` | see guardian details |
| communication | `COMMUNICATION_VIEW`, `ANNOUNCEMENT_CREATE`, `ANNOUNCEMENT_PUBLISH` | view, write and publish announcements |
| reporting | `REPORT_VIEW`, `REPORT_CREATE` | view and create reports (`REPORT_VIEW` has no Java reference found; unverified) |
| authorization | `MEMBERSHIP_VIEW` | see the member list |
| certificate | `CERTIFICATE_VIEW`, `CERTIFICATE_REQUEST`, `CERTIFICATE_APPROVE` | view, request and approve certificates (unverified meaning) |

Total permissions: 36 (31 in school `V1`, +3 in `V28`, +2 in `V35`).

## What they see in the app
Menu items follow the permissions above (`web/src/layout/AppLayout.tsx` hides groups the person lacks permission for). The exact menu has NOT been checked by logging in as a Principal.

## What they cannot do
Derived by comparing against the permission catalogue, not by trying it:
- **See or collect fees:** no `FEE_VIEW` or `FEE_COLLECT`.
- **Create roles or members:** no `ROLE_MANAGE` or `MEMBERSHIP_MANAGE` (view only).
- **Change organisation details:** no `ORGANISATION_MANAGE`.
- **Mark daily attendance or enter marks:** no `ATTENDANCE_MARK` or `MARKS_ENTER` (they correct and approve instead).
- **Edit student records or admit students:** no `STUDENT_MANAGE`.
- **Issue certificates:** approve only. `CERTIFICATE_ISSUE` sits with Administrator.
- **Manage library, transport or inventory.**

## Data scope
Whole school. No section limit applies, because attendance marking by teachers is the only scoped permission here and the Principal does not hold `ATTENDANCE_MARK` (`attendance/AttendanceAccessService.java:45-55`).

## Configuration that must exist first
- [ ] Academic year created → `steps/academic-year.md` (not written yet)
- [ ] Starter template applied, or the Principal role created by hand → `steps/starter-template.md` (not written yet)
- [ ] Staff or member created and invited → `steps/members-and-roles.md` (not written yet)
- [ ] Classes and sections exist, otherwise there is nothing to approve or correct

## Symptoms when it's missing
| What the person sees | Likely cause | Fix |
|---|---|---|
| "Principal" not in the role list | Starter template not applied, or the role item was excluded | Settings → School setup template, include the Roles item, apply |
| Fees menu missing | Role has no `FEE_VIEW` | Add it in Settings → Roles, if the school wants the Principal to see fees |
| Cannot approve marks | No `MARKS_APPROVE`, or marks were never submitted by the teacher | Check the role, then check the teacher submitted marks |
| Cannot give a colleague access | Only `MEMBERSHIP_VIEW` held | Owner or a role with `MEMBERSHIP_MANAGE` does it |
| Cannot give access to a staff member | Staff status is resigned or terminated | Reinstate staff first |

## Common mistakes
- Expecting the "Principal" designation to give Principal access. It does not. The role has to be assigned separately.
- Giving the Vice Principal the Principal role by default without checking the lock/unlock and publish permissions are wanted.
- Applying the template twice and assuming roles reset. The template skips roles that already exist (`SchoolTemplateService.java:438-452`), so edits are kept.

## Known gaps (not in the product yet)
- No Vice Principal role (audit AUTH-04).
- Principal has no fee visibility by default. Whether that is intended is a product decision, not a bug.
- Per-school role copies can differ from this list if someone edited the role after the template was applied.

## Screenshots
none yet

---
Maintenance: when the seed migrations, the Roles screen or the checklist change, update this page and set `verified_on`. Do not copy permission lists by hand from another page.
