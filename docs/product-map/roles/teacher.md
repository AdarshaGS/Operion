---
role: Teacher
role_key: teacher
kind: role
session: admin   # from the role flags (not portal). How teachers reach TeacherPortalService is NOT verified
person_type: staff
designations: [Teacher, Assistant Teacher]   # guidance only. Assistant Teacher has no role of its own
permissions: [CLASS_VIEW, SUBJECT_VIEW, STUDENT_VIEW, STUDENT_DOCUMENT_VIEW, ATTENDANCE_MARK, ATTENDANCE_VIEW, EXAM_VIEW, MARKS_ENTER, MARKS_SUBMIT, GUARDIAN_VIEW, COMMUNICATION_VIEW, ANNOUNCEMENT_CREATE]
scoped_by: own-sections
how_created: starter-template
requires_steps: [academic-year, starter-template, classes-and-sections, subjects, teacher-assignments, members-and-roles]
ui_routes: [/settings/roles, /hr, /members]
source: school/V1__school_baseline.sql:3101 (12 codes, no later changes); attendance/AttendanceAccessService.java:45-65; portal/TeacherPortalService.java:64-68; onboarding/SchoolTemplateService.java:438-454
verified_on: 2026-11-11
verified_by_running_app: no
---

# Teacher

> Takes attendance for their own class, enters and submits marks, and sees the students and guardians they need.

## What this person does
- Marks daily attendance for the section they are class teacher of.
- Enters marks for exams and submits them for approval.
- Views classes, subjects, students, student documents and guardian details.
- Creates announcements.

## Who gets this role
- **Typical people / designations:** Teacher. "Assistant Teacher" is a designation with no matching role, so the school must give them this role by hand.
- **Role vs designation:** the designation is the HR job title. The role "Teacher" is what gives access. Neither sets the other.
- **Class teacher:** there is no role or permission called "class teacher". Attendance marking is limited to the sections the person is assigned to as homeroom teacher.

## How to create the login
1. Apply the starter template so the "Teacher" role exists (`/settings/school-template`).
2. Create the staff profile (`/hr/staff/new`) with access, or add a member (`/members/new`).
3. Give them the "Teacher" role. They get a staff invite to set their login (`identity/auth/StaffInviteService.java:69`).
4. Assign them to classes and subjects (teacher assignments). Without this they see nothing to work on.

A resigned or terminated staff member cannot be given access (`hr/StaffAccessService.java:95-107`).

## Permissions granted
12 codes. Meanings are plain-language readings of the names, NOT individually confirmed unless a source is shown.

| Module | Permission code | What it allows | Confirmed in |
|---|---|---|---|
| academic | `CLASS_VIEW`, `SUBJECT_VIEW` | see classes and subjects | unverified meaning |
| student | `STUDENT_VIEW`, `STUDENT_DOCUMENT_VIEW` | see students and their documents | unverified meaning |
| attendance | `ATTENDANCE_MARK` | mark attendance, limited to own homeroom sections | `AttendanceAccessService.java:45-55` |
| attendance | `ATTENDANCE_VIEW` | see attendance | unverified meaning |
| examination | `EXAM_VIEW` | see exams | unverified meaning |
| examination | `MARKS_ENTER`, `MARKS_SUBMIT` | enter marks and submit them for approval | unverified meaning |
| parent | `GUARDIAN_VIEW` | see guardian details | unverified meaning |
| communication | `COMMUNICATION_VIEW`, `ANNOUNCEMENT_CREATE` | see and write announcements | unverified meaning |

Total permissions: 12.

## What they see in the app
Menu items follow the permissions above. The exact menu has NOT been checked by logging in as a Teacher. `TeacherPortalService` also exposes the teacher's own sections and leave balance, but how a teacher reaches it was not verified.

## What they cannot do
Derived by comparing against the catalogue, not by trying it:
- **Mark attendance for another teacher's section.** That needs `ATTENDANCE_MARK_ANY_SECTION` (`AttendanceAccessService.java:45-65`). The refusal reads: only the class teacher of this section, or a holder of that permission, can mark it.
- **Correct, lock or unlock attendance:** no `ATTENDANCE_CORRECT`, `ATTENDANCE_LOCK`, `ATTENDANCE_UNLOCK`.
- **Approve marks or correct after publishing:** no `MARKS_APPROVE`, `MARKS_CORRECT`, `MARKS_CORRECT_AFTER_PUBLISH`.
- **Publish announcements:** can create but not `ANNOUNCEMENT_PUBLISH`.
- **See fees, HR or staff attendance.**
- **Edit student records:** view only.

## Data scope
- **Attendance:** own homeroom sections only (`AttendanceAccessService.java:45-65`).
- **Own teaching sections:** taken from active teacher assignments (`TeacherPortalService.java:64-68`).
- **Students and exams:** whether `STUDENT_VIEW` and `EXAM_VIEW` are narrowed to the teacher's own classes is NOT verified. The permission itself does not say.

## Configuration that must exist first
- [ ] Academic year → `steps/academic-year.md` (not written yet)
- [ ] Starter template applied → `steps/starter-template.md` (not written yet)
- [ ] Classes and sections → `steps/classes-and-sections.md` (not written yet)
- [ ] Subjects assigned to classes → `steps/subjects.md` (not written yet)
- [ ] Teacher assigned to section(s) and subject(s) → `steps/teacher-assignments.md` (not written yet)
- [ ] Teacher's staff profile and login → `steps/members-and-roles.md` (not written yet)
- [ ] Working calendar (attendance can only be marked inside the year and not on a holiday)

## Symptoms when it's missing
| What the person sees | Likely cause | Fix |
|---|---|---|
| "Teacher" not in the role list | Starter template not applied | Settings → School setup template |
| No sections to mark attendance for | Not assigned as homeroom teacher of a section | Assign the teacher to the section |
| "Only the class teacher of this section … can mark its attendance" | Section belongs to another teacher | Assign correctly, or give `ATTENDANCE_MARK_ANY_SECTION` to a cover role |
| Cannot enter marks | No exam schedule for the class and subject, or subject not assigned to the class | Fix exam setup and subject configuration |
| Marks stay unapproved | Submitted, but nobody holds `MARKS_APPROVE` | Principal or another approver approves |

## Common mistakes
- Creating the login but never assigning the teacher to a section or subject.
- Expecting "Assistant Teacher" or a subject teacher to have their own role. The template has one Teacher role.
- Granting `ATTENDANCE_MARK_ANY_SECTION` to every teacher for convenience. That removes the own-section limit.

## Known gaps (not in the product yet)
- No Class Teacher or Subject Teacher role (audit AUTH-04). The scope comes from assignment, not role.
- The audit lists teachers marking only their own sections (ATT-02) as needing a product decision. Check its status against the current code before relying on this page for that behaviour.

## Screenshots
none yet

---
Maintenance: when the seed migrations, the Roles screen or the checklist change, update this page and set `verified_on`. Do not copy permission lists by hand from another page.
