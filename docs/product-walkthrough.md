# Operion: Product Setup and Walkthrough

A guide for the person who sets up, explains and demos Operion to a school. It describes what the product does today, based on the code and the docs in this repo. Anything not confirmed in the code is tagged **(unverified)**.

Audience: product owner, implementer, or sales/demo lead. No engineering background is needed for sections 1 to 6. Section 7 is for whoever runs the app locally.

---

## 1. How Operion is organised

Operion is a multi-tenant school ERP. Each school is an **Organisation** and all of its data is kept inside that organisation.

> Screenshot: the Settings page open on the "Organisation" group, showing the organisation profile.

### 1.1 The main objects

| Object | What it is | Where you see it |
|---|---|---|
| **Organisation** | One school (one tenant). Has a unique slug used at login. | Settings > Organisation > Organisation profile (`/setup/structure`) |
| **Campus** | A physical site of the organisation. Owned by the organisation. Top-bar campus selector is display context only (see section 6). | Settings > Organisation > Campuses |
| **Academic year** | A time container (for example "2026-27"). One year is marked **current**. Classes, exams and fees hang off it. | Settings > Configuration > Academic years (`/settings/academic-years`) |
| **Grade level, Class, Section** | Grade level (for example "Grade 3"), class in a year at a campus, and section within a class (for example "3-A"). | Academics > Setup wizard (`/academics/setup`) |
| **Subject** | Taught in classes. Mapped to classes as "class offerings". | Academics > Subjects, Class offerings |

Source: `src/main/java/com/operion/organisation/` (Organisation, Campus, AcademicYear, Department, Designation), `src/main/java/com/operion/academic/`.

### 1.2 People, logins and memberships

Operion separates the person, the login and the job. Keep these four apart when you explain the product:

| Concept | Meaning | Code |
|---|---|---|
| **Person** | A human being: name, date of birth, gender, phone, email, address. Can exist without a login (whether the User link is optional was not checked **(unverified)**). | `identity/Person.java` |
| **User** | A login: email, phone, password, status. Scoped to one organisation. | `identity/User.java` |
| **Membership** (in the UI: "Member") | Links a User and a Person to a **Role** and a **Campus** (optionally a Department). The membership is what gives someone access. One membership can be flagged as the owner. | `authorization/OrganisationMembership.java` |
| **Staff profile** | The HR record for an employee (joining details, designation, leave, payroll). Created in the HR module. How it links to Person was not read **(unverified)**. | `hr/` (`StaffCreatePage`, `StaffProfilesPanel`) |
| **Student** | An admitted learner, linked to a Person. Has admission details, a section enrolment and a roll number. | `student/Student.java` |
| **Guardian** | A parent or guardian of a student, entered on the student record ("Primary guardian" section of the admission form). | `parent/` package |

Rule of thumb for the demo: **a Person can exist without a login, a login without a membership can't do anything, and a membership is only as powerful as its Role.**

> Screenshot: the Members page (`/members`) with one member row showing name, role and campus.

#### Giving a staff member login access (being added)

_Placeholder. This section will describe how a staff member is given a login and a role. The flow is not finished, so no steps are written here yet. Do not demo it as a finished feature._

### 1.3 Roles and permissions

- **Permission**: a single capability, named like `STUDENT_VIEW`, `ATTENDANCE_MARK`, `FEE_COLLECT`. The list is fixed in code and seeded in the database (`src/main/resources/db/migration/V2__seed_permissions.sql` and later module migrations). Schools cannot create new permissions.
- **Role**: a named bundle of permissions. Schools create their own roles, or start from the preset roles in the School Template (section 2, step 3).
- **Membership**: attaches a Role to a person at a campus.

Every API call is checked against the permissions of the caller's membership (`@RequirePermission` on controllers, enforced by `authorization/PermissionInterceptor.java`). The screens also hide what the user cannot use, but the backend check is the real one.

### 1.4 Modules (on and off per school)

The product is split into modules. A module that is switched off disappears from the sidebar and its API calls are refused. The platform owner controls this per organisation. The default list is in `src/main/java/com/operion/modules/AppModule.java`.

| Default **on** | Default **off** |
|---|---|
| Dashboard, Students & Admissions, Academics, Attendance, Examinations, Fees, Communication, HR | Library, Learning, Transport, Messaging, Inventory, Purchase, Sales, Reports, Payroll, Performance, Regulatory & Privacy |

Some modules need others. Switching one on also switches on what it needs (`AppModule.requires()`):

- Attendance, Examinations: need Students, Academics
- Learning: needs Students, Academics, Examinations
- Fees, Library, Regulatory & Privacy: need Students
- Transport: needs Students, Fees
- Purchase, Sales: need Inventory
- Payroll: needs HR
- Performance: needs HR, Academics

Core areas (organisation, identity, authorization, audit, settings, platform) are always on and are not listed in the module switch.

The School Starter Pack (section 2, step 3) asks plain-language questions and switches modules on from the answers. The starter pack dialog recalculates the modules as you answer, and nothing is saved until you press Apply (`web/src/modules/settings/StarterPackDialog.tsx`).

### 1.5 The setup guide on the dashboard

The Dashboard shows a checklist of 12 steps, plus a 13th (Academic year closure) that is listed as a later step and does not count towards finishing setup. It is guidance, not a lock. Each step is marked done when the data exists:

1. Organisation profile (school profile and settings)
2. Academic year creation (done only while a year is current)
3. Members and roles
4. Classes and sections
5. Subject configuration (every class of the latest year has a subject)
6. Fee structures
7. Student admissions
8. Student record management (at least one student)
9. Attendance management (attendance started)
10. Examination creation
11. Marks entry (marks approved)
12. Library administration (library stocked)
13. Academic year closure (later step, not counted)

Source: `web/src/modules/dashboard/SetupProgress.tsx`, `src/main/java/com/operion/dashboard/api/SetupChecklist.java`.

> Screenshot: the Dashboard with the setup guide card showing the current step highlighted.

---

## 2. First-time configuration

Do these in order. Each step lists what it needs first. The steps follow the dependency order in the code, not the order of the sidebar.

### 2.1 Summary table

| Step | Where | What to enter | Needs first | Notes |
|---|---|---|---|---|
| 0. Create the organisation and first login | Provisioning API (`POST /api/v1/platform/organisations`, platform admin only). UI path **(unverified)** | Organisation name, legal name, slug, first admin email, password, first and last name | Nothing | Creates the Owner role and the first admin membership. Only the Owner role is created. |
| 1. Institution setup | `/setup/structure`, then `/setup/review`, `/setup/go-live` | Organisation profile, business settings, campuses (optional) | Step 0 | Go-live check reports readiness in three stages (configuration, data, access). |
| 2. Academic year | `/settings/academic-years` | Year name, start and end dates. Mark it **current**. | Step 1 | The setup guide and the academic setup wizard both require a current year. |
| 3. School setup template and starter pack | Settings > Organisation > School setup template | Preset categories to add (grades, departments, designations, roles, fee categories, classes, sections, subjects, leave types, and more). Starter pack questions. | Step 1 | Opt-in and additive. Items that already exist are skipped. Classes need the current year. |
| 4. Team members and roles | `/members`, Settings > Access & Security > Roles and Members | Role bundles, people, logins, membership per campus | Steps 1, 3 | Only Owner exists until a template or custom roles are added. |
| 5. Grade levels, classes, sections | `/academics/setup` (wizard) | Academic year, grade levels, subjects, classes and sections | Step 2 | Wizard checks: current year set, at least one grade level, at least one class with a section. |
| 6. Subjects and class offerings | `/academics` (Subjects, Class offerings tabs) | Subjects, and which subjects each class takes | Step 5 | Setup guide step "Subject configuration" checks the latest year. |
| 7. Teacher assignments | `/academics` (Teacher assignments tab) | Teacher per section and subject | Steps 4, 6 | Informational only. No attendance, exam or marks endpoint filters by it (see section 6). |
| 8. Periods, rooms, timetable | `/academics` (Periods, Rooms, Timetable tabs) | Periods, rooms, then subject quotas per section | Steps 6, 7 | Generate draft, then publish. |
| 9. Fee setup | `/fees/setup` | Fee categories, fee structures, admission fee settings | Steps 2, 5 | Fees sidebar item is disabled until at least one student exists. |
| 10. Admissions | `/admissions`, public form at `/apply/:orgSlug` | Applicants, admission details, conversion to student | Steps 2, 5 | Public form takes an organisation slug. |
| 11. Students and enrolment | `/students/new`, then student detail | Person details, primary guardian, admission details, then enrol into a section | Steps 2, 5 | Enrolment needs academic year, section, roll number, date. |
| 12. Attendance | `/attendance/mark` | Daily register per section | Steps 4, 11 | Sidebar item disabled until at least one student exists. |
| 13. Examinations and marks | `/examinations` | Exams, schedules, grading scales, marks | Steps 5, 6, 11 | Grading scale defaults come from the school template. |
| 14. Library (optional) | `/library` | Books and stock | Library module on (needs Students) | Off by default. |
| 15. Go-live check | `/setup/go-live` | Nothing to enter. Review the readiness report. | All required steps | Use before handing over to the school. |

### 2.2 Steps in detail

#### Step 0. Create the organisation and first login

1. Provision the organisation as a platform admin (organisation creation is not public): log in with `POST /api/v1/platform/auth/login` and send the returned token as `Authorization: Bearer ...`. The request body takes `name`, `legalName`, `slug`, `adminEmail`, `adminPassword`, `adminFirstName`, `adminLastName`. Example from `README.md`:

   ```bash
   curl -X POST http://localhost:8080/api/v1/platform/organisations \
     -H "Content-Type: application/json" \
     -H "Authorization: Bearer <platform token>" \
     -d '{"name":"Demo School","legalName":"Demo School Trust","slug":"demo-school",
          "adminEmail":"admin@demo-school.test","adminPassword":"<choose a strong password>",
          "adminFirstName":"Admin","adminLastName":"User"}'
   ```

2. Log in on the web app at `http://localhost:5173/login` with the organisation slug, email and password. The UI for provisioning a school was not found in the web app **(unverified)**, so the API is the documented route.
3. Result: one Owner role and one admin membership. The Owner has every permission and the system describes it as "cannot be locked out" (`organisation/OrganisationService.java`, `organisation/DefaultRoles.java`).

> Screenshot: the login page with the organisation slug field.

#### Step 1. Institution setup

1. Open `/setup/structure`. The wizard has these steps: **Organisation profile**, **Business settings**, **Campuses** (optional), **Review and finish**.
2. Fill in the profile and business details on the first two steps (the individual fields were not listed **(unverified)**). Add campuses if the school has more than one site.
3. Open `/setup/review` to check what was entered.
4. Open `/setup/go-live` to see the readiness report. It shows the state in three stages: CONFIGURATION, DATA, ACCESS.

> Screenshot: the structure setup wizard on the "Organisation profile" step.

#### Step 2. Academic year

1. Open `/settings/academic-years` (Settings > Configuration > Academic years).
2. Create the year with a name and start and end dates.
3. Mark it current. A year starts as not current. The setup guide step only completes while a year is current.

#### Step 3. School setup template and starter pack

1. Open Settings > Organisation > School setup template (`SchoolTemplatePanel`).
2. The template has these categories: Grades, Departments, Designations, Roles, Fee categories, Inventory categories, Inventory items, Asset types, Classes, Sections, Subjects, Leave types, and more. Choose what to add.
3. The template is opt-in and additive. Items that already exist are skipped, so running it again does not duplicate them.
4. The **School Starter Pack** asks plain questions ("does your school need...?") and turns modules on. Answer, review the modules it suggests, then press Apply.
5. The template's master items are in `src/main/resources/db/migration/V100__school_template_master_items.sql` and the school baseline in `src/main/resources/db/migration/school/V1__school_baseline.sql`. Starter pack defaults: `school/V24__starter_pack_core_staff_defaults.sql`, `school/V132__school_starter_pack_modules.sql`.

> Screenshot: the School setup template panel with categories listed.

The walkthrough does not list the exact starter pack questions. They are served by the backend (`recommendStarterPack` in `web/src/api/schoolTemplate`) and were not read in full **(unverified)**.

#### Step 4. Team members and roles

1. Create the roles the school needs, or add the preset roles from step 3. Preset roles include Principal, Administrator, Academic Admin, Teacher, Accountant, HR Manager, Librarian, Transport Manager, Inventory Manager, Purchase Manager, Receptionist and Staff. Section 4 lists what each one can do.
2. Add people and give them memberships under `/members` (Members page, `/members/new` and `/members/invite`). Each membership needs a role and a campus.
3. Check the membership list on `/members`.

The e2e suite comment says the access chain is driven from the Members/Users panel (`e2e/api/roles.ts`, `UsersPanel.tsx`). The exact steps in the screens were not walked through **(unverified)**.

#### Step 5. Grade levels, classes and sections

1. Open `/academics/setup`. The wizard has five steps:
   1. Academic year
   2. School setup template
   3. Grade levels
   4. Subjects
   5. Classes and sections
2. The review at the end checks: academic year marked current, at least one grade level, at least one class with a section.
3. Add sections to each class. Sections have a name, capacity and room (`e2e/api/organisations.ts`).

> Screenshot: the academic setup wizard, review step with the three checks.

#### Step 6. Subjects and class offerings

1. Open `/academics`, Subjects tab. Add subjects.
2. Open the Class offerings tab and map subjects to classes.

#### Step 7. Teacher assignments

1. Open `/academics`, Teacher assignments tab.
2. Assign a teacher to a section (homeroom, subject teacher or co-teacher).

Gap: assignments are recorded but no attendance, exam or marks endpoint checks them. Any user with the write permission can act on any class (`e2e/README.md`, "Known gaps").

#### Step 8. Periods, rooms and timetable

1. Open `/academics`, Periods tab, and add periods.
2. Open the Rooms tab and add rooms.
3. Open a section (`/academics/classes/:classId/sections/:sectionId`). The section timetable panel has these actions: **Generate draft**, **Publish**, **Unpublish**, **Save**, **Clear slot** (`web/src/modules/academics/SectionTimetablePanel.tsx`).
4. Generate a draft, check it, then publish.
5. The Timetable and Substitutions tabs on `/academics` cover the whole school. Substitutions and the Uncovered list are in the same area.

> Screenshot: a section timetable with the generated draft shown before Publish.

#### Step 9. Fee setup

1. Open `/fees/setup`. The panels are: fee categories, fee structures, admission fee settings and bulk invoices (`FeeCategoriesPanel`, `FeeStructuresPanel`, `AdmissionFeeSettingsPanel`, `BulkInvoicePanel`).
2. Add fee categories (the template adds tuition, admission, examination, transport, library and other categories).
3. Create fee structures per class or group.

Note: the setup guide counts "Fee structure configuration" as an academic-phase step. The structure setup page says fees come with the school template. Both are true in the code; the order in practice is unverified.

#### Step 10. Admissions

1. Open `/admissions`. Add an applicant (first name, last name, date of birth, gender, phone, email, source, notes).
2. Open the applicant and fill in admission details (admission number, date, source, previous school, TC number, entrance score). Leave the admission number blank to auto-generate it.
3. Use **Open student record** to convert the applicant to a student.
4. To take applications from the web, use the public admission form `/apply/:orgSlug`. The admissions page has a "Public admission form link" dialog that asks for the organisation slug.

#### Step 11. Students and enrolment

1. Open `/students/new`. Enter the person details, the primary guardian (name, gender, mobile, relationship), admission details (admission number, date, academic year, class, section, roll number), and emergency contact.
2. Open the student record (`/students/:studentId`) and enrol the student into a section. Enrolment needs an academic year, a section, a roll number and a date.
3. A student moves from Admitted to Active when enrolled. The setup guide "student record" step completes when a student exists.

Gap: admission fields are view-only after they are set. There is no edit (`e2e/README.md`).

#### Step 12. Attendance

1. Open `/attendance`. The Overview tab shows the state of the day.
2. Open `/attendance/mark` (Class Register). Choose the class, section and date, then mark each student.
3. The Staff Attendance tab marks staff attendance.
4. The Attendance sidebar item is disabled until at least one student exists.

> Screenshot: the class register with a section selected and statuses set.

#### Step 13. Examinations and marks

1. Open `/examinations`. The tabs are Overview, Grading Scales and Settings. The overview shows Total Examinations, Upcoming Examinations, Marks Pending and Results Published.
2. Create an exam with **CreateExamDialog**. Open the exam (`/examinations/exams/:examId`) to see its schedules.
3. Open a schedule (`/examinations/exams/:examId/schedules/:scheduleId`) and enter marks in the marks entry page.
4. Marks are approved before results are published (permission `MARKS_APPROVE`).
5. Open a report card and print it (`/examinations/report-cards/:reportCardId/print`).

Grading scale defaults (A+ to F bands) come from the school template master items (`V100__school_template_master_items.sql`).

#### Step 14. Library (optional)

1. Turn on the Library module (off by default). It needs Students.
2. Open `/library`. Add books, open a book at `/library/books/:bookId`.

Library was not walked through in full **(unverified)**.

#### Step 15. Go-live check

1. Open `/setup/go-live`. Read the readiness report.
2. Fix every item in CONFIGURATION, DATA and ACCESS before the school starts.

> Screenshot: the go-live readiness report with its three stages.

---

## 3. Day-to-day operations

Each section is a checklist. Page names match the sidebar or the page title.

### 3.1 Dashboard

1. Open `/dashboard`.
2. Read the setup guide. The next step is highlighted.
3. Dismiss the guide with the close button if the school does not need it. Whether the dismissal is remembered per browser or per user was not checked **(unverified)**.

### 3.2 Students and admissions (sidebar: Students, Admissions)

1. Add new admissions on `/admissions`, or add a student directly on `/students/new`.
2. Check the student list on `/students`. The top-bar search also finds students by name (`ContextSelectors` and `AppLayout.tsx` lookup).
3. Open a student (`/students/:studentId`). Check enrolment, guardians and fees.
4. At the start of a new year, open `/students/promotion` to move students forward (details **(unverified)**).
5. Certificates are issued from `/students/certificates` (details **(unverified)**).

### 3.3 Academics and timetable (sidebar: Academics)

1. Check the tabs on `/academics`: Grade levels, Sections, Subjects, Class offerings, Teacher assignments, Periods, Rooms, Timetable, Substitutions, Uncovered.
2. When a teacher is absent, add a substitution from the Substitutions tab. Check the Uncovered tab for slots nobody covers.
3. Republish the timetable after a change. Unpublish first if you need to edit a published timetable.

### 3.4 Attendance (sidebar: Attendance)

1. Each morning, open `/attendance/mark`. Pick the section and date.
2. Mark each student and save the register (the save control was not read **(unverified)**).
3. Corrections use the status change and reason fields on the register.
4. Mark staff attendance on the Staff Attendance tab.
5. Overview shows the day's totals.

Permissions for correction, locking and unlocking exist as `ATTENDANCE_CORRECT`, `ATTENDANCE_LOCK`, `ATTENDANCE_UNLOCK` (`V2__seed_permissions.sql` and school role bundles). How locked registers behave on screen was not read **(unverified)**.

### 3.5 Examinations (sidebar: Examinations)

1. Create the exam for the term (`/examinations`, Create exam).
2. Add schedules per subject.
3. Enter marks on the marks entry page.
4. Approve marks. Results are not published until approved.
5. Print report cards from `/examinations/report-cards/:reportCardId/print`.
6. Adjust grading bands on the Grading Scales tab if needed.

### 3.6 Fees (sidebar: Fees)

1. Open `/fees/collect` to take a payment. Use the collect dialog.
2. Open `/fees/demand` to raise demands or invoices for a class or group.
3. Check the collection page for totals (Total Fees, Collected, Outstanding, Overdue) and filters (Paid, Partial, Unpaid, Overdue).
4. On a student record, the student fees panel shows that student's dues.
5. Waivers and refunds need approval permissions (`FEE_WAIVER_APPROVE`, `FEE_REFUND_APPROVE`). `MODULE_INVENTORY.md` says discounts are gated under `FEE_ASSIGNMENT_MANAGE` and that `FEE_DISCOUNT_APPROVE` is unused **(unverified against code)**.

Online payment (Razorpay) is not for go-live (see section 6).

### 3.7 HR (sidebar: HR)

1. Add a staff member on `/hr/staff/new`. Open a staff profile at `/hr/staff/:staffProfileId`.
2. Check staff profiles on the Staff Profiles tab.
3. Review leave requests in the Leave Requests inbox. Leave types are managed in the Leave Types panel (the tab placement was not confirmed **(unverified)**).
4. Arrange cover for absent staff on the Leave Cover panel.
5. Job applications are on the Job Applications panel (details **(unverified)**).

### 3.8 Payroll (sidebar: Payroll, off by default)

1. Turn on Payroll. It needs HR.
2. Set salary components on the Salary Components tab, and salary structures for staff.
3. Create a payroll run on the Payroll runs tab. Open it at `/payroll/runs/:runId`.
4. Open a payslip at `/payroll/payslips/:payslipId`.

The run approval and lock steps were not read **(unverified)**.

### 3.9 Performance (sidebar: Performance, off by default)

1. Turn on Performance. It needs HR and Academics.
2. Open `/performance` and then a staff member at `/performance/staff/:personId`.

### 3.10 Members (sidebar: Members)

1. Open `/members` to see people, their role and campus.
2. Open a member at `/members/:userId`.
3. Invite or add new people from `/members/invite` and `/members/new`.

### 3.11 Communication (sidebar: Communication, on by default)

1. Open `/communication`, Announcements panel.
2. Create an announcement. It can be published or cancelled (`ANNOUNCEMENT_PUBLISH`, `ANNOUNCEMENT_CANCEL`).

Channel details (email, SMS, WhatsApp) were not read in full **(unverified)**. SMS and WhatsApp senders are not for go-live (section 6).

### 3.12 Settings (sidebar: Settings, always on)

Settings groups from `web/src/modules/settings/settingsSections.tsx`:

1. **Organisation**: Organisation profile, Business settings, Campuses, Departments, Designations, School setup template.
2. **Access & Security**: Members, Roles, Audit logs, Profile change requests.
3. **Configuration**: Academic years, Working calendar, Branding & documents, Letter formats, Certificate types, ID Card Studio, Notifications, Custom fields.
4. **Compliance**: Privacy & consent, Regulatory exports, Transfer hand-offs.
5. **System**: Imports & exports, Integrations & API.

Compliance items belong to the Regulatory & Privacy module, which is off by default.

### 3.13 Modules with pages but not for go-live

These sidebar items exist in the code but are not in the go-live scope (see section 6). Walkthrough steps are not written for them:

- **Transport** (`/transport`, routes and vehicles): GPS is deferred.
- **Inventory**, **Purchase**, **Sales** (`/inventory`, `/purchase`, `/sales`): off by default.
- **Messaging** (`/messaging`): disable until after the freeze.
- **Learning** (`/learning`): homework and quizzes. Guardian-facing, disable until after the freeze.
- **Reports** (`/reports`): off by default. Principal-facing.
- **Portals** (`/portal/**`): guardian and teacher portals. Not for go-live.

---

## 4. Roles and who sees what

### 4.1 Seeded roles

| Role | Created when | What it can do (summary) |
|---|---|---|
| **Owner** | Every new organisation | Every permission. Cannot be locked out. |
| **Administrator** | School template (Roles) | Day-to-day operations, short of financial approvals and payroll. |
| **Principal** | School template (Roles) | Academic leadership, approvals, staff oversight. Includes `MARKS_APPROVE`, `ATTENDANCE_LOCK`, `REPORT_CARD_PUBLISH`, `HR_LEAVE_MANAGE`. |
| **Academic Admin** | School template (Roles) | Academic structure and exam configuration. Includes `CLASS_MANAGE`, `SUBJECT_MANAGE`, `EXAM_MANAGE`, `REPORT_CARD_PUBLISH`. |
| **Teacher** | School template (Roles) | Classroom attendance, marks entry, guardian communication. Includes `ATTENDANCE_MARK`, `MARKS_ENTER`, `MARKS_SUBMIT`, `STUDENT_VIEW`. |
| **Accountant** | School template (Roles) | Fee collection, invoicing, financial approvals. Includes `FEE_COLLECT`, `FEE_INVOICE_MANAGE`, `FEE_REFUND_APPROVE`, `FEE_WAIVER_APPROVE`. |
| **HR Manager** | School template (Roles) | Staff records, leave, recruitment, HR payroll view. |
| **Librarian** | School template (Roles) | Library catalogue, borrowing, fines. |
| **Transport Manager** | School template (Roles) | Vehicles, routes, assignments, trip log. |
| **Inventory Manager** | School template (Roles) | Items, stock, adjustments, suppliers, customers. |
| **Purchase Manager** | School template (Roles) | Purchase orders and supplier returns. |
| **Receptionist** | School template (Roles) | Front-desk enquiries, guardian and membership view, announcements. |
| **Staff** | School template (Roles) | Read-only baseline: student view, communication, attendance view. |

Sources: the ROLE rows in `src/main/resources/db/migration/V100__school_template_master_items.sql` and `src/main/resources/db/migration/school/V1__school_baseline.sql`. The permission lists above are a subset. Check the migration for the full bundle before promising a role's access.

The **Guardian** role exists as a template item but carries only `PARENT_PORTAL_ACCESS`, and the guardian portal has no screens yet (section 6).

### 4.2 Permission groups

Permissions are grouped by module. These are the groups in the catalogue (`permissions.module`):

Core: students, attendance, fees, reports, role and membership management, organisation management. Modules: academic, examination, library, learning, transport, communication, messaging, inventory, purchase, sales, reporting (Reports), hr, payroll, performance, parent, certificate.

Each permission is named `<AREA>_VIEW`, `<AREA>_MANAGE`, or an action such as `ATTENDANCE_MARK`, `FEE_COLLECT`, `MARKS_APPROVE`.

### 4.3 Who sees what

- **Sidebar**: a module is hidden if it is switched off for the school, or if the user has none of its view permissions (`web/src/layout/AppLayout.tsx`).
- **Dashboard and Settings**: Dashboard needs `ORGANISATION_MANAGE`. The Settings sidebar item has no permission gate. Whether each Settings section checks its own permissions was not read **(unverified)**.
- **Setup guide**: only users with `ORGANISATION_MANAGE` see the checklist.
- **Backend**: every API call is checked against the caller's permissions, so a user who calls an endpoint without permission is refused.

---

## 5. Demo script (20 to 30 minutes)

Run this on a seeded demo school that already has students, classes and a published timetable. Set the timings to your own pace.

| Time | Screen | Talking point | Impressive thing |
|---|---|---|---|
| 0:00 to 2:00 | Login, Dashboard (`/dashboard`) | One place for the whole school, with a guide to set it up. | The setup guide shows what's done and what's next. |
| 2:00 to 4:00 | Settings > Organisation (`/setup/structure`) | Set up the school once, not per module. | Campuses and business settings in one wizard. |
| 4:00 to 6:00 | Academics setup (`/academics/setup`) | Structure first (year, grades, classes, sections) and everything follows it. | The wizard checks the setup and tells you what is missing. |
| 6:00 to 9:00 | Students (`/students`, `/students/new`) | One student record: person, guardians, admission, enrolment. | Admission number is auto-generated if left blank. |
| 9:00 to 11:00 | Admissions (`/admissions`) | Enquiries become admitted students. | Public admission link for the school website. |
| 11:00 to 13:00 | Attendance register (`/attendance/mark`) | Daily register per class, one screen. | Overview tab shows the day's state (totals not checked **(unverified)**). |
| 13:00 to 16:00 | Timetable (`/academics`, section timetable) | Generate a draft, check it, then publish. | Generate draft creates a timetable draft from the section setup (quota logic not read **(unverified)**). |
| 16:00 to 19:00 | Fees (`/fees/demand`, `/fees/collect`) | Fees defined once and applied to classes. | Collection page shows Total Fees, Collected, Outstanding and Overdue. |
| 19:00 to 22:00 | Examinations (`/examinations`, marks entry, report card) | Marks entered once, approved, then published on the report card. | Report card has a print page. |
| 22:00 to 24:00 | HR (`/hr`, leave inbox) | Staff records and leave in the same system as students. | Leave requests in one inbox, with cover arrangements. |
| 24:00 to 26:00 | Members and roles (`/members`, Settings > Roles) | Each person gets a role and a campus. Access follows the role. | The Accountant sees fees but not marks. Show it live if you can. |
| 26:00 to 28:00 | Go-live check (`/setup/go-live`) | The school knows what is left before launch. | A readiness report in three stages. |

Keep off the demo: Transport, Learning, Messaging, Inventory, Purchase, Sales, Payroll (unless switched on for the demo school), Performance, Portals, Online payment (section 6).

> Screenshot: the full demo school dashboard, for the opening slide.

---

## 6. Known limitations

Each item says where it was found. Items from the audit may be out of date. The audit (`docs/product-audit/PRODUCT_GAP_AUDIT.md`) is dated 2026-09-30 and refers to migrations V1 to V130.

| Limitation | Evidence |
|---|---|
| Teacher assignments are informational. Attendance, exam and marks endpoints do not check them. | `e2e/README.md`, "Known gaps" (open) |
| Guardian and parent portal has no screens. Attendance, fees, results and notices are not reachable for guardians. | `e2e/README.md`, "Known gaps" (open) |
| Platform admin app has no module catalogue or feature-flag screen. Subscriptions are the closest thing. | `e2e/README.md`, "Known gaps" (open) |
| Student admission fields are view-only once set. There is no edit. | `e2e/README.md`, "Known gaps" (open) |
| Campus and academic year selectors in the top bar are display only. No data is filtered by them yet. | `e2e/README.md`, "Known gaps" (open); `web/src/layout/ContextSelectors.tsx` |
| Online fee payment (Razorpay) has never been tested against the real API. Shared-account compliance question unresolved. Keep off for go-live. | `docs/stabilisation/MODULE_INVENTORY.md` |
| Transport: GPS deferred, light test coverage. Keep off for go-live. | `docs/stabilisation/MODULE_INVENTORY.md` |
| Messaging, Learning, Regulatory & Privacy, SMS and WhatsApp senders: disabled until after the feature freeze. | `docs/stabilisation/MODULE_INVENTORY.md` |
| Communication has very thin API and UI tests (3 API, 1 UI). | `docs/stabilisation/MODULE_INVENTORY.md` |
| Seeded platform admin password must be rotated before go-live. No screen to change it. | `docs/stabilisation/MODULE_INVENTORY.md`, item 3 (value not copied here) |
| Discounts are gated under the fee assignment permission. `FEE_DISCOUNT_APPROVE` is seeded but unused. | `docs/stabilisation/MODULE_INVENTORY.md`, item 4 |
| Provisioning a school in the web app: no UI found. The API is the documented route. | `README.md` (First login); no screen found in `web/src/` **(unverified)** |
| The root `README.md` says "No RBAC/permission enforcement yet". The code has `PermissionInterceptor` and `@RequirePermission`. The README statement is stale. | `README.md` vs `src/main/java/com/operion/authorization/` |
| The product gap audit refers to migrations V1 to V130. Current migrations go past V132. Treat its findings as a starting point. | `docs/product-audit/PRODUCT_GAP_AUDIT.md` |
| Stabilisation phase runs 2026-10-21 to 2026-11-30. Scope is principal and school staff, website only. No mobile app, no parent or student logins at go-live. | `docs/stabilisation/MODULE_INVENTORY.md` |

---

## 7. Quick reference

### 7.1 Addresses

| What | Address |
|---|---|
| Web app (frontend) | `http://localhost:5173` (Docker: nginx serving the production build) |
| API (backend) | `http://localhost:8080` (health: `http://localhost:8080/actuator/health`) |
| MySQL (Docker) | `localhost:3306` |

Source: `docs/setup/README.md`.

### 7.2 How to run

With Docker (from `docs/setup/README.md`):

```bash
SPRING_PROFILES_ACTIVE=dev docker compose up --build
```

Native, from `README.md`:

```bash
./gradlew bootRun              # backend on :8080
cd web && npm install && npm run dev   # frontend (Vite dev server; default port 5173 **(unverified)**)
```

Database credentials, JWT secrets and other variables are in `docs/setup/README.md`. Do not copy them into demo material.

Tests: `./gradlew test` for the backend. The end-to-end suite is under `e2e/` (see `e2e/README.md`).

### 7.3 Glossary

| Term | Meaning |
|---|---|
| Organisation | One school (tenant). Has a slug used at login. |
| Campus | A site of the organisation. |
| Academic year | A year container. One is marked current. |
| Grade level | A stage or year group, for example Grade 3. |
| Class | A grade level in a year at a campus. |
| Section | A division of a class, for example 3-A. |
| Person | A human being in the system. May or may not have a login. |
| User | A login. |
| Membership | Links a user and a person to a role and a campus. Gives access. |
| Role | A named bundle of permissions. |
| Permission | A single capability, for example `ATTENDANCE_MARK`. |
| Owner | The first role created for every school. Has every permission. |
| Module | A feature area that a school can switch on or off. |
| School Template | Preset categories (roles, classes, fee categories, and more) added to a school on request. |
| Starter Pack | Questions that switch on the modules a school needs. |
| Setup guide | The 12-step checklist on the dashboard (plus a later "Academic year closure" step that is not counted). Guidance only, does not block the app. |
| Go-live check | Readiness report at `/setup/go-live`. |
| Staff ID | The identifier a school gives a staff member. Entered as "Staff ID" when adding a member or staff profile, and shown on the staff profile. |
| Enquiry | A first contact from a prospective student, from the public admission form or recorded by staff ("Record enquiry"). |
| Applicant | The person an enquiry or application is about. Approving an applicant admits them as a student. |
| Enrolment | A student's placement in a section for an academic year ("Enrol student"). Carries a roll number. |
| Fee category | A type of fee, for example Tuition or Transport. Used as a line in a fee structure. |
| Fee structure | The fees set for a class in a year: one or more fee category lines, each with an amount and due dates. |

**House style.** UI labels use sentence case ("Collect fee", "Class register"). Text uses British spelling (organisation, enrol, enquiry, catalogue). Use "Sign in" and "Sign out", never "Log in" or "Logout".
