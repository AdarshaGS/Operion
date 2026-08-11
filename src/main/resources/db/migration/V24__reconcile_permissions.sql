-- Reconciles the permission catalog ahead of wiring up enforcement (RBAC was previously
-- data-model-only, per ai-context/erp-system-plan.md - no code path ever read these rows).
--
-- 1. STUDENT_CREATE/STUDENT_UPDATE/STUDENT_DELETE (V2, Foundation-era) predate the
--    Student module's own, more precise STUDENT_MANAGE (V6) - two codes covering the same
--    "admit/edit a student" action. Nothing references the V2 codes anywhere in the
--    codebase, so they're dropped rather than kept as unused duplicates.
DELETE rp FROM role_permissions rp
    JOIN permissions p ON p.id = rp.permission_id
    WHERE p.code IN ('STUDENT_CREATE', 'STUDENT_UPDATE', 'STUDENT_DELETE');
DELETE FROM permissions WHERE code IN ('STUDENT_CREATE', 'STUDENT_UPDATE', 'STUDENT_DELETE');

-- 2. Campus/AcademicYear/Person controllers were added later (frontend-driven backend
--    additions) with no permission code ever seeded for them. All three are org-level
--    configuration/identity primitives in the same bucket as ORGANISATION_MANAGE
--    (V2) covers - reused rather than adding three near-duplicate *_MANAGE codes.
--    No new INSERT needed: ORGANISATION_MANAGE already exists and now gets applied.
