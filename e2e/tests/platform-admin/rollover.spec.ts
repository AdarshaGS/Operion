import { expect, test } from "@playwright/test";
import { api } from "../../api/client";
import {
	createAcademicYear,
	createGradeLevel,
	createSchoolClass,
	createSection,
	listCampuses,
	login,
	markAcademicYearCurrent,
	provisionOrganisation,
} from "../../api/organisations";
import { FRONTEND_BASE_URL } from "../../env";

// Copy-forward: set a new academic year up from the current one. Runs against its own freshly
// provisioned organisation.

interface Report {
	dryRun: boolean;
	steps: { key: string; items: { name: string; copied: number; alreadyThere: number }[] }[];
}

test("copy forward: preview then copy classes into a new year, from the UI, twice safely", async ({ browser }) => {
	const run = Date.now();
	const slug = `ro-${run}`;
	const password = "Rollover!123";
	await provisionOrganisation({
		name: `Rollover Org ${run}`,
		legalName: `Rollover Org ${run} Pvt Ltd`,
		slug,
		adminEmail: `owner@${slug}.test`,
		adminPassword: password,
		adminFirstName: "Roll",
		adminLastName: "Owner",
	});
	const owner = await login(slug, `owner@${slug}.test`, password);
	const [campus] = await listCampuses(owner.token);

	const from = await createAcademicYear(owner.token, "2026-2027", "2026-06-01", "2027-03-31");
	await markAcademicYearCurrent(owner.token, from.id);
	const to = await createAcademicYear(owner.token, "2027-2028", "2027-06-01", "2028-03-31");
	const grade = await createGradeLevel(owner.token, `Grade R${run}`, 1, "PRIMARY");
	const sourceClass = await createSchoolClass(owner.token, from.id, campus.id, grade.id, "Grade R - 2026");
	await createSection(owner.token, sourceClass.id, "A", 40, "101");

	const context = await browser.newContext({
		baseURL: FRONTEND_BASE_URL,
		storageState: {
			cookies: [],
			origins: [
				{
					origin: FRONTEND_BASE_URL,
					localStorage: [
						{
							name: "operion.session",
							value: JSON.stringify({
								token: owner.token,
								expiresAt: owner.expiresAt,
								userId: owner.userId,
								organisationId: owner.organisationId,
							}),
						},
					],
				},
			],
		},
	});
	const page = await context.newPage();
	await page.goto("/settings/academic-years");
	const row = page.getByRole("row", { name: /2027-2028/ });
	await row.getByRole("button", { name: "Copy forward" }).click();
	const dialog = page.getByRole("dialog", { name: /Copy forward into 2027-2028/ });
	await expect(dialog).toBeVisible();

	// Copy is locked until a preview has been seen, and the preview saves nothing.
	await expect(dialog.getByRole("button", { name: "Copy forward" })).toBeDisabled();
	await dialog.getByRole("button", { name: "Preview" }).click();
	await expect(dialog.getByText("What will be copied")).toBeVisible();
	await expect(dialog.getByText(/Classes: 1 to copy/)).toBeVisible();
	const before = await api.get<{ id: number }[]>(`/api/v1/school-classes?academicYearId=${to.id}`, owner.token);
	expect(before).toHaveLength(0);

	// Changing a choice discards the preview.
	await dialog.getByLabel("Teacher assignments").uncheck();
	await expect(dialog.getByText("What will be copied")).toHaveCount(0);
	await expect(dialog.getByRole("button", { name: "Copy forward" })).toBeDisabled();
	await dialog.getByRole("button", { name: "Preview" }).click();
	await dialog.getByRole("button", { name: "Copy forward" }).click();
	await expect(dialog).toBeHidden();
	await expect(page.getByText(/Copied 2026-2027 into 2027-2028/)).toBeVisible();

	const after = await api.get<{ displayName: string }[]>(`/api/v1/school-classes?academicYearId=${to.id}`, owner.token);
	expect(after).toHaveLength(1);

	// Running it again adds nothing.
	const again = await api.post<Report>(
		`/api/v1/academic-years/${to.id}/rollover/preview`,
		{ sourceYearId: from.id, steps: ["CLASSES"], feeIncreasePercent: null },
		owner.token,
	);
	expect(again.steps[0].items.every((item) => item.copied === 0)).toBe(true);
	await context.close();
});
