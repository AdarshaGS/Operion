// Teacher flow: a staff member holding a real, tenant-defined "classroom teacher"
// permission bundle (see fixtures/roles.ts's `teacher` entry) - not a hardcoded role name,
// per #92. Runs unauthenticated (see playwright.config.ts) and logs in through the real
// form as its first step, using the same credentials global-setup.ts claimed this fixture
// with - `tokenFor("teacher")` for the direct-backend checks still reads the storageState
// file global-setup wrote, independent of how this test itself authenticates.
//
// Known gap (see e2e/README.md): TeacherAssignment (api/roles.ts's assignTeacher) records
// who teaches what, but no backend endpoint actually filters attendance/exam/marks data by
// it - any account with ATTENDANCE_MARK/MARKS_ENTER can act on any class, not just an
// assigned one. This flow therefore verifies the real boundary (module-level RBAC) and does
// not assert assignment-scoped data visibility, which isn't backend-enforced today.

import { readFileSync } from "node:fs";
import { join } from "node:path";
import { expect, test } from "@playwright/test";
import { addExamSchedule, createExam } from "../../api/examinations";
import { API_BASE_URL } from "../../api/client";
import { AppLayoutPage } from "../../pages/AppLayoutPage";
import { AttendancePage } from "../../pages/attendance/AttendancePage";
import { LoginPage } from "../../pages/auth/LoginPage";
import { MarksEntryPage } from "../../pages/examinations/MarksEntryPage";
import { tokenFor } from "../../support/auth";
import { trackDiagnostics } from "../../support/diagnostics";
import { AUTH_DIR } from "../../env";
import { FIXED_PASSWORD, type SeedData } from "../../global-setup";

const seed: SeedData = JSON.parse(readFileSync(join(AUTH_DIR, "seed.json"), "utf-8"));

test("Teacher: sees only permitted modules, marks attendance, enters marks, and is blocked from admin/org/pricing surfaces", async ({
	page,
	request,
}) => {
	const diagnostics = trackDiagnostics(page);

	await test.step("1. Log in as Teacher", async () => {
		const login = new LoginPage(page);
		await login.open();
		await login.login(seed.organisationSlug, `teacher@${seed.organisationSlug}.test`, FIXED_PASSWORD);
		await expect(page).toHaveURL(/\/students/); // no ORGANISATION_MANAGE - IndexRedirect sends non-admins here
	});

	await test.step("Only permitted modules are visible/enabled - the rest of the sidebar is genuinely disabled, not just missing a link", async () => {
		const layout = new AppLayoutPage(page);
		await layout.open();

		for (const label of ["Students", "Academics", "Attendance", "Examinations", "Communication"]) {
			expect(await layout.isNavItemEnabled(label), `${label} should be enabled for the teacher bundle`).toBe(true);
		}
		for (const label of ["Dashboard", "Fees", "Transport", "Library", "Inventory", "Purchase", "Sales", "Reports", "HR"]) {
			expect(await layout.isNavItemEnabled(label), `${label} should be disabled for the teacher bundle`).toBe(false);
		}
		expect(await layout.isNavItemEnabled("Settings")).toBe(true); // ungated per AppLayout.tsx

		diagnostics.assertClean("nav check");
	});

	await test.step("Mark attendance for the assigned class through the real UI", async () => {
		// A date distinct from tests/tenant/attendance/mark-attendance.spec.ts's 2026-06-15
		// (same shared org, same section - a shared date would race against that spec).
		const attendance = new AttendancePage(page);
		await attendance.open();
		await attendance.loadRegister(seed.className, seed.sectionName, "2026-06-16");

		await expect(attendance.draftRow("E2E Student")).toBeVisible();
		await attendance.setDraftStatus("E2E Student", "PRESENT");
		await attendance.submitMarks();

		await expect(attendance.registerRow("E2E Student")).toBeVisible();
		await expect(attendance.registerRow("E2E Student")).toContainText("PRESENT");

		diagnostics.assertClean("attendance");
	});

	await test.step("Enter marks for an exam schedule through the real UI", async () => {
		// Exam + schedule creation needs EXAM_MANAGE, which this bundle deliberately doesn't
		// have (only owner creates exams - see tests/tenant/examinations/create-exam.spec.ts) -
		// seeded here as an owner-token precondition so this step can focus on MARKS_ENTER.
		const ownerToken = tokenFor("owner");
		const exam = await createExam(ownerToken, seed.academicYearId, `Teacher Flow Exam ${Date.now()}`, "UNIT_TEST");
		const schedule = await addExamSchedule(ownerToken, exam.id, {
			schoolClassId: seed.classId,
			subjectId: seed.subjectId,
			examDate: "2026-07-01",
			maxMarks: 100,
			passMarks: 35,
		});

		const marksEntry = new MarksEntryPage(page);
		await marksEntry.open(exam.id, schedule.id);
		await marksEntry.enterMarksFor("E2E Student", 88, "Good work");
		await marksEntry.submitMarks();

		await expect(marksEntry.savedMarksRow("E2E Student")).toBeVisible();
		await expect(marksEntry.savedMarksRow("E2E Student")).toContainText("88");

		diagnostics.assertClean("marks entry");
	});

	await test.step("Cannot manage roles, org configuration, or fee pricing - the backend blocks it, not just the UI", async () => {
		const teacherToken = tokenFor("teacher");
		const headers = { Authorization: `Bearer ${teacherToken}` };

		const createRoleResponse = await request.post(`${API_BASE_URL}/api/v1/roles`, {
			headers,
			data: { name: `Should not exist ${Date.now()}`, description: "", permissionCodes: [] },
		});
		expect(createRoleResponse.status()).toBe(403);

		const dashboardResponse = await request.get(`${API_BASE_URL}/api/v1/dashboard/summary`, { headers });
		expect(dashboardResponse.status()).toBe(403); // ORGANISATION_MANAGE-gated - org config/metrics

		const feeStructureResponse = await request.post(`${API_BASE_URL}/api/v1/fees/structures`, {
			headers,
			data: { academicYearId: seed.academicYearId, schoolClassId: seed.classId, feeCategoryId: 1, installments: [] },
		});
		expect(feeStructureResponse.status()).toBe(403); // FEE_STRUCTURE_MANAGE-gated - pricing
	});
});
