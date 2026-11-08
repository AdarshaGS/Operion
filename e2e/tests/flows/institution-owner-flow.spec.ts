// Institution Owner flow: the one long "zero to running org" journey that only a fresh,
// dedicated organisation can honestly demonstrate - the shared organisation every other
// project/spec runs against (see global-setup.ts) is already fully seeded by the time any
// test sees it, so it can never show the setup checklist moving from empty to complete.
// This spec therefore provisions its own organisation via the public signup endpoint (no
// UI form exists for that yet - see the comment above provisionOrganisation()) and drives
// everything after that through the real UI in one continuous, real-login session.
//
// The dashboard's "Next setup step" card follows the school lifecycle: 13 steps when every
// module is on, of which 12 are counted ("N of 12 setup tasks complete") - the last one
// (academic year closure) is listed but never required - so the card is expected to stay
// visible for the whole journey below. This flow drives the Institution setup, Academic
// year creation, Team members & roles, Class & section, Subject configuration, Fee
// structure and Student record steps, in the card's own order - fee structure comes before
// the student steps so students know their fees in advance (a student record also satisfies
// Student admissions; see the students step). Screens opened from the card return to
// /dashboard on Back (see the academic year and fee steps).
//
// Scope note: steps 6-7 of the ticket ("primary action + validation for every module")
// are deliberately NOT re-driven exhaustively here - every module already has a real,
// UI-driven creation/primary-workflow spec under tests/tenant/<module>/ against the shared
// org (see e2e/README.md's Coverage section). Duplicating all seventeen of those inside
// this already-long journey would be redundant, not more thorough. What's unique to this
// flow - and therefore what it actually covers - is the onboarding chain itself (the only
// place "does the checklist really move as data is added, in dependency order, starting
// from genuinely nothing" can be proven), plus one natural continuation into Students so
// the chain doesn't just stop at "checklist all green".

import { expect, test } from "@playwright/test";
import { provisionOrganisation } from "../../api/organisations";
import { AppLayoutPage, NAV_ITEMS } from "../../pages/AppLayoutPage";
import { LoginPage } from "../../pages/auth/LoginPage";
import { AcademicYearsPage } from "../../pages/settings/AcademicYearsPage";
import { AcademicsPage } from "../../pages/academics/AcademicsPage";
import { SchoolClassSectionsPage } from "../../pages/academics/SchoolClassSectionsPage";
import { DashboardPage } from "../../pages/dashboard/DashboardPage";
import { FeesPage } from "../../pages/fees/FeesPage";
import { MembersPage } from "../../pages/members/MembersPage";
import { RolesPage } from "../../pages/settings/RolesPage";
import { StructureSetupPage } from "../../pages/setup/StructureSetupPage";
import { StudentCreatePage } from "../../pages/students/StudentCreatePage";
import { StudentDetailPage } from "../../pages/students/StudentDetailPage";
import { StudentListPage } from "../../pages/students/StudentListPage";
import { trackDiagnostics } from "../../support/diagnostics";
import { DESKTOP_VIEWPORT, verifyFullPageScroll } from "../../support/scroll";

test.use({ viewport: DESKTOP_VIEWPORT });

test("Institution Owner: onboarding checklist moves in real dependency order, driven end to end through the real UI", async ({ page }) => {
	const diagnostics = trackDiagnostics(page);
	const run = Date.now();
	const orgSlug = `owner-flow-${run.toString(36)}`;
	const adminEmail = `owner@${orgSlug}.test`;
	const adminPassword = "E2ePassw0rd!123";

	await provisionOrganisation({
		name: `Owner Flow Org ${run}`,
		legalName: `Owner Flow Org ${run} Pvt Ltd`,
		slug: orgSlug,
		adminEmail,
		adminPassword,
		adminFirstName: "Owner",
		adminLastName: "FlowOrg",
	});

	const dashboard = new DashboardPage(page);
	const layout = new AppLayoutPage(page);

	await test.step("1. Log in as Institution Owner", async () => {
		const login = new LoginPage(page);
		await login.open();
		await login.login(orgSlug, adminEmail, adminPassword);
		await expect(page).toHaveURL(/\/dashboard/);
	});

	await test.step("2-3. Dashboard: greeting, setup progress, summary cards, full-page scroll", async () => {
		await expect(dashboard.greeting()).toContainText("Owner");
		await expect(layout.organisationWordmark()).toContainText(`Owner Flow Org ${run}`);

		await expect(dashboard.setupProgressCard()).toBeVisible();
		await expect(dashboard.setupProgressCount()).toContainText("0 of 12"); // genuinely nothing configured yet

		await expect(page.getByText("No recent activity")).toBeVisible();

		// With zero students, the hero stat cards and per-module rollup are collapsed behind
		// a "Summary stats" accordion (#125); the setup guide card above is the one next step.
		// The stats themselves are checked later (5f) once a student actually exists, which
		// is also when this collapsing stops applying.

		// Note (known gap): the campus/academic-year pills in the top bar are pure display
		// context today - ContextSelectors.tsx is explicit that nothing downstream is
		// filtered by them yet, so "selector changes visible page data" isn't asserted here.
		await expect(page.getByLabel("Campus")).toContainText("Main Campus");

		await verifyFullPageScroll(page);
		diagnostics.assertClean("dashboard");
	});

	const academicYearName = `${run} Academic Year`;
	await test.step("4a. Institution setup (setup step 1): linear wizard - profile, business, campuses; no academic year in it; checklist advances to 1 of 12", async () => {
		await dashboard.goToSetupStep("Institution setup");
		await expect(page).toHaveURL(/\/setup\/structure/);
		await expect(page.getByRole("heading", { name: "Structure setup" })).toBeVisible();

		const structure = new StructureSetupPage(page);
		// Organisation profile opens directly on step 0 - clear the required "Organisation
		// name" field and confirm the browser's own required-field validation blocks it,
		// same mechanism MUI TextField `required` relies on everywhere in this app.
		const orgNameField = page.getByLabel("Organisation name");
		await orgNameField.fill("");
		await page.getByRole("button", { name: "Save" }).click();
		expect(await orgNameField.evaluate((el: HTMLInputElement) => el.validity.valueMissing)).toBe(true);
		await orgNameField.fill(`Owner Flow Org ${run}`);

		// Linear flow: later steps stay locked until the required ones before them are done.
		await expect(structure.stepTab("Campuses")).toBeDisabled();
		await expect(page.getByRole("tab", { name: "Review and finish" })).toBeDisabled();
		// Academic years are managed on their own page, not in this wizard.
		await expect(structure.stepTab("Academic year")).toHaveCount(0);

		// Saving the profile advances to Business settings.
		await structure.fillOrganisationProfile({ contactName: "Owner FlowOrg", contactEmail: adminEmail });
		await expect(page.getByRole("heading", { name: "Business settings" })).toBeVisible();
		await structure.saveBusinessSettings();

		// Campuses are optional - the auto-created Main Campus is listed and we move on.
		// Scope to the table cell: the top-bar Campus pill also shows "Main Campus".
		await expect(page.getByRole("cell", { name: "Main Campus" })).toBeVisible();
		await structure.continue();

		await expect(structure.finishButton()).toBeEnabled();
		await structure.finishSetup();
		await expect(page).toHaveURL(/\/dashboard/);

		await dashboard.open();
		await expect(dashboard.setupProgressCount()).toContainText("1 of 12");
		diagnostics.assertClean("structure step");
	});

	await test.step("4a-2. Academic year creation (setup step 2): create a year on /settings/academic-years, a non-current year does not count, marking it current does; Back returns to the dashboard; checklist advances to 2 of 12", async () => {
		await dashboard.goToSetupStep("Academic year creation");
		await expect(page).toHaveURL(/\/settings\/academic-years/);

		const years = new AcademicYearsPage(page);
		await years.addAcademicYear({ name: academicYearName, startDate: "2026-06-01", endDate: "2027-03-31" });
		await expect(years.yearRow(academicYearName)).toBeVisible();

		// A year that isn't current doesn't satisfy the step.
		await dashboard.open();
		await expect(dashboard.setupProgressCount()).toContainText("1 of 12");

		await dashboard.goToSetupStep("Academic year creation");
		await expect(page).toHaveURL(/\/settings\/academic-years/);
		await years.markCurrent(academicYearName);
		await expect(years.yearRow(academicYearName).getByText("Current", { exact: true })).toBeVisible();

		// Opened from the dashboard card, Back goes to the dashboard (not Settings).
		await years.backToDashboard();
		await expect(page).toHaveURL(/\/dashboard/);

		await dashboard.open();
		await expect(dashboard.setupProgressCount()).toContainText("2 of 12");
		diagnostics.assertClean("academic year step");
	});

	await test.step("4b. Roles (reached directly - only inviting a member counts toward Team members & roles): validation, save 5 roles; checklist stays at 2 of 12", async () => {
		await page.goto("/settings/roles");
		await expect(page).toHaveURL(/\/settings\/roles/);

		const roles = new RolesPage(page);
		await page.getByRole("button", { name: "Add role" }).click();
		await page.getByRole("button", { name: "Add", exact: true }).click(); // empty submit
		expect(await page.getByLabel("Name").evaluate((el: HTMLInputElement) => el.validity.valueMissing)).toBe(true);
		await page.keyboard.press("Escape");

		const roleFixtures: { module: string; code: string }[] = [
			{ module: "student", code: "STUDENT_VIEW" },
			{ module: "attendance", code: "ATTENDANCE_VIEW" },
			{ module: "fees", code: "FEE_VIEW" },
			{ module: "library", code: "LIBRARY_VIEW" },
			{ module: "reporting", code: "REPORT_VIEW" },
		];
		for (const [index, fixture] of roleFixtures.entries()) {
			const name = `Owner Flow Role ${index + 1} ${run}`;
			await roles.addRole({ name, description: "Created by institution-owner-flow", module: fixture.module, permissionCode: fixture.code });
			await expect(roles.roleRow(name)).toBeVisible();
		}

		await dashboard.open();
		await expect(dashboard.setupProgressCount()).toContainText("2 of 12"); // creating roles does not complete Team members & roles
		diagnostics.assertClean("roles step");
	});

	const memberEmail = `invited-${run}@${orgSlug}.test`;
	await test.step("4c. Team members & roles (setup step 3): add a member with a password; checklist advances to 3 of 12", async () => {
		await page.goto("/members");
		await expect(page).toHaveURL(/\/members/);

		const members = new MembersPage(page);
		await members.addMember({ firstName: "Invited", lastName: "Member", email: memberEmail, password: "OwnerFlowPassw0rd!1", roleName: `Owner Flow Role 1 ${run}` });
		await expect(page.getByText("Invited Member")).toBeVisible();

		await dashboard.open();
		await expect(dashboard.setupProgressCount()).toContainText("3 of 12");
		diagnostics.assertClean("members step");
	});

	const gradeLevelName = "Grade 1";
	const subjectName = "Mathematics";
	const className = "Grade 1 - A";
	const sectionName = "A";
	await test.step("4d. Class & section creation: grade level + subject, then a class alone, do NOT flip it; a section does", async () => {
		await dashboard.goToSetupStep("Class & section creation");
		await expect(page).toHaveURL(/\/academics\/setup/);

		const academics = new AcademicsPage(page);
		await academics.addGradeLevel({ name: gradeLevelName, sequenceOrder: 1, stage: "PRIMARY" });
		await expect(academics.gradeLevelRow(gradeLevelName)).toBeVisible();
		await academics.addSubject({ name: subjectName, code: `MATH${run}` });
		await expect(academics.subjectRow(subjectName)).toBeVisible();

		// Regression check: a step cannot be marked complete if required data is missing -
		// class & section creation needs a class AND a section. Grade level + subject
		// alone must not flip it.
		await dashboard.open();
		await expect(dashboard.setupProgressCount()).toContainText("3 of 12");

		await academics.open();
		await academics.addClass({ academicYearName, campusName: "Main Campus", gradeLevelName, displayName: className });
		await expect(academics.classRow(className)).toBeVisible();

		// A class without any section still isn't "classes and sections created".
		await dashboard.open();
		await expect(dashboard.setupProgressCount()).toContainText("3 of 12");

		await academics.open();
		await academics.openClass(className);
		const sections = new SchoolClassSectionsPage(page);
		await sections.addSection({ name: sectionName, capacity: 40, room: "Room 1" });
		await expect(sections.sectionRow(sectionName)).toBeVisible();

		await dashboard.open();
		await expect(dashboard.setupProgressCount()).toContainText("4 of 12");
		diagnostics.assertClean("academic setup step");
	});

	await test.step("4d-2. Subject configuration: map a subject onto the (only) class of the current year, checklist advances to 5 of 12", async () => {
		await dashboard.goToSetupStep("Subject configuration");
		await expect(page).toHaveURL(/\/academics$/);

		const academics = new AcademicsPage(page);
		await academics.openClass(className);
		const classPage = new SchoolClassSectionsPage(page);
		await classPage.assignSubject({ subjectName, weeklyPeriods: 5 });
		await expect(classPage.classSubjectRow(subjectName)).toBeVisible();

		await dashboard.open();
		await expect(dashboard.setupProgressCount()).toContainText("5 of 12");
		diagnostics.assertClean("subject configuration step");
	});

	await test.step("4e. Fee structure configuration (before the student steps, so students know their fees in advance): configure a category + structure; Back returns to the dashboard; checklist advances to 6 of 12", async () => {
		await dashboard.goToSetupStep("Fee structure configuration");
		await expect(page).toHaveURL(/\/fees\/setup/);

		const fees = new FeesPage(page);
		const categoryName = `Tuition ${run}`;
		await fees.addCategory({ code: `TUI${run}`, name: categoryName });
		await expect(fees.categoryRow(`TUI${run}`)).toBeVisible();

		await fees.selectStructureScope(academicYearName, className);
		await fees.setUpStructure(`${className} Fees ${academicYearName}`);
		await fees.addStructure({ categoryName, amount: 12000, installmentDueDate: "2026-07-01" });
		await expect(fees.structureRow(categoryName)).toBeVisible();

		// Opened from the dashboard card, Back goes to the dashboard (not fee collection).
		await page.getByRole("button", { name: "Back to dashboard" }).click();
		await expect(page).toHaveURL(/\/dashboard/);

		await dashboard.open();
		await expect(dashboard.setupProgressCount()).toContainText("6 of 12");
		diagnostics.assertClean("fees step");
	});

	const admissionNumber = `OWN-${run}`;
	await test.step("4f. Student record management: validation, admit, view - checklist advances to 8 of 12 (a student record also satisfies Student admissions)", async () => {
		await dashboard.goToSetupStep("Student record management");
		await expect(page).toHaveURL(/\/students/);

		const studentList = new StudentListPage(page);
		await studentList.admitStudent();
		await expect(page).toHaveURL(/\/students\/new/);

		const create = new StudentCreatePage(page);
		await page.getByRole("button", { name: "Admit student" }).click(); // empty submit
		expect(await page.getByLabel("First name").evaluate((el: HTMLInputElement) => el.validity.valueMissing)).toBe(true);

		await create.fill({ firstName: "Ada", lastName: "FlowStudent", admissionNumber, admissionDate: "2026-06-01" });
		await create.submit();

		const detail = new StudentDetailPage(page);
		await detail.expectStudentIdInUrl();
		await expect(detail.studentName("Ada FlowStudent")).toBeVisible();
		await expect(detail.admissionNumber(admissionNumber)).toBeVisible();
		await expect(detail.statusChip("ADMITTED")).toBeVisible();
		// Known gap: StudentDetailPage has no edit affordance yet - "view" is exercised
		// above, "edit" is not built (see e2e/README.md).

		// Student.status only moves ADMITTED -> ACTIVE via enrollment (Student.java). The
		// lifecycle card's Student record step (studentRecordsExist) counts any student record, and
		// Student admissions (admissionsCompleted) is satisfied by one too, so the card moves on admit.
		// Enrolling here keeps the legacy studentsAdded flag and the dashboard hero stats
		// (checked in 5f) honest.
		await detail.enroll({ academicYearName, className, sectionName, rollNumber: 1, enrolledDate: "2026-06-01" });
		await expect(detail.statusChip("ACTIVE")).toBeVisible();
		await expect(detail.enrolledBanner()).toBeVisible();

		await dashboard.open();
		// Attendance, exam, marks and library are covered by their own tests/tenant/<module>
		// specs, so the card is deliberately still open here - it only unmounts once all 12
		// counted steps are done (year closure is never required).
		await expect(dashboard.setupProgressCard()).toBeVisible();
		await expect(dashboard.setupProgressCount()).toContainText("8 of 12");
		diagnostics.assertClean("students step");
	});

	await test.step("5f. Dashboard summary cards: with a student now enrolled, the hero stats and per-module rollup render directly (no longer collapsed behind the empty-state accordion)", async () => {
		await expect(dashboard.statTile("Active students")).toBeVisible();
		await expect(dashboard.statTile("Fees due")).toBeVisible();
		for (const section of ["Attendance today", "Fees", "Staff", "Examinations", "Library", "Transport", "Inventory", "Communication"]) {
			await expect(dashboard.sectionHeading(section)).toBeVisible();
		}
		await verifyFullPageScroll(page);
		diagnostics.assertClean("dashboard summary cards");
	});

	await test.step("6. Sidebar navigation is enabled for every entitled module (Owner bypasses all permission checks)", async () => {
		for (const item of NAV_ITEMS) {
			expect(await layout.isNavItemEnabled(item.label), `${item.label} should be enabled for Owner`).toBe(true);
		}
	});

	await test.step("9. Institution Owner cannot access Platform Administration - a different auth plane entirely", async () => {
		await page.goto("/platform/dashboard");
		await expect(page).toHaveURL(/\/platform\/login/);
	});
});
