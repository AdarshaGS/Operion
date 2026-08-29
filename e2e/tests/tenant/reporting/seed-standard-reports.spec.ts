import { expect, test } from "@playwright/test";
import { ReportsPage } from "../../../pages/reporting/ReportsPage";

test("seeding standard reports through the real UI populates the reports list", async ({ page }) => {
	const reports = new ReportsPage(page);
	await reports.open();

	await expect(page.getByText("No reports here yet.")).toBeVisible();
	await reports.seedStandardReports();

	await expect(page.getByText("No reports here yet.")).not.toBeVisible();
	await expect(page.getByRole("table")).toBeVisible();
});
