# Flush Context — School ERP Platform (Operion)

> Fill this in at the end of a work session and save it. It's the diff between "what load-context.md says the project is" and "what actually happened this session." Next session: read this alongside load-context.md, then fold anything durable back into load-context.md and clear this file for the next round.

## Session date

2026-08-09

## Module / milestone worked on

Milestone 3 — Student Management (`Student`, `StudentEnrollment`, `StudentDocument`, `StudentExit`, `Guardian`, `StudentGuardian`), built end to end (entities → migrations → service → API → tests) in one session, on top of the Foundation + Academic Foundation milestones from the previous session.

## Decisions made

- **StudentAdmission fields** — merged onto `Student` rather than a separate entity — Option A (merge, recommended for MVP) vs Option B (separate insert-only entity, correctly models re-admission after withdrawal). Chose A: simpler for MVP; revisit as a separate entity only if re-admission after withdrawal becomes a real requirement.
- **Build scope for the session** — one pass covering Student + Enrollment + Documents + Exit + Guardian/Parent together, vs. splitting Guardian/Parent into a follow-up session. Chose one pass: matches how Academic Foundation was built (multiple related entities in one commit) and Guardian/StudentGuardian was small enough not to warrant its own session.
- **Package layout for Guardian/StudentGuardian** — put them in a separate `com.operion.parent` package rather than inside `com.operion.student`. Reason: the repo already had empty `.gitkeep`-scaffolded `student` and `parent` packages from the Academic Foundation session, matching the conceptual module layout in load-context.md — followed that existing signal rather than collapsing them into one package.
- **StudentEnrollment has no generic lifecycle `status` column** — only `enrollment_status` (ENROLLED/PROMOTED/REPEATED/TRANSFERRED/WITHDRAWN/GRADUATED) — followed erp-system-plan.md §2.2's explicit field list literally rather than adding a status column by default convention, since the enum already fully captures the row's lifecycle.

## What was actually built

- **Entities/enums** — `src/main/java/com/operion/student/`: `Student`, `StudentStatus`, `StudentEnrollment`, `StudentEnrollmentStatus`, `StudentDocument`, `StudentDocumentStatus`, `DocumentVerificationStatus`, `StudentExit`, `StudentExitType`. `src/main/java/com/operion/parent/`: `Guardian`, `GuardianStatus`, `GuardianRelationshipType`, `StudentGuardian`, `StudentGuardianStatus`.
- **Repositories** — one per entity in the same packages (`StudentRepository`, `StudentEnrollmentRepository`, `StudentDocumentRepository`, `StudentExitRepository`, `GuardianRepository`, `StudentGuardianRepository`).
- **Services** — `StudentService` (admit/enroll/promote/reassignSection/recordExit/addDocument/verifyDocument) and `ParentService` (getOrCreateGuardian/linkGuardian/updateRelationship), both enforcing their `is_current`/`is_primary_guardian` uniqueness rules service-side in a transaction, same convention as `AcademicService`.
- **Migrations** — `src/main/resources/db/migration/V5__student_schema.sql` (6 tables: `students`, `student_enrollments`, `student_documents`, `student_exits`, `guardians`, `student_guardians`) and `V6__seed_student_permissions.sql` (8 permission codes: `STUDENT_VIEW/MANAGE`, `STUDENT_ENROLLMENT_MANAGE`, `STUDENT_DOCUMENT_VIEW/MANAGE`, `STUDENT_EXIT_MANAGE`, `GUARDIAN_VIEW/MANAGE`).
- **API** — `src/main/java/com/operion/student/api/` and `src/main/java/com/operion/parent/api/`: controllers + request/response DTOs under `/api/v1/students/...` and `/api/v1/guardians/...`.
- **Tests** — `StudentEnrollmentLifecycleTest` (promotion insert-only history + exit cascading into enrollment/student status), `StudentGuardianPrimaryTest` (primary-guardian uniqueness), `StudentTenantIsolationTest` (extends the Foundation isolation-test pattern to the new tables). Full suite: 13 tests, all passing.
- **Docs** — `ai-context/load-context.md`'s "Current status" section updated twice this session: once to fix a stale carry-over from the Academic Foundation session (it hadn't been updated to reflect Academic Foundation was already built), then again to record Student Management as built.

## Open questions (unresolved, carry to next session)

- Three-way fork still open, flagged at both the start and end of this session: **RBAC/permission enforcement** (outstanding since Foundation — permission codes exist but nothing checks them) vs. **milestone 4 (Attendance)** vs. **backfilling Academic Foundation's test gap** (`GradeLevel`/`SchoolClass`/`Section`/`Subject`/`ClassSubject` still have no dedicated tests of their own — they're only incidentally exercised as fixtures inside this session's Student tests). Surface all three at the start of next session per load-context.md's "How to work with me" rule, don't default to one.
- Nothing was committed to git this session — working tree has the Student Management changes staged only on disk, no commit made (user hadn't asked; offered at end of session).

## Explicitly deferred / rejected

- Separate `StudentAdmission` entity — deferred (not rejected), pending a real re-admission-after-withdrawal use case; merged fields onto `Student` for now, see Decisions above.
- Splitting this session into "Student+Enrollment core" now / "Guardian+Parent" later — rejected in favor of building the whole milestone in one pass, see Decisions above.

## Next step

Start of next session: ask the user to pick one of the three open items above (RBAC enforcement / Attendance / Academic Foundation test backfill) before writing any code — don't assume. If they want to commit the Student Management work first, do that before starting the next module.

---

### Fold-back checklist (before clearing this file)

- [x] Any new standing principle or correction → nothing new beyond what's already in erp-system-plan.md §2.2-2.4 (this session implemented that design, didn't revise it).
- [x] Milestone order still correct? → yes, unchanged (Foundation → Academic → Student/Parent → Attendance → Fees → ...).
- [x] "Current status" line in load-context.md updated to match where things actually stand → done live during the session (both the stale-carryover fix and the Student Management addition).
