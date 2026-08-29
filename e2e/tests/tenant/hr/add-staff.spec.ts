import { expect, test } from "@playwright/test";
import { StructureSetupPage } from "../../../pages/setup/StructureSetupPage";
import { StaffCreatePage } from "../../../pages/hr/StaffCreatePage";

test("adding a staff member with an HR profile through the real UI, after creating its designation", async ({ page }) => {
	const run = Date.now();
	const designationName = `UI Designation ${run}`;

	const setup = new StructureSetupPage(page);
	await setup.open();
	await setup.goToStep("Designations");
	await setup.addDesignation(designationName);
	await expect(setup.designationRow(designationName)).toBeVisible();

	const staffCreate = new StaffCreatePage(page);
	await staffCreate.open();
	await staffCreate.fillMemberDetails({ firstName: "UI", lastName: `Staff ${run}`, employeeCode: `EMP-${run}`, joiningDate: "2026-06-01" });
	await staffCreate.fillHrExtension({ designationName });
	await staffCreate.submit();

	await expect(page).toHaveURL(/\/hr\/staff\/\d+$/);
	await expect(page.getByRole("heading", { name: `UI Staff ${run}` })).toBeVisible();
});
