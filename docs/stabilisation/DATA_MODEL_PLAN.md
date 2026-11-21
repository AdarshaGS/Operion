# Data-model gap plan (PRODUCT_GAP_AUDIT §12)

Written 2026-11-11 on `feature/two-database-split`. Current state was re-verified against the code on that date; the §12 audit text (2026-09-30) was otherwise still accurate except: `students.student_id` is unique per organisation (school/V6), closed-year guards now cover enrolment/attendance/exams/promotion/section-move/exit, and invoice generation is already deduplicated by `uq_invoices_assignment_installment`.

## Facts that shape the plan

- H2 tests build the schema from entities (`ddl-auto=create-drop`, Flyway off), so a DB constraint is only tested if it is also declared on the entity's `@Table(uniqueConstraints=…)`.
- Prod runs `ddl-auto=validate`, `open-in-view=false`; DTO mappers walk associations outside transactions, so a global LAZY switch would throw `LazyInitializationException` across most endpoints.
- Next free school migration is V37 (`common/persistence/DatabaseMigrationConfig.java`).
- "Unset old, set new" paths flush in the wrong order for a unique index: `AcademicYearService.markCurrent`, `ParentService` link/update, `StudentService.promote`, `TransportService.reassignRoute`. Fix with `saveAndFlush` on the unset row.
- Bugs found while planning: `StudentEnrollment.reassignSection` keeps the old `rollNumber` (clashes with `uq_student_enrollments_section_roll`); `reassignSection` does not lock the target section (capacity race); `StudentGuardian.deactivate()` leaves `is_primary_guardian` set; `ApiExceptionHandler` does not map `OptimisticLockingFailureException` (would 500); timetable `clearSection`/`removeEntry` delete substitution history.

## Decisions

1. `@Version` only on form-edited entities: `Student`, `Person`, `StudentGuardian`, `MarksEntry`. Clients send an optional `version` in PATCH bodies; the service compares explicitly and returns 409 on mismatch; missing `version` behaves as today (keeps mobile/e2e/automation working).
2. One-current invariants via an app-maintained nullable marker column (`1` or `NULL`) + `UNIQUE(scope, marker)`, declared on both Flyway and the entity. Rejected generated columns (H2/MySQL DDL differs) and triggers (invisible to H2 tests).
3. Section-change history as a separate `student_section_moves` table; enrollment rows are not split (would break `uq_student_enrollments_student_year` and orphan marks/invoices/transport that hang off `student_enrollment_id`).
4. Closed enrollments keep reserving their roll number (needed by `reopen`).
5. Hard deletes stay, with guards: working-calendar entries dated in the past are refused; timetable entry/section clears and substitution removal refuse when past substitutions exist and only delete future ones. No `deleted_at` columns.
6. Retention: purge `login_attempts` >30d (unless locked), `email_outbox` SENT/FAILED >180d, `notification_recipients` terminal >365d. `audit_logs` never purged (8-year financial trail; archive tier deferred).
7. No global LAZY; add `hibernate.default_batch_fetch_size=50`. Hot paths get JOIN FETCH/`@EntityGraph` later.

## Phases (one migration each; revert = revert commit + forward DROP migration)

- **Phase 0** — run the detection SQL below on every school DB. Phase 2 is blocked until it is clean.
- **Phase 1 (V37)** — `idx_persons_org_phone`, `idx_persons_org_email`, `idx_payments_org_reference`; 409 handler for `OptimisticLockingFailureException` + new `StaleUpdateException`; `default_batch_fetch_size=50`; fix `reassignSection` (lock target section, assign next roll) and declare `uq_student_enrollments_section_roll` on the entity.
- **Phase 2 (V38)** — marker columns + unique keys on `academic_years` (org, current), `student_enrollments` (student, current), `student_guardians` (student, primary∧active), `student_transport_assignments` (enrollment, active); sync markers in entity mutators; `saveAndFlush` fixes; `deactivate()` clears primary. Migration dedupes guardians (keep lowest id) and transport (keep highest id, end the rest); academic years and enrollments are NOT auto-fixed — a duplicate fails the migration on purpose.
- **Phase 3 (V39)** — `version BIGINT NOT NULL DEFAULT 0` on students, persons, student_guardians, marks_entries; `@Version`; `version` in the four responses; optional `version` in the four PATCH requests with explicit stale check; web sends it back from the loaded record.
- **Phase 4 (V40)** — `student_section_moves` table (backfilled from `audit_logs` STUDENT_SECTION_REASSIGNED rows), `StudentSectionMove` entity + `GET …/section-moves`; delete guards from decision 5.
- **Phase 5 (V41 if an index is needed)** — closed-year guard on fee structure/assignment/invoice generation, timetable `setEntry`, transport assign/reassign, learning writes (payments/refunds/adjustments on closed-year invoices stay allowed); `RetentionScheduler` modelled on `RefreshTokenCleanupScheduler` with `app.retention.*` periods, id-page-then-delete (portable).

## Phase 0 detection SQL (each must return 0 rows)

```sql
SELECT organisation_id, COUNT(*) FROM academic_years WHERE is_current=1 GROUP BY organisation_id HAVING COUNT(*)>1;
SELECT student_id, COUNT(*) FROM student_enrollments WHERE is_current=1 GROUP BY student_id HAVING COUNT(*)>1;
SELECT student_id, COUNT(*) FROM student_guardians WHERE is_primary_guardian=1 AND status='ACTIVE' GROUP BY student_id HAVING COUNT(*)>1;
SELECT student_enrollment_id, COUNT(*) FROM student_transport_assignments WHERE status='ACTIVE' GROUP BY student_enrollment_id HAVING COUNT(*)>1;
```

## Risks

- New entity-level uniques may expose sloppy H2 test fixtures; budget time.
- Phase 3 adds a `version` field to four responses/requests (additive; check strict deserialisers in mobile and operion-automation).
- Phases 2/4 turn some former 200s into 409s (racing duplicate primary, past calendar delete, timetable delete with past substitutions); check `e2e/tests/api/**` and automation suites.
- `reassign-section` responses may carry a new `rollNumber`.
- `default_batch_fetch_size` changes SQL shape; watch dashboard/report queries.

## Deferred (with reason)

Term (feature; exams use `exam_type`); FeeConcessionPolicy (per-assignment discount with approver covers go-live); WebhookEvent log (parent payments not in go-live audience; `gateway_payment_id` unique already); ImportRunRow (rows already returned in the response); DeviceToken/RFID/TenantExport (no push, hardware or offboarding flow yet); timetable effective dating; `payment_reference` uniqueness (re-presented cheques reuse it); global LAZY, paginating the 43 controller `findAll()`s, prefix student search (`StudentController.list` is used as a lookup map by the web app); `custom_fields` JSON divergence; audit-log archive tier.
