import { expect, test } from "@playwright/test";

test("platform admin storageState lands authenticated on the platform dashboard", async ({ page }) => {
	await page.goto("/platform/dashboard");
	await expect(page).not.toHaveURL(/\/platform\/login/);
	await expect(page.getByRole("heading", { name: /dashboard/i })).toBeVisible();
});
