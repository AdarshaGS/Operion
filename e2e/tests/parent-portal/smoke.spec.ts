import { expect, test } from "@playwright/test";

test("guardian storageState lands authenticated (no ORGANISATION_MANAGE, so index redirects to /students)", async ({ page }) => {
	await page.goto("/");
	await expect(page).not.toHaveURL(/\/login/);
	await expect(page).toHaveURL(/\/students/);
});
