import { expect, test } from "@playwright/test";
import { PlatformPlansPage } from "../../pages/platform/PlansPage";

test("creating a billing plan through the real platform-admin UI", async ({ page }) => {
	const run = Date.now();
	const code = `UIPLAN${run % 100000}`;

	const plans = new PlatformPlansPage(page);
	await plans.open();
	await plans.addPlan({ code, name: `UI Plan ${run}`, pricePerStudentPerYear: 1500 });

	await expect(plans.planRow(code)).toBeVisible();
	await expect(plans.planRow(code)).toContainText("1,500");
});
