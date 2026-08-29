// Student/Guardian flow. Operion has no separate "Student" login at all - a
// guardian claims a portal invite (see api/parentPortal.ts) and lands in the same SPA
// under an auto-created "Guardian" role carrying only PARENT_PORTAL_ACCESS (see
// PortalInviteService and fixtures/roles.ts). Runs unauthenticated and logs in through the
// real form as its first step, using the same password the invite was claimed with in
// global-setup.ts - the claim-invite flow itself is covered by tests/parent-portal/smoke.spec.ts.
//
// Known gap (see e2e/README.md): the ticket for this flow asks for a guardian to see their
// linked student's attendance, fee invoices/receipts, exam results, notices, and library
// loans. None of that exists yet - PARENT_PORTAL_ACCESS is the *only* permission the
// Guardian role carries, there is no guardian-scoped "my student" endpoint anywhere in the
// backend (every attendance/fee/exam/library/communication endpoint requires the matching
// staff-facing *_VIEW permission), and the frontend has no purpose-built guardian screens -
// it reuses the same staff AppLayout and routes. This flow therefore verifies the real,
// current boundary itemized against every item the ticket asks for, rather than asserting
// features that don't exist.

import { readFileSync } from "node:fs";
import { join } from "node:path";
import { expect, test } from "@playwright/test";
import { AppLayoutPage } from "../../pages/AppLayoutPage";
import { LoginPage } from "../../pages/auth/LoginPage";
import { trackDiagnostics } from "../../support/diagnostics";
import { verifyFullPageScroll } from "../../support/scroll";
import { AUTH_DIR } from "../../env";
import { FIXED_PASSWORD, type SeedData } from "../../global-setup";

const seed: SeedData = JSON.parse(readFileSync(join(AUTH_DIR, "seed.json"), "utf-8"));

test("Student/Guardian: only ever reaches a blocked landing page - no student, fee, attendance, results, notices, or library data is exposed", async ({
	page,
}) => {
	const diagnostics = trackDiagnostics(page);

	await test.step("1. Log in as Guardian - lands on /students per IndexRedirect, and it is genuinely blocked, not silently empty", async () => {
		const login = new LoginPage(page);
		await login.open();
		await login.login(seed.organisationSlug, `guardian@${seed.organisationSlug}.test`, FIXED_PASSWORD);
		await expect(page).toHaveURL(/\/students/);
		await expect(page.getByRole("alert")).toBeVisible();
		await verifyFullPageScroll(page);
	});

	await test.step("Every module the ticket asks about is unreachable - itemized, not just a generic nav check", async () => {
		const layout = new AppLayoutPage(page);
		await layout.open();

		const shouldBeBlocked: Record<string, string> = {
			Students: "no linked-student view exists - STUDENT_VIEW is required and not granted",
			Attendance: "no guardian-scoped attendance view exists - ATTENDANCE_VIEW is required and not granted",
			Fees: "no invoice/receipt view exists - FEE_VIEW is required and not granted",
			Examinations: "no results view exists - EXAM_VIEW is required and not granted",
			Communication: "no notices view exists - COMMUNICATION_VIEW is required and not granted",
			Library: "no loans view exists - LIBRARY_VIEW is required and not granted",
			HR: "staff administration - never guardian-reachable",
			Dashboard: "org-wide administration - ORGANISATION_MANAGE is required and not granted",
		};
		for (const [label, reason] of Object.entries(shouldBeBlocked)) {
			expect(await layout.isNavItemEnabled(label), `${label} should be blocked: ${reason}`).toBe(false);
		}
		// Settings is the one ungated nav item for everyone (see AppLayout.tsx) - visible,
		// but its content is still whatever the backend allows PARENT_PORTAL_ACCESS to see.
		expect(await layout.isNavItemEnabled("Settings")).toBe(true);

		diagnostics.assertClean("nav check");
	});

	await test.step("Cannot reach platform administration - a different auth plane entirely", async () => {
		await page.goto("/platform/dashboard");
		await expect(page).toHaveURL(/\/platform\/login/);
	});
});
