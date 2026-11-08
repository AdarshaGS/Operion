import { expect, test } from "@playwright/test";
import {
	admitStudent,
	createAcademicYear,
	createGradeLevel,
	createPerson,
	createSchoolClass,
	createSection,
	enrollStudent,
	listCampuses,
	login,
	markAcademicYearCurrent,
	provisionOrganisation,
} from "../../../api/organisations";
import { StudentPromotionPage } from "../../../pages/students/StudentPromotionPage";

// Own organisation, not the shared global-setup one: this spec adds a second academic year and a grade-2
// class, which other specs that pick "the first academic year / class" in the shared org must not see.
// The browser starts with no stored session; the new org owner's session is injected into localStorage
// before the app loads (same shape as global-setup's tenantStorageState).
test.use({ storageState: { cookies: [], origins: [] } });

test("year-end promotion wizard: preview the plan, decide the exception, apply (Grade 1 -> Grade 2)", async ({ page }) => {
	const run = Date.now();
	const slug = `promo-${run.toString(36)}`;
	const email = `owner@${slug}.test`;
	const password = "E2ePassw0rd!123";

	await provisionOrganisation({
		name: `Promo Org ${run}`,
		legalName: `Promo Org ${run} Pvt Ltd`,
		slug,
		adminEmail: email,
		adminPassword: password,
		adminFirstName: "Owner",
		adminLastName: "Promo",
	});
	const session = await login(slug, email, password);
	const token = session.token;
	const [campus] = await listCampuses(token);

	const sourceYearName = `Promo Source ${run}`;
	const targetYearName = `Promo Target ${run}`;
	const sourceYear = await createAcademicYear(token, sourceYearName, "2026-06-01", "2027-03-31");
	await markAcademicYearCurrent(token, sourceYear.id);
	const targetYear = await createAcademicYear(token, targetYearName, "2027-06-01", "2028-03-31");

	const grade1 = await createGradeLevel(token, "Promo Grade 1", 1, "PRIMARY");
	const grade2 = await createGradeLevel(token, "Promo Grade 2", 2, "PRIMARY");
	const sourceClass = await createSchoolClass(token, sourceYear.id, campus.id, grade1.id, "Promo Grade 1");
	const sourceSection = await createSection(token, sourceClass.id, "A", 40, "Room 1");
	const targetClass = await createSchoolClass(token, targetYear.id, campus.id, grade2.id, "Promo Grade 2");
	await createSection(token, targetClass.id, "A", 40, "Room 2");

	const person = await createPerson(token, { firstName: "Promo", lastName: "Student", dateOfBirth: "2015-04-10", gender: "FEMALE" });
	const student = await admitStudent(token, { personId: person.id, admissionNumber: `PROMO-${run}`, admissionDate: "2026-06-01" });
	await enrollStudent(token, student.id, {
		academicYearId: sourceYear.id,
		sectionId: sourceSection.id,
		rollNumber: 1,
		enrolledDate: "2026-06-01",
	});

	await page.addInitScript(
		(stored) => window.localStorage.setItem("operion.session", stored),
		JSON.stringify({
			token: session.token,
			expiresAt: session.expiresAt,
			userId: session.userId,
			organisationId: session.organisationId,
		}),
	);

	const promotion = new StudentPromotionPage(page);

	await test.step("Years: the current year and the next one are preselected; preview", async () => {
		await promotion.open();
		await expect(promotion.fromYear()).toHaveText(sourceYearName);
		await expect(promotion.toYear()).toHaveText(targetYearName);
		await promotion.preview();
	});

	await test.step("Class plan: Grade 1 moves into the Grade 2 class of the target year", async () => {
		await expect(promotion.classTarget("Promo Grade 1")).toContainText("Promo Grade 2");
		await promotion.continueToStudents();
	});

	await test.step("Students & apply: no published result, but Promote is the default so Apply is ready", async () => {
		await expect(promotion.studentRow("Promo Student")).toContainText("no published final result");
		await expect(promotion.applyButton()).toBeEnabled();

		// The suggested section (same name, "A") is preselected; its remaining capacity shows in the label.
		await expect(promotion.targetSection("Promo Student")).toContainText("A (40 left)");
		await expect(promotion.applyButton()).toBeEnabled();
	});

	await test.step("Apply: one student succeeded, none failed", async () => {
		await promotion.apply();
		await expect(promotion.resultsSummary()).toHaveText("1 succeeded, 0 skipped (already applied), 0 failed.");
		await expect(promotion.studentRow("Promo Student")).toContainText("Done");
	});
});
